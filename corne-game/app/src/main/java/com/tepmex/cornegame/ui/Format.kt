package com.tepmex.cornegame.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatWpm(wpm: Double): String = String.format(Locale.getDefault(), "%.1f", wpm)

fun formatAccuracy(percent: Double): String = String.format(Locale.getDefault(), "%.0f%%", percent)

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(Locale.getDefault(), minutes, seconds)
}

fun formatChar(ch: Char): String = if (ch == ' ') "␣" else ch.toString()

fun formatWhen(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
