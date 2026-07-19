package com.tepmex.ankientertainer.data.hanzi

/**
 * Unicode-aware Han character extraction for metadata placeholders.
 * Iterates by code point (not UTF-16 Char) and keeps first-occurrence order.
 */
object HanziQuery {
    const val DEFAULT_MAX_UNIQUE = 20

    data class Extraction(
        val characters: List<String>,
        val truncated: Boolean,
    )

    fun extractUniqueHan(
        query: String,
        maxUnique: Int = DEFAULT_MAX_UNIQUE,
    ): Extraction {
        val ordered = LinkedHashSet<String>()
        var truncated = false
        var i = 0
        while (i < query.length) {
            val cp = query.codePointAt(i)
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                val ch = String(Character.toChars(cp))
                if (ch !in ordered) {
                    if (ordered.size >= maxUnique) {
                        truncated = true
                    } else {
                        ordered.add(ch)
                    }
                }
            }
            i += Character.charCount(cp)
        }
        return Extraction(characters = ordered.toList(), truncated = truncated)
    }
}
