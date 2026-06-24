package com.tepmex.ankidashboard.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE
private val MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM")

object DashboardAnalytics {

    private const val REVLOG_LEARN = 0
    private const val REVLOG_REVIEW = 1
    private const val REVLOG_RELRN = 2
    private const val REVLOG_CRAM = 3

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

    /**
     * Reconstruct daily review debt from revlog: after each review at P with interval I,
     * the card is due at P+I until the next review at L (when L > P+I).
     */
    fun buildDebtHistoryFromRevlog(
        cardReviews: Map<Long, List<CardReview>>,
        crtSec: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Pair<String, Int>> {
        val dailyDebt = HashMap<String, Int>()
        val nowMs = System.currentTimeMillis()

        for (reviews in cardReviews.values) {
            if (reviews.isEmpty()) continue
            val sorted = reviews.sortedBy { it.id }
            for (i in sorted.indices) {
                val review = sorted[i]
                if (review.type == REVLOG_CRAM) continue
                val dueMs = dueMsAfterReview(review, crtSec)
                val endMs = sorted.getOrNull(i + 1)?.id ?: nowMs
                if (dueMs < endMs) {
                    addDebtPeriod(dailyDebt, dueMs, endMs, startDate, endDate)
                }
            }
        }

        val result = ArrayList<Pair<String, Int>>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            val dayStr = current.toString()
            result.add(dayStr to (dailyDebt[dayStr] ?: 0))
            current = current.plusDays(1)
        }
        return result
    }

    private fun dueMsAfterReview(review: CardReview, crtSec: Long): Long {
        when (review.type) {
            REVLOG_REVIEW -> {
                val reviewColDay = collectionDayAt(review.id, crtSec)
                return collectionDayStartMs(reviewColDay + review.ivl, crtSec)
            }
            REVLOG_LEARN, REVLOG_RELRN -> {
                val minutes = review.ivl.coerceAtLeast(1)
                return review.id + minutes.toLong() * 60_000
            }
            else -> return inferDueMs(review, crtSec)
        }
    }

    private fun inferDueMs(review: CardReview, crtSec: Long): Long {
        if (review.ivl <= 0) return review.id
        if (review.ease == 1 || review.ivl < 1440) {
            return review.id + review.ivl.toLong() * 60_000
        }
        val reviewColDay = collectionDayAt(review.id, crtSec)
        return collectionDayStartMs(reviewColDay + review.ivl, crtSec)
    }

    private fun addDebtPeriod(
        dailyDebt: MutableMap<String, Int>,
        dueMs: Long,
        endMs: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ) {
        if (dueMs >= endMs) return
        var day = millisToLocalDate(dueMs)
        if (day.isBefore(startDate)) day = startDate
        val lastDay = minOf(millisToLocalDate(endMs), endDate)
        while (!day.isAfter(lastDay)) {
            val key = day.format(DAY_FMT)
            dailyDebt[key] = (dailyDebt[key] ?: 0) + 1
            day = day.plusDays(1)
        }
    }

    private fun collectionDayAt(ms: Long, crtSec: Long): Int =
        ((ms / 1000 - crtSec) / 86400).toInt()

    private fun collectionDayStartMs(collectionDay: Int, crtSec: Long): Long =
        (crtSec + collectionDay.toLong() * 86400) * 1000

    private fun millisToLocalDate(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun millisToDay(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY_FMT)

    fun plotDateRange(): Pair<LocalDate, LocalDate> {
        val end = LocalDate.now()
        val start = end.minus(730, ChronoUnit.DAYS)
        return start to end
    }
}
