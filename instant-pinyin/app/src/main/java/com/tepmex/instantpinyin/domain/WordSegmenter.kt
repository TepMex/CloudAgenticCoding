package com.tepmex.instantpinyin.domain

import com.tepmex.instantpinyin.ocr.OcrLine

/**
 * One piece of a still reading: a dictionary word, a single character, or a
 * punctuation mark that only keeps the line readable.
 *
 * [word] is true when forward maximum matching took a lexicon entry of two or
 * more characters. Pleco then searches that whole surface.
 */
data class TextToken(
    val surface: String,
    val pinyin: String,
    val russian: String,
    val word: Boolean,
) {
    val plecoQuery: String?
        get() = if (pinyin.isNotEmpty()) surface else null
}

object WordSegmenter {
    fun lines(
        ocrLines: List<OcrLine>,
        lexicon: Lexicon,
        pinyinOf: (String) -> String = PinyinLookup::of,
    ): List<List<TextToken>> =
        ocrLines.map { segment(it.text, lexicon, pinyinOf) }.filter { it.isNotEmpty() }

    fun segment(
        text: String,
        lexicon: Lexicon,
        pinyinOf: (String) -> String = PinyinLookup::of,
    ): List<TextToken> {
        val out = ArrayList<TextToken>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            if (Character.isWhitespace(cp)) {
                i += width
                continue
            }
            if (!Hanzi.isHanzi(cp)) {
                out.add(TextToken(String(Character.toChars(cp)), "", "", false))
                i += width
                continue
            }
            val matched = longestWord(text, i, lexicon)
            if (matched != null) {
                val lex = lexicon.lookup(matched.surface)
                out.add(
                    TextToken(
                        surface = matched.surface,
                        pinyin = lex?.pinyin?.ifBlank { "—" } ?: "—",
                        russian = lex?.russian.orEmpty(),
                        word = true,
                    ),
                )
                i = matched.end
            } else {
                val hanzi = String(Character.toChars(cp))
                val lex = lexicon.lookup(hanzi)
                val fromChars = pinyinOf(hanzi)
                val pinyin = if (fromChars.isBlank() || fromChars == "—") {
                    lex?.pinyin?.ifBlank { "—" } ?: "—"
                } else {
                    fromChars
                }
                out.add(TextToken(hanzi, pinyin, lex?.russian.orEmpty(), word = false))
                i += width
            }
        }
        return out
    }

    private data class Match(val surface: String, val end: Int)

    private fun longestWord(text: String, start: Int, lexicon: Lexicon): Match? {
        for (len in lexicon.maxLength downTo 2) {
            val taken = takeHanzi(text, start, len) ?: continue
            if (lexicon.lookup(taken.surface) != null) {
                return Match(taken.surface, taken.end)
            }
        }
        return null
    }

    private data class Slice(val surface: String, val end: Int)

    private fun takeHanzi(text: String, start: Int, count: Int): Slice? {
        var i = start
        var n = 0
        while (n < count && i < text.length) {
            val cp = text.codePointAt(i)
            if (!Hanzi.isHanzi(cp)) return null
            i += Character.charCount(cp)
            n++
        }
        if (n < count) return null
        return Slice(text.substring(start, i), i)
    }
}
