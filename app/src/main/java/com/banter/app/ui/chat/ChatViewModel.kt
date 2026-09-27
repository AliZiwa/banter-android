package com.banter.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banter.app.AppContainer
import com.banter.app.data.Character
import com.banter.app.data.ChatMessage
import com.banter.app.data.Scenario
import com.banter.app.agent.CharacterAgents
import com.banter.app.director.Director
import com.banter.app.llm.EngineState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val scenario: Scenario,
    val messages: List<ChatMessage> = emptyList(),
    val engine: EngineState = EngineState.NoModel,
    val autoChat: Boolean = true,
    val typing: String? = null,
    val error: String? = null,
) {
    /** Without a loaded model there is nobody to talk to, so the composer is closed. */
    val canChat: Boolean get() = engine is EngineState.Ready
}

/**
 * Holds the transcript and runs the director.
 *
 * The loop is started and stopped by the screen's lifecycle, not by the ViewModel, so the
 * characters fall silent the moment the chat is not being looked at.
 */
class ChatViewModel(private val container: AppContainer) : ViewModel(), Director.Stage {

    private val _state = MutableStateFlow(ChatUiState(scenario = container.store.scenario.value))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val agents = CharacterAgents(engine = { container.engineManager.engine })
    private val director = Director(voices = agents)
    private var loop: Job? = null

    override val transcript: List<ChatMessage> get() = _state.value.messages

    init {
        viewModelScope.launch {
            _state.update { it.copy(messages = container.store.loadTranscript()) }
        }
        viewModelScope.launch {
            container.store.scenario.collect { scenario ->
                _state.update { it.copy(scenario = scenario) }
            }
        }
        viewModelScope.launch {
            container.engineManager.state.collect { engine ->
                _state.update { it.copy(engine = engine) }
            }
        }
        viewModelScope.launch { container.engineManager.sync() }
    }

    // --- Lifecycle, driven by the chat screen -------------------------------------------------

    fun onScreenVisible() {
        if (_state.value.autoChat) startLoop()
    }

    fun onScreenHidden() {
        stopLoop()
    }

    fun setAutoChat(enabled: Boolean) {
        _state.update { it.copy(autoChat = enabled) }
        if (enabled) startLoop() else stopLoop()
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = viewModelScope.launch {
            director.run(scenario = { _state.value.scenario }, stage = this@ChatViewModel)
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
        clearStreaming()
    }

    // --- User actions -------------------------------------------------------------------------

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        appendAndPersist(
            ChatMessage(speakerId = null, speakerName = ChatMessage.USER_NAME, text = trimmed),
        )
        director.onUserMessage()
    }

    fun clearChat() {
        stopLoop()
        _state.update { it.copy(messages = emptyList()) }
        viewModelScope.launch {
            container.store.clearTranscript()
            if (_state.value.autoChat) startLoop()
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Called after the cast is edited, so a stale identity never keeps talking. */
    fun onScenarioEdited() {
        stopLoop()
        // An agent's persona is fixed for its lifetime, so edited profiles need new agents.
        agents.close()
        if (_state.value.autoChat) startLoop()
    }

    // --- Director.Stage -----------------------------------------------------------------------

    override fun beginTurn(speaker: Character): String {
        val placeholder = ChatMessage(
            speakerId = speaker.id,
            speakerName = speaker.name,
            text = "",
            streaming = true,
        )
        _state.update { it.copy(messages = it.messages + placeholder, typing = speaker.name) }
        return placeholder.id
    }

    override fun endTurn(id: String, text: String?) {
        _state.update { current ->
            val messages = if (text.isNullOrBlank()) {
                current.messages.filterNot { it.id == id }
            } else {
                current.messages.map {
                    if (it.id == id) it.copy(text = text, streaming = false) else it
                }
            }
            current.copy(messages = messages, typing = null)
        }
        persist()
    }

    override fun onError(error: Throwable) {
        _state.update { it.copy(error = error.message ?: "The model stopped responding.") }
    }

    // --- Plumbing -----------------------------------------------------------------------------

    private fun clearStreaming() {
        _state.update { current ->
            current.copy(messages = current.messages.filterNot { it.streaming }, typing = null)
        }
    }

    private fun appendAndPersist(message: ChatMessage) {
        _state.update { it.copy(messages = it.messages + message) }
        persist()
    }

    private fun persist() {
        val snapshot = _state.value.messages.filterNot { it.streaming }
        viewModelScope.launch { container.store.saveTranscript(snapshot) }
    }

    override fun onCleared() {
        stopLoop()
        agents.close()
        super.onCleared()
    }
}
