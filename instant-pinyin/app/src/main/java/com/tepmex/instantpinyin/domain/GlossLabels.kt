package com.tepmex.instantpinyin.domain

import kotlin.math.max
import kotlin.math.min

data class GlossLabel(
    val text: String,
    val gloss: String,
    val span: PxBox,
    val pill: PxBox,
    val textSizePx: Float,
    /** True when the pill sits under the word (horizontal text, pinyin above). */
    val below: Boolean,
) {
    fun hit(x: Float, y: Float): Boolean = pill.inflate(8f).contains(x, y)
}

object GlossLabels {
    fun layout(
        glyphs: List<ReadingGlyph>,
        lexicon: RuGlossLexicon,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
    ): List<GlossLabel> {
        if (viewWidth <= 0f || viewHeight <= 0f || glyphs.isEmpty()) return emptyList()
        val labels = ArrayList<GlossLabel>()
        for (line in linesOf(glyphs)) {
            for (run in runsOf(line)) {
                val pieces = GlossSegmenter.segment(run.map { it.hanzi }, lexicon)
                var index = 0
                for (piece in pieces) {
                    val gloss = piece.gloss
                    val slice = run.subList(index, index + piece.text.length)
                    index += piece.text.length
                    if (gloss.isNullOrBlank()) continue
                    labels.add(place(slice, piece.text, gloss, imageWidth, imageHeight, viewWidth, viewHeight))
                }
            }
        }
        return labels
    }

    private fun place(
        slice: List<ReadingGlyph>,
        text: String,
        gloss: String,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
    ): GlossLabel {
        val union = unionBox(slice.map { it.box })
        val viewBox = PreviewMap.centerCrop(union, imageWidth, imageHeight, viewWidth, viewHeight)
        val vertical = viewBox.height > viewBox.width * 1.15f
        val textSize = min(viewBox.width, viewBox.height).times(if (vertical) 0.34f else 0.38f).coerceIn(10f, 28f)
        val pill = if (vertical) {
            beside(viewBox, gloss, textSize, viewWidth, viewHeight)
        } else {
            below(viewBox, gloss, textSize, viewWidth, viewHeight)
        }
        return GlossLabel(text, gloss, viewBox, pill, textSize, below = !vertical)
    }

    private fun below(
        span: PxBox,
        gloss: String,
        textSize: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): PxBox {
        val pillW = estimateWidth(gloss, textSize)
        val pillH = textSize * 1.35f
        val gap = max(2f, textSize * 0.12f)
        val maxTop = (viewHeight - pillH).coerceAtLeast(0f)
        val top = (span.bottom + gap).coerceIn(0f, maxTop)
        val maxLeft = (viewWidth - pillW).coerceAtLeast(0f)
        val left = (span.centerX - pillW / 2f).coerceIn(0f, maxLeft)
        return PxBox(left, top, left + pillW, top + pillH)
    }

    private fun beside(
        span: PxBox,
        gloss: String,
        textSize: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): PxBox {
        val pillW = estimateWidth(gloss, textSize)
        val pillH = textSize * 1.35f
        val gap = max(2f, textSize * 0.12f)
        var left = span.left - gap - pillW
        if (left < 0f) left = span.right + gap
        val maxLeft = (viewWidth - pillW).coerceAtLeast(0f)
        left = left.coerceIn(0f, maxLeft)
        val maxTop = (viewHeight - pillH).coerceAtLeast(0f)
        val top = (span.centerY - pillH / 2f).coerceIn(0f, maxTop)
        return PxBox(left, top, left + pillW, top + pillH)
    }

    private fun estimateWidth(gloss: String, textSize: Float): Float {
        val body = gloss.length * textSize * 0.92f
        val pad = textSize * 0.7f
        return max(body + pad, textSize * 1.6f)
    }

    internal fun linesOf(glyphs: List<ReadingGlyph>): List<List<ReadingGlyph>> {
        val remaining = glyphs.toMutableList()
        val lines = ArrayList<List<ReadingGlyph>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.minWith(compareBy({ it.box.top }, { it.box.left }))
            val vertical = seed.box.height > seed.box.width * 1.15f
            val group = remaining.filter { sameBand(seed, it, vertical) }
            remaining.removeAll(group.toSet())
            val sorted = if (vertical) group.sortedBy { it.box.top } else group.sortedBy { it.box.left }
            lines.add(sorted)
        }
        return lines
    }

    internal fun runsOf(line: List<ReadingGlyph>): List<List<ReadingGlyph>> {
        if (line.isEmpty()) return emptyList()
        val vertical = line.first().box.height > line.first().box.width * 1.15f
        val runs = ArrayList<List<ReadingGlyph>>()
        val current = ArrayList<ReadingGlyph>()
        for (glyph in line) {
            val previous = current.lastOrNull()
            if (previous != null && gap(previous, glyph, vertical) > breakGap(previous, glyph, vertical)) {
                runs.add(current.toList())
                current.clear()
            }
            current.add(glyph)
        }
        if (current.isNotEmpty()) runs.add(current.toList())
        return runs
    }

    private fun sameBand(seed: ReadingGlyph, other: ReadingGlyph, vertical: Boolean): Boolean {
        val otherVertical = other.box.height > other.box.width * 1.15f
        if (vertical != otherVertical) return false
        val overlap = if (vertical) {
            overlap(seed.box.left, seed.box.right, other.box.left, other.box.right)
        } else {
            overlap(seed.box.top, seed.box.bottom, other.box.top, other.box.bottom)
        }
        val limit = if (vertical) {
            min(seed.box.width, other.box.width)
        } else {
            min(seed.box.height, other.box.height)
        }
        return limit > 0f && overlap >= limit * 0.45f
    }

    private fun gap(previous: ReadingGlyph, next: ReadingGlyph, vertical: Boolean): Float =
        if (vertical) next.box.top - previous.box.bottom else next.box.left - previous.box.right

    private fun breakGap(previous: ReadingGlyph, next: ReadingGlyph, vertical: Boolean): Float {
        val size = if (vertical) {
            max(previous.box.height, next.box.height)
        } else {
            max(previous.box.width, next.box.width)
        }
        return size * 0.45f
    }

    private fun overlap(a0: Float, a1: Float, b0: Float, b1: Float): Float =
        max(0f, min(a1, b1) - max(a0, b0))

    private fun unionBox(boxes: List<PxBox>): PxBox = PxBox(
        left = boxes.minOf { it.left },
        top = boxes.minOf { it.top },
        right = boxes.maxOf { it.right },
        bottom = boxes.maxOf { it.bottom },
    )
}
