package com.tepmex.instantpinyin.domain

import kotlin.math.max

object OverlayTracker {
    data class Track(
        val hanzi: String,
        val pinyin: String,
        val box: PxBox,
        val misses: Int = 0,
    )

    fun step(
        previous: List<Track>,
        observed: List<ReadingGlyph>,
        smooth: Float = 0.62f,
        maxMisses: Int = 1,
    ): List<Track> {
        val used = BooleanArray(previous.size)
        val next = ArrayList<Track>(observed.size + previous.size)
        for (glyph in observed) {
            var best = -1
            var bestDist = Float.POSITIVE_INFINITY
            for (i in previous.indices) {
                if (used[i]) continue
                val track = previous[i]
                if (track.hanzi != glyph.hanzi) continue
                if (!near(track.box, glyph.box)) continue
                val d = dist2(track.box, glyph.box)
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            if (best >= 0) {
                used[best] = true
                next.add(
                    Track(
                        hanzi = glyph.hanzi,
                        pinyin = glyph.pinyin,
                        box = lerp(previous[best].box, glyph.box, smooth),
                        misses = 0,
                    ),
                )
            } else {
                next.add(Track(glyph.hanzi, glyph.pinyin, glyph.box, misses = 0))
            }
        }
        for (i in previous.indices) {
            if (used[i]) continue
            val track = previous[i]
            if (track.misses + 1 <= maxMisses) {
                next.add(track.copy(misses = track.misses + 1))
            }
        }
        next.sortWith(compareBy({ it.box.top }, { it.box.left }))
        return next
    }

    fun glyphsOf(tracks: List<Track>): List<ReadingGlyph> =
        tracks.map { ReadingGlyph(it.hanzi, it.pinyin, it.box) }

    private fun near(a: PxBox, b: PxBox): Boolean {
        val limit = max(max(a.width, b.width), max(max(a.height, b.height), 8f)) * 1.4f
        return dist2(a, b) <= limit * limit
    }

    private fun dist2(a: PxBox, b: PxBox): Float {
        val dx = a.centerX - b.centerX
        val dy = a.centerY - b.centerY
        return dx * dx + dy * dy
    }

    private fun lerp(a: PxBox, b: PxBox, t: Float): PxBox = PxBox(
        left = a.left + (b.left - a.left) * t,
        top = a.top + (b.top - a.top) * t,
        right = a.right + (b.right - a.right) * t,
        bottom = a.bottom + (b.bottom - a.bottom) * t,
    )
}
