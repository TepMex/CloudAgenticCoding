package com.tepmex.instantpinyin.domain

data class GlossPiece(
    val text: String,
    val gloss: String?,
)

/**
 * Forward maximum matching: a 4-hanzi word wins over 3, then 2.
 * A character left over takes its own gloss when the lexicon has one.
 */
object GlossSegmenter {
    private val lengths = intArrayOf(4, 3, 2)

    fun segment(chars: List<String>, lexicon: RuGlossLexicon): List<GlossPiece> {
        val out = ArrayList<GlossPiece>()
        var i = 0
        while (i < chars.size) {
            var taken = 0
            var gloss: String? = null
            for (length in lengths) {
                if (i + length > chars.size) continue
                val word = join(chars, i, length)
                val hit = lexicon.word(word) ?: continue
                taken = length
                gloss = hit
                break
            }
            if (taken == 0) {
                val one = chars[i]
                out.add(GlossPiece(one, lexicon.char(one)))
                i += 1
            } else {
                out.add(GlossPiece(join(chars, i, taken), gloss))
                i += taken
            }
        }
        return out
    }

    private fun join(chars: List<String>, start: Int, length: Int): String {
        val builder = StringBuilder(length)
        for (offset in 0 until length) builder.append(chars[start + offset])
        return builder.toString()
    }
}
