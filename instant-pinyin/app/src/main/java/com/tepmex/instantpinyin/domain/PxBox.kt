package com.tepmex.instantpinyin.domain

data class PxBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom

    fun inflate(pad: Float): PxBox = PxBox(left - pad, top - pad, right + pad, bottom + pad)
}
