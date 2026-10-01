package com.banter.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The contracts the domain needs from the outside world. The data layer implements them; the
 * presentation layer only ever sees these.
 */

/** The cast and their world. One scenario at a time. */
interface ScenarioRepository {
    val scenario: Flow<Scenario>
    suspend fun save(scenario: Scenario)
}

/** The transcript, oldest first. */
interface TranscriptRepository {
    val messages: Flow<List<ChatMessage>>
    suspend fun append(message: ChatMessage)
    suspend fun clear()
}

/** Getting the model file onto the device, and knowing whether there is room for it. */
interface ModelRepository {
    fun installed(): InstalledModel?
    fun freeBytes(): Long

    /** True on Wi-Fi or any other connection the user is not paying per megabyte for. */
    fun isUnmetered(): Boolean
    suspend fun delete(): Boolean

    /** Copies a file the user picked with the system picker. [uri] is a content: URI string. */
    fun import(uri: String, displayName: String): Flow<Transfer>

    /** Direct download. Gated repositories need [token] (a Hugging Face read token). */
    fun download(url: String, token: String?): Flow<Transfer>
}

/** The lifecycle of the one loaded model. */
interface EngineRepository {
    val state: StateFlow<EngineState>

    /** Loads a newly installed model, drops a deleted one, does nothing if already in step. */
    suspend fun sync()

    /** Frees the model's memory. */
    suspend fun unload()
}

/**
 * Whoever can speak as a character.
 *
 * The director does not care that there is an agent framework behind this, and the tests do
 * not need a model to exercise the turn loop.
 */
interface Voices {
    /** False when no model is loaded; nobody can talk. */
    val ready: Boolean

    /** The raw line this character says next, before cleaning. */
    suspend fun reply(character: Character, scenario: Scenario, recent: List<ChatMessage>): String
}
