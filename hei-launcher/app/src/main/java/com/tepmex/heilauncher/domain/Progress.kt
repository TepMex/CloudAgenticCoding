package com.tepmex.heilauncher.domain

import java.time.LocalDate
import java.time.LocalTime

data class Progress(val percent: Int, val fraction: Double)

object DayYearProgress {
    private const val SECONDS_PER_DAY = 86_400

    /** Truncated share of the local day. 13:02 → 54. The bar uses the exact fraction. */
    fun day(time: LocalTime): Progress {
        val seconds = time.toSecondOfDay().coerceIn(0, SECONDS_PER_DAY - 1)
        val percent = (seconds * 100) / SECONDS_PER_DAY
        return Progress(percent, seconds / SECONDS_PER_DAY.toDouble())
    }

    /**
     * Completed local days over the length of the year, integer division.
     * 5 October 2026 → 75. The last day of the year is 100.
     * The bar matches the label.
     */
    fun year(date: LocalDate): Progress {
        val length = date.lengthOfYear()
        if (date.dayOfYear >= length) return Progress(100, 1.0)
        val completed = date.dayOfYear - 1
        val percent = (completed * 100) / length
        return Progress(percent.coerceIn(0, 100), percent / 100.0)
    }
}
