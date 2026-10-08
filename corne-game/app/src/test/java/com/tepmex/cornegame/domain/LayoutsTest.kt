package com.tepmex.cornegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    @Test
    fun cornedeon2mHasFortyEightKeysOnBothLanguages() {
        listOf(Language.EN, Language.RU).forEach { language ->
            val model = keyboardModel(TypingSession(language, "a"))
            val faces = model.leftRows.flatten() + model.rightRows.flatten()
            assertEquals(KEY_COUNT, faces.size)
            assertEquals(4, model.leftRows.size)
            assertEquals(4, model.rightRows.size)
            model.leftRows.forEach { assertEquals(6, it.size) }
            model.rightRows.forEach { assertEquals(6, it.size) }
            assertEquals("L1", model.leftRows[3][4].legend)
            assertEquals("L2", model.rightRows[3][1].legend)
            assertEquals(TrainerAction.Lower, model.leftRows[3][4].tap)
            assertEquals(TrainerAction.Upper, model.rightRows[3][1].tap)
        }
    }

    @Test
    fun englishBaseMatchesFirmware() {
        assertEquals("q", baseLegend(Language.EN, 1))
        assertEquals("w", baseLegend(Language.EN, 2))
        assertEquals("y", baseLegend(Language.EN, 24))
        assertEquals("p", baseLegend(Language.EN, 28))
        assertEquals(";", baseLegend(Language.EN, 34))
        assertEquals("'", baseLegend(Language.EN, 35))
        assertEquals(",", baseLegend(Language.EN, 38))
        assertEquals(".", baseLegend(Language.EN, 39))
        assertEquals("/", baseLegend(Language.EN, 40))
        assertEquals("␣", baseLegend(Language.EN, 23))
        assertEquals("␣", baseLegend(Language.EN, 42))
        assertEquals("⌫", baseLegend(Language.EN, 29))
        assertEquals('[', baseHold(Language.EN, 28))
        assertEquals(']', baseHold(Language.EN, 37))
        assertEquals('`', baseHold(Language.EN, 5))
    }

    @Test
    fun russianBaseIsJcukenOnTheSameKeys() {
        assertEquals('й', baseOutput(Language.RU, 1))
        assertEquals('ц', baseOutput(Language.RU, 2))
        assertEquals('н', baseOutput(Language.RU, 24))
        assertEquals('з', baseOutput(Language.RU, 28))
        assertEquals('ж', baseOutput(Language.RU, 34))
        assertEquals('э', baseOutput(Language.RU, 35))
        assertEquals('б', baseOutput(Language.RU, 38))
        assertEquals('ю', baseOutput(Language.RU, 39))
        assertEquals('.', baseOutput(Language.RU, 40))
        assertEquals('х', baseHold(Language.RU, 28))
        assertEquals('ъ', baseHold(Language.RU, 37))
        assertEquals('ё', baseHold(Language.RU, 5))
        assertTrue('х' !in baseLetters(Language.RU))
        assertTrue('ъ' !in baseLetters(Language.RU))
        assertTrue('ё' !in baseLetters(Language.RU))
        assertEquals(30, baseLetters(Language.RU).size)
    }

    @Test
    fun layersCoverCasePunctuationAndDigits() {
        val lowerA = strokeFor('a', Language.EN)
        assertEquals(Latch.NONE, lowerA?.latch)
        assertEquals(setOf(7), lowerA?.keyIds)

        val upperA = strokeFor('A', Language.EN)
        assertEquals(Latch.SHIFT, upperA?.latch)
        assertEquals(false, upperA?.hold)

        val paren = strokeFor('(', Language.EN)
        assertEquals(Latch.LOWER, paren?.latch)
        assertEquals(setOf(2), paren?.keyIds)

        val digit = strokeFor('2', Language.EN)
        assertEquals(Latch.LOWER, digit?.latch)

        val spaces = strokeFor(' ', Language.EN)
        assertEquals(setOf(23, 42), spaces?.keyIds)

        assertEquals(Latch.NONE, strokeFor(',', Language.EN)?.latch)
        assertEquals(Latch.SHIFT, strokeFor(',', Language.RU)?.latch)
        assertEquals(Latch.NONE, strokeFor('.', Language.RU)?.latch)

        val yo = strokeFor('ё', Language.RU)
        assertEquals(Latch.NONE, yo?.latch)
        assertEquals(true, yo?.hold)
        assertEquals(setOf(5), yo?.keyIds)

        assertNotNull(strokeFor('Ё', Language.RU))
        assertEquals(Latch.SHIFT, strokeFor('Ё', Language.RU)?.latch)
        assertEquals(true, strokeFor('Ё', Language.RU)?.hold)
        assertNull(strokeFor('€', Language.EN))
    }

    @Test
    fun coachHighlightsLayerKeyBeforeTheSymbol() {
        val waiting = keyboardModel(TypingSession(Language.EN, "(2) a"))
        assertTrue(waiting.leftRows[3][4].primary)
        assertTrue(waiting.leftRows[0][2].hinted)
        assertTrue(waiting.caption.contains("L1"))

        val latched = keyboardModel(
            TypingSession(Language.EN, "(2) a").apply(TrainerAction.Lower, 1).first,
        )
        assertEquals("(", latched.leftRows[0][2].legend)
        assertTrue(latched.leftRows[0][2].primary)
        assertEquals("7", latched.rightRows[0][0].legend)
        assertEquals("L2", latched.rightRows[3][1].legend)

        val shift = keyboardModel(TypingSession(Language.EN, "A"))
        assertTrue(shift.leftRows[2][0].primary)
        assertTrue(shift.rightRows[2][5].primary)
        val shifted = keyboardModel(
            TypingSession(Language.EN, "A").apply(TrainerAction.Shift, 1).first,
        )
        assertEquals("A", shifted.leftRows[1][1].legend)
        assertTrue(shifted.leftRows[1][1].primary)

        val lang = keyboardModel(TypingSession(Language.EN, "$LANGUAGE_SWITCH й"))
        assertTrue(lang.leftRows[3][3].primary)
        assertTrue(lang.caption.contains("Alt"))
    }

    @Test
    fun fingerZonesFollowCornedeonColumns() {
        val model = keyboardModel(TypingSession(Language.EN, "a"))
        val faces = (model.leftRows.flatten() + model.rightRows.flatten()).associateBy { it.id }
        assertEquals(Finger.LEFT_PINKY, faces.getValue(0).finger)
        assertEquals(Finger.LEFT_PINKY, faces.getValue(1).finger)
        assertEquals(Finger.LEFT_PINKY, faces.getValue(7).finger)
        assertEquals(Finger.LEFT_PINKY, faces.getValue(13).finger)
        assertEquals(Finger.LEFT_RING, faces.getValue(2).finger)
        assertEquals(Finger.LEFT_MIDDLE, faces.getValue(3).finger)
        assertEquals(Finger.LEFT_INDEX, faces.getValue(4).finger)
        assertEquals(Finger.LEFT_INDEX, faces.getValue(5).finger)
        assertEquals(Finger.RIGHT_INDEX, faces.getValue(24).finger)
        assertEquals(Finger.RIGHT_INDEX, faces.getValue(25).finger)
        assertEquals(Finger.RIGHT_MIDDLE, faces.getValue(26).finger)
        assertEquals(Finger.RIGHT_RING, faces.getValue(27).finger)
        assertEquals(Finger.RIGHT_PINKY, faces.getValue(28).finger)
        assertEquals(Finger.RIGHT_PINKY, faces.getValue(29).finger)
        assertEquals(Finger.LEFT_THUMB, faces.getValue(22).finger)
        assertEquals(Finger.RIGHT_THUMB, faces.getValue(43).finger)
        assertEquals(Finger.LEFT_PINKY, fingerFor(1))
    }

    @Test
    fun upperLayerShowsFunctionKeysWhenLatched() {
        val session = TypingSession(Language.EN, "a").copy(latch = Latch.UPPER)
        val model = keyboardModel(session)
        assertEquals("F1", model.rightRows[2][0].legend)
        assertEquals("F12", model.rightRows[2][3].legend)
        assertEquals("*", model.leftRows[0][0].legend)
        assertEquals("L1", model.leftRows[3][4].legend)
        assertEquals("L2", model.rightRows[3][1].legend)
    }
}
