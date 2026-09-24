package com.tepmex.instantpinyin.domain

import com.tepmex.instantpinyin.ocr.OcrLine

/**
 * One piece of a still reading: a dictionary word of at most four hanzi, a
 * single character, or a punctuation mark that only keeps the line readable.
 *
 * The still uses the same [RuGlossLexicon] as the live overlay. Tapping one
 * character opens that character; tapping the Russian gloss opens [surface].
 */
data class TextToken(
    val surface: String,
    val readings: List<String>,
    val russian: String,
    val word: Boolean,
) {
    val pinyin: String get() = readings.joinToString(" ")

    val characters: List<String>
        get() {
            val out = ArrayList<String>(readings.size)
            var i = 0
            while (i < surface.length && out.size < readings.size) {
                val count = Character.charCount(surface.codePointAt(i))
                out.add(surface.substring(i, i + count))
                i += count
            }
            return out
        }

    /** Whole word, or the single character, when the gloss is tapped. */
    val glossQuery: String?
        get() = if (readings.isNotEmpty()) surface else null

    fun characterQuery(index: Int): String? = characters.getOrNull(index)
}

object WordSegmenter {
    fun lines(
        ocrLines: List<OcrLine>,
        lexicon: RuGlossLexicon,
        pinyinOf: (String) -> String = PinyinLookup::of,
    ): List<List<TextToken>> =
        ocrLines.map { segment(it.text, lexicon, pinyinOf) }.filter { it.isNotEmpty() }

    fun segment(
        text: String,
        lexicon: RuGlossLexicon,
        pinyinOf: (String) -> String = PinyinLookup::of,
    ): List<TextToken> {
        val out = ArrayList<TextToken>()
        val hanzi = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            if (Character.isWhitespace(cp)) {
                flush(hanzi, lexicon, pinyinOf, out)
            } else if (!Hanzi.isHanzi(cp)) {
                flush(hanzi, lexicon, pinyinOf, out)
                out.add(TextToken(String(Character.toChars(cp)), emptyList(), "", word = false))
            } else {
                hanzi.add(String(Character.toChars(cp)))
            }
            i += width
        }
        flush(hanzi, lexicon, pinyinOf, out)
        return out
    }

    private fun flush(
        hanzi: MutableList<String>,
        lexicon: RuGlossLexicon,
        pinyinOf: (String) -> String,
        out: MutableList<TextToken>,
    ) {
        if (hanzi.isEmpty()) return
        for (piece in GlossSegmenter.segment(hanzi, lexicon)) {
            val chars = codePoints(piece.text)
            val readings = chars.map { reading ->
                val py = pinyinOf(reading)
                if (py.isBlank()) "—" else py
            }
            out.add(
                TextToken(
                    surface = piece.text,
                    readings = readings,
                    russian = piece.gloss.orEmpty(),
                    word = chars.size >= 2,
                ),
            )
        }
        hanzi.clear()
    }

    private fun codePoints(text: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val count = Character.charCount(text.codePointAt(i))
            out.add(text.substring(i, i + count))
            i += count
        }
        return out
    }
}
