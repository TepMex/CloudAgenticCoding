package com.tepmex.heilauncher.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * One pixel per local day from 21.01.1993 through 21.01.2083.
 * Days through today are filled. Days after today through 21.01.2063 are the
 * lighter unfilled run. Days after 21.01.2063 are the darker unfilled run.
 */
object LifeSpan {
    val birth: LocalDate = LocalDate.of(1993, 1, 21)
    val nearEnd: LocalDate = LocalDate.of(2063, 1, 21)
    val farEnd: LocalDate = LocalDate.of(2083, 1, 21)

    /** Inclusive day count from [birth] through [farEnd]. */
    val dayCount: Int = (ChronoUnit.DAYS.between(birth, farEnd) + 1).toInt()

    /** Index of 21.01.2063, the last lighter pixel. */
    val nearEndIndex: Int = ChronoUnit.DAYS.between(birth, nearEnd).toInt()

    enum class Pixel { Filled, Near, Far }

    /**
     * Index of [today] in the bar, or -1 when [today] is before [birth].
     * Clamped to the last pixel when [today] is after [farEnd].
     */
    fun todayIndex(today: LocalDate): Int = when {
        today.isBefore(birth) -> -1
        today.isAfter(farEnd) -> dayCount - 1
        else -> ChronoUnit.DAYS.between(birth, today).toInt()
    }

    fun pixel(index: Int, today: LocalDate): Pixel {
        val filledThrough = todayIndex(today)
        return when {
            index <= filledThrough -> Pixel.Filled
            index <= nearEndIndex -> Pixel.Near
            else -> Pixel.Far
        }
    }

    /**
     * Whole local civil hours from [now] until 21.01.2063 00:00.
     * A partial hour is dropped. Zero once that midnight has been reached.
     * Daylight-saving shifts are ignored: every local day counts as 24 hours.
     */
    fun hoursUntilNearEnd(now: LocalDateTime): Long {
        val seconds = ChronoUnit.DAYS.between(now.toLocalDate(), nearEnd) * 86_400L -
            now.toLocalTime().toSecondOfDay()
        return (seconds / 3_600L).coerceAtLeast(0L)
    }

    data class Grid(val columns: Int, val rows: Int)

    /**
     * Pack [dayCount] pixels into a strip [widthPx] wide.
     * Column count follows the width so the hours label beside the bar can keep
     * its own space; row count is whatever is left to place every day.
     */
    fun grid(widthPx: Int): Grid {
        val columns = widthPx.coerceAtLeast(1).coerceAtMost(dayCount)
        val rows = (dayCount + columns - 1) / columns
        return Grid(columns, rows)
    }
}
