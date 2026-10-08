package com.tepmex.cornegame.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    @Test
    fun dictionariesStayOnTheBaseLayer() {
        listOf(Language.EN, Language.RU).forEach { language ->
            val words = usableWords(language)
            assertTrue(words.size >= 40)
            assertEquals(words.size, words.toSet().size)
            val allowed = baseLetters(language)
            words.forEach { word ->
                assertTrue(word.length in 3..10)
                assertTrue(word.all { it in allowed })
            }
        }
        assertFalse(usableWords(Language.RU).any { 'ё' in it || 'Ё' in it || 'х' in it || 'ъ' in it })
    }

    @Test
    fun promptCoversLowerCaseShiftPunctuationAndLanguageSwitch() {
        listOf(Language.EN, Language.RU).forEach { language ->
            val prompt = generatePrompt(language, Random(1))
            assertTrue(prompt.startsWith("("))
            assertTrue(LANGUAGE_SWITCH in prompt)
            assertFalse(prompt.contains("  "))
            val switchAt = prompt.indexOf(LANGUAGE_SWITCH)
            val head = prompt.substring(0, switchAt)
            val tail = prompt.substring(switchAt + 1)
            assertTrue(head.any { it.isLowerCase() && it.isLetter() })
            assertTrue(head.any { it.isUpperCase() && it.isLetter() })
            assertTrue(head.any { it.isDigit() })
            assertTrue('(' in head && ')' in head)
            assertTrue(tail.any { it.isLetter() })
            assertTrue(tail.trim().endsWith("."))
            var current = language
            prompt.forEach { char ->
                if (char == LANGUAGE_SWITCH) {
                    current = current.other()
                } else {
                    assertNotNull(strokeFor(char, current))
                }
            }
        }
    }

    @Test
    fun correctTypingCompletesAndComputesWpm() {
        var session = TypingSession(Language.EN, "cat dog")
        val text = "cat dog"
        text.forEachIndexed { index, ch ->
            val (next, effect) = session.apply(TrainerAction.Character(ch), 10_000L + index * 10_000L)
            session = next
            if (index < text.lastIndex) assertFalse(effect.completed) else assertTrue(effect.completed)
        }
        assertTrue(session.completed)
        assertEquals(0, session.errors)
        // 7 знаков за минуту от первого до последнего: (7/5) / 1 = 1.4
        assertEquals(1.4, session.stats(999_999).wpm, 0.001)
        assertEquals(60_000L, session.stats(999_999).elapsedMs)
        assertEquals(100.0, session.stats(999_999).accuracyPercent, 0.001)
    }

    @Test
    fun mistakeDoesNotAdvanceAndEnterCountsAsSpace() {
        var session = TypingSession(Language.EN, "a b")
        val (wrong, effect) = session.apply(TrainerAction.Character('x'), 1_000)
        assertTrue(effect.mistake)
        assertEquals(0, wrong.index)
        assertEquals(1, wrong.errors)
        session = wrong.apply(TrainerAction.Character('a'), 2_000).first
        session = session.apply(TrainerAction.Character(' '), 3_000).first
        assertEquals(2, session.index)
        assertEquals('b', session.nextChar)
        val buckets = topMisses(session.misses)
        assertEquals('a', buckets.single().expected)
        assertEquals('x', buckets.single().pressedInstead.single().actual)
    }

    @Test
    fun backspaceUndoesACorrectCharacterWithoutChangingAccuracy() {
        var session = TypingSession(Language.EN, "ab")
        session = session.apply(TrainerAction.Character('a'), 1_000).first
        val accuracy = session.stats(1_000).accuracyPercent
        session = session.apply(TrainerAction.Backspace, 2_000).first
        assertEquals(0, session.index)
        assertEquals(1, session.correctPresses)
        assertEquals(accuracy, session.stats(2_000).accuracyPercent, 0.001)
        val (ignored, _) = session.apply(TrainerAction.Backspace, 3_000)
        assertEquals(0, ignored.index)
        assertEquals(1_000L, ignored.startedAtMs)
        val (beforeStart, _) = TypingSession(Language.EN, "ab").apply(TrainerAction.Backspace, 3_000)
        assertEquals(0, beforeStart.index)
        assertNull(beforeStart.startedAtMs)
    }

    @Test
    fun topMissesKeepsTenBuckets() {
        val target = "abcdefghijk"
        var session = TypingSession(Language.EN, target)
        target.forEachIndexed { index, expected ->
            repeat(index + 1) {
                session = session.apply(TrainerAction.Character('z'), 1).first
            }
            session = session.apply(TrainerAction.Character(expected), 1).first
        }
        val top = topMisses(session.misses)
        assertEquals(10, top.size)
        assertEquals('k', top.first().expected)
        assertEquals(11, top.first().total)
        assertFalse(top.any { it.expected == 'a' })
    }

    @Test
    fun shiftLatchAndLowerLayerThenLanguageSwitch() {
        var session = TypingSession(Language.EN, "A(2)${LANGUAGE_SWITCH}й")
        assertNull(session.startedAtMs)
        val (armed, armEffect) = session.apply(TrainerAction.Shift, 1_000)
        assertFalse(armEffect.mistake)
        assertEquals(Latch.SHIFT, armed.latch)
        assertNull(armed.startedAtMs)
        session = armed.apply(TrainerAction.Character('A'), 2_000).first
        assertEquals(1, session.index)
        assertEquals(Latch.NONE, session.latch)

        val (notShift, shiftMiss) = session.apply(TrainerAction.Shift, 2_500)
        assertTrue(shiftMiss.mistake)
        session = notShift
        assertEquals(1, session.errors)

        actionsToType("(2)", Language.EN).forEachIndexed { step, action ->
            session = session.apply(action, 3_000L + step).first
        }
        assertEquals(LANGUAGE_SWITCH, session.nextChar)
        assertEquals(Language.EN, session.typingLanguage)

        session = session.apply(TrainerAction.Alt, 11_000).first
        assertTrue(session.altArmed)
        session = session.apply(TrainerAction.Shift, 12_000).first
        assertEquals(Language.RU, session.typingLanguage)
        assertEquals('й', session.nextChar)
        session = session.apply(TrainerAction.Character('й'), 13_000).first
        assertTrue(session.completed)
        assertEquals(1, session.errors)
    }

    @Test
    fun backspaceUndoesLanguageSwitchAndCancelsLatchFirst() {
        var session = TypingSession(Language.EN, "a${LANGUAGE_SWITCH}б")
        session = session.apply(TrainerAction.Character('a'), 1_000).first
        session = session.apply(TrainerAction.Alt, 2_000).first
        session = session.apply(TrainerAction.Backspace, 3_000).first
        assertFalse(session.altArmed)
        assertEquals(1, session.index)
        session = session.apply(TrainerAction.AltShift, 4_000).first
        assertEquals(Language.RU, session.typingLanguage)
        session = session.apply(TrainerAction.Backspace, 5_000).first
        assertEquals(1, session.index)
        assertEquals(Language.EN, session.typingLanguage)
        assertEquals(LANGUAGE_SWITCH, session.nextChar)
    }

    @Test
    fun hardwareMapperKeepsCaseAndAltShift() {
        assertEquals(TrainerAction.Character(' '), mapHardwareKey(AndroidKeyCodes.SPACE, 0))
        assertNull(mapHardwareKey(AndroidKeyCodes.ENTER, '\n'.code))
        assertNull(mapHardwareKey(AndroidKeyCodes.NUMPAD_ENTER, 0))
        assertEquals(TrainerAction.Backspace, mapHardwareKey(AndroidKeyCodes.DEL, 0))
        assertEquals(TrainerAction.Character('Q'), mapHardwareKey(45, 'Q'.code))
        assertEquals(TrainerAction.Character('й'), mapHardwareKey(45, 'й'.code))
        assertEquals(TrainerAction.Character('Й'), mapHardwareKey(45, 'Й'.code))
        assertEquals(TrainerAction.Character(';'), mapHardwareKey(74, ';'.code))
        assertNull(mapHardwareKey(AndroidKeyCodes.SHIFT_LEFT, 0))
        assertEquals(
            TrainerAction.AltShift,
            mapHardwareKey(AndroidKeyCodes.SHIFT_LEFT, 0, altPressed = true),
        )
        assertNull(mapHardwareKey(61, 0))
    }
}
