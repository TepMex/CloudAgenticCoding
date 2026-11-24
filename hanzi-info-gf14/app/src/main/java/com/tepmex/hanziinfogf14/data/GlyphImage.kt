package com.tepmex.hanziinfogf14.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Renders one character the same way as tools/build_glyph_embeddings.py:
 * white 224×224 canvas, black Noto Sans CJK SC at 156 px, ink box centered.
 * Android and FreeType do not produce identical pixels; catalog characters
 * use the precomputed vectors and do not go through this renderer.
 */
object GlyphImage {
    fun png(character: String, typeface: Typeface): ByteArray {
        val size = EmbeddingModel.IMAGE_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = EmbeddingModel.FONT_PX.toFloat()
            this.typeface = typeface
            textAlign = Paint.Align.LEFT
        }
        val bounds = Rect()
        paint.getTextBounds(character, 0, character.length, bounds)
        val x = (size - bounds.width()) / 2f - bounds.left
        val y = (size - bounds.height()) / 2f - bounds.top
        canvas.drawText(character, x, y, paint)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }
}
