package com.banter.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One fictional member of the group chat.
 *
 * [blurb] is who they are ("a barber who gives advice nobody asked for"), [quirk] is how they
 * talk, and [secret] is a private truth handed only to this character. The secret is what makes
 * a scenario interesting: give all three of them "you are dating Stella" and none of them knows
 * the others are, so the chat discovers it on its own.
 */
@Serializable
data class Character(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val blurb: String,
    val quirk: String = "",
    val secret: String = "",
) {
    val initials: String
        get() = name.trim().split(" ").filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

/** The cast plus the world they share. */
@Serializable
data class Scenario(
    val title: String,
    val sharedHistory: String,
    val cast: List<Character>,
    /**
     * What the characters call the person holding the phone.
     *
     * This must not be "You". The transcript is fed to the model as plain lines like
     * `Grace: ...`, and a line reading `You: where is Stella?` is read by the model as a
     * statement about *itself* — which is exactly how a group chat starts answering the wrong
     * person. A real name keeps the user a third party the characters can address.
     */
    val userName: String = "Guest",
) {
    fun characterById(id: String): Character? = cast.firstOrNull { it.id == id }
}

/** A line in the transcript. [speakerId] is null for the phone's owner. */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val speakerId: String?,
    val speakerName: String,
    val text: String,
    val at: Long = System.currentTimeMillis(),
    val streaming: Boolean = false,
) {
    val isUser: Boolean get() = speakerId == null

    companion object {
        /** Label for the user's own bubbles in the UI. Prompts use [Scenario.userName]. */
        const val USER_NAME = "You"
    }
}
