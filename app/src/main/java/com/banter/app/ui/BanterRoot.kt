package com.banter.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.banter.app.AppContainer
import com.banter.app.ui.cast.CastScreen
import com.banter.app.ui.chat.ChatScreen
import com.banter.app.ui.chat.ChatViewModel
import com.banter.app.ui.model.ModelScreen
import com.banter.app.ui.model.ModelViewModel
import kotlinx.coroutines.launch

private enum class Screen { Chat, Cast, Model }

@Composable
fun BanterRoot(container: AppContainer) {
    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }
    val scope = rememberCoroutineScope()

    val chatViewModel: ChatViewModel = viewModel(
        factory = viewModelFactory { initializer { ChatViewModel(container) } },
    )
    val modelViewModel: ModelViewModel = viewModel(
        factory = viewModelFactory { initializer { ModelViewModel(container) } },
    )

    BackHandler(enabled = screen != Screen.Chat) { screen = Screen.Chat }

    when (screen) {
        Screen.Chat -> {
            val state by chatViewModel.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                onSend = chatViewModel::send,
                onToggleAutoChat = chatViewModel::setAutoChat,
                onScreenVisible = chatViewModel::onScreenVisible,
                onScreenHidden = chatViewModel::onScreenHidden,
                onDismissError = chatViewModel::dismissError,
                onOpenCast = { screen = Screen.Cast },
                onOpenModel = { screen = Screen.Model },
            )
        }

        Screen.Cast -> {
            val scenario by container.store.scenario.collectAsStateWithLifecycle()
            CastScreen(
                scenario = scenario,
                onSave = { updated -> scope.launch { container.store.saveScenario(updated) } },
                onBack = {
                    chatViewModel.onScenarioEdited()
                    screen = Screen.Chat
                },
            )
        }

        Screen.Model -> {
            val state by modelViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { modelViewModel.refresh() }
            ModelScreen(
                state = state,
                onImport = modelViewModel::import,
                onDownload = modelViewModel::download,
                onCancel = modelViewModel::cancel,
                onDelete = modelViewModel::delete,
                onBack = { screen = Screen.Chat },
            )
        }
    }
}
