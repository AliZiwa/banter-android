package com.banter.app.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Screen { Home, Chat, Cast, Model }

/** Four screens and no navigation library: a `when` is the whole router. */
@Composable
fun BanterRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    val chat: ChatViewModel = hiltViewModel()
    val model: ModelViewModel = hiltViewModel()

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) {
            Screen.Cast, Screen.Model -> Screen.Chat
            else -> Screen.Home
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val from = if (forward) 1 else -1
            (slideInHorizontally { it / 4 * from } + fadeIn())
                .togetherWith(slideOutHorizontally { -it / 4 * from } + fadeOut())
        },
        label = "screen",
    ) { current ->
        when (current) {
            Screen.Home -> {
                val state by chat.state.collectAsStateWithLifecycle()
                HomeScreen(
                    saved = state.scenario,
                    engine = state.engine,
                    onStart = { scenario ->
                        chat.onIntent(ChatIntent.StartScenario(scenario))
                        screen = Screen.Chat
                    },
                    onCustom = { screen = Screen.Cast },
                    onOpenModel = { screen = Screen.Model },
                )
            }

            Screen.Chat -> {
                val state by chat.state.collectAsStateWithLifecycle()
                ChatScreen(
                    state = state,
                    effects = chat.effects,
                    onIntent = chat::onIntent,
                    onBack = { screen = Screen.Home },
                    onOpenCast = { screen = Screen.Cast },
                    onOpenModel = { screen = Screen.Model },
                )
            }

            Screen.Cast -> {
                val state by chat.state.collectAsStateWithLifecycle()
                CastScreen(
                    scenario = state.scenario,
                    onSave = { chat.onIntent(ChatIntent.SaveScenario(it)) },
                    onBack = {
                        chat.onIntent(ChatIntent.CastClosed)
                        screen = Screen.Chat
                    },
                )
            }

            Screen.Model -> {
                val state by model.state.collectAsStateWithLifecycle()
                ModelScreen(
                    state = state,
                    onIntent = model::onIntent,
                    onBack = { screen = Screen.Chat },
                )
            }
        }
    }
}
