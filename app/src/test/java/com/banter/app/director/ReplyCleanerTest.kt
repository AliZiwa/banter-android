package com.banter.app.director

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The failure modes here are the ones a small model actually produces. */
class ReplyCleanerTest {

    private val names = listOf("Musa", "Grace", "Pastor Ben", "Guest")

    private fun clean(raw: String) = ReplyCleaner.clean(raw, speaker = "Musa", names = names)

    @Test
    fun `strips the model's own name prefix`() {
        assertEquals("Grace you are wrong.", clean("Musa: Grace you are wrong."))
    }

    @Test
    fun `a reply that is really somebody else's line is rejected`() {
        // The model continued the transcript instead of answering. "Guest: Hello?" shown as
        // Nakato's message was the single most confusing thing on screen.
        assertNull(clean("Guest: Hello?"))
        assertNull(clean("Grace: I will not."))
        assertNull(clean("@Pastor Ben: behave"))
    }

    @Test
    fun `strips a label for somebody not even in the cast`() {
        assertEquals("My scalp needs a trim.", clean("Stella: My scalp needs a trim."))
    }

    @Test
    fun `strips a bracketed or at-prefixed label`() {
        assertEquals("fine, I am coming.", clean("@Stella: fine, I am coming."))
    }

    @Test
    fun `leaves a lowercase word before a colon alone`() {
        assertEquals("honestly: I am tired.", clean("honestly: I am tired."))
    }

    @Test
    fun `keeps only the first message when the model writes everyone's lines`() {
        val raw = """
            eh Grace, leave me alone.
            Grace: I will not.
            Pastor Ben: Both of you, behave.
        """.trimIndent()
        assertEquals("eh Grace, leave me alone.", clean(raw))
    }

    @Test
    fun `removes stage directions`() {
        assertEquals("fine, I will come.", clean("*laughs* fine, I will come."))
    }

    @Test
    fun `unwraps a fully quoted message`() {
        assertEquals("I am not paying.", clean("\"I am not paying.\""))
    }

    @Test
    fun `keeps quotes that are part of the sentence`() {
        val raw = "she said \"no\" and walked off."
        assertEquals(raw, clean(raw))
    }

    @Test
    fun `separates punctuation glued to the next word`() {
        assertEquals("The shop is fine, business is slow.", clean("The shop is fine,business is slow."))
    }

    @Test
    fun `leaves numbers alone`() {
        assertEquals("I paid 1,500 at 3:30.", clean("I paid 1,500 at 3:30."))
    }

    @Test
    fun `drops a stray brace left by the model`() {
        assertEquals("The design is intricate.", clean("}The design is intricate."))
    }

    @Test
    fun `rejects an empty generation`() {
        assertNull(clean("   \n  "))
        assertNull(clean("Musa:"))
    }

    @Test
    fun `trims an over-long answer`() {
        val cleaned = clean((1..60).joinToString(" ") { "word$it" })!!
        assertTrue(cleaned.split(" ").size <= 35)
        assertTrue(cleaned.endsWith("…"))
    }
}
