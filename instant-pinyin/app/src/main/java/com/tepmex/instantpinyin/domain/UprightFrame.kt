package com.tepmex.instantpinyin.domain

/**
 * CameraX crop rectangles are in sensor-buffer space. The upright bitmap is that
 * buffer rotated clockwise by `rotationDegrees`. This maps the buffer crop into
 * that upright bitmap.
 */
object UprightFrame {
    fun cropRect(
        bufferWidth: Int,
        bufferHeight: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        rotationDegrees: Int,
    ): PxBox {
        val rot = ((rotationDegrees % 360) + 360) % 360
        val corners = arrayOf(
            left.toFloat() to top.toFloat(),
            right.toFloat() to top.toFloat(),
            right.toFloat() to bottom.toFloat(),
            left.toFloat() to bottom.toFloat(),
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for ((x, y) in corners) {
            val (mx, my) = mapPoint(x, y, bufferWidth, bufferHeight, rot)
            if (mx < minX) minX = mx
            if (my < minY) minY = my
            if (mx > maxX) maxX = mx
            if (my > maxY) maxY = my
        }
        return PxBox(minX, minY, maxX, maxY)
    }

    fun mapPoint(
        x: Float,
        y: Float,
        bufferWidth: Int,
        bufferHeight: Int,
        rotationDegrees: Int,
    ): Pair<Float, Float> {
        val rot = ((rotationDegrees % 360) + 360) % 360
        return when (rot) {
            90 -> (bufferHeight - y) to x
            180 -> (bufferWidth - x) to (bufferHeight - y)
            270 -> y to (bufferWidth - x)
            else -> x to y
        }
    }
}
