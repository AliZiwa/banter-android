package com.banter.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Scenario + transcript persistence. Two small JSON files in the app's private storage;
 * a chat app this size does not need a database.
 */
class BanterStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val scenarioFile = File(context.filesDir, "scenario.json")
    private val transcriptFile = File(context.filesDir, "transcript.json")

    private val _scenario = MutableStateFlow(readScenario())
    val scenario: StateFlow<Scenario> = _scenario.asStateFlow()

    private fun readScenario(): Scenario =
        runCatching { json.decodeFromString<Scenario>(scenarioFile.readText()) }
            .getOrElse { Presets.stella }

    suspend fun saveScenario(scenario: Scenario) {
        _scenario.value = scenario
        withContext(Dispatchers.IO) {
            runCatching { scenarioFile.writeText(json.encodeToString(scenario)) }
        }
    }

    suspend fun loadTranscript(): List<ChatMessage> = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<List<ChatMessage>>(transcriptFile.readText()) }
            .getOrElse { emptyList() }
            // A half-written streaming message from a previous run is not worth keeping.
            .filterNot { it.streaming }
    }

    suspend fun saveTranscript(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        runCatching {
            transcriptFile.writeText(json.encodeToString(messages.takeLast(MAX_PERSISTED)))
        }
        Unit
    }

    suspend fun clearTranscript() = withContext(Dispatchers.IO) {
        runCatching { transcriptFile.delete() }
        Unit
    }

    private companion object {
        const val MAX_PERSISTED = 200
    }
}
