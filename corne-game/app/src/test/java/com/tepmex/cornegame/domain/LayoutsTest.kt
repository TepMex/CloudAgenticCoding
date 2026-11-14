package com.tepmex.cornegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertNull(baseHold(Language.EN, 28))
        assertNull(baseHold(Language.EN, 37))
        assertNull(baseHold(Language.EN, 5))
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
        assertNull(baseHold(Language.RU, 28))
        assertNull(baseHold(Language.RU, 37))
        assertNull(baseHold(Language.RU, 5))
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
        assertEquals(Latch.LOWER, yo?.latch)
        assertEquals(false, yo?.hold)
        assertEquals(setOf(1), yo?.keyIds)
        assertEquals(setOf(8), strokeFor('х', Language.RU)?.keyIds)
        assertEquals(setOf(9), strokeFor('ъ', Language.RU)?.keyIds)
        assertEquals(Latch.LOWER, strokeFor('`', Language.EN)?.latch)
        assertEquals(setOf(1), strokeFor('`', Language.EN)?.keyIds)

        assertNull(strokeFor('Ё', Language.RU))
        assertNull(strokeFor('€', Language.EN))
    }

    @Test
    fun coachHighlightsLayerKeyBeforeTheSymbol() {
        val waiting = keyboardModel(TypingSession(Language.EN, "(2) a"))
        assertTrue(waiting.leftRows[3][4].primary)
        assertTrue(waiting.leftRows[0][2].hinted)
        assertTrue(waiting.caption.contains("L1"))

        val latched = keyboardModel(
            TypingSession(Language.EN, "(2) a"),
            BoardLive(screenLower = true),
        )
        assertEquals("(", latched.leftRows[0][2].legend)
        val remembered = keyboardModel(
            TypingSession(Language.EN, "(2) a").apply(TrainerAction.Lower, 1).first,
        )
        assertEquals("w", remembered.leftRows[0][2].legend)
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
        val session = TypingSession(Language.EN, "a")
        val model = keyboardModel(session, BoardLive(screenUpper = true))
        assertEquals("F1", model.rightRows[2][0].legend)
        assertEquals("F12", model.rightRows[2][3].legend)
        assertEquals("*", model.leftRows[0][0].legend)
        assertEquals("Calc", model.leftRows[3][3].legend)
        assertEquals(";", model.rightRows[1][5].legend)
        assertEquals("L1", model.leftRows[3][4].legend)
        assertEquals("L2", model.rightRows[3][1].legend)

        val russian = keyboardModel(
            session.copy(language = Language.RU),
            BoardLive(screenUpper = true),
        )
        assertEquals("ж", russian.rightRows[1][5].legend)
    }

    @Test
    fun lowerLayerMatchesVialExport() {
        val english = keyboardModel(
            TypingSession(Language.EN, "a"),
            BoardLive(screenLower = true),
        )
        assertEquals("Esc", english.leftRows[0][0].legend)
        assertEquals("`", english.leftRows[0][1].legend)
        assertEquals("(", english.leftRows[0][2].legend)
        assertEquals("[", english.leftRows[1][2].legend)
        assertEquals("{", english.leftRows[2][2].legend)
        assertEquals("4", english.rightRows[1][0].legend)
        assertEquals(".", english.rightRows[1][3].legend)
        assertEquals("_", english.rightRows[1][4].legend)
        assertEquals("\\", english.rightRows[1][5].legend)
        assertEquals("1", english.rightRows[2][0].legend)
        assertEquals("=", english.rightRows[2][3].legend)
        assertEquals("/", english.rightRows[2][4].legend)
        assertEquals("Alt", english.leftRows[3][3].legend)

        val russian = keyboardModel(
            TypingSession(Language.RU, "а"),
            BoardLive(screenLower = true),
        )
        assertEquals("ё", russian.leftRows[0][1].legend)
        assertEquals("х", russian.leftRows[1][2].legend)
        assertEquals("ъ", russian.leftRows[1][3].legend)
        assertEquals("Х", russian.leftRows[2][2].legend)
        assertEquals("Ъ", russian.leftRows[2][3].legend)
        assertEquals("Б", russian.leftRows[2][4].legend)
        assertEquals("Ю", russian.leftRows[2][5].legend)
        assertEquals(".", russian.rightRows[1][3].legend)
        assertEquals("_", russian.rightRows[1][4].legend)
        assertEquals(".", russian.rightRows[2][4].legend)
        assertEquals("/", russian.leftRows[0][5].legend)
    }

    @Test
    fun heldShiftShowsWhatTheKeyProducesNow() {
        val held = keyboardModel(
            TypingSession(Language.EN, "A"),
            BoardLive(hardwareShift = true),
        )
        assertEquals("A", held.leftRows[1][1].legend)
        assertTrue(held.leftRows[1][1].primary)
        assertFalse(held.leftRows[2][0].primary)

        val lower = keyboardModel(
            TypingSession(Language.EN, "a"),
            BoardLive(screenLower = true, hardwareShift = true),
        )
        assertEquals("&", lower.rightRows[0][0].legend)
        assertEquals("(", lower.leftRows[0][2].legend)
        assertEquals("~", lower.leftRows[0][1].legend)

        val russian = keyboardModel(
            TypingSession(Language.RU, "а"),
            BoardLive(screenLower = true, hardwareShift = true),
        )
        assertEquals("Ё", russian.leftRows[0][1].legend)
        assertEquals("?", russian.rightRows[0][0].legend)
        assertEquals(",", russian.rightRows[2][4].legend)
    }

    @Test
    fun keyboardLayoutReplacesLegendsAndExerciseLanguageStaysInTheCaption() {
        val table = LegendTable(plain = mapOf(45 to 'й'), shifted = mapOf(45 to 'Й'))
        val model = keyboardModel(
            TypingSession(Language.EN, "a"),
            BoardLive(language = Language.RU),
            table,
        )
        assertEquals("й", model.leftRows[0][1].legend)
        assertEquals(TrainerAction.Character('й'), model.leftRows[0][1].tap)
        assertTrue(model.caption.contains("RU"))
        assertTrue(model.caption.contains("текст EN"))
    }

    @Test
    fun fingerOnLayerShowsThatLayerBeforeTheLatch() {
        val model = keyboardModel(
            TypingSession(Language.EN, "a"),
            BoardLive(screenLower = true),
        )
        assertEquals("(", model.leftRows[0][2].legend)
        assertEquals("7", model.rightRows[0][0].legend)
        assertTrue(model.caption.contains("знаки"))
    }
}
