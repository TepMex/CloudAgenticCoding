package com.tepmex.instantpinyin.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * The live reader's recognition window, as fractions of the preview (0–1).
 * OCR runs on the matching slice of the upright frame.
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun clamped(minSpan: Float = MIN_SPAN): NormRect {
        var l = left.coerceIn(0f, 1f)
        var t = top.coerceIn(0f, 1f)
        var r = right.coerceIn(0f, 1f)
        var b = bottom.coerceIn(0f, 1f)
        if (r - l < minSpan) {
            val mid = ((l + r) / 2f).coerceIn(minSpan / 2f, 1f - minSpan / 2f)
            l = mid - minSpan / 2f
            r = mid + minSpan / 2f
        }
        if (b - t < minSpan) {
            val mid = ((t + b) / 2f).coerceIn(minSpan / 2f, 1f - minSpan / 2f)
            t = mid - minSpan / 2f
            b = mid + minSpan / 2f
        }
        return NormRect(l, t, r, b)
    }

    companion object {
        const val MIN_SPAN = 0.18f

        /** Inset so the border and handles are visible on a fresh install. */
        val Default = NormRect(0.06f, 0.14f, 0.94f, 0.78f)
    }
}

enum class ZoneDrag {
    MOVE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

data class ImageCrop(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

object RecognitionZone {
    fun drag(zone: NormRect, mode: ZoneDrag, dx: Float, dy: Float, viewWidth: Float, viewHeight: Float): NormRect {
        if (viewWidth <= 0f || viewHeight <= 0f) return zone
        val nx = dx / viewWidth
        val ny = dy / viewHeight
        var left = zone.left
        var top = zone.top
        var right = zone.right
        var bottom = zone.bottom
        when (mode) {
            ZoneDrag.MOVE -> {
                left += nx
                right += nx
                top += ny
                bottom += ny
                val w = right - left
                val h = bottom - top
                if (left < 0f) {
                    right -= left
                    left = 0f
                }
                if (top < 0f) {
                    bottom -= top
                    top = 0f
                }
                if (right > 1f) {
                    left -= right - 1f
                    right = 1f
                }
                if (bottom > 1f) {
                    top -= bottom - 1f
                    bottom = 1f
                }
                left = left.coerceAtLeast(0f)
                top = top.coerceAtLeast(0f)
                right = right.coerceAtMost(1f)
                bottom = bottom.coerceAtMost(1f)
                if (right - left < w) {
                    left = (right - w).coerceAtLeast(0f)
                }
                if (bottom - top < h) {
                    top = (bottom - h).coerceAtLeast(0f)
                }
            }
            ZoneDrag.LEFT, ZoneDrag.TOP_LEFT, ZoneDrag.BOTTOM_LEFT ->
                left = (left + nx).coerceIn(0f, right - NormRect.MIN_SPAN)
            ZoneDrag.RIGHT, ZoneDrag.TOP_RIGHT, ZoneDrag.BOTTOM_RIGHT ->
                right = (right + nx).coerceIn(left + NormRect.MIN_SPAN, 1f)
            else -> Unit
        }
        when (mode) {
            ZoneDrag.TOP, ZoneDrag.TOP_LEFT, ZoneDrag.TOP_RIGHT ->
                top = (top + ny).coerceIn(0f, bottom - NormRect.MIN_SPAN)
            ZoneDrag.BOTTOM, ZoneDrag.BOTTOM_LEFT, ZoneDrag.BOTTOM_RIGHT ->
                bottom = (bottom + ny).coerceIn(top + NormRect.MIN_SPAN, 1f)
            else -> Unit
        }
        return NormRect(left, top, right, bottom)
    }

    /**
     * View-fraction window mapped into upright image pixels.
     * Null when the window already covers the whole bitmap.
     */
    fun bitmapCrop(
        zone: NormRect,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
    ): ImageCrop? {
        if (imageWidth <= 1 || imageHeight <= 1 || viewWidth <= 1f || viewHeight <= 1f) return null
        val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
        val dx = (viewWidth - imageWidth * scale) / 2f
        val dy = (viewHeight - imageHeight * scale) / 2f
        fun imageX(viewX: Float) = (viewX - dx) / scale
        fun imageY(viewY: Float) = (viewY - dy) / scale
        val rawLeft = imageX(zone.left * viewWidth)
        val rawTop = imageY(zone.top * viewHeight)
        val rawRight = imageX(zone.right * viewWidth)
        val rawBottom = imageY(zone.bottom * viewHeight)
        val left = floor(rawLeft.toDouble()).toInt().coerceIn(0, imageWidth - 1)
        val top = floor(rawTop.toDouble()).toInt().coerceIn(0, imageHeight - 1)
        val right = ceil(rawRight.toDouble()).toInt().coerceIn(left + 1, imageWidth)
        val bottom = ceil(rawBottom.toDouble()).toInt().coerceIn(top + 1, imageHeight)
        if (left == 0 && top == 0 && right == imageWidth && bottom == imageHeight) return null
        if (right - left < 8 || bottom - top < 8) return null
        return ImageCrop(left, top, right - left, bottom - top)
    }
}
