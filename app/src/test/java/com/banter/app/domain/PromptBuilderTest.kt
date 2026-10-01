package com.banter.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private val musa = Character(id = "m", name = "Musa", blurb = "barber")
    private val grace = Character(id = "g", name = "Grace", blurb = "teacher")
    private val scenario = Scenario(
        title = "Test",
        sharedHistory = "school",
        cast = listOf(musa, grace),
        userName = "Ali",
    )

    private fun said(who: Character, text: String) =
        ChatMessage(speakerId = who.id, speakerName = who.name, text = text)

    private fun userSaid(text: String) =
        ChatMessage(speakerId = null, speakerName = ChatMessage.USER_NAME, text = text)

    @Test
    fun `only the last two messages are context`() {
        val transcript = listOf(
            userSaid("old question nobody remembers"),
            said(grace, "second"),
            said(musa, "third"),
            userSaid("what about the money"),
        )
        val prompt = PromptBuilder.turn(musa, scenario, transcript)
        assertTrue(prompt.contains("Musa: third"))
        assertTrue(prompt.contains("Ali: what about the money"))
        assertFalse(prompt.contains("old question"))
        assertFalse(prompt.contains("second"))
    }

    @Test
    fun `when the user spoke last, the turn is told to answer them and nothing else`() {
        val transcript = listOf(
            said(musa, "I am nearby, small jam at the stage"),
            userSaid("why are we using dollars?"),
        )
        val prompt = PromptBuilder.turn(musa, scenario, transcript)
        assertEquals(
            "Musa: I am nearby, small jam at the stage\n" +
                "Ali: why are we using dollars?\n\n" +
                "Answer Ali's last message directly.\n" +
                "Musa:",
            prompt,
        )
        // The self-repeat reminder was found to pull the model back onto its own old topic.
        assertFalse(prompt.contains("already said"))
    }

    @Test
    fun `in idle chat a character is reminded of its own last line`() {
        val transcript = listOf(
            said(musa, "I am nearby, small jam at the stage"),
            said(grace, "you said that yesterday"),
        )
        val prompt = PromptBuilder.turn(musa, scenario, transcript)
        assertTrue(prompt.contains("Musa already said \"I am nearby, small jam at the stage\""))
        assertFalse(prompt.contains("Answer Ali"))
    }

    @Test
    fun `no reminder on a character's first line`() {
        val prompt = PromptBuilder.turn(musa, scenario, listOf(said(grace, "hello all")))
        assertFalse(prompt.contains("already said"))
    }

    @Test
    fun `the user is never labelled You`() {
        // "You: ..." reads to the model as a statement about itself, which is how a group chat
        // starts answering the wrong person.
        val prompt = PromptBuilder.turn(musa, scenario, listOf(userSaid("where is Stella?")))
        assertFalse(prompt.contains("You:"))
        assertTrue(prompt.contains("Ali: where is Stella?"))
    }

    @Test
    fun `a blank or second-person user name falls back to Guest`() {
        val blank = PromptBuilder.turn(musa, scenario.copy(userName = "  "), listOf(userSaid("hi")))
        val you = PromptBuilder.turn(musa, scenario.copy(userName = "You"), listOf(userSaid("hi")))
        assertTrue(blank.contains("Guest: hi"))
        assertTrue(you.contains("Guest: hi"))
    }

    @Test
    fun `the persona carries the profile the user defined`() {
        val withVoice = musa.copy(
            quirk = "Confident one-liners.",
            secret = "you are dating Stella",
        )
        val system = PromptBuilder.system(withVoice, scenario.copy(cast = listOf(withVoice, grace)))
        assertTrue(system.contains("You are Musa, barber"))
        assertTrue(system.contains("Confident one-liners."))
        assertTrue(system.contains("dating Stella"))
        assertTrue(system.contains("Ali is also here"))
    }

    @Test
    fun `a character's secret is not shown to the others`() {
        val withSecret = scenario.copy(cast = listOf(musa.copy(secret = "dating Stella"), grace))
        assertFalse(PromptBuilder.system(grace, withSecret).contains("dating Stella"))
    }
}
