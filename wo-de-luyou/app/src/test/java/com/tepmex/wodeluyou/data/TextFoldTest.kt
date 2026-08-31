package com.tepmex.wodeluyou.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFoldTest {
    @Test
    fun stripsPinyinTonesAndLowercases() {
        assertEquals("wulumuqi", TextFold.fold("Wūlǔmùqí"))
        assertEquals("chengdu", TextFold.fold("Chéngdū"))
    }

    @Test
    fun treatsUmlautUAsU() {
        assertEquals("lushui", TextFold.fold("lǜshuǐ"))
    }
}
