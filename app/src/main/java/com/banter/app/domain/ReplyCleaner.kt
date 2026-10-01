package com.banter.app.domain

/**
 * Small models are bad at stopping. Told to write one line as Musa, a 1B model writes Musa's
 * line, then Grace's answer, then a closing narration — and labels them. This trims the output
 * back down to the one message that was asked for.
 */
object ReplyCleaner {

    private const val MAX_WORDS = 34
    private val FENCE = Regex("```[a-zA-Z]*")
    private val ACTION = Regex("\\*[^*]{0,80}\\*")
    private val WHITESPACE = Regex("\\s+")

    /** Any speaker label: "Musa:", "@Grace:", "[Pastor Ben]" — including invented names. */
    private val LABEL = Regex("^\\s*[@\\[]?([A-Za-z][\\w'’-]{0,23})\\]?\\s*:\\s*")

    /** Punctuation glued to the next word: "the energy is high,a refreshing surge". */
    private val TIGHT = Regex("(?<![0-9])([,;:!?])(?=[A-Za-z])")

    fun clean(raw: String, speaker: String, names: Collection<String>): String? {
        // A chat message never opens with a brace or a label.
        var text = raw.replace(FENCE, "").trim().trimStart('}', '{')

        // One message only: drop everything from the first line break onward.
        text = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

        // "Guest: Hello?" from Nakato is not Nakato saying hello — it is the model continuing
        // the transcript. Somebody else's line is worthless as this character's reply.
        if (opensWithLabel(text, names.filterNot { it.equals(speaker, ignoreCase = true) })) {
            return null
        }
        text = stripLabels(text, listOf(speaker))
        text = text.replace(ACTION, " ").replace(WHITESPACE, " ").trim()
        text = text.replace(TIGHT, "$1 ")
        text = stripWrappingQuotes(text)

        val words = text.split(' ').filter { it.isNotBlank() }
        if (words.size > MAX_WORDS) text = words.take(MAX_WORDS).joinToString(" ") + "…"

        return text.takeIf { it.isNotBlank() && it.any(Char::isLetterOrDigit) }
    }

    private fun opensWithLabel(text: String, names: Collection<String>): Boolean =
        names.any { name ->
            name.isNotBlank() &&
                text.trimStart('@', '[').startsWith(name, ignoreCase = true) &&
                text.trimStart('@', '[').drop(name.length).trimStart(']', ' ').startsWith(":")
        }

    private fun stripLabels(text: String, names: Collection<String>): String {
        var out = text.trimStart()
        while (true) {
            val known = names.firstOrNull { name ->
                name.isNotBlank() &&
                    out.startsWith(name, ignoreCase = true) &&
                    out.drop(name.length).trimStart().startsWith(":")
            }
            if (known != null) {
                out = out.drop(known.length).trimStart().removePrefix(":").trimStart()
                continue
            }
            // An unknown but capitalised name. Lowercase words are left alone so a real
            // sentence such as "honestly: I am tired" survives.
            val match = LABEL.find(out) ?: return out
            if (!match.groupValues[1].first().isUpperCase()) return out
            out = out.removeRange(match.range).trimStart()
        }
    }

    private fun stripWrappingQuotes(text: String): String {
        val pairs = listOf('"' to '"', '\'' to '\'', '“' to '”')
        var out = text.trim()
        for ((open, close) in pairs) {
            if (out.length > 1 && out.first() == open && out.last() == close) {
                val inner = out.substring(1, out.length - 1)
                if (!inner.contains(close)) out = inner.trim()
            }
        }
        return out
    }
}
