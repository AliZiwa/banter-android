package com.banter.app.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.banter.app.data.ChatMessage
import com.banter.app.data.Scenario
import com.banter.app.llm.EngineState
import com.banter.app.ui.theme.SpeakerColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onToggleAutoChat: (Boolean) -> Unit,
    onScreenVisible: () -> Unit,
    onScreenHidden: () -> Unit,
    onDismissError: () -> Unit,
    onOpenCast: () -> Unit,
    onOpenModel: () -> Unit,
) {
    // The characters only talk while somebody is watching. This is the battery rule from the
    // article, expressed in the one place that can actually enforce it.
    LifecycleResumeEffect(Unit) {
        onScreenVisible()
        onPauseOrDispose { onScreenHidden() }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            onDismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.scenario.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = state.typing?.let { "$it is typing…" }
                                ?: state.scenario.cast.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleAutoChat(!state.autoChat) }) {
                        Icon(
                            imageVector = if (state.autoChat) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.autoChat) "Pause the chat" else "Resume the chat",
                        )
                    }
                    IconButton(onClick = onOpenCast) {
                        Icon(Icons.Default.Group, contentDescription = "Edit the cast")
                    }
                    IconButton(onClick = onOpenModel) {
                        Icon(Icons.Default.Memory, contentDescription = "Model settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            EngineBanner(state.engine, onOpenModel)
            Transcript(
                messages = state.messages,
                scenario = state.scenario,
                hasModel = state.canChat,
                modifier = Modifier.weight(1f),
            )
            Composer(enabled = state.canChat, onSend = onSend)
        }
    }
}

@Composable
private fun EngineBanner(engine: EngineState, onOpenModel: () -> Unit) {
    val (text, tone) = when (engine) {
        EngineState.NoModel ->
            "No model installed. Add one to start the chat." to MaterialTheme.colorScheme.secondaryContainer
        EngineState.Loading ->
            "Waking the model up… this takes a few seconds." to MaterialTheme.colorScheme.secondaryContainer
        EngineState.Idle ->
            "Model unloaded." to MaterialTheme.colorScheme.surfaceVariant
        is EngineState.Ready -> return
        is EngineState.Failed ->
            "Model failed: ${engine.message}" to MaterialTheme.colorScheme.errorContainer
    }
    Surface(color = tone, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (engine is EngineState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (engine !is EngineState.Loading) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onOpenModel),
                )
            }
        }
    }
}

@Composable
private fun Transcript(
    messages: List<ChatMessage>,
    scenario: Scenario,
    hasModel: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    if (messages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (hasModel) {
                    "Say something to start.\nThey will take it from there."
                } else {
                    "No model on this phone yet.\nAdd one and they can talk."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(0.6f),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            Bubble(message = message, color = scenario.colorFor(message.speakerId))
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, color: Color) {
    val mine = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message.speakerName.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.size(8.dp))
        }

        Surface(
            color = if (mine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!mine) {
                    Text(
                        text = message.speakerName,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (message.text.isBlank() && message.streaming) {
                    TypingDots()
                } else {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun Composer(enabled: Boolean, onSend: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (draft.isNotBlank()) {
            onSend(draft)
            draft = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Say something…") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        IconButton(
            onClick = submit,
            enabled = enabled && draft.isNotBlank(),
        ) {
            Icon(Icons.Default.Send, contentDescription = "Send")
        }
    }
}

private fun Scenario.colorFor(speakerId: String?): Color {
    if (speakerId == null) return SpeakerColors.first()
    val index = cast.indexOfFirst { it.id == speakerId }
    return SpeakerColors[(if (index >= 0) index else 0) % SpeakerColors.size]
}
