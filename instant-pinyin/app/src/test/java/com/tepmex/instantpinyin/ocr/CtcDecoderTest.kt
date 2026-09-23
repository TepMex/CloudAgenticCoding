package com.tepmex.instantpinyin.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class CtcDecoderTest {
    private val charset = CtcDecoder.charsetFromDictLines(listOf("你", "好", "世界"))

    @Test
    fun charsetIsBlankThenDictThenSpace() {
        assertEquals("", charset[0])
        assertEquals("你", charset[1])
        assertEquals(" ", charset.last())
        assertEquals(5, charset.size)
    }

    @Test
    fun greedyDropsBlankAndCollapsesRepeats() {
        // blank, 你, 你, 好, blank, 好
        assertEquals("你好好", CtcDecoder.greedy(intArrayOf(0, 1, 1, 2, 0, 2), charset))
    }

    @Test
    fun greedyFromLogitsPicksArgmaxPerTimestep() {
        val classes = charset.size
        val time = 3
        val logits = FloatArray(time * classes)
        fun set(t: Int, c: Int, v: Float) {
            logits[t * classes + c] = v
        }
        set(0, 1, 3f)
        set(1, 1, 4f)
        set(2, 2, 5f)
        assertEquals("你好", CtcDecoder.greedyFromLogits(logits, time, classes, charset))
    }
}
