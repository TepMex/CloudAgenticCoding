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
 * Which hanzi get a reading in the ruby band.
 * [UNKNOWN] is the default: a hanzi that already occurs in a known word keeps an empty slot.
 * [ALL] fills every hanzi.
 */
enum class PinyinScope {
    UNKNOWN,
    ALL,
    ;

    fun toggle(): PinyinScope = if (this == UNKNOWN) ALL else UNKNOWN

    /** Short name shown beside «Пиньинь». */
    val label: String
        get() = when (this) {
            UNKNOWN -> "только неизвестные"
            ALL -> "все иероглифы"
        }
}

/**
 * Pinyin drawn above one glyph.
 * In [PinyinScope.UNKNOWN], a hanzi that already occurs in a known word keeps an empty ruby slot.
 */
fun pinyinToShow(
    glyph: String,
    familiarHanzi: Set<String>,
    reading: String,
    scope: PinyinScope = PinyinScope.UNKNOWN,
): String = if (scope == PinyinScope.UNKNOWN && glyph in familiarHanzi) "" else reading

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
    ASSIST,
    STRUCTURE,
    ;

    fun step(forward: Boolean): ReadingLayer {
        val n = entries.size
        val delta = if (forward) 1 else -1
        return entries[(ordinal + delta).mod(n)]
    }

    /** Readings stay on every layer after Текст, including when the layer changes. */
    val showsPinyin: Boolean
        get() = this != TEXT

    val label: String
        get() = when (this) {
            TEXT -> "Текст"
            PINYIN -> "Пиньинь"
            STRUCTURE -> "Структура"
            ASSIST -> "Справка"
        }
}

/** Floating card on the reference layer. [NONE] is the screen with no window. */
enum class AssistCard {
    NONE,
    WORDS,
    CHENGYU,
    REFERENCE,
    LITERAL,
    RETELLING,
    ;

    fun step(forward: Boolean): AssistCard {
        val n = entries.size
        val delta = if (forward) 1 else -1
        return entries[(ordinal + delta).mod(n)]
    }

    /** Whole-page Russian rendering, requested when this window is opened. */
    val needsReading: Boolean
        get() = this == LITERAL || this == RETELLING

    val label: String
        get() = when (this) {
            NONE -> ""
            WORDS -> "Незнакомые слова"
            CHENGYU -> "成语"
            REFERENCE -> "Имена и места"
            LITERAL -> "Подстрочный перевод"
            RETELLING -> "Пересказ"
        }

    /** One line under the title, so the two Russian windows stay distinct. */
    val hint: String
        get() = when (this) {
            LITERAL -> "Нюансы китайского оригинала важнее русской структуры."
            RETELLING -> "Те же нюансы, более художественный и родной русский."
            else -> ""
        }
}

sealed interface OverlaySwipe {
    data object TogglePinyin : OverlaySwipe
    data object ToggleLegend : OverlaySwipe
    data class StepCard(val forward: Boolean) : OverlaySwipe
}

/**
 * Horizontal swipe never turns a page.
 * On [ReadingLayer.PINYIN] either direction toggles unknown-only and all-hanzi pinyin.
 * On [ReadingLayer.STRUCTURE] either direction toggles the legend.
 * On [ReadingLayer.ASSIST] left steps the window forward and right steps back.
 */
fun overlaySwipe(layer: ReadingLayer, dragPx: Float, slopPx: Float = 80f): OverlaySwipe? {
    if (dragPx.isNaN() || kotlin.math.abs(dragPx) < slopPx) return null
    val forward = dragPx < 0f
    return when (layer) {
        ReadingLayer.PINYIN -> OverlaySwipe.TogglePinyin
        ReadingLayer.STRUCTURE -> OverlaySwipe.ToggleLegend
        ReadingLayer.ASSIST -> OverlaySwipe.StepCard(forward)
        else -> null
    }
}

/** Top-bar title: layer index and name, plus the pinyin scope on Пиньинь. */
fun layerCaption(layer: ReadingLayer, scope: PinyinScope): String = buildString {
    append(layer.ordinal)
    append(' ')
    append(layer.label)
    if (layer == ReadingLayer.PINYIN) {
        append(" · ")
        append(scope.label)
    }
}

object RubyFit {
    /** Pinyin size at the largest hanzi. `zhuāng` at this size is the comfort slot. */
    const val COMFORT_PINYIN_SP = 12f

    /**
     * Preferred pinyin floor. A slot that cannot hold [SAMPLE] at this size
     * scales pinyin further; that smaller ruby is still treated as readable.
     */
    const val MIN_PINYIN_SP = 8f

    /**
     * Densest characters-per-line setting added past the 8sp floor.
     * The slider reaches this count (9 through 12) whenever the width can place the slots.
     */
    const val DENSE_COLUMNS_MAX = 12

    const val SAMPLE = "zhuāng"

    /**
     * Passage share of the reader body. Every layer keeps the full height.
     * Dictionaries and the structure legend float over the text.
     */
    const val PASSAGE_FRACTION = 1f

    fun passagePx(bodyPx: Int): Int =
        (bodyPx.coerceAtLeast(1) * PASSAGE_FRACTION).toInt().coerceAtLeast(1)

    /** Slot wide enough for the pinyin sample and for the hanzi measured at that budget. */
    fun cellPx(pinyinSamplePx: Int, hanziPx: Int): Int =
        maxOf(pinyinSamplePx, hanziPx).coerceAtLeast(1)

    /**
     * Characters per line from the largest type ([comfortCellPx], the current size)
     * through at least [DENSE_COLUMNS_MAX], past the 8sp cell ([minCellPx]).
     * A wider screen that already fits more than [DENSE_COLUMNS_MAX] at 8sp keeps that larger count.
     */
    fun columnRange(contentWidthPx: Int, comfortCellPx: Int, minCellPx: Int): IntRange {
        val width = contentWidthPx.coerceAtLeast(1)
        val fewest = columnsThatFit(width, comfortCellPx)
        val readableCell = minCellPx.coerceAtMost(comfortCellPx).coerceAtLeast(1)
        val readable = columnsThatFit(width, readableCell).coerceAtLeast(fewest)
        val dense = DENSE_COLUMNS_MAX.coerceAtMost(width).coerceAtLeast(fewest)
        return fewest..maxOf(readable, dense)
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

    /**
     * Pinyin size for one slot. Scales from [comfortSp] with the slot and never grows past it.
     * [minSp] is a floor only while the slot can still hold the comfort sample at that size.
     * Narrower slots, including 9–12 characters per line, keep the proportional size.
     */
    fun pinyinSp(comfortSp: Float, comfortSamplePx: Float, slotPx: Float, minSp: Float): Float {
        if (comfortSamplePx <= 0f || slotPx <= 0f || comfortSp <= 0f) return comfortSp
        val capped = (comfortSp * (slotPx / comfortSamplePx)).coerceAtMost(comfortSp)
        val floor = minSp.coerceIn(1f, comfortSp)
        val minSamplePx = comfortSamplePx * (floor / comfortSp)
        return if (slotPx + 0.01f >= minSamplePx) capped.coerceAtLeast(floor) else capped.coerceAtLeast(1f)
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
