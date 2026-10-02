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

    /** Ideographs that occur inside a known word. A character is familiar when it is in this set. */
    fun familiarHanzi(raw: String): Set<String> = hanzi(words(raw).joinToString("\n")).toSet()
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

data class RubyCell(val glyph: String, val pinyin: String, val index: Int)

/**
 * Pinyin drawn above one glyph.
 * A hanzi that already occurs in a known word keeps an empty ruby slot.
 */
fun pinyinToShow(glyph: String, familiarHanzi: Set<String>, reading: String): String =
    if (glyph in familiarHanzi) "" else reading

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
        val start = i
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
        row.add(RubyCell(glyph, pinyin, start))
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
    /** Pinyin size at the largest hanzi. `zhuāng` at this size is the comfort slot. */
    const val COMFORT_PINYIN_SP = 12f

    /** Smallest pinyin that stays readable when more characters are placed on a line. */
    const val MIN_PINYIN_SP = 8f

    const val SAMPLE = "zhuāng"

    /**
     * Passage share of the reader body on every layer.
     * Glossary layers use the rest, so the main text keeps more than 70%.
     */
    const val PASSAGE_FRACTION = 0.75f

    fun passagePx(bodyPx: Int): Int =
        (bodyPx.coerceAtLeast(1) * PASSAGE_FRACTION).toInt().coerceAtLeast(1)

    /** Slot wide enough for the pinyin sample and for the hanzi measured at that budget. */
    fun cellPx(pinyinSamplePx: Int, hanziPx: Int): Int =
        maxOf(pinyinSamplePx, hanziPx).coerceAtLeast(1)

    /**
     * Characters per line from the largest type ([comfortCellPx], the current size)
     * through the smallest type whose pinyin is still readable ([minCellPx]).
     */
    fun columnRange(contentWidthPx: Int, comfortCellPx: Int, minCellPx: Int): IntRange {
        val width = contentWidthPx.coerceAtLeast(1)
        val fewest = columnsThatFit(width, comfortCellPx)
        val most = columnsThatFit(width, minCellPx.coerceAtMost(comfortCellPx)).coerceAtLeast(fewest)
        return fewest..most
    }

    fun columnsThatFit(contentWidthPx: Int, cellPx: Int): Int =
        (contentWidthPx.coerceAtLeast(1) / cellPx.coerceAtLeast(1)).coerceAtLeast(1)

    /** Zero keeps the largest type. Any other value is clamped into [range]. */
    fun resolveColumns(preferred: Int, range: IntRange): Int =
        if (preferred <= 0) range.first else preferred.coerceIn(range.first, range.last)

    /**
     * Width of one character slot. The largest type keeps [comfortCellPx],
     * so a full row never runs past [contentWidthPx]. Smaller type divides the width evenly.
     */
    fun slotPx(contentWidthPx: Int, columns: Int, comfortCellPx: Int, fewestColumns: Int): Int {
        val cols = columns.coerceAtLeast(1)
        val width = contentWidthPx.coerceAtLeast(1)
        val comfort = comfortCellPx.coerceAtLeast(1)
        val slot = if (cols <= fewestColumns) comfort.coerceAtMost(width) else width / cols
        return slot.coerceAtLeast(1)
    }

    fun pinyinSp(comfortSp: Float, comfortSamplePx: Float, slotPx: Float, minSp: Float): Float {
        if (comfortSamplePx <= 0f || slotPx <= 0f) return comfortSp
        val scaled = comfortSp * (slotPx / comfortSamplePx)
        val floor = minSp.coerceAtMost(comfortSp)
        return scaled.coerceIn(floor, comfortSp)
    }

    /** sp that draws a probed glyph at [slotPx], so the ink stays inside the slot. */
    fun glyphSp(slotPx: Float, probeWidthPx: Float, probeSp: Float): Float {
        if (probeWidthPx <= 0f || probeSp <= 0f || slotPx <= 0f) return probeSp
        return probeSp * (slotPx / probeWidthPx)
    }

    /**
     * Hanzi size for a slot. Never larger than [naturalSp] (the current comfort size)
     * and never wider than the slot.
     */
    fun hanziSp(naturalSp: Float, slotPx: Float, probeWidthPx: Float, probeSp: Float): Float {
        val fitted = glyphSp(slotPx, probeWidthPx, probeSp)
        if (naturalSp <= 0f) return fitted
        return minOf(naturalSp, fitted)
    }
}
