package com.tepmex.myweek.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StepNamesTest {
    @Test
    fun stripsNumbersFromTheEndsAndKeepsEmoji() {
        assertEquals("к шагов", StepNames.label("20к шагов"))
        assertEquals("к шагов", StepNames.label("40к шагов"))
        assertEquals("Книга", StepNames.label("Книга 1"))
        assertEquals("Книга", StepNames.label("Книга 2"))
        assertEquals("📚 Книга", StepNames.label("📚 Книга 2"))
        assertEquals("к шагов", StepNames.label("  20к шагов 3 "))
    }

    @Test
    fun keepsANumberInTheMiddle() {
        assertEquals("глава 3 книга", StepNames.label("глава 3 книга"))
    }

    @Test
    fun stripsAThousandsGroupAtTheEdge() {
        assertEquals("шагов", StepNames.label("20 000 шагов"))
        assertEquals("Книга", StepNames.label("Книга №1"))
        assertEquals("Книга", StepNames.label("1. Книга"))
    }
}
