package com.banter.app.ui.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banter.app.AppContainer
import com.banter.app.llm.EngineState
import com.banter.app.llm.ModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelUiState(
    val installedName: String? = null,
    val installedBytes: Long = 0,
    val freeBytes: Long = 0,
    val unmetered: Boolean = false,
    val engine: EngineState = EngineState.NoModel,
    val progress: Progress? = null,
    val error: String? = null,
) {
    data class Progress(val bytes: Long, val total: Long) {
        val fraction: Float? = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    }
}

class ModelViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ModelUiState())
    val state: StateFlow<ModelUiState> = _state.asStateFlow()

    private var transfer: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            container.engineManager.state.collect { engine ->
                _state.update { it.copy(engine = engine) }
            }
        }
    }

    fun refresh() {
        val installed = container.modelStore.installed()
        _state.update {
            it.copy(
                installedName = installed?.name,
                installedBytes = installed?.length() ?: 0,
                freeBytes = container.modelStore.freeBytes(),
                unmetered = container.modelStore.isUnmetered(),
            )
        }
    }

    fun import(uri: Uri, displayName: String) =
        start(container.modelStore.importFrom(uri, displayName))

    fun download(url: String, token: String) =
        start(container.modelStore.download(url.trim(), token.takeIf { it.isNotBlank() }))

    fun cancel() {
        transfer?.cancel()
        transfer = null
        _state.update { it.copy(progress = null) }
    }

    fun delete() {
        viewModelScope.launch {
            container.modelStore.delete()
            container.engineManager.sync()
            refresh()
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun start(flow: Flow<ModelStore.Transfer>) {
        if (transfer?.isActive == true) return
        _state.update { it.copy(progress = ModelUiState.Progress(0, -1), error = null) }
        transfer = viewModelScope.launch {
            try {
                flow.collect { step ->
                    when (step) {
                        is ModelStore.Transfer.Running ->
                            _state.update {
                                it.copy(progress = ModelUiState.Progress(step.bytes, step.total))
                            }

                        is ModelStore.Transfer.Done -> {
                            _state.update { it.copy(progress = null) }
                            refresh()
                            // Loading can take several seconds; the engine state drives the UI.
                            container.engineManager.sync()
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.update {
                    it.copy(progress = null, error = error.message ?: "The transfer failed.")
                }
            } finally {
                transfer = null
            }
        }
    }
}
