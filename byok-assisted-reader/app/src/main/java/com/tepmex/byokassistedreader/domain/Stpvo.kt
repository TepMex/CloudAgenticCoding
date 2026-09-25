package com.tepmex.byokassistedreader.domain

enum class StpvoRole {
    SUBJECT,
    TIME,
    PLACE,
    VERB,
    OBJECT,
    ;

    val ru: String
        get() = when (this) {
            SUBJECT -> "Кто"
            TIME -> "Когда"
            PLACE -> "Где"
            VERB -> "Что делает"
            OBJECT -> "С чем"
        }

    companion object {
        fun fromName(raw: String): StpvoRole? = when (raw.trim().lowercase()) {
            "subject", "who", "кто" -> SUBJECT
            "time", "when", "когда" -> TIME
            "place", "where", "где" -> PLACE
            "verb", "action", "глагол" -> VERB
            "object", "obj", "объект" -> OBJECT
            else -> null
        }
    }
}

data class StpvoPart(val role: StpvoRole, val text: String)

data class ColoredSpan(val start: Int, val end: Int, val role: StpvoRole)

fun alignParts(sentence: String, parts: List<StpvoPart>): List<ColoredSpan> {
    val used = BooleanArray(sentence.length)
    val spans = ArrayList<ColoredSpan>()
    for (part in parts) {
        val needle = part.text
        if (needle.isEmpty()) continue
        var from = 0
        while (from <= sentence.length - needle.length) {
            val at = sentence.indexOf(needle, from)
            if (at < 0) break
            val end = at + needle.length
            if ((at until end).all { !used[it] }) {
                for (i in at until end) used[i] = true
                spans.add(ColoredSpan(at, end, part.role))
                break
            }
            from = at + 1
        }
    }
    return spans
}
