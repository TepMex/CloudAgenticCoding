package com.tepmex.myweek.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val locale = Locale.forLanguageTag("ru")
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
private val full = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
private val weekday = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)

fun weekRange(start: LocalDate): String {
    val end = start.plusDays(6)
    return when {
        start.month == end.month && start.year == end.year ->
            "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(monthYear)}"
        start.year == end.year ->
            "${start.format(dayMonth)} – ${end.format(full)}"
        else ->
            "${start.format(full)} – ${end.format(full)}"
    }
}

fun sprintDate(start: LocalDate): String = start.format(full)

fun todayLabel(date: LocalDate): String = date.format(weekday)
