package com.tepmex.ankientertainer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlin.math.max

object PosterBitmapLoader {
    private val cache = object : LruCache<String, Bitmap>(8) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun load(context: Context, assetPath: String, maxWidthPx: Int): Bitmap {
        val key = "$assetPath@$maxWidthPx"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSize(bounds.outWidth, maxWidthPx)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.assets.open(assetPath).use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("Failed to decode $assetPath")
        cache.put(key, bitmap)
        return bitmap
    }

    internal fun sampleSize(sourceWidth: Int, maxWidthPx: Int): Int {
        if (sourceWidth <= 0 || maxWidthPx <= 0) return 1
        var sample = 1
        var half = sourceWidth / 2
        while (half / sample >= maxWidthPx) {
            sample *= 2
        }
        return max(1, sample)
    }
}
