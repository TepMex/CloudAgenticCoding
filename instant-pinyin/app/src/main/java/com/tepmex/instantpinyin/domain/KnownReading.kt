package com.tepmex.instantpinyin.domain

/**
 * What the reader may draw once the learner has marked hanzi as known.
 * A known character keeps its place and its tap, and loses its pinyin.
 * A word loses its Russian gloss only when every hanzi in it is known.
 * Only Known drops characters that are not in that set.
 */
object KnownReading {
    fun knownSet(text: String): Set<String> = Hanzi.uniqueSet(text)

    fun isFullyKnown(surface: String, known: Set<String>): Boolean {
        var any = false
        var i = 0
        while (i < surface.length) {
            val cp = surface.codePointAt(i)
            if (Hanzi.isHanzi(cp)) {
                any = true
                if (String(Character.toChars(cp)) !in known) return false
            }
            i += Character.charCount(cp)
        }
        return any
    }

    fun visibleGlyphs(
        glyphs: List<ReadingGlyph>,
        known: Set<String>,
        onlyKnown: Boolean,
    ): List<ReadingGlyph> {
        if (!onlyKnown) return glyphs
        return glyphs.filter { it.hanzi in known }
    }

    fun mutePinyin(label: PinyinLabel, known: Set<String>): PinyinLabel =
        if (label.hanzi in known) label.copy(pinyin = "") else label

    fun keepGloss(surface: String, known: Set<String>): Boolean = !isFullyKnown(surface, known)

    fun present(
        lines: List<List<TextToken>>,
        known: Set<String>,
        onlyKnown: Boolean,
    ): List<List<ShownToken>> =
        lines.map { line -> line.mapNotNull { presentToken(it, known, onlyKnown) } }
            .filter { it.isNotEmpty() }

    private fun presentToken(token: TextToken, known: Set<String>, onlyKnown: Boolean): ShownToken? {
        if (token.glossQuery == null) {
            return ShownToken(token.surface, emptyList(), russian = null, glossQuery = null)
        }
        val chars = token.characters.mapIndexed { index, hanzi ->
            val reading = token.readings.getOrElse(index) { "" }
            ShownChar(hanzi, pinyin = if (hanzi in known) "" else reading)
        }
        val visible = if (onlyKnown) chars.filter { it.hanzi in known } else chars
        if (visible.isEmpty()) return null
        val russian = if (isFullyKnown(token.surface, known)) null else token.russian
        val glossQuery = if (russian == null) null else token.glossQuery
        return ShownToken(token.surface, visible, russian, glossQuery)
    }
}

data class ShownChar(
    val hanzi: String,
    val pinyin: String,
)

data class ShownToken(
    val surface: String,
    val chars: List<ShownChar>,
    val russian: String?,
    val glossQuery: String?,
)
