package com.banter.app.domain

import kotlin.random.Random

/** Decides who talks next. */
object SpeakerPicker {

    fun next(cast: List<Character>, recent: List<ChatMessage>, random: Random): Character? {
        if (cast.isEmpty()) return null
        if (cast.size == 1) return cast.first()

        // Being named and then ignored is the fastest way for the illusion to break.
        addressed(cast, recent)?.let { return it }

        val lastSpeakerId = recent.lastOrNull { !it.isUser }?.speakerId
        return cast.filter { it.id != lastSpeakerId }.randomOrNull(random) ?: cast.random(random)
    }

    /** If the user's latest message names somebody, that somebody answers. */
    private fun addressed(cast: List<Character>, recent: List<ChatMessage>): Character? {
        val last = recent.lastOrNull()?.takeIf { it.isUser } ?: return null
        return cast.firstOrNull { character ->
            Regex("(?<![\\w@])@?${Regex.escape(character.name)}\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(last.text)
        }
    }
}
