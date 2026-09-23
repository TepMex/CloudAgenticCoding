package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UprightFrameTest {
    @Test
    fun fullFrameAt90BecomesSwappedSize() {
        val box = UprightFrame.cropRect(200, 100, 0, 0, 200, 100, 90)
        assertEquals(0f, box.left, 0.01f)
        assertEquals(0f, box.top, 0.01f)
        assertEquals(100f, box.right, 0.01f)
        assertEquals(200f, box.bottom, 0.01f)
    }

    @Test
    fun topLeftQuarterAt90LandsTopRight() {
        val box = UprightFrame.cropRect(200, 100, 0, 0, 100, 50, 90)
        assertEquals(50f, box.left, 0.01f)
        assertEquals(0f, box.top, 0.01f)
        assertEquals(100f, box.right, 0.01f)
        assertEquals(100f, box.bottom, 0.01f)
    }

    @Test
    fun fullFrameAt180StaysTheSameExtent() {
        val box = UprightFrame.cropRect(30, 40, 0, 0, 30, 40, 180)
        assertEquals(0f, box.left, 0.01f)
        assertEquals(0f, box.top, 0.01f)
        assertEquals(30f, box.right, 0.01f)
        assertEquals(40f, box.bottom, 0.01f)
    }

    @Test
    fun zeroRotationKeepsTheCrop() {
        val box = UprightFrame.cropRect(80, 60, 10, 5, 40, 25, 0)
        assertEquals(10f, box.left, 0.01f)
        assertEquals(5f, box.top, 0.01f)
        assertEquals(40f, box.right, 0.01f)
        assertEquals(25f, box.bottom, 0.01f)
    }
}
