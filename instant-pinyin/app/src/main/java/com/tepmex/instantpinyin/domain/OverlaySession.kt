package com.tepmex.instantpinyin.domain

import com.tepmex.instantpinyin.ocr.OcrLine

/** Holds the last live reading so one missed frame does not blink the overlay off. */
class OverlaySession {
    private var tracks: List<OverlayTracker.Track> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    fun observe(lines: List<OcrLine>, width: Int, height: Int): List<ReadingGlyph> {
        if (width != imageWidth || height != imageHeight) {
            tracks = emptyList()
            imageWidth = width
            imageHeight = height
        }
        val observed = ReadingGlyphs.fromLines(lines)
        tracks = OverlayTracker.step(tracks, observed)
        return OverlayTracker.glyphsOf(tracks)
    }
}
