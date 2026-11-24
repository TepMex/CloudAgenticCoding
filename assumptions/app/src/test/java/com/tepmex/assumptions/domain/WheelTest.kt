package com.tepmex.assumptions.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelTest {
    @Test
    fun mildNamesRunClockwiseFromTheTop() {
        assertEquals(
            listOf(
                "Спокойствие",
                "Признание",
                "Опасение",
                "Отвлечение",
                "Задумчивость",
                "Скука",
                "Досада",
                "Интерес",
            ),
            Emotions.families.map { it.mild.name },
        )
    }

    @Test
    fun deeperBandsAndDyadsMatchTheReferenceWheel() {
        assertEquals(
            listOf("Радость", "Доверие", "Страх", "Удивление", "Печаль", "Брезгливость", "Гнев", "Ожидание"),
            Emotions.families.map { it.medium.name },
        )
        assertEquals(
            listOf("Экстаз", "Восхищение", "Ужас", "Изумление", "Горе", "Отвращение", "Ярость", "Бдительность"),
            Emotions.families.map { it.intense.name },
        )
        assertEquals(
            listOf(
                "Любовь",
                "Подчинение",
                "Трепет",
                "Неодобрение",
                "Сожаление",
                "Презрение",
                "Агрессия",
                "Оптимизм",
            ),
            Emotions.dyads.map { it.emotion.name },
        )
    }

    @Test
    fun everyEmotionHasAUniqueId() {
        val ids = Emotions.families.flatMap { it.bands.map { band -> band.id } } +
            Emotions.dyads.map { it.emotion.id }
        assertEquals(32, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("joy-0", Emotions.families[0].mild.id)
        assertEquals("dyad-love", Emotions.dyadAfter(0).emotion.id)
        assertEquals("dyad-optimism", Emotions.dyadBefore(0).emotion.id)
    }

    @Test
    fun lightPetalsUseInkAndDarkPetalsUsePaper() {
        assertEquals(0xFF1C1915, labelArgb(Emotions.families[0].intense.colorArgb))
        assertEquals(0xFFFFFBF7, labelArgb(Emotions.families[2].intense.colorArgb))
        assertTrue(Emotions.dyads.all { labelArgb(it.emotion.colorArgb) == 0xFF1C1915 })
    }

    @Test
    fun closedWheelIsEightMildSectorsAndNothingElse() {
        val wedges = WheelLayout.layout(WheelState())
        assertEquals(8, wedges.size)
        assertEquals(
            Emotions.families.map { it.mild.id },
            wedges.map { it.emotionId },
        )
        assertEquals(360f, wedges.sumOf { it.sweepDeg.toDouble() }.toFloat(), 0.05f)
        assertTrue(wedges.all { (it.hit as WheelHit.PrimaryBand).depth == 0 })
        emotionsAt(WheelState(), radius = 0.7f).let { names ->
            assertEquals("Спокойствие", names.getValue(0f))
            assertEquals("Признание", names.getValue(45f))
            assertEquals("Опасение", names.getValue(90f))
            assertEquals("Отвлечение", names.getValue(135f))
            assertEquals("Задумчивость", names.getValue(180f))
            assertEquals("Скука", names.getValue(225f))
            assertEquals("Досада", names.getValue(270f))
            assertEquals("Интерес", names.getValue(315f))
        }
        assertTrue(WheelLayout.hitTestPolar(WheelState(), 0f, 0.05f) is WheelHit.Hub)
    }

    @Test
    fun pressOpensOneLevelAndTheTwoNeighboringDyads() {
        val opened = WheelReducer.reduce(WheelState(), WheelHit.PrimaryBand(0, 0))
        assertEquals(0, opened.focusedIndex)
        assertEquals(1, opened.revealedDepth)
        assertEquals("joy-0", opened.selectedId)
        val ids = WheelLayout.layout(opened).map { it.emotionId }.toSet()
        assertTrue(ids.contains("joy-0"))
        assertTrue(ids.contains("joy-1"))
        assertFalse(ids.contains("joy-2"))
        assertTrue(ids.contains("dyad-love"))
        assertTrue(ids.contains("dyad-optimism"))
        assertEquals("Радость", emotionAt(opened, 0f, 0.3f))
        assertEquals("Спокойствие", emotionAt(opened, 0f, 0.8f))
        assertEquals("Любовь", emotionAt(opened, 30f, 0.8f))
        assertEquals("Оптимизм", emotionAt(opened, 330f, 0.8f))
    }

    @Test
    fun stayingOnTheMildBandDoesNotOpenTheIntenseOne() {
        val once = WheelReducer.reduce(WheelState(), WheelHit.PrimaryBand(0, 0))
        val again = WheelReducer.reduce(once, WheelHit.PrimaryBand(0, 0))
        assertEquals(1, again.revealedDepth)
        assertEquals("joy-0", again.selectedId)
        assertFalse(WheelLayout.layout(again).any { it.emotionId == "joy-2" })
    }

    @Test
    fun movingOntoTheOpenedLevelRevealsTheNext() {
        val opened = WheelReducer.reduce(WheelState(), WheelHit.PrimaryBand(0, 0))
        val deeper = WheelReducer.reduce(opened, WheelHit.PrimaryBand(0, 1))
        assertEquals(2, deeper.revealedDepth)
        assertEquals("joy-1", deeper.selectedId)
        assertTrue(WheelLayout.layout(deeper).any { it.emotionId == "joy-2" })
        assertEquals("Экстаз", emotionAt(deeper, 0f, 0.25f))
        val onIntense = WheelReducer.reduce(deeper, WheelHit.PrimaryBand(0, 2))
        assertEquals(2, onIntense.revealedDepth)
        assertEquals("joy-2", onIntense.selectedId)
    }

    @Test
    fun dyadSelectsWithoutOpeningFurtherAndHubCollapses() {
        val opened = WheelReducer.reduce(WheelState(), WheelHit.PrimaryBand(0, 0))
        val dyad = WheelReducer.reduce(opened, WheelHit.Dyad("dyad-love"))
        assertEquals(1, dyad.revealedDepth)
        assertEquals("dyad-love", dyad.selectedId)
        assertEquals(WheelState(), WheelReducer.reduce(dyad, WheelHit.Hub))
    }

    @Test
    fun anotherSectorResetsToASingleOpenLevel() {
        var state = WheelReducer.reduce(WheelState(), WheelHit.PrimaryBand(0, 0))
        state = WheelReducer.reduce(state, WheelHit.PrimaryBand(0, 1))
        state = WheelReducer.reduce(state, WheelHit.PrimaryBand(6, 0))
        assertEquals(6, state.focusedIndex)
        assertEquals(1, state.revealedDepth)
        assertEquals("anger-0", state.selectedId)
        assertEquals("Досада", Emotions.find(state.selectedId!!)?.name)
        assertFalse(WheelLayout.layout(state).any { it.emotionId == "anger-2" })
    }

    @Test
    fun openWheelCoversTheOuterRingWithoutOverlap() {
        val closed = WheelState()
        val open = WheelReducer.reduce(closed, WheelHit.PrimaryBand(3, 0))
        assertCoverage(closed, 0.82f)
        assertCoverage(open, 0.9f)
        assertNoOverlap(WheelLayout.layout(closed))
        assertNoOverlap(WheelLayout.layout(open))
        val deep = WheelReducer.reduce(open, WheelHit.PrimaryBand(3, 1))
        assertCoverage(deep, 0.9f)
        assertNoOverlap(WheelLayout.layout(deep))
    }

    @Test
    fun pixelHitAgreesWithTheTopSector() {
        val hit = WheelLayout.hitTest(WheelState(), 200f, 200f, 100f, 30f)
        assertEquals(WheelHit.PrimaryBand(0, 0), hit)
    }

    private fun emotionsAt(state: WheelState, radius: Float): Map<Float, String> =
        listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).associateWith { angle ->
            emotionAt(state, angle, radius)
        }

    private fun emotionAt(state: WheelState, angle: Float, radius: Float): String {
        val hit = WheelLayout.hitTestPolar(state, angle, radius)
        val id = when (hit) {
            is WheelHit.PrimaryBand -> Emotions.band(hit.index, hit.depth).id
            is WheelHit.Dyad -> hit.id
            else -> error("No emotion at $angle / $radius, hit=$hit")
        }
        return Emotions.find(id)!!.name
    }

    private fun assertCoverage(state: WheelState, radius: Float) {
        for (step in 0 until 360) {
            val angle = step + 0.2f
            assertNotNull(
                "uncovered angle $angle at r=$radius for $state",
                WheelLayout.hitTestPolar(state, angle, radius),
            )
        }
    }

    private fun assertNoOverlap(wedges: List<Wedge>) {
        for (left in wedges.indices) {
            for (right in left + 1 until wedges.size) {
                val a = wedges[left]
                val b = wedges[right]
                if (a.outer <= b.inner + 0.001f || b.outer <= a.inner + 0.001f) continue
                val steps = (a.sweepDeg * 2).toInt().coerceAtLeast(2)
                for (step in 1 until steps) {
                    val angle = a.startDeg + a.sweepDeg * step / steps
                    val radius = (maxOf(a.inner, b.inner) + minOf(a.outer, b.outer)) / 2f
                    assertFalse(
                        "${a.emotionId} overlaps ${b.emotionId} at $angle / $radius",
                        a.contains(angle, radius) && b.contains(angle, radius),
                    )
                }
            }
        }
    }
}

class JournalRulesTest {
    @Test
    fun pleasureScaleIncludesNeutralAndRejectsTheEndsOutside() {
        assertEquals(11, PleasureScale.values.size)
        assertEquals(-5, PleasureScale.values.first())
        assertEquals(5, PleasureScale.values.last())
        assertTrue(PleasureScale.values.contains(0))
        assertFalse(PleasureScale.contains(-6))
        assertFalse(PleasureScale.contains(6))
    }

    @Test
    fun expectationRequiresEventOutcomeAndAnExplicitScore() {
        assertEquals(
            setOf(ExpectationField.Event, ExpectationField.Outcome, ExpectationField.Score),
            expectationErrors("  ", "", null),
        )
        assertEquals(setOf(ExpectationField.Score), expectationErrors("экзамен", "сдам", 9))
        assertTrue(expectationErrors(" экзамен ", "сдам", -2).isEmpty())
    }

    @Test
    fun scoresFormatWithASignedScale() {
        assertEquals("−5", formatScore(-5))
        assertEquals("0", formatScore(0))
        assertEquals("+3", formatScore(3))
    }

    @Test
    fun dayLabelUsesTheDeviceZone() {
        val zone = java.time.ZoneId.of("Asia/Omsk")
        val today = java.time.LocalDate.of(2026, 11, 24)
        val morning = today.atTime(9, 15).atZone(zone).toInstant().toEpochMilli()
        val yesterday = today.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Сегодня", dayLabel(morning, zone, today))
        assertEquals("Вчера", dayLabel(yesterday, zone, today))
        assertEquals("09:15", clockLabel(morning, zone))
        assertNull(signOf(0))
    }
}
