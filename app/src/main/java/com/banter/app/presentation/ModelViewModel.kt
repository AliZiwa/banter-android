package com.banter.app.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banter.app.domain.EngineRepository
import com.banter.app.domain.EngineState
import com.banter.app.domain.InstalledModel
import com.banter.app.domain.ModelRepository
import com.banter.app.domain.Transfer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Contract -----------------------------------------------------------------------------

data class ModelState(
    val installed: InstalledModel? = null,
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

sealed interface ModelIntent {
    data object Refresh : ModelIntent
    data class Import(val uri: Uri, val displayName: String) : ModelIntent
    data class Download(val url: String, val token: String) : ModelIntent
    data object Cancel : ModelIntent
    data object Delete : ModelIntent
    data object DismissError : ModelIntent
}

// --- ViewModel ----------------------------------------------------------------------------

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val models: ModelRepository,
    private val engines: EngineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ModelState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var transfer: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            engines.state.collect { engine -> _state.update { it.copy(engine = engine) } }
        }
    }

    fun onIntent(intent: ModelIntent) {
        when (intent) {
            ModelIntent.Refresh -> refresh()
            is ModelIntent.Import -> start(models.import(intent.uri.toString(), intent.displayName))
            is ModelIntent.Download ->
                start(models.download(intent.url.trim(), intent.token.takeIf { it.isNotBlank() }))
            ModelIntent.Cancel -> {
                transfer?.cancel()
                transfer = null
                _state.update { it.copy(progress = null) }
            }
            ModelIntent.Delete -> viewModelScope.launch {
                models.delete()
                engines.sync()
                refresh()
            }
            ModelIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun refresh() = _state.update {
        it.copy(
            installed = models.installed(),
            freeBytes = models.freeBytes(),
            unmetered = models.isUnmetered(),
        )
    }

    private fun start(flow: Flow<Transfer>) {
        if (transfer?.isActive == true) return
        _state.update { it.copy(progress = ModelState.Progress(0, -1), error = null) }
        transfer = viewModelScope.launch {
            try {
                flow.collect { step ->
                    when (step) {
                        is Transfer.Running ->
                            _state.update { it.copy(progress = ModelState.Progress(step.bytes, step.total)) }

                        is Transfer.Done -> {
                            _state.update { it.copy(progress = null) }
                            refresh()
                            // Loading can take several seconds; the engine state drives the UI.
                            engines.sync()
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.update { it.copy(progress = null, error = error.message ?: "The transfer failed.") }
            } finally {
                transfer = null
            }
        }
    }
}
