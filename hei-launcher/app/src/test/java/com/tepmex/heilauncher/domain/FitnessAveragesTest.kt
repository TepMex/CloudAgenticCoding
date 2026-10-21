package com.tepmex.heilauncher.domain

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitnessAveragesTest {
    @Test
    fun october2026ComparesEachWindowWithTheWindowEndingYesterday() {
        val today = LocalDate.of(2026, 10, 21)
        val steps = listOf(
            reading(2026, 1, 1, 1000.0),
            reading(2026, 10, 1, 2000.0),
            reading(2026, 10, 11, 100_000.0),
            reading(2026, 10, 19, 3000.0),
            reading(2026, 10, 20, 4000.0),
            reading(2026, 10, 21, 8000.0),
        )
        val table = FitnessAverages.compute(steps, emptyList(), today)

        assertEquals(118_000.0 / 6.0, table.steps.year.value!!, 0.001)
        assertEquals(AverageTrend.Down, table.steps.year.trend)
        assertEquals(117_000.0 / 5.0, table.steps.month.value!!, 0.001)
        assertEquals(AverageTrend.Down, table.steps.month.trend)
        assertEquals(5_000.0, table.steps.week.value!!, 0.001)
        assertEquals(AverageTrend.Up, table.steps.week.trend)
        assertEquals("19667", FitnessAverages.formatSteps(table.steps.year.value!!))
        assertEquals("23400", FitnessAverages.formatSteps(table.steps.month.value!!))
        assertEquals("5000", FitnessAverages.formatSteps(table.steps.week.value!!))
        assertNull(table.sleepHours.week.value)
        assertEquals(AverageTrend.Flat, table.sleepHours.week.trend)
    }

    @Test
    fun roundingToTheDisplayedPrecisionCanHideATinyChange() {
        val today = LocalDate.of(2026, 10, 21)
        val steps = listOf(
            reading(2026, 10, 19, 1000.0),
            reading(2026, 10, 20, 1000.0),
            reading(2026, 10, 21, 1001.0),
        )
        val week = FitnessAverages.compute(steps, emptyList(), today).steps.week
        assertEquals(3001.0 / 3.0, week.value!!, 0.001)
        assertEquals(AverageTrend.Flat, week.trend)
        assertEquals("1000", FitnessAverages.formatSteps(week.value!!))
    }

    @Test
    fun januaryFirstStillComparesWithThePreviousPeriod() {
        val today = LocalDate.of(2026, 1, 1)
        val steps = listOf(
            reading(2025, 6, 1, 1000.0),
            reading(2025, 12, 31, 40.0),
            reading(2026, 1, 1, 80.0),
        )
        val table = FitnessAverages.compute(steps, emptyList(), today)
        assertEquals(80.0, table.steps.year.value!!, 0.001)
        assertEquals(AverageTrend.Down, table.steps.year.trend)
        assertEquals(80.0, table.steps.month.value!!, 0.001)
        assertEquals(AverageTrend.Up, table.steps.month.trend)
        assertEquals(60.0, table.steps.week.value!!, 0.001)
        assertEquals(AverageTrend.Up, table.steps.week.trend)
    }

    @Test
    fun missingYesterdayWindowDrawsNoTrend() {
        val today = LocalDate.of(2026, 10, 21)
        val steps = listOf(reading(2026, 10, 21, 10.0))
        val year = FitnessAverages.compute(steps, emptyList(), today).steps.year
        assertEquals(10.0, year.value!!, 0.001)
        assertEquals(AverageTrend.Flat, year.trend)
    }

    @Test
    fun newerSampleForTheSameDayReplacesTheOlderOne() {
        val today = LocalDate.of(2026, 10, 21)
        val steps = listOf(
            DailyReading(today, 10.0, updatedEpochSec = 5),
            DailyReading(today, 80.0, updatedEpochSec = 4),
            DailyReading(today, 30.0, updatedEpochSec = 9),
        )
        val year = FitnessAverages.compute(steps, emptyList(), today).steps.year
        assertEquals(30.0, year.value!!, 0.001)
    }

    @Test
    fun sleepHoursKeepTwoDecimals() {
        val today = LocalDate.of(2026, 10, 21)
        val sleep = listOf(
            reading(2026, 10, 19, 8.0),
            reading(2026, 10, 20, 7.1),
            reading(2026, 10, 21, 6.0),
        )
        val week = FitnessAverages.compute(emptyList(), sleep, today).sleepHours.week
        assertEquals((8.0 + 7.1 + 6.0) / 3.0, week.value!!, 0.001)
        assertEquals(AverageTrend.Down, week.trend)
        assertEquals("7.03", FitnessAverages.formatSleepHours(week.value!!, Locale.US))
        assertEquals("7,03", FitnessAverages.formatSleepHours(week.value!!, Locale("ru")))
        assertEquals("7.22", FitnessAverages.formatSleepHours(433.0 / 60.0, Locale.US))
    }

    @Test
    fun historyStartsEarlyEnoughForYesterdayWindows() {
        assertEquals(LocalDate.of(2026, 1, 1), FitnessWindows.historyStart(LocalDate.of(2026, 10, 21)))
        assertEquals(LocalDate.of(2025, 1, 1), FitnessWindows.historyStart(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2025, 12, 29), FitnessWindows.historyStart(LocalDate.of(2026, 1, 5)))
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 1, 5) to LocalDate.of(2026, 1, 8),
                LocalDate.of(2026, 1, 9) to LocalDate.of(2026, 1, 10),
            ),
            FitnessWindows.chunks(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), 4),
        )
    }

    private fun reading(year: Int, month: Int, day: Int, value: Double) =
        DailyReading(LocalDate.of(year, month, day), value)
}
