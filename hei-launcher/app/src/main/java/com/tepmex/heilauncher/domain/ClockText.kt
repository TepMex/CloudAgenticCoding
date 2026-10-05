package com.tepmex.heilauncher.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ClockFormat {
    SYSTEM,
    H12,
    H24,
}

fun formatClock(
    time: LocalTime,
    format: ClockFormat,
    systemIs24Hour: Boolean,
    locale: Locale,
): String {
    val use24 = when (format) {
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
        ClockFormat.SYSTEM -> systemIs24Hour
    }
    val pattern = if (use24) "H:mm" else "h:mm a"
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

fun formatHomeDate(date: LocalDate, locale: Locale): String {
    if (locale.language == "en") {
        val dow = ENGLISH_DAYS[date.dayOfWeek.value - 1]
        val month = ENGLISH_MONTHS[date.monthValue - 1]
        return "$dow, ${ordinal(date.dayOfMonth)} $month"
    }
    if (locale.language == "ru") {
        val dow = RUSSIAN_DAYS[date.dayOfWeek.value - 1]
        val month = RUSSIAN_MONTHS[date.monthValue - 1]
        return "$dow, ${date.dayOfMonth} $month"
    }
    return DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date)
}

fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) {
        "th"
    } else {
        when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$day$suffix"
}

private val ENGLISH_DAYS = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
)

private val ENGLISH_MONTHS = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)

private val RUSSIAN_DAYS = listOf(
    "понедельник",
    "вторник",
    "среда",
    "четверг",
    "пятница",
    "суббота",
    "воскресенье",
)

private val RUSSIAN_MONTHS = listOf(
    "января",
    "февраля",
    "марта",
    "апреля",
    "мая",
    "июня",
    "июля",
    "августа",
    "сентября",
    "октября",
    "ноября",
    "декабря",
)
