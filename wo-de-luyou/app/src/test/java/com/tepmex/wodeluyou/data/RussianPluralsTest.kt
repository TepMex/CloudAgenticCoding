package com.tepmex.wodeluyou.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianPluralsTest {
    @Test
    fun wordForms() {
        assertEquals("1 слово", RussianPlurals.words(1))
        assertEquals("2 слова", RussianPlurals.words(2))
        assertEquals("3 слова", RussianPlurals.words(3))
        assertEquals("21 слово", RussianPlurals.words(21))
        assertEquals("23 слова", RussianPlurals.words(23))
        assertEquals("20 слов", RussianPlurals.words(20))
        assertEquals("49 слов", RussianPlurals.words(49))
    }
}
