package com.tepmex.instantpinyin.domain

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
    fun layout(
        glyphs: List<ReadingGlyph>,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
        textRotation: Int = 0,
    ): List<PinyinLabel> {
        if (viewWidth <= 0f || viewHeight <= 0f) return emptyList()
        return glyphs.map { glyph ->
            val viewBox = PreviewMap.centerCrop(glyph.box, imageWidth, imageHeight, viewWidth, viewHeight)
            val textSize = min(viewBox.width, viewBox.height).times(0.46f).coerceIn(11f, 40f)
            val placed = if (glyph.vertical) {
                beside(viewBox, glyph.pinyin, textSize, viewWidth, viewHeight)
            } else {
                above(viewBox, glyph.pinyin, textSize, viewWidth, viewHeight)
            }
            val pill = LabelFacing.face(placed, textRotation)
            PinyinLabel(glyph.hanzi, glyph.pinyin, viewBox, pill, textSize, textRotation)
        }
    }

    private fun above(
        glyph: PxBox,
        pinyin: String,
        textSize: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): PxBox {
        val pillW = estimateWidth(pinyin, textSize)
        val pillH = textSize * 1.35f
        val gap = max(2f, textSize * 0.12f)
        val top = (glyph.top - gap - pillH).coerceAtLeast(0f)
        val maxLeft = (viewWidth - pillW).coerceAtLeast(0f)
        val left = (glyph.centerX - pillW / 2f).coerceIn(0f, maxLeft)
        val bottom = (top + pillH).coerceAtMost(viewHeight)
        return PxBox(left, top, left + pillW, bottom)
    }

    private fun beside(
        glyph: PxBox,
        pinyin: String,
        textSize: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): PxBox {
        val pillW = estimateWidth(pinyin, textSize)
        val pillH = textSize * 1.35f
        val gap = max(2f, textSize * 0.12f)
        var left = glyph.right + gap
        if (left + pillW > viewWidth) left = glyph.left - gap - pillW
        val maxLeft = (viewWidth - pillW).coerceAtLeast(0f)
        left = left.coerceIn(0f, maxLeft)
        val maxTop = (viewHeight - pillH).coerceAtLeast(0f)
        val top = (glyph.centerY - pillH / 2f).coerceIn(0f, maxTop)
        return PxBox(left, top, left + pillW, top + pillH)
    }

    fun estimateWidth(pinyin: String, textSize: Float): Float {
        val body = pinyin.length * textSize * 0.62f
        val pad = textSize * 0.7f
        return max(body + pad, textSize * 1.4f)
    }
}
