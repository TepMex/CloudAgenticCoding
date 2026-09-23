package com.tepmex.instantpinyin.domain

import kotlin.math.max

/** PreviewView.ScaleType.FILL_CENTER: scale the frame until it covers the view, then center. */
object PreviewMap {
    fun centerCrop(
        box: PxBox,
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
    ): PxBox {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) return box
        val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
        val dx = (viewWidth - imageWidth * scale) / 2f
        val dy = (viewHeight - imageHeight * scale) / 2f
        return PxBox(
            left = box.left * scale + dx,
            top = box.top * scale + dy,
            right = box.right * scale + dx,
            bottom = box.bottom * scale + dy,
        )
    }
}
