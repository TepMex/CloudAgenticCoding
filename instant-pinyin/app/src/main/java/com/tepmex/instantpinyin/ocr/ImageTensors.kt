package com.tepmex.instantpinyin.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.roundToInt

object ImageTensors {
    private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun constrain(src: Bitmap, maxSide: Int = 1920): Bitmap {
        val software = if (src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
        val side = maxOf(software.width, software.height)
        if (side <= maxSide) return software
        val scale = maxSide.toFloat() / side
        val w = (software.width * scale).roundToInt().coerceAtLeast(1)
        val h = (software.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(software, w, h, true)
        if (software !== src && software !== scaled) software.recycle()
        return scaled
    }

    fun letterboxRgb(src: Bitmap, dst: Int = DET_SIZE): Pair<Bitmap, Letterbox> {
        val lb = ProbMapBoxes.letterboxOf(src.width, src.height, dst)
        val out = Bitmap.createBitmap(dst, dst, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.BLACK)
        val canvas = Canvas(out)
        val dw = src.width * lb.scale
        val dh = src.height * lb.scale
        val m = Matrix()
        m.setScale(lb.scale, lb.scale)
        m.postTranslate(lb.padX, lb.padY)
        canvas.drawBitmap(src, m, null)
        return out to lb
    }

    /** NCHW ImageNet-normalized RGB, values from a 640×640 letterboxed bitmap. */
    fun detNchw(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val plane = w * h
        val out = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xff) / 255f
            val g = ((p shr 8) and 0xff) / 255f
            val b = (p and 0xff) / 255f
            out[i] = (r - IMAGENET_MEAN[0]) / IMAGENET_STD[0]
            out[plane + i] = (g - IMAGENET_MEAN[1]) / IMAGENET_STD[1]
            out[2 * plane + i] = (b - IMAGENET_MEAN[2]) / IMAGENET_STD[2]
        }
        return out
    }

    fun recNchw(line: Bitmap): FloatArray {
        val w = 320
        val h = 48
        val pixels = IntArray(w * h)
        line.getPixels(pixels, 0, w, 0, 0, w, h)
        val plane = w * h
        val out = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xff) / 255f
            val g = ((p shr 8) and 0xff) / 255f
            val b = (p and 0xff) / 255f
            out[i] = (r - 0.5f) / 0.5f
            out[plane + i] = (g - 0.5f) / 0.5f
            out[2 * plane + i] = (b - 0.5f) / 0.5f
        }
        return out
    }

    fun cropLine(src: Bitmap, box: TextBox): Bitmap {
        val x = box.x.coerceIn(0, src.width - 1)
        val y = box.y.coerceIn(0, src.height - 1)
        val w = box.width.coerceAtLeast(1).coerceAtMost(src.width - x)
        val h = box.height.coerceAtLeast(1).coerceAtMost(src.height - y)
        val raw = Bitmap.createBitmap(src, x, y, w, h)
        // Copy so later recycle() cannot free pixels still owned by [src].
        var crop = raw.copy(Bitmap.Config.ARGB_8888, false)
        if (raw !== src) raw.recycle()
        // PaddleOCR rot90 (counter-clockwise) turns a top-to-bottom column into
        // left-to-right upright text for the recognizer.
        if (crop.height > crop.width * 1.4f) {
            val rotated = Bitmap.createBitmap(
                crop,
                0,
                0,
                crop.width,
                crop.height,
                Matrix().apply { postRotate(-90f) },
                true,
            )
            if (rotated !== crop) crop.recycle()
            crop = rotated
        }
        val targetH = 48
        val rw = (targetH.toFloat() * crop.width / crop.height).roundToInt().coerceIn(1, 320)
        val scaled = if (crop.width == rw && crop.height == targetH) {
            crop
        } else {
            Bitmap.createScaledBitmap(crop, rw, targetH, true)
        }
        if (scaled !== crop) crop.recycle()
        val canvasBmp = Bitmap.createBitmap(320, 48, Bitmap.Config.ARGB_8888)
        canvasBmp.eraseColor(Color.BLACK)
        Canvas(canvasBmp).drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== canvasBmp) scaled.recycle()
        return canvasBmp
    }
}
