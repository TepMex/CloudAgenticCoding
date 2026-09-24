package com.tepmex.instantpinyin.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.roundToInt

object ScanImages {
    fun decode(resolver: ContentResolver, uri: Uri, maxSide: Int = 1920): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val side = maxOf(w, h)
            if (side > maxSide) {
                val scale = maxSide.toFloat() / side
                decoder.setTargetSize(
                    (w * scale).roundToInt().coerceAtLeast(1),
                    (h * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        if (decoded.config == Bitmap.Config.ARGB_8888) return decoded
        val copy = decoded.copy(Bitmap.Config.ARGB_8888, false)
        if (copy !== decoded) decoded.recycle()
        return copy
    }
}
