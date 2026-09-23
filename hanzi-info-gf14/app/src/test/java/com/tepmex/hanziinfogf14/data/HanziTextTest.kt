package com.tepmex.hanziinfogf14.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HanziTextTest {
    @Test
    fun skipsLatinAndKeepsIdeographs() {
        assertEquals(listOf("你", "好"), HanziText.ideographs("Hello 你好!"))
    }

    @Test
    fun placeholderIsNotAGlyph() {
        assertEquals(ComponentGlyph.MARK, ComponentGlyph.display("&CDP-8DE4;"))
        assertEquals("氵", ComponentGlyph.display("氵"))
    }
}
