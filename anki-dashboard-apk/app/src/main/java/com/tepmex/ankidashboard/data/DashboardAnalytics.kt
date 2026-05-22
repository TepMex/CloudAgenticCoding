package com.tepmex.ankidashboard.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE
private val MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM")

object DashboardAnalytics {

    fun extractFirstReviewDays(cardReviews: Map<Long, List<CardReview>>): Map<String, Int> {
        val dailyNew = linkedMapOf<String, Int>()
        for (reviews in cardReviews.values) {
            if (reviews.isEmpty()) continue
            val minId = reviews.minOf { it.id }
            val day = millisToDay(minId)
            dailyNew[day] = (dailyNew[day] ?: 0) + 1
        }
        return dailyNew
    }

    fun buildWordsLearnedSeries(
        cardReviews: Map<Long, List<CardReview>>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Pair<String, Int>> {
        val dailyNew = extractFirstReviewDays(cardReviews)
        var cumulative = 0
        for ((day, count) in dailyNew) {
            if (day < startDate.toString()) {
                cumulative += count
            }
        }
        val result = ArrayList<Pair<String, Int>>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            val dayStr = current.toString()
            result.add(dayStr to cumulative)
            cumulative += dailyNew[dayStr] ?: 0
            current = current.plusDays(1)
        }
        return result
    }

    fun newVocabByMonth(cardReviews: Map<Long, List<CardReview>>): List<Pair<String, Int>> {
        val dailyNew = extractFirstReviewDays(cardReviews)
        if (dailyNew.isEmpty()) return emptyList()

        val monthToCount = linkedMapOf<String, Int>()
        var earliest: LocalDate? = null
        for ((day, count) in dailyNew) {
            val d = LocalDate.parse(day, DAY_FMT)
            val monthKey = d.format(MONTH_FMT)
            monthToCount[monthKey] = (monthToCount[monthKey] ?: 0) + count
            if (earliest == null || d.isBefore(earliest)) {
                earliest = d
            }
        }
        val start = earliest!!.withDayOfMonth(1)
        val end = LocalDate.now().withDayOfMonth(1)
        val result = ArrayList<Pair<String, Int>>()
        var current = start
        while (!current.isAfter(end)) {
            val key = current.format(MONTH_FMT)
            result.add(key to (monthToCount[key] ?: 0))
            current = current.plusMonths(1)
        }
        return result
    }

    fun calculateReviewScore(cardReviews: Map<Long, List<CardReview>>, lastDays: Int = 7): Double {
        val cutoff = LocalDate.now().minusDays(lastDays.toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        var totalReviews = 0
        var totalTimeSeconds = 0.0
        for (reviews in cardReviews.values) {
            for (review in reviews) {
                if (review.id > cutoff) {
                    totalReviews++
                    totalTimeSeconds += review.time / 1000.0
                }
            }
        }
        return if (totalReviews == 0) 0.0 else totalTimeSeconds / totalReviews
    }

    fun calculateTotalHoursSpent(cardReviews: Map<Long, List<CardReview>>): Double {
        var totalMs = 0L
        for (reviews in cardReviews.values) {
            for (review in reviews) {
                totalMs += review.time
            }
        }
        return totalMs / (1000.0 * 60.0 * 60.0)
    }

    fun calculateLongMemory(cardReviews: Map<Long, List<CardReview>>): Int {
        var count = 0
        for (reviews in cardReviews.values) {
            val last = reviews.lastOrNull() ?: continue
            if (last.ivl > 360) count++
        }
        return count
    }

    fun mistakesByDay(cardReviews: Map<Long, List<CardReview>>): Map<String, Int> {
        val mistakes = linkedMapOf<String, Int>()
        for (reviews in cardReviews.values) {
            for (review in reviews) {
                if (review.ease == 1) {
                    val day = millisToDay(review.id)
                    mistakes[day] = (mistakes[day] ?: 0) + 1
                }
            }
        }
        return mistakes
    }

    private fun millisToDay(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY_FMT)

    fun plotDateRange(): Pair<LocalDate, LocalDate> {
        val end = LocalDate.now()
        val start = end.minus(730, ChronoUnit.DAYS)
        return start to end
    }
}
