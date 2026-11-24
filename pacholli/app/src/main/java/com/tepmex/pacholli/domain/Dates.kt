package com.tepmex.pacholli.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object Dates {
    fun weekday(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Пн"
        DayOfWeek.TUESDAY -> "Вт"
        DayOfWeek.WEDNESDAY -> "Ср"
        DayOfWeek.THURSDAY -> "Чт"
        DayOfWeek.FRIDAY -> "Пт"
        DayOfWeek.SATURDAY -> "Сб"
        DayOfWeek.SUNDAY -> "Вс"
    }

    fun label(date: LocalDate): String = "$date (${weekday(date)})"

    fun timeLabel(minutes: Int?): String {
        if (minutes == null) return ""
        val hour = minutes / 60
        val minute = minutes % 60
        return "%02d:%02d".format(hour, minute)
    }

    /** Inclusive range. Null means every date. */
    fun range(anchor: LocalDate, span: Span): Pair<LocalDate, LocalDate>? = when (span) {
        Span.DAY -> anchor to anchor
        Span.WEEK -> {
            val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday to monday.plusDays(6)
        }
        Span.MONTH -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
        Span.YEAR -> LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
        Span.ALL -> null
    }

    fun shift(anchor: LocalDate, span: Span, direction: Int): LocalDate = when (span) {
        Span.DAY -> anchor.plusDays(direction.toLong())
        Span.WEEK -> anchor.plusWeeks(direction.toLong())
        Span.MONTH -> anchor.plusMonths(direction.toLong())
        Span.YEAR -> anchor.plusYears(direction.toLong())
        Span.ALL -> anchor
    }

    fun contains(date: LocalDate, anchor: LocalDate, span: Span): Boolean {
        val window = range(anchor, span) ?: return true
        return !date.isBefore(window.first) && !date.isAfter(window.second)
    }
}
