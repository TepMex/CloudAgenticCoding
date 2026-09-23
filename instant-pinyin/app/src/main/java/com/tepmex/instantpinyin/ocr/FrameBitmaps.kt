package com.tepmex.instantpinyin.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.tepmex.instantpinyin.domain.UprightFrame
import kotlin.math.ceil
import kotlin.math.floor

object FrameBitmaps {
    /** Copy an `OUTPUT_IMAGE_FORMAT_RGBA_8888` analysis frame into an ARGB bitmap. */
    fun rgba(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        buffer.rewind()
        val pixels = IntArray(width * height)
        var offset = 0
        val rowPadding = rowStride - pixelStride * width
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = buffer.get().toInt() and 0xff
                val g = buffer.get().toInt() and 0xff
                val b = buffer.get().toInt() and 0xff
                val a = buffer.get().toInt() and 0xff
                pixels[offset++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                val extra = pixelStride - 4
                if (extra > 0) buffer.position(buffer.position() + extra)
            }
            if (rowPadding > 0 && y < height - 1) {
                buffer.position(buffer.position() + rowPadding)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * Crop [source] down to the upright viewport.
     *
     * [source] is the raw analysis buffer (or an already-rotated bitmap of the
     * upright size). The returned bitmap is safe to recycle on its own.
     */
    fun uprightViewport(
        source: Bitmap,
        bufferWidth: Int,
        bufferHeight: Int,
        cropLeft: Int,
        cropTop: Int,
        cropRight: Int,
        cropBottom: Int,
        rotationDegrees: Int,
    ): Bitmap {
        val rot = ((rotationDegrees % 360) + 360) % 360
        val upright = if (source.width == bufferWidth && source.height == bufferHeight && rot != 0) {
            val rotated = Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                Matrix().apply { postRotate(rot.toFloat()) },
                true,
            )
            if (rotated !== source) source.recycle()
            rotated
        } else {
            source
        }
        val expectedW = if (rot == 90 || rot == 270) bufferHeight else bufferWidth
        val expectedH = if (rot == 90 || rot == 270) bufferWidth else bufferHeight
        if (upright.width != expectedW || upright.height != expectedH) return upright
        val crop = UprightFrame.cropRect(
            bufferWidth,
            bufferHeight,
            cropLeft,
            cropTop,
            cropRight,
            cropBottom,
            rot,
        )
        val left = floor(crop.left.toDouble()).toInt().coerceIn(0, expectedW - 1)
        val top = floor(crop.top.toDouble()).toInt().coerceIn(0, expectedH - 1)
        val right = ceil(crop.right.toDouble()).toInt().coerceIn(left + 1, expectedW)
        val bottom = ceil(crop.bottom.toDouble()).toInt().coerceIn(top + 1, expectedH)
        if (left == 0 && top == 0 && right == expectedW && bottom == expectedH) return upright
        val out = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(upright, -left.toFloat(), -top.toFloat(), null)
        upright.recycle()
        return out
    }
}
