package com.tepmex.assumptions.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class EmotionEntry(
    val id: Long,
    val emotionId: String,
    val createdAtEpochMs: Long,
)

data class Expectation(
    val id: Long,
    val eventText: String,
    val expectedOutcome: String,
    val beforeScore: Int,
    val afterScore: Int?,
    val afterNote: String,
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long?,
)

object PleasureScale {
    const val MIN: Int = -5
    const val MAX: Int = 5
    val values: List<Int> = (MIN..MAX).toList()

    fun contains(score: Int): Boolean = score in MIN..MAX
}

enum class ExpectationField { Event, Outcome, Score }

fun expectationErrors(event: String, outcome: String, score: Int?): Set<ExpectationField> {
    val errors = linkedSetOf<ExpectationField>()
    if (event.trim().isEmpty()) errors += ExpectationField.Event
    if (outcome.trim().isEmpty()) errors += ExpectationField.Outcome
    if (score == null || !PleasureScale.contains(score)) errors += ExpectationField.Score
    return errors
}

fun formatScore(score: Int): String = when {
    score > 0 -> "+$score"
    score < 0 -> "−${-score}"
    else -> "0"
}

private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun dayLabel(epochMs: Long, zone: ZoneId, today: LocalDate): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(dayFormatter)
    }
}

fun clockLabel(epochMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).format(clockFormatter)
