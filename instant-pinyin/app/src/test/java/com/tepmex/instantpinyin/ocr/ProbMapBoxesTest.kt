package com.tepmex.instantpinyin.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbMapBoxesTest {
    @Test
    fun findsTwoSeparatedBlobsLeftToRight() {
        val w = 64
        val h = 64
        val prob = FloatArray(w * h)
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0..y1) {
                for (x in x0..x1) {
                    prob[y * w + x] = 0.9f
                }
            }
        }
        fill(4, 8, 14, 20)
        fill(40, 8, 52, 20)
        val boxes = ProbMapBoxes.find(
            prob = prob,
            width = w,
            height = h,
            thresh = 0.3f,
            minSide = 4,
            minMean = 0.5f,
            unclip = 0.1f,
        )
        assertEquals(2, boxes.size)
        assertTrue(boxes[0].x < boxes[1].x)
    }

    @Test
    fun mapsLetterboxCoordinatesBackToSource() {
        val lb = ProbMapBoxes.letterboxOf(srcWidth = 320, srcHeight = 160, dst = 640)
        assertEquals(2f, lb.scale, 0.001f)
        assertEquals(0f, lb.padX, 0.001f)
        assertEquals(160f, lb.padY, 0.001f)
        val mapped = ProbMapBoxes.mapToSource(TextBox(20, 180, 40, 20, 1f), lb)
        assertEquals(10, mapped.x)
        assertEquals(10, mapped.y)
        assertEquals(20, mapped.width)
        assertEquals(10, mapped.height)
    }
}
