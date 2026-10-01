package com.banter.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banter.app.domain.Character
import com.banter.app.domain.ChatMessage
import com.banter.app.domain.Director
import com.banter.app.domain.EngineRepository
import com.banter.app.domain.EngineState
import com.banter.app.domain.Presets
import com.banter.app.domain.Scenario
import com.banter.app.domain.ScenarioRepository
import com.banter.app.domain.TranscriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- Contract -----------------------------------------------------------------------------

data class ChatState(
    val scenario: Scenario = Presets.stella,
    val messages: List<ChatMessage> = emptyList(),
    val engine: EngineState = EngineState.NoModel,
    val autoChat: Boolean = true,
    val typing: Character? = null,
) {
    /** Without a loaded model there is nobody to talk to, so the composer is closed. */
    val canChat: Boolean get() = engine is EngineState.Ready
}

sealed interface ChatIntent {
    data class Send(val text: String) : ChatIntent
    data class SetAutoChat(val enabled: Boolean) : ChatIntent
    data object ScreenVisible : ChatIntent
    data object ScreenHidden : ChatIntent
    data class SaveScenario(val scenario: Scenario) : ChatIntent

    /** Picked from the home screen: this group, a fresh transcript, and nobody mid-sentence. */
    data class StartScenario(val scenario: Scenario) : ChatIntent
    data object CastClosed : ChatIntent
}

sealed interface ChatEffect {
    data class ShowError(val message: String) : ChatEffect
}

// --- ViewModel ----------------------------------------------------------------------------

/**
 * Reduces repositories plus a little screen-only state into one [ChatState], and runs the
 * director while the screen is visible.
 *
 * The loop is started and stopped by the screen's lifecycle, not by the ViewModel, so the
 * characters fall silent the moment the chat is not being looked at.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val scenarios: ScenarioRepository,
    private val transcripts: TranscriptRepository,
    engines: EngineRepository,
    private val director: Director,
) : ViewModel(), Director.Stage {

    /** The part of the state that belongs to this screen alone. */
    private data class Local(val autoChat: Boolean = true, val typing: Character? = null)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ChatState> = combine(
        scenarios.scenario,
        transcripts.messages,
        engines.state,
        local,
    ) { scenario, messages, engine, screen ->
        ChatState(scenario, messages, engine, screen.autoChat, screen.typing)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatState())

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects: Flow<ChatEffect> = _effects.receiveAsFlow()

    private var loop: Job? = null

    init {
        viewModelScope.launch { engines.sync() }
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Send -> send(intent.text)
            is ChatIntent.SetAutoChat -> {
                local.update { it.copy(autoChat = intent.enabled) }
                if (intent.enabled) startLoop() else stopLoop()
            }
            ChatIntent.ScreenVisible -> if (state.value.autoChat) startLoop()
            ChatIntent.ScreenHidden -> stopLoop()
            is ChatIntent.SaveScenario -> viewModelScope.launch { scenarios.save(intent.scenario) }
            is ChatIntent.StartScenario -> viewModelScope.launch {
                director.onCastEdited()
                if (intent.scenario.title != state.value.scenario.title) transcripts.clear()
                scenarios.save(intent.scenario)
            }
            ChatIntent.CastClosed -> director.onCastEdited()
        }
    }

    private fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            transcripts.append(
                ChatMessage(speakerId = null, speakerName = ChatMessage.USER_NAME, text = trimmed),
            )
            director.onUserMessage()
        }
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = viewModelScope.launch {
            director.run(scenario = { state.value.scenario }, stage = this@ChatViewModel)
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
        typing(null)
    }

    // --- Director.Stage -----------------------------------------------------------------------

    override val transcript: List<ChatMessage> get() = state.value.messages

    override fun typing(speaker: Character?) = local.update { it.copy(typing = speaker) }

    override suspend fun say(speaker: Character, text: String) =
        transcripts.append(ChatMessage(speakerId = speaker.id, speakerName = speaker.name, text = text))

    override fun onError(error: Throwable) {
        _effects.trySend(ChatEffect.ShowError(error.message ?: "The model stopped responding."))
    }

    override fun onCleared() {
        stopLoop()
        super.onCleared()
    }
}
