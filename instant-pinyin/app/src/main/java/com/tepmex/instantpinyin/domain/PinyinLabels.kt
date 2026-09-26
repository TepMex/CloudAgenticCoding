package com.tepmex.instantpinyin.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PinyinLabel(
    val hanzi: String,
    val pinyin: String,
    val glyph: PxBox,
    val pill: PxBox,
    val textSizePx: Float,
    /** Clockwise degrees so the baseline stays upright for how the phone is held. */
    val textRotation: Int = 0,
) {
    fun hit(x: Float, y: Float): Boolean {
        if (pinyin.isNotEmpty() && pill.inflate(8f).contains(x, y)) return true
        return glyph.inflate(8f).contains(x, y)
    }
}

object PinyinLabels {
    private const val MIN_TEXT = 6f

    fun layout(
        glyphs: List<ReadingGlyph>,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
        textRotation: Int = 0,
    ): List<PinyinLabel> {
        if (viewWidth <= 0f || viewHeight <= 0f || glyphs.isEmpty()) return emptyList()
        val labels = ArrayList<PinyinLabel>()
        for (line in GlossLabels.linesOf(glyphs)) {
            val drafts = line.map { glyph ->
                val viewBox = PreviewMap.centerCrop(glyph.box, imageWidth, imageHeight, viewWidth, viewHeight)
                val preferred = min(viewBox.width, viewBox.height).times(0.46f).coerceIn(11f, 40f)
                Draft(glyph, viewBox, preferred)
            }
            labels += placeLine(drafts, viewWidth, viewHeight, textRotation)
        }
        return untangle(labels, viewWidth, viewHeight)
    }

    private data class Draft(
        val glyph: ReadingGlyph,
        val viewBox: PxBox,
        val preferred: Float,
    )

    /**
     * Even indexes (pinyin1, pinyin3, …) sit on the near tier, next to the ink.
     * Odd indexes (pinyin2, …) sit one tier further out so neighbors do not share a row.
     */
    private fun placeLine(
        line: List<Draft>,
        viewWidth: Float,
        viewHeight: Float,
        textRotation: Int,
    ): List<PinyinLabel> {
        val vertical = line.first().glyph.vertical
        val toTheLeft = vertical && !roomOnTheRight(line, viewWidth)
        return line.mapIndexed { index, draft ->
            val tier = index % 2
            val slot = slot(line, index, vertical, if (vertical) viewHeight else viewWidth)
            val textSize = fitTextSize(draft.glyph.pinyin, draft.preferred, slot.end - slot.start)
            val placed = if (vertical) {
                beside(draft.viewBox, draft.glyph.pinyin, textSize, tier, toTheLeft, slot, viewWidth, viewHeight)
            } else {
                above(draft.viewBox, draft.glyph.pinyin, textSize, tier, slot, viewWidth, viewHeight)
            }
            PinyinLabel(
                draft.glyph.hanzi,
                draft.glyph.pinyin,
                draft.viewBox,
                LabelFacing.face(placed, textRotation),
                textSize,
                textRotation,
            )
        }
    }

    private fun roomOnTheRight(line: List<Draft>, viewWidth: Float): Boolean {
        val sample = line.maxBy { it.glyph.pinyin.length }
        val pillW = estimateWidth(sample.glyph.pinyin, sample.preferred)
        val edge = line.maxOf { it.viewBox.right }
        return edge + pillW * 2f + sample.preferred < viewWidth
    }

    private data class Slot(val start: Float, val end: Float)

    /** Along-line interval this pill may occupy without meeting the next same-tier neighbor. */
    private fun slot(line: List<Draft>, index: Int, vertical: Boolean, extent: Float): Slot {
        val center = if (vertical) line[index].viewBox.centerY else line[index].viewBox.centerX
        val prev = (index - 2 downTo 0 step 2).firstOrNull()
        val next = (index + 2 until line.size step 2).firstOrNull()
        var start = 0f
        var end = extent
        if (prev != null) {
            val other = if (vertical) line[prev].viewBox.centerY else line[prev].viewBox.centerX
            start = (center + other) / 2f + 2f
        }
        if (next != null) {
            val other = if (vertical) line[next].viewBox.centerY else line[next].viewBox.centerX
            end = (center + other) / 2f - 2f
        }
        start = start.coerceIn(0f, extent)
        end = end.coerceIn(start, extent)
        return Slot(start, end)
    }

    private fun fitTextSize(pinyin: String, preferred: Float, maxAlong: Float): Float {
        if (maxAlong.isInfinite() || maxAlong <= 0f) return preferred
        var size = preferred
        while (size > MIN_TEXT && estimateWidth(pinyin, size) > maxAlong) {
            size -= 0.5f
        }
        val width = estimateWidth(pinyin, size)
        if (width > maxAlong && width > 0f) {
            size = (size * maxAlong / width).coerceAtLeast(MIN_TEXT)
        }
        return size.coerceIn(MIN_TEXT, preferred)
    }

    private fun above(
        glyph: PxBox,
        pinyin: String,
        textSize: Float,
        tier: Int,
        slot: Slot,
        viewWidth: Float,
        viewHeight: Float,
    ): PxBox {
        val natural = estimateWidth(pinyin, textSize)
        val room = (slot.end - slot.start).coerceAtLeast(0f)
        val pillW = if (room > 0f) min(natural, room) else natural
        val pillH = textSize * 1.35f
        val gap = max(2f, textSize * 0.12f)
        val lift = tier * (pillH + gap)
        val top = (glyph.top - gap - pillH - lift).coerceAtLeast(0f)
        val left = placeAlong(glyph.centerX, pillW, slot, viewWidth)
        val bottom = (top + pillH).coerceAtMost(viewHeight)
        return PxBox(left, top, left + pillW, bottom)
    }

    private fun beside(
        glyph: PxBox,
        pinyin: String,
        textSize: Float,
        tier: Int,
        toTheLeft: Boolean,
        slot: Slot,
        viewWidth: Float,
        viewHeight: Float,
    ): PxBox {
        val pillW = estimateWidth(pinyin, textSize)
        val naturalH = textSize * 1.35f
        val room = (slot.end - slot.start).coerceAtLeast(0f)
        val pillH = if (room > 0f) min(naturalH, room) else naturalH
        val gap = max(2f, textSize * 0.12f)
        val lift = tier * (pillW + gap)
        var left = if (toTheLeft) glyph.left - gap - pillW - lift else glyph.right + gap + lift
        val maxLeft = (viewWidth - pillW).coerceAtLeast(0f)
        left = left.coerceIn(0f, maxLeft)
        val top = placeAlong(glyph.centerY, pillH, slot, viewHeight)
        return PxBox(left, top, left + pillW, top + pillH)
    }

    private fun placeAlong(center: Float, size: Float, slot: Slot, extent: Float): Float {
        val minStart = slot.start.coerceIn(0f, extent)
        val maxStart = (slot.end - size).coerceAtLeast(minStart)
        val clampedMax = min(maxStart, (extent - size).coerceAtLeast(0f))
        return (center - size / 2f).coerceIn(minStart, max(minStart, clampedMax))
    }

    /** Push any remaining overlap further away from the character. */
    private fun untangle(labels: List<PinyinLabel>, viewWidth: Float, viewHeight: Float): List<PinyinLabel> {
        if (labels.size < 2) return labels
        val out = labels.toMutableList()
        repeat(6) {
            var moved = false
            for (i in out.indices) {
                for (j in i + 1 until out.size) {
                    if (!overlaps(out[i].pill, out[j].pill)) continue
                    val di = axisDistance(out[i])
                    val dj = axisDistance(out[j])
                    val move = if (di >= dj) i else j
                    val shifted = pushAway(out[move], out[if (move == i) j else i].pill, viewWidth, viewHeight)
                    if (shifted.pill != out[move].pill) {
                        out[move] = shifted
                        moved = true
                    }
                }
            }
            if (!moved) return out
        }
        return out
    }

    private fun axisDistance(label: PinyinLabel): Float {
        val dx = abs(label.pill.centerX - label.glyph.centerX)
        val dy = abs(label.pill.centerY - label.glyph.centerY)
        return max(dx, dy)
    }

    private fun pushAway(label: PinyinLabel, other: PxBox, viewWidth: Float, viewHeight: Float): PinyinLabel {
        val pill = label.pill
        val verticalStack = abs(pill.centerY - label.glyph.centerY) >= abs(pill.centerX - label.glyph.centerX)
        val shifted = if (verticalStack) {
            val overlap = min(pill.bottom, other.bottom) - max(pill.top, other.top)
            if (overlap <= 0f) pill
            else {
                val dir = if (pill.centerY <= label.glyph.centerY) -1f else 1f
                translate(pill, 0f, (overlap + 2f) * dir, viewWidth, viewHeight)
            }
        } else {
            val overlap = min(pill.right, other.right) - max(pill.left, other.left)
            if (overlap <= 0f) pill
            else {
                val dir = if (pill.centerX <= label.glyph.centerX) -1f else 1f
                translate(pill, (overlap + 2f) * dir, 0f, viewWidth, viewHeight)
            }
        }
        return if (shifted == pill) label else label.copy(pill = shifted)
    }

    private fun translate(box: PxBox, dx: Float, dy: Float, viewWidth: Float, viewHeight: Float): PxBox {
        var left = box.left + dx
        var top = box.top + dy
        if (left < 0f) left = 0f
        if (top < 0f) top = 0f
        if (left + box.width > viewWidth) left = (viewWidth - box.width).coerceAtLeast(0f)
        if (top + box.height > viewHeight) top = (viewHeight - box.height).coerceAtLeast(0f)
        return PxBox(left, top, left + box.width, top + box.height)
    }

    private fun overlaps(a: PxBox, b: PxBox): Boolean =
        a.left < b.right - 0.5f && b.left < a.right - 0.5f && a.top < b.bottom - 0.5f && b.top < a.bottom - 0.5f

    fun estimateWidth(pinyin: String, textSize: Float): Float {
        val body = pinyin.length * textSize * 0.62f
        val pad = textSize * 0.7f
        return max(body + pad, textSize * 1.4f)
    }
}
