package com.banter.app.domain

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
)

/** The cast plus the world they share. */
@Serializable
data class Scenario(
    val title: String,
    val sharedHistory: String,
    val cast: List<Character>,
    /** One emoji that stands for the group on the home screen and in the chat title. */
    val emoji: String = "💬",
    /** Index into the presentation layer's accent palette. The whole chat takes this colour. */
    val accent: Int = 0,
    /** Ready-made first lines shown on an empty chat, so the first tap is easy. */
    val openers: List<String> = emptyList(),
    /**
     * What the characters call the person holding the phone.
     *
     * This must not be "You". The transcript is fed to the model as plain lines like
     * `Grace: ...`, and a line reading `You: where is Stella?` is read by the model as a
     * statement about *itself* — which is exactly how a group chat starts answering the wrong
     * person. A real name keeps the user a third party the characters can address.
     */
    val userName: String = "Guest",
)

/** A line in the transcript. [speakerId] is null for the phone's owner. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val speakerId: String?,
    val speakerName: String,
    val text: String,
    val at: Long = System.currentTimeMillis(),
) {
    val isUser: Boolean get() = speakerId == null

    companion object {
        /** Label for the user's own bubbles in the UI. Prompts use [Scenario.userName]. */
        const val USER_NAME = "You"
    }
}

sealed interface EngineState {
    /** No model installed. There is no fallback: without a model there is no conversation. */
    data object NoModel : EngineState

    /** A model is installed but not currently in memory. */
    data object Idle : EngineState

    data object Loading : EngineState
    data class Ready(val label: String) : EngineState
    data class Failed(val message: String) : EngineState
}

/** The one model file on the device. */
data class InstalledModel(val name: String, val bytes: Long, val path: String)

/** Progress of a model import or download. [total] is -1 when unknown. */
sealed interface Transfer {
    data class Running(val bytes: Long, val total: Long) : Transfer
    data class Done(val model: InstalledModel) : Transfer
}

/**
 * Google's own Gemma 4 E2B conversion: ungated, instruction-tuned, and the model that turned
 * this chat from surreal filler into people talking. 2.59 GB.
 */
const val SUGGESTED_MODEL_URL =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/" +
        "gemma-4-E2B-it.litertlm"

/** Ready-made casts, so the first run has something to say. */
object Presets {

    val stella = Scenario(
        title = "The Stella Problem",
        sharedHistory = "You all went to St. Jude Primary School in Kampala together, years " +
            "ago. You still argue about who was better at football and who copied whose homework.",
        emoji = "💔",
        accent = 4,
        openers = listOf("Who here knows Stella?", "Musa, how is business?", "Pastor, did you watch the match?"),
        cast = listOf(
            Character(
                name = "Musa",
                blurb = "a barber who gives advice nobody asked for",
                quirk = "Talks in confident one-liners. Compares everything to haircuts.",
                secret = "You are dating a lady called Stella. You have never mentioned her " +
                    "to this group and you have no idea anyone else here knows her.",
            ),
            Character(
                name = "Grace",
                blurb = "a primary school teacher who corrects everyone's English",
                quirk = "Polite, but cannot resist fixing grammar and spelling mid-conversation.",
                secret = "You are dating a lady called Stella. You have never mentioned her " +
                    "to this group and you have no idea anyone else here knows her.",
            ),
            Character(
                name = "Pastor Ben",
                blurb = "a pastor who takes football far too seriously",
                quirk = "Slips scripture and football punditry into the same sentence.",
                secret = "You are dating a lady called Stella. You have never mentioned her " +
                    "to this group and you have no idea anyone else here knows her.",
            ),
        ),
    )

    val reunion = Scenario(
        title = "The Class Reunion",
        sharedHistory = "You are planning the St. Jude Primary School reunion in Kampala. " +
            "Money is in Ugandan shillings. Nobody wants to pay for the hall and everyone " +
            "claims they are 'handling transport'.",
        emoji = "🎓",
        accent = 0,
        openers = listOf("Who has paid for the hall?", "Kato, where are you?", "Nakato, what is the budget?"),
        cast = listOf(
            Character(
                name = "Auntie Rose",
                blurb = "an auntie who runs three businesses and one very long WhatsApp status",
                quirk = "Types like she is dictating. Volunteers other people for work.",
                secret = "You already booked the hall and you intend to claim the credit loudly.",
            ),
            Character(
                name = "Kato",
                blurb = "a taxi driver who knows a shortcut for everything",
                quirk = "Turns every topic into traffic. Always five minutes away.",
                secret = "You have not saved a single shilling for the reunion and you are stalling.",
            ),
            Character(
                name = "Nakato",
                blurb = "an accountant who keeps receipts, literally",
                quirk = "Quotes exact numbers. Deeply suspicious of round figures.",
                secret = "You suspect somebody is inflating the budget and you are quietly collecting proof.",
            ),
        ),
    )

    val all = listOf(stella, reunion)
}
