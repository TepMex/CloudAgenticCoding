package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionZoneTest {
    @Test
    fun dragLeftKeepsAMinimumWidth() {
        val zone = NormRect(0.2f, 0.2f, 0.8f, 0.8f)
        val moved = RecognitionZone.drag(zone, ZoneDrag.LEFT, dx = -500f, dy = 0f, viewWidth = 100f, viewHeight = 100f)
        assertEquals(0f, moved.left, 0.001f)
        assertTrue(moved.right - moved.left >= NormRect.MIN_SPAN - 0.001f)
    }

    @Test
    fun moveStaysInsideThePreview() {
        val zone = NormRect(0.1f, 0.1f, 0.4f, 0.5f)
        val moved = RecognitionZone.drag(zone, ZoneDrag.MOVE, dx = -80f, dy = -80f, viewWidth = 100f, viewHeight = 100f)
        assertEquals(0f, moved.left, 0.001f)
        assertEquals(0f, moved.top, 0.001f)
        assertEquals(0.3f, moved.right, 0.001f)
        assertEquals(0.4f, moved.bottom, 0.001f)
    }

    @Test
    fun fullFrameIsNotCropped() {
        val crop = RecognitionZone.bitmapCrop(
            NormRect(0f, 0f, 1f, 1f),
            imageWidth = 100,
            imageHeight = 200,
            viewWidth = 50f,
            viewHeight = 100f,
        )
        assertNull(crop)
    }

    @Test
    fun insetMapsThroughTheSameCenterCropAsThePreview() {
        val crop = RecognitionZone.bitmapCrop(
            NormRect(0f, 0f, 0.5f, 1f),
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 200f,
            viewHeight = 100f,
        )
        assertEquals(0, crop!!.left)
        assertEquals(25, crop.top)
        assertEquals(50, crop.width)
        assertEquals(50, crop.height)
    }
}