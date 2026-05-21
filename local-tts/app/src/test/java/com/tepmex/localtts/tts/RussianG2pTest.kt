package com.tepmex.localtts.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianG2pTest {
    @Test
    fun latinVowelsGetStressSuffix() {
        assertEquals("a0", RussianG2p.convert("a"))
        assertEquals("e0 o0", RussianG2p.convert("eo"))
    }

    @Test
    fun foreignWordUsesStressedPhonemeIds() {
        val phones = RussianG2p.convert("moller").split(" ")
        assertTrue(phones.contains("o0"))
        assertTrue(phones.contains("e0"))
    }
}
