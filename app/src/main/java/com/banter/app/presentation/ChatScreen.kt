package com.banter.app.presentation

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.banter.app.domain.Character
import com.banter.app.domain.ChatMessage
import com.banter.app.domain.EngineState
import com.banter.app.domain.Scenario
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    effects: Flow<ChatEffect>,
    onIntent: (ChatIntent) -> Unit,
    onBack: () -> Unit,
    onOpenCast: () -> Unit,
    onOpenModel: () -> Unit,
) {
    // The characters only talk while somebody is watching. This is the battery rule from the
    // article, expressed in the one place that can actually enforce it.
    LifecycleResumeEffect(Unit) {
        onIntent(ChatIntent.ScreenVisible)
        onPauseOrDispose { onIntent(ChatIntent.ScreenHidden) }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ChatEffect.ShowError -> snackbar.showSnackbar(effect.message)
            }
        }
    }

    val tint = state.scenario.tint
    var draft by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(screenBackground(tint)),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text(
                        text = "${state.scenario.emoji} ${state.scenario.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back to the groups", tint = tint)
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(ChatIntent.SetAutoChat(!state.autoChat)) }) {
                        Icon(
                            imageVector = if (state.autoChat) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.autoChat) "Pause the chat" else "Resume the chat",
                            tint = tint,
                        )
                    }
                    IconButton(onClick = onOpenCast) {
                        Icon(Icons.Default.Group, contentDescription = "Edit the cast", tint = tint)
                    }
                    IconButton(onClick = onOpenModel) {
                        Icon(Icons.Default.Memory, contentDescription = "Model settings", tint = tint)
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
            if (state.messages.isEmpty() && state.typing == null) {
                EmptyState(
                    scenario = state.scenario,
                    hasModel = state.canChat,
                    onSuggest = { draft = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Transcript(
                    messages = state.messages,
                    typing = state.typing,
                    scenario = state.scenario,
                    modifier = Modifier.weight(1f),
                )
            }
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                enabled = state.canChat,
                tint = tint,
                onSend = {
                    val text = draft
                    draft = ""
                    onIntent(ChatIntent.Send(text))
                },
            )
        }
    }
}

/** The accent, washed out, fading into the plain background by the middle of the screen. */
@Composable
private fun screenBackground(tint: Color): Brush {
    val base = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(
        0f to tint.copy(alpha = 0.14f).compositeOver(base),
        0.5f to base,
        1f to base,
    )
}

@Composable
private fun EngineBanner(engine: EngineState, onOpenModel: () -> Unit) {
    val (text, tone) = when (engine) {
        EngineState.NoModel ->
            "No model installed. Add one to start the chat." to MaterialTheme.colorScheme.surface
        EngineState.Loading ->
            "Waking the model up… this takes a few seconds." to MaterialTheme.colorScheme.surface
        EngineState.Idle ->
            "Model unloaded." to MaterialTheme.colorScheme.surfaceVariant
        is EngineState.Ready -> return
        is EngineState.Failed ->
            "Model failed: ${engine.message}" to MaterialTheme.colorScheme.errorContainer
    }
    Surface(color = tone.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (engine is EngineState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (engine !is EngineState.Loading) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenModel),
                )
            }
        }
    }
}

/** Before the first message: who is here, and three ways to start. */
@Composable
private fun EmptyState(
    scenario: Scenario,
    hasModel: Boolean,
    onSuggest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = scenario.tint
    val names = scenario.cast.joinToString(", ") { it.name }
    val openers = scenario.openers.map(String::trim).filter(String::isNotEmpty)
        .ifEmpty { listOf("Hello everyone", "What did I miss?", "Who is around?") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = scenario.emoji, fontSize = 72.sp)
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Say something in ${scenario.title}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = if (hasModel) {
                "$names are here. Everything runs on your phone."
            } else {
                "$names are here, but there is no model on this phone yet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(20.dp))
        openers.forEach { line ->
            FilledTonalButton(
                onClick = { onSuggest(line) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = tint.copy(alpha = 0.16f),
                    contentColor = tint,
                ),
                modifier = Modifier.padding(vertical = 2.dp),
            ) { Text(line) }
        }
    }
}

@Composable
private fun Transcript(
    messages: List<ChatMessage>,
    typing: Character?,
    scenario: Scenario,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val count = messages.size + (if (typing != null) 1 else 0)
    LaunchedEffect(count) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            Bubble(
                mine = message.isUser,
                speakerName = message.speakerName,
                speakerColor = scenario.colorFor(message.speakerId),
                tint = scenario.tint,
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        typing?.let { speaker ->
            item(key = "typing") {
                Bubble(
                    mine = false,
                    speakerName = speaker.name,
                    speakerColor = scenario.colorFor(speaker.id),
                    tint = scenario.tint,
                ) { TypingDots() }
            }
        }
    }
}

/**
 * A chat bubble. The user sits on the right in the group's colour, a character on the left
 * with a coloured avatar and their name above the text.
 */
@Composable
private fun Bubble(
    mine: Boolean,
    speakerName: String,
    speakerColor: Color,
    tint: Color,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mine) {
            Spacer(Modifier.weight(1f, fill = true).widthIn(min = 48.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(speakerColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = speakerName.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Surface(
            color = if (mine) tint else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!mine) {
                    Text(
                        text = speakerName,
                        color = speakerColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(2.dp))
                }
                content()
            }
        }

        if (!mine) Spacer(Modifier.width(24.dp))
    }
}

/** Shown while a character is thinking. Three dots that pulse in turn. */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 6.dp)) {
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
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

/** A capsule to type in and a round arrow to send, the same shape as the iOS composer. */
@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    enabled: Boolean,
    tint: Color,
    onSend: () -> Unit,
) {
    val canSend = enabled && draft.isNotBlank()
    val submit = { if (canSend) onSend() }
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say something…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = tint,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            IconButton(onClick = submit, enabled = canSend, modifier = Modifier.alpha(if (canSend) 1f else 0.4f)) {
                Icon(
                    imageVector = Icons.Default.ArrowCircleUp,
                    contentDescription = "Send",
                    tint = tint,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private fun Scenario.colorFor(speakerId: String?): Color {
    if (speakerId == null) return tint
    val index = cast.indexOfFirst { it.id == speakerId }
    return SpeakerColors[(if (index >= 0) index else 0) % SpeakerColors.size]
}
