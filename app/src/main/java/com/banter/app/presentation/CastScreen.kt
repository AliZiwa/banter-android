package com.banter.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banter.app.domain.Character
import com.banter.app.domain.Presets
import com.banter.app.domain.Scenario

/**
 * Where the user invents the group: who is in it, what they all share, and what each of them is
 * privately hiding. Everything on this screen ends up inside a prompt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastScreen(
    scenario: Scenario,
    onSave: (Scenario) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(scenario) { mutableStateOf(scenario) }

    fun update(block: (Scenario) -> Scenario) {
        draft = block(draft)
        onSave(draft)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The cast") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to the chat")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    update { it.copy(cast = it.cast + Character(name = "New friend", blurb = "")) }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.all.forEach { preset ->
                        AssistChip(
                            onClick = { update { preset } },
                            label = { Text(preset.title) },
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.emoji,
                        onValueChange = { value -> update { it.copy(emoji = value.take(2)) } },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(88.dp),
                    )
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { value -> update { it.copy(title = value) } },
                        label = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                // The colour the whole chat takes on. Same five as the iOS app.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Colour", style = MaterialTheme.typography.labelLarge)
                    AccentPalette.forEachIndexed { index, color ->
                        val selected = draft.tint == color
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .padding(4.dp)
                                .background(color, CircleShape)
                                .clickable { update { it.copy(accent = index) } },
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = draft.openers.joinToString("\n"),
                    onValueChange = { value ->
                        // Kept raw so a newline or trailing space survives typing; the chat trims.
                        update { it.copy(openers = value.split('\n')) }
                    },
                    label = { Text("Opening lines") },
                    supportingText = { Text("One per line. Shown as suggestions before the first message.") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = draft.userName,
                    onValueChange = { value -> update { it.copy(userName = value) } },
                    label = { Text("What they call you") },
                    supportingText = {
                        Text("Use a real name. Calling yourself \"You\" confuses the model into answering itself.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = draft.sharedHistory,
                    onValueChange = { value -> update { it.copy(sharedHistory = value) } },
                    label = { Text("What they all share") },
                    supportingText = { Text("Everyone is told this. School, a village, an old argument.") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            itemsIndexed(draft.cast, key = { _, member -> member.id }) { index, member ->
                CharacterCard(
                    character = member,
                    onChange = { changed ->
                        update { current ->
                            current.copy(
                                cast = current.cast.toMutableList()
                                    .also { it[index] = changed },
                            )
                        }
                    },
                    onDelete = {
                        update { current ->
                            current.copy(cast = current.cast.filterNot { it.id == member.id })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: Character,
    onChange: (Character) -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = character.name.ifBlank { "Unnamed" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove ${character.name}")
                }
            }
            HorizontalDivider()

            OutlinedTextField(
                value = character.name,
                onValueChange = { onChange(character.copy(name = it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = character.blurb,
                onValueChange = { onChange(character.copy(blurb = it)) },
                label = { Text("Who they are") },
                placeholder = { Text("a barber who gives advice nobody asked for") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = character.quirk,
                onValueChange = { onChange(character.copy(quirk = it)) },
                label = { Text("How they talk") },
                placeholder = { Text("Confident one-liners. Compares everything to haircuts.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = character.secret,
                onValueChange = { onChange(character.copy(secret = it)) },
                label = { Text("Private, only for them") },
                supportingText = {
                    Text("Give several characters the same secret and let them find out.")
                },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
