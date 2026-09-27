package com.banter.app.director

import com.banter.app.data.Character
import com.banter.app.data.ChatMessage
import com.banter.app.data.Scenario

/** Builds the character's identity, and the couple of lines they are replying to. */
object PromptBuilder {

    /** How much of the chat a character sees. Deliberately tiny for this POC. */
    const val CONTEXT_MESSAGES = 2

    fun system(speaker: Character, scenario: Scenario): String = buildString {
        val user = scenario.promptUserName()
        val others = scenario.cast.filter { it.id != speaker.id }.joinToString(", ") { it.name }

        appendLine("You are ${speaker.name}, ${speaker.blurb}.")
        if (speaker.quirk.isNotBlank()) appendLine(speaker.quirk)
        appendLine()

        appendLine("You are in a group chat with: ${others.ifBlank { "nobody yet" }}.")
        appendLine("$user is also here. $user is a real person, not one of the friends.")
        if (scenario.sharedHistory.isNotBlank()) {
            appendLine("What you all share: ${scenario.sharedHistory}")
        }
        if (speaker.secret.isNotBlank()) {
            appendLine("Private, known only to you: ${speaker.secret}")
        }
        appendLine()

        appendLine("Reply as ${speaker.name} to the last message. Rules:")
        appendLine("- One short message, under 25 words. Nothing else.")
        appendLine("- Answer what was actually said. Plain everyday speech, no poetry.")
        appendLine("- Never write anyone else's line, and never start with \"${speaker.name}:\".")
        appendLine("- No asterisks, no stage directions, no quotes around the message.")
    }.trim()

    /** The last couple of messages, then whose turn it is. */
    fun turn(speaker: Character, scenario: Scenario, transcript: List<ChatMessage>): String =
        buildString {
            val user = scenario.promptUserName()
            val said = transcript.filter { it.text.isNotBlank() }
            said.takeLast(CONTEXT_MESSAGES)
                .forEach { appendLine("${if (it.isUser) user else it.speakerName}: ${it.text}") }

            appendLine()
            if (said.lastOrNull()?.isUser == true) {
                // The human spoke. That is the whole job of this turn, said in one line, last —
                // the position a small model weights most.
                appendLine("Answer $user's last message directly.")
            } else {
                // Idle chat between characters is where repetition creeps in: with two lines
                // of context a character cannot know it has said this before. The reminder is
                // kept out of turns that answer the user, where it was found to pull the model
                // back onto its own old topic instead.
                said.lastOrNull { it.speakerId == speaker.id }?.let { own ->
                    appendLine("(${speaker.name} already said \"${own.text}\". Say something new.)")
                }
            }
            append("${speaker.name}:")
        }

    /**
     * Guards against a blank or second-person name reaching the prompt: a line reading
     * "You: where is Stella?" is read by the model as a statement about itself.
     */
    private fun Scenario.promptUserName(): String {
        val trimmed = userName.trim()
        return if (trimmed.isBlank() || trimmed.equals("you", true)) "Guest" else trimmed
    }
}
