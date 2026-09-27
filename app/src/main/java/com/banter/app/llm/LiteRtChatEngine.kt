package com.banter.app.llm

import android.util.Log
import com.banter.app.BuildConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.NoRepeatNgramConfig
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Runs a .litertlm model on the device through LiteRT-LM.
 *
 * Loading the model is expensive (seconds, and most of the file stays resident), so one
 * [Engine] is created and kept. Generating is CPU-bound and blocking, so every call is pinned
 * to a single background thread and serialised — two characters must never talk through the
 * runtime at once.
 *
 * Each reply is its own short-lived conversation. The prompt carries the little context this
 * app needs, so there is nothing worth keeping between turns.
 */
class LiteRtChatEngine(
    private val modelFile: File,
    private val cacheDir: File,
    private val backend: BackendChoice = BackendChoice.CPU,
    private val contextTokens: Int = DEFAULT_CONTEXT_TOKENS,
) : ChatEngine {

    enum class BackendChoice { CPU, GPU }

    private val inference: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "banter-inference") }
            .asCoroutineDispatcher()

    private val lock = Mutex()

    @Volatile
    private var engine: Engine? = null

    override val label: String
        get() = "${modelFile.name} (${backend.name})"

    override val isLoaded: Boolean
        get() = engine != null

    override suspend fun load() {
        if (engine != null) return
        withContext(inference) {
            lock.withLock {
                if (engine != null) return@withLock
                val created = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = when (backend) {
                            BackendChoice.CPU -> Backend.CPU(
                                threadCount = Runtime.getRuntime().availableProcessors()
                                    .coerceIn(2, 4),
                            )
                            BackendChoice.GPU -> Backend.GPU()
                        },
                        maxNumTokens = contextTokens,
                        cacheDir = cacheDir.absolutePath,
                    ),
                )
                created.initialize()
                engine = created
            }
        }
    }

    override fun reply(spec: ReplySpec): Flow<String> = flow {
        lock.withLock {
            val active = engine ?: error("Model is not loaded")
            val conversation = active.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(spec.system),
                    samplerConfig = SamplerConfig(
                        topK = spec.topK,
                        topP = spec.topP,
                        temperature = spec.temperature,
                        seed = spec.seed,
                    ),
                    maxOutputToken = spec.maxOutputTokens,
                ),
            )
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "prompt[${spec.speaker}]:\n${spec.prompt}")

                var text = ""
                // Positional: (prompt, extraContext, repetitionPenalty, noRepeatNgram).
                conversation.sendMessageAsync(
                    spec.prompt,
                    emptyMap(),
                    RepetitionPenaltyConfig(
                        spec.repetitionPenalty,
                        spec.presencePenalty,
                        spec.frequencyPenalty,
                        REPETITION_WINDOW,
                    ),
                    NoRepeatNgramConfig(spec.noRepeatNgram, REPETITION_WINDOW),
                ).collect { message ->
                    val piece = message.plainText()
                    if (piece.isEmpty()) return@collect
                    // Across LiteRT-LM versions this stream has carried both incremental deltas
                    // and cumulative snapshots. Detect which instead of assuming.
                    text = if (piece.length >= text.length && piece.startsWith(text)) {
                        piece
                    } else {
                        text + piece
                    }
                    emit(text)
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "raw[${spec.speaker}]: $text")
            } finally {
                runCatching { conversation.cancelProcess() }
                runCatching { conversation.close() }
            }
        }
    }.flowOn(inference)

    override fun close() {
        val current = engine ?: return
        engine = null
        runCatching { current.close() }
            .onFailure { Log.w(TAG, "Closing engine failed", it) }
        (inference as? AutoCloseable)?.let { runCatching { it.close() } }
    }

    private companion object {
        const val TAG = "LiteRtChatEngine"
        const val DEFAULT_CONTEXT_TOKENS = 2048
        const val REPETITION_WINDOW = 512

        /** Flattens a [Message] down to the text parts; Banter is text-only. */
        fun Message.plainText(): String =
            contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
    }
}
