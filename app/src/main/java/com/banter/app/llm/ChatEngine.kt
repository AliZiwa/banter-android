package com.banter.app.llm

import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/** One request for one character's next line. */
data class ReplySpec(
    val speaker: String,
    val system: String,
    val prompt: String,
    val maxOutputTokens: Int = 64,
    val temperature: Double = 0.8,
    val topK: Int = 40,
    val topP: Double = 0.9,
    val seed: Int = Random.nextInt(),
    /** Keeps a small model from locking onto a phrase and repeating it. */
    val repetitionPenalty: Float = 1.1f,
    val presencePenalty: Float = 0.4f,
    val frequencyPenalty: Float = 0.3f,
    val noRepeatNgram: Int = 4,
)

/**
 * Anything that can write the next chat line.
 *
 * [reply] emits cumulatively — each emission is the whole message so far.
 */
interface ChatEngine : AutoCloseable {
    /** Short human label for the settings screen, e.g. "gemma-4-E2B-it.litertlm (CPU)". */
    val label: String

    val isLoaded: Boolean

    suspend fun load()

    fun reply(spec: ReplySpec): Flow<String>
}

sealed interface EngineState {
    /** No model installed. There is no fallback: without a model there is no conversation. */
    data object NoModel : EngineState

    /** A model is installed but not currently in memory. */
    data object Idle : EngineState

    data object Loading : EngineState
    data class Ready(val label: String) : EngineState
    data class Failed(val message: String) : EngineState
}
