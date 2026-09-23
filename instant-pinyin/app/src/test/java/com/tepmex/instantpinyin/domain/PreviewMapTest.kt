package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewMapTest {
    @Test
    fun sameAspectIsAUniformScale() {
        val mapped = PreviewMap.centerCrop(
            PxBox(10f, 20f, 30f, 40f),
            imageWidth = 100,
            imageHeight = 200,
            viewWidth = 50f,
            viewHeight = 100f,
        )
        assertEquals(5f, mapped.left, 0.01f)
        assertEquals(10f, mapped.top, 0.01f)
        assertEquals(15f, mapped.right, 0.01f)
        assertEquals(20f, mapped.bottom, 0.01f)
    }

    @Test
    fun widerViewCropsTheTopAndBottom() {
        val mapped = PreviewMap.centerCrop(
            PxBox(0f, 25f, 10f, 35f),
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 200f,
            viewHeight = 100f,
        )
        assertEquals(0f, mapped.left, 0.01f)
        assertEquals(0f, mapped.top, 0.01f)
        assertEquals(20f, mapped.right, 0.01f)
        assertEquals(20f, mapped.bottom, 0.01f)
    }
}
