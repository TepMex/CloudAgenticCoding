package com.tepmex.heilauncher.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.pow
import kotlin.math.round

enum class FitnessPeriod { Year, Month, Week }

enum class AverageTrend { Up, Down, Flat }

data class DailyReading(
    val date: LocalDate,
    val value: Double,
    val updatedEpochSec: Long = 0L,
)

data class AverageCell(val value: Double?, val trend: AverageTrend)

data class FitnessPeriodRow(
    val year: AverageCell,
    val month: AverageCell,
    val week: AverageCell,
)

data class FitnessAverages(
    val steps: FitnessPeriodRow,
    val sleepHours: FitnessPeriodRow,
) {
    companion object {
        fun compute(
            steps: List<DailyReading>,
            sleepHours: List<DailyReading>,
            today: LocalDate,
        ): FitnessAverages = FitnessAverages(
            steps = row(steps, today, places = 0),
            sleepHours = row(sleepHours, today, places = 2),
        )

        fun formatSteps(value: Double): String = round(value).toLong().toString()

        fun formatSleepHours(value: Double, locale: Locale): String =
            String.format(locale, "%.2f", roundPlaces(value, 2))

        private fun row(readings: List<DailyReading>, today: LocalDate, places: Int): FitnessPeriodRow {
            val byDate = HashMap<LocalDate, Double>()
            val winner = HashMap<LocalDate, Long>()
            for (reading in readings) {
                val previous = winner[reading.date]
                if (previous == null || reading.updatedEpochSec >= previous) {
                    winner[reading.date] = reading.updatedEpochSec
                    byDate[reading.date] = reading.value
                }
            }
            val yesterday = today.minusDays(1)
            return FitnessPeriodRow(
                year = cell(byDate, FitnessPeriod.Year, today, yesterday, places),
                month = cell(byDate, FitnessPeriod.Month, today, yesterday, places),
                week = cell(byDate, FitnessPeriod.Week, today, yesterday, places),
            )
        }

        private fun cell(
            byDate: Map<LocalDate, Double>,
            period: FitnessPeriod,
            today: LocalDate,
            yesterday: LocalDate,
            places: Int,
        ): AverageCell {
            val average = mean(byDate, FitnessWindows.range(period, today))
                ?: return AverageCell(null, AverageTrend.Flat)
            val previous = mean(byDate, FitnessWindows.range(period, yesterday))
            return AverageCell(average, trend(average, previous, places))
        }

        private fun mean(byDate: Map<LocalDate, Double>, range: ClosedRange<LocalDate>): Double? {
            var sum = 0.0
            var count = 0
            for ((date, value) in byDate) {
                if (date in range) {
                    sum += value
                    count++
                }
            }
            if (count == 0) return null
            return sum / count
        }

        private fun trend(average: Double, previous: Double?, places: Int): AverageTrend {
            if (previous == null) return AverageTrend.Flat
            val currentRounded = roundPlaces(average, places)
            val previousRounded = roundPlaces(previous, places)
            return when {
                currentRounded > previousRounded -> AverageTrend.Up
                currentRounded < previousRounded -> AverageTrend.Down
                else -> AverageTrend.Flat
            }
        }

        private fun roundPlaces(value: Double, places: Int): Double {
            val factor = 10.0.pow(places)
            return round(value * factor) / factor
        }
    }
}

data class FitnessHistory(
    val steps: List<DailyReading>,
    val sleepHours: List<DailyReading>,
)

/**
 * Calendar windows for the fitness table.
 * A week starts on Monday. Yesterday’s window is the same kind of period
 * ending yesterday, which is what the pyramid compares against.
 */
object FitnessWindows {
    const val CHUNK_DAYS = 30

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun range(period: FitnessPeriod, end: LocalDate): ClosedRange<LocalDate> = when (period) {
        FitnessPeriod.Year -> end.withDayOfYear(1)..end
        FitnessPeriod.Month -> end.withDayOfMonth(1)..end
        FitnessPeriod.Week -> weekStart(end)..end
    }

    /** First local day that any current or yesterday window still needs. */
    fun historyStart(today: LocalDate): LocalDate {
        val yesterday = today.minusDays(1)
        return FitnessPeriod.entries
            .flatMap { period -> listOf(range(period, today).start, range(period, yesterday).start) }
            .min()
    }

    fun chunks(from: LocalDate, to: LocalDate, size: Int = CHUNK_DAYS): List<Pair<LocalDate, LocalDate>> {
        require(size >= 1)
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<Pair<LocalDate, LocalDate>>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val chunkEnd = minOf(cursor.plusDays((size - 1).toLong()), to)
            out += cursor to chunkEnd
            cursor = chunkEnd.plusDays(1)
        }
        return out
    }
}

