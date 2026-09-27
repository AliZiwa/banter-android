package com.banter.app.director

import com.banter.app.data.Character
import com.banter.app.data.ChatMessage
import com.banter.app.data.Scenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class DirectorTest {

    private val musa = Character(id = "m", name = "Musa", blurb = "barber")
    private val grace = Character(id = "g", name = "Grace", blurb = "teacher")
    private val scenario = Scenario(
        title = "Test",
        sharedHistory = "school",
        cast = listOf(musa, grace),
        userName = "Ali",
    )

    /** Stands in for the Koog agents so the turn loop can be tested without a model. */
    private class FakeVoices(
        private val takes: Long = 0,
        override val ready: Boolean = true,
        /** Mimics a model stuck on one phrase. */
        private val stuck: Boolean = false,
    ) : Voices {
        val prompts = mutableListOf<String>()
        private var counter = 0

        override suspend fun reply(
            character: Character,
            scenario: Scenario,
            recent: List<ChatMessage>,
        ): String {
            prompts += PromptBuilder.turn(character, scenario, recent)
            if (takes > 0) kotlinx.coroutines.delay(takes)
            return if (stuck) "I am nearby, small jam" else "line number ${counter++}"
        }
    }

    private class FakeStage : Director.Stage {
        val messages = mutableListOf<ChatMessage>()

        override val transcript: List<ChatMessage> get() = messages.toList()

        override fun beginTurn(speaker: Character): String {
            val id = UUID.randomUUID().toString()
            messages += ChatMessage(
                id = id,
                speakerId = speaker.id,
                speakerName = speaker.name,
                text = "",
                streaming = true,
            )
            return id
        }

        override fun endTurn(id: String, text: String?) {
            val index = messages.indexOfFirst { it.id == id }
            if (index < 0) return
            if (text == null) {
                messages.removeAt(index)
            } else {
                messages[index] = messages[index].copy(text = text, streaming = false)
            }
        }

        override fun onError(error: Throwable) = Unit

        fun userSays(text: String) {
            messages += ChatMessage(
                speakerId = null,
                speakerName = ChatMessage.USER_NAME,
                text = text,
            )
        }

        val replies get() = messages.filterNot { it.isUser }
    }

    @Test
    fun `nobody speaks until the user starts the chat`() = runTest {
        val engine = FakeVoices()
        val stage = FakeStage()
        backgroundScope.launchDirector(engine, stage)

        advanceTimeBy(120_000)

        assertTrue("the group talked to an empty room", stage.messages.isEmpty())
    }

    @Test
    fun `nobody answers instantly - there is a beat first`() = runTest {
        val engine = FakeVoices()
        val stage = FakeStage()
        val director = backgroundScope.launchDirector(engine, stage)

        stage.userSays("eh is anyone there")
        director.onUserMessage()

        advanceTimeBy(2_000)
        assertTrue("somebody replied the instant the message landed", stage.replies.isEmpty())

        advanceTimeBy(2_500)
        assertEquals(1, stage.replies.size)
        assertTrue(engine.prompts.first().contains("Ali: eh is anyone there"))
    }

    @Test
    fun `silence for ten to fifteen seconds lets somebody carry it on`() = runTest {
        val engine = FakeVoices()
        val stage = FakeStage()
        val director = backgroundScope.launchDirector(engine, stage)

        stage.userSays("hello")
        director.onUserMessage()
        advanceTimeBy(4_000)
        assertEquals("the first reply is missing", 1, stage.replies.size)

        // Still inside the window: nobody else should have spoken yet.
        advanceTimeBy(8_000)
        assertEquals("somebody jumped in too early", 1, stage.replies.size)

        // Past the window: the next profile picks the last message up.
        advanceTimeBy(9_000)
        assertEquals("nobody carried the conversation on", 2, stage.replies.size)
    }

    @Test
    fun `the chat keeps itself going while the user stays quiet`() = runTest {
        val engine = FakeVoices()
        val stage = FakeStage()
        val director = backgroundScope.launchDirector(engine, stage)

        stage.userSays("hello")
        director.onUserMessage()
        advanceTimeBy(100_000)

        // Roughly one every 10-15s, so nowhere near a runaway loop.
        assertTrue("the chat stalled: ${stage.replies.size}", stage.replies.size in 6..11)
    }

    @Test
    fun `a new message cuts the wait short`() = runTest {
        val engine = FakeVoices()
        val stage = FakeStage()
        val director = backgroundScope.launchDirector(engine, stage)

        stage.userSays("hello")
        director.onUserMessage()
        advanceTimeBy(4_000)
        val before = stage.replies.size

        stage.userSays("actually wait")
        director.onUserMessage()
        advanceTimeBy(4_000)

        assertEquals("the group ignored the new message", before + 1, stage.replies.size)
        assertTrue(engine.prompts.last().contains("Ali: actually wait"))
    }

    @Test
    fun `a reply in flight is abandoned when the user types`() = runTest {
        val engine = FakeVoices(takes = 8_000)
        val stage = FakeStage()
        val director = backgroundScope.launchDirector(engine, stage)

        stage.userSays("hello")
        director.onUserMessage()
        advanceTimeBy(7_000) // past the beat, mid-generation

        stage.userSays("no wait, something else")
        director.onUserMessage()
        advanceTimeBy(1_000)

        // The abandoned bubble is gone; only the new turn's typing bubble may be blank.
        assertTrue(
            "an abandoned bubble was left behind",
            stage.messages.count { !it.isUser && it.text.isBlank() } <= 1,
        )
        advanceTimeBy(10_000)
        assertTrue(engine.prompts.last().contains("no wait, something else"))
    }

    @Test
    fun `a character repeating itself word for word is not shown twice`() = runTest {
        val engine = FakeVoices(stuck = true)
        val stage = FakeStage()
        val director = backgroundScope.launchDirector(engine, stage)

        stage.userSays("hello")
        director.onUserMessage()
        advanceTimeBy(120_000)

        // Two characters, each may say the line once; a third copy means the guard failed.
        assertTrue(
            "the same line was shown ${stage.replies.size} times",
            stage.replies.size <= scenario.cast.size,
        )
    }

    @Test
    fun `no model means no conversation`() = runTest {
        val stage = FakeStage()
        val director = Director(voices = FakeVoices(ready = false), random = Random(7))
        backgroundScope.launch { director.run(scenario = { scenario }, stage = stage) }

        stage.userSays("hello")
        director.onUserMessage()
        advanceTimeBy(60_000)

        assertTrue("something answered without a model", stage.replies.isEmpty())
    }

    private fun CoroutineScope.launchDirector(engine: FakeVoices, stage: FakeStage): Director {
        val director = Director(voices = engine, random = Random(7))
        launch { director.run(scenario = { scenario }, stage = stage) }
        return director
    }
}
