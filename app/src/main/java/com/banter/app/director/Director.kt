package com.banter.app.director

import com.banter.app.data.Character
import com.banter.app.data.ChatMessage
import com.banter.app.data.Scenario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The turn loop.
 *
 * You start the conversation; a character answers. If you say nothing for ten to fifteen
 * seconds, one of the others picks up the last message and carries it on. That is the whole
 * behaviour.
 *
 * It runs only while the chat screen is on screen: generation is the most expensive thing the
 * phone does here.
 */
class Director(
    private val voices: Voices,
    private val random: Random = Random.Default,
) {

    /** The screen talks to the loop through this; the loop never touches Compose state itself. */
    interface Stage {
        val transcript: List<ChatMessage>

        /** Shows a "typing" bubble and returns its id. */
        fun beginTurn(speaker: Character): String

        /**
         * Replaces the typing bubble with the finished message, or removes it when [text] is
         * null. Text is only ever shown once final: streaming into a bubble that might then be
         * dropped looks like the character typing and erasing itself.
         */
        fun endTurn(id: String, text: String?)

        fun onError(error: Throwable)
    }

    private val nudges = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var turn: Job? = null

    /** Set when the next turn is answering something you just typed. */
    @Volatile
    private var answeringUser = false

    /**
     * You have typed something. Whatever is being written right now was a reply to the previous
     * message, so it is abandoned and the next turn answers you instead.
     */
    fun onUserMessage() {
        answeringUser = true
        turn?.cancel()
        nudges.trySend(Unit)
    }

    suspend fun run(scenario: () -> Scenario, stage: Stage) = coroutineScope {
        while (isActive) {
            // Nobody speaks into an empty room, and nobody speaks without a model.
            if (!voices.ready || stage.transcript.none { it.isUser }) {
                waitForUser(QUIET_MS)
                continue
            }

            val current = scenario()
            val speaker = SpeakerPicker.next(current.cast, stage.transcript, random)
            if (speaker == null) {
                delay(QUIET_MS)
                continue
            }

            // A beat before anyone starts typing. Replying the instant you hit send reads as a
            // machine answering, not a person noticing.
            if (answeringUser) {
                answeringUser = false
                delay(random.nextLong(READ_MIN_MS, READ_MAX_MS))
            }

            // Clear any signal left over from the message we are about to answer, so the gap
            // after this reply is a real gap rather than one skipped by a stale nudge.
            while (nudges.tryReceive().isSuccess) Unit

            // A child job, so cancelling an abandoned turn does not tear down the loop.
            val work = launch { takeTurn(current, speaker, stage) }
            turn = work
            try {
                work.join()
            } finally {
                turn = null
            }
            ensureActive()

            // Your turn. If you say nothing, somebody picks the thread back up.
            waitForUser(random.nextLong(IDLE_MIN_MS, IDLE_MAX_MS))
        }
    }

    private suspend fun takeTurn(scenario: Scenario, speaker: Character, stage: Stage) {
        val names = scenario.cast.map { it.name } + scenario.userName + ChatMessage.USER_NAME

        val id = stage.beginTurn(speaker)
        val raw: String
        try {
            raw = voices.reply(speaker, scenario, stage.transcript)
        } catch (cancellation: CancellationException) {
            stage.endTurn(id, null)
            throw cancellation
        } catch (error: Throwable) {
            stage.endTurn(id, null)
            stage.onError(error)
            delay(ERROR_BACKOFF_MS)
            return
        }
        val cleaned = ReplyCleaner.clean(raw, speaker.name, names)
        val ownLast = stage.transcript.lastOrNull { it.speakerId == speaker.id && it.id != id }?.text
        stage.endTurn(id, cleaned?.takeUnless { it.equals(ownLast, ignoreCase = true) })
    }

    /** Waits out the gap, returning early the moment you send something. */
    private suspend fun waitForUser(millis: Long) {
        withTimeoutOrNull(millis) { nudges.receive() }
    }

    private companion object {
        /** How long somebody takes to notice your message before they start typing. */
        const val READ_MIN_MS = 2_500L
        const val READ_MAX_MS = 3_500L

        /** The pause after a reply before somebody carries the conversation on. */
        const val IDLE_MIN_MS = 10_000L
        const val IDLE_MAX_MS = 15_000L

        const val QUIET_MS = 1_000L
        const val ERROR_BACKOFF_MS = 3_000L
    }
}
