package com.banter.app.director

import com.banter.app.data.Character
import com.banter.app.data.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SpeakerPickerTest {

    private val musa = Character(id = "m", name = "Musa", blurb = "barber")
    private val grace = Character(id = "g", name = "Grace", blurb = "teacher")
    private val ben = Character(id = "b", name = "Pastor Ben", blurb = "pastor")
    private val cast = listOf(musa, grace, ben)

    private fun said(who: Character) =
        ChatMessage(speakerId = who.id, speakerName = who.name, text = "hello")

    private fun userSaid(text: String) =
        ChatMessage(speakerId = null, speakerName = ChatMessage.USER_NAME, text = text)

    @Test
    fun `nobody speaks when the cast is empty`() {
        assertNull(SpeakerPicker.next(emptyList(), emptyList(), Random(1)))
    }

    @Test
    fun `a lone character always speaks`() {
        assertEquals(musa, SpeakerPicker.next(listOf(musa), listOf(said(musa)), Random(1)))
    }

    @Test
    fun `nobody answers themselves twice in a row`() {
        repeat(200) { seed ->
            assertNotEquals(grace, SpeakerPicker.next(cast, listOf(said(grace)), Random(seed)))
        }
    }

    @Test
    fun `being named by the user gets you the floor`() {
        val recent = listOf(said(musa), userSaid("Pastor Ben, explain yourself"))
        repeat(50) { seed -> assertEquals(ben, SpeakerPicker.next(cast, recent, Random(seed))) }
    }

    @Test
    fun `an at-mention also reaches the right person`() {
        val recent = listOf(said(musa), userSaid("@Grace are you there"))
        repeat(50) { seed -> assertEquals(grace, SpeakerPicker.next(cast, recent, Random(seed))) }
    }

    @Test
    fun `a name inside a longer word is not a mention`() {
        val recent = listOf(said(musa), userSaid("that was gracious of you"))
        val picks = (1..200).map { SpeakerPicker.next(cast, recent, Random(it)) }.toSet()
        assertEquals(setOf(grace, ben), picks)
    }
}
