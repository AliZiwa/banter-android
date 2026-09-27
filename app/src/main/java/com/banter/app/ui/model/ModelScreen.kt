package com.banter.app.ui.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banter.app.llm.EngineState
import com.banter.app.llm.ModelStore

/**
 * Model management, honest about the awkward part: the Gemma builds are licence-gated, so a
 * plain download often fails with a 401. Importing a file you already have is the path that
 * works, and it is offered first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    state: ModelUiState,
    onImport: (Uri, String) -> Unit,
    onDownload: (String, String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(ModelStore.SUGGESTED_URL) }
    var token by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { onImport(it, context.displayNameOf(it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to the chat")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state)

            state.progress?.let { progress ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Copying the model", fontWeight = FontWeight.SemiBold)
                        if (progress.fraction != null) {
                            LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Text(
                            text = "${ModelStore.format(progress.bytes)} of " +
                                ModelStore.format(progress.total),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
            }

            if (state.progress == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Import a .litertlm file", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Download the model on a computer, move it to the phone, " +
                                "then pick it here. No data bundle spent twice.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose file") }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Or download it here", fontWeight = FontWeight.SemiBold)
                        if (!state.unmetered) {
                            Text(
                                text = "You are not on Wi-Fi. This file is about 2.6 GB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Model URL") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Hugging Face token (gated models)") },
                            supportingText = {
                                Text("Accept the Gemma licence on Hugging Face, then paste a read token.")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { onDownload(url, token) },
                            enabled = url.isNotBlank(),
                        ) { Text("Download") }
                    }
                }
            }

            if (state.installedName != null && state.progress == null) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete the model and free ${ModelStore.format(state.installedBytes)}")
                }
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: ModelUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = when (val engine = state.engine) {
                    EngineState.NoModel -> "No model installed"
                    EngineState.Loading -> "Loading the model…"
                    EngineState.Idle -> "Model installed, not loaded"
                    is EngineState.Ready -> "Ready: ${engine.label}"
                    is EngineState.Failed -> "Model failed to load"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            (state.engine as? EngineState.Failed)?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall)
            }
            state.installedName?.let {
                Text(
                    text = "$it · ${ModelStore.format(state.installedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${ModelStore.format(state.freeBytes)} free",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (state.unmetered) "On Wi-Fi" else "On mobile data",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun Context.displayNameOf(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
        }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "model.litertlm"
}
