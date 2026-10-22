package com.tepmex.myweek.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object DebugCalendar {
    /** The following Monday, even when [today] is already Monday. */
    fun nextMonday(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    /**
     * Tuesday of this week when [today] is Monday, so planning closes
     * and the sprint stays the current one. Any other day is already ordinary.
     */
    fun ordinaryDay(today: LocalDate): LocalDate =
        if (today.dayOfWeek == DayOfWeek.MONDAY) today.plusDays(1) else today

    fun nextDay(today: LocalDate): LocalDate = today.plusDays(1)
}
