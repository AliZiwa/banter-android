package com.banter.app.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Owns the one loaded engine for the whole process.
 *
 * A language model is the most expensive thing this app will ever hold, so it is loaded once,
 * shared, and dropped deliberately. There is no stand-in when no model is installed: a fake
 * voice reading from a list of canned lines is not a chat, and pretending otherwise only hides
 * the fact that the model is missing.
 */
class EngineManager(
    private val context: Context,
    private val modelStore: ModelStore,
) {

    private val _state = MutableStateFlow<EngineState>(EngineState.NoModel)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val lock = Mutex()

    @Volatile
    private var loaded: LiteRtChatEngine? = null

    @Volatile
    private var loadedFrom: File? = null

    /** The engine to talk to, or null when there is nothing to talk to. */
    val engine: ChatEngine?
        get() = loaded?.takeIf { it.isLoaded }

    /**
     * Brings the engine in line with what is on disk: loads a newly installed model, drops one
     * that was deleted, and does nothing if the right model is already resident.
     */
    suspend fun sync() = lock.withLock {
        val file = modelStore.installed()

        if (file == null) {
            release()
            _state.value = EngineState.NoModel
            return@withLock
        }
        if (loaded?.isLoaded == true && loadedFrom?.absolutePath == file.absolutePath) {
            return@withLock
        }

        release()
        _state.value = EngineState.Loading
        val candidate = LiteRtChatEngine(modelFile = file, cacheDir = context.cacheDir)
        try {
            candidate.load()
            loaded = candidate
            loadedFrom = file
            _state.value = EngineState.Ready(candidate.label)
        } catch (t: Throwable) {
            Log.e(TAG, "Loading ${file.name} failed", t)
            runCatching { candidate.close() }
            _state.value = EngineState.Failed(
                t.message ?: "This phone could not load ${file.name}.",
            )
        }
    }

    /** Frees the model's memory. Called when the user leaves the chat for good. */
    suspend fun unload() = lock.withLock {
        release()
        _state.value =
            if (modelStore.installed() == null) EngineState.NoModel else EngineState.Idle
    }

    private fun release() {
        loaded?.let { runCatching { it.close() } }
        loaded = null
        loadedFrom = null
    }

    private companion object {
        const val TAG = "EngineManager"
    }
}
