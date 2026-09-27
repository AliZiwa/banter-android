package com.banter.app.director

import com.banter.app.data.Character
import com.banter.app.data.ChatMessage
import com.banter.app.data.Scenario

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
