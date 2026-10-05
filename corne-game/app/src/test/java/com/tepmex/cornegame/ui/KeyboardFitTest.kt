package com.tepmex.cornegame.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardFitTest {
    @Test
    fun landscapeKeyboardStaysInsideTheBox() {
        // Телефон ~800×360 dp, шапка и строка цели уже съели высоту.
        val fit = fitKeyboard(maxWidth = 760f, maxHeight = 180f, keyScale = 1f)
        assertTrue(fit.width <= 760f + 0.5f)
        assertTrue(fit.height <= 180f + 0.5f)
        assertTrue(fit.thumbWidth > fit.key)
    }

    @Test
    fun keyScaleShrinksWithoutChangingTheGapBudget() {
        val full = fitKeyboard(maxWidth = 400f, maxHeight = 700f, keyScale = 1f)
        val small = fitKeyboard(maxWidth = 400f, maxHeight = 700f, keyScale = 0.65f)
        assertTrue(small.key < full.key)
        assertTrue(small.width < full.width)
        assertTrue(small.width <= 400f + 0.5f)
    }
}
