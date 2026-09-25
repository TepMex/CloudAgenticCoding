package com.tepmex.byokassistedreader.domain

object Hanzi {
    fun isHanzi(cp: Int): Boolean =
        cp in 0x3400..0x4DBF ||
            cp in 0x4E00..0x9FFF ||
            cp in 0xF900..0xFAFF ||
            cp in 0x20000..0x2A6DF ||
            cp in 0x2A700..0x2B73F ||
            cp in 0x2B740..0x2B81F ||
            cp in 0x2B820..0x2CEAF ||
            cp in 0x2CEB0..0x2EBEF ||
            cp in 0x30000..0x3134F
}

object KnownLexicon {
    private val split = Regex("[\\s,，、;；]+")

    fun words(raw: String): List<String> {
        val seen = LinkedHashSet<String>()
        for (token in raw.split(split)) {
            val word = token.trim()
            if (word.isEmpty()) continue
            if (word.codePoints().anyMatch(Hanzi::isHanzi)) seen.add(word)
        }
        return seen.toList()
    }

    fun hanzi(raw: String): List<String> {
        val seen = LinkedHashSet<String>()
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            if (Hanzi.isHanzi(cp)) seen.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return seen.toList()
    }
}

data class Sentence(val text: String, val complete: Boolean)

fun splitSentences(text: String): List<Sentence> {
    val out = ArrayList<Sentence>()
    val buf = StringBuilder()
    for (ch in text) {
        buf.append(ch)
        if (ch == '。') {
            val sentence = buf.toString().trim()
            if (sentence.isNotEmpty()) out.add(Sentence(sentence, complete = true))
            buf.clear()
        }
    }
    val tail = buf.toString().trim()
    if (tail.isNotEmpty()) out.add(Sentence(tail, complete = false))
    return out
}

fun packPages(
    sentences: List<Sentence>,
    heightOf: (Sentence) -> Int,
    capacity: Int,
): List<List<Sentence>> {
    if (sentences.isEmpty()) return emptyList()
    val pages = ArrayList<List<Sentence>>()
    var current = ArrayList<Sentence>()
    var used = 0
    val budget = capacity.coerceAtLeast(1)
    for (sentence in sentences) {
        val height = heightOf(sentence).coerceAtLeast(1)
        if (current.isNotEmpty() && used + height > budget) {
            pages.add(current.toList())
            current = ArrayList()
            used = 0
        }
        current.add(sentence)
        used += height
    }
    if (current.isNotEmpty()) pages.add(current.toList())
    return pages
}

data class RubyCell(val glyph: String, val pinyin: String)

fun rubyRows(
    text: String,
    columns: Int,
    pinyinOf: (String) -> String,
): List<List<RubyCell>> {
    val width = columns.coerceAtLeast(1)
    val rows = ArrayList<List<RubyCell>>()
    var row = ArrayList<RubyCell>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)
        if (cp == '\n'.code || cp == '\r'.code) {
            if (row.isNotEmpty()) {
                rows.add(row.toList())
                row = ArrayList()
            }
            continue
        }
        if (cp == ' '.code || cp == '\t'.code) continue
        val glyph = String(Character.toChars(cp))
        val pinyin = if (Hanzi.isHanzi(cp)) pinyinOf(glyph) else ""
        if (row.size == width) {
            rows.add(row.toList())
            row = ArrayList()
        }
        row.add(RubyCell(glyph, pinyin))
    }
    if (row.isNotEmpty()) rows.add(row.toList())
    return rows
}

enum class ReadingLayer {
    TEXT,
    PINYIN,
    STRUCTURE,
    GLOSS_ZH,
    GLOSS_RU,
    ;

    fun step(forward: Boolean): ReadingLayer {
        val n = entries.size
        val delta = if (forward) 1 else -1
        return entries[(ordinal + delta).mod(n)]
    }

    val label: String
        get() = when (this) {
            TEXT -> "Текст"
            PINYIN -> "Пиньинь"
            STRUCTURE -> "Структура"
            GLOSS_ZH -> "简单"
            GLOSS_RU -> "По-русски"
        }
}

object RubyFit {
    const val READABLE_PINYIN_SP = 12f
    const val SAMPLE = "zhuāng"

    /** Hanzi em-square in px. Wide enough that [SAMPLE] at 12sp stays inside the character. */
    fun hanziPx(widestPinyinPx: Int): Int = widestPinyinPx.coerceAtLeast(1)

    fun pinyinSp(readableSp: Float, widestPx: Float, hanziPx: Float): Float {
        if (widestPx <= 0f || hanziPx <= 0f) return readableSp
        if (widestPx <= hanziPx) return readableSp
        return readableSp * (hanziPx / widestPx)
    }
}
