package com.tepmex.myweek.domain

/**
 * Collapses numbered variants of one affair.
 *
 * Digits at the start or the end of the description are removed, including a
 * thousands space ("20 000") and the punctuation glued to that number.
 * Letters stay, so "20к шагов" and "40к шагов" both become "к шагов", while
 * "Книга 1" and "Книга 2" become "Книга". Emoji in the description is kept.
 * A number in the middle of the description is kept.
 */
object StepNames {
    private val leading = Regex("""^\d+(?:[ \u00A0]\d+)*[ \u00A0]*[.)\]:—–\-]*""")
    private val trailing = Regex("""[#№(.:—–\-]*[ \u00A0]*\d+(?:[ \u00A0]\d+)*$""")
    private val spaces = Regex("[ \\u00A0]+")

    fun label(title: String): String {
        var text = title.trim()
        repeat(8) {
            val next = text.replace(leading, "").replace(trailing, "").trim()
            if (next == text) return spaces.replace(text, " ").trim()
            text = next
        }
        return spaces.replace(text, " ").trim()
    }
}
