package com.tepmex.tinglistories.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

const val LISTEN_WINDOW_SHORT = 5
const val LISTEN_WINDOW_LONG = 10

/**
 * One answered story on the statistics chart.
 * [indicator] is the percent of correct answers divided by the listen count.
 * [window5] and [window10] average that indicator over the last 5 and 10
 * counted listens and stay absent until the window is full.
 */
data class StatsPoint(
    val catalogNumber: Int,
    val indicator: Double,
    val window5: Double?,
    val window10: Double?,
)

/** Integer ticks for the indicator axis. [top] is the highest tick. */
data class ListenAxis(
    val top: Int,
    val ticks: List<Int>,
)

private data class ScoredStory(
    val catalogNumber: Int,
    val listens: Int,
    val indicator: Double,
)

/**
 * Answered stories only, in catalog order.
 * A story with no grade, a grade max of 0, or no listens is left out, so its
 * plays do not fill a window and do not break the line.
 */
fun storyStats(library: Library): List<StatsPoint> {
    val scored = scoredStories(library)
    val listens = scored.map { it.listens }
    val indicators = scored.map { it.indicator }
    val short = trailingListenWindow(listens, indicators, LISTEN_WINDOW_SHORT)
    val long = trailingListenWindow(listens, indicators, LISTEN_WINDOW_LONG)
    return scored.mapIndexed { index, story ->
        StatsPoint(
            catalogNumber = story.catalogNumber,
            indicator = story.indicator,
            window5 = short[index],
            window10 = long[index],
        )
    }
}

/** `score / max × 100`. A missing grade, or a max of zero, has no percent. */
fun correctPercent(progress: StoryProgress): Double? {
    val grade = progress.evaluation ?: return null
    if (grade.max <= 0) return null
    return grade.score.coerceIn(0, grade.max) * 100.0 / grade.max.toDouble()
}

/**
 * Percent of correct answers divided by the listen count.
 * Absent when [correctPercent] is absent or the story was never played.
 */
fun listenScore(progress: StoryProgress): Double? {
    val percent = correctPercent(progress) ?: return null
    val listens = progress.listenCount
    if (listens <= 0) return null
    return percent / listens.toDouble()
}

/** Plays that enter [listenScore]. Listens without an answer are not here. */
fun countedListenCount(library: Library): Int =
    scoredStories(library).sumOf { it.listens }

/** Plays on stories that have no grade yet. */
fun unansweredListenCount(library: Library): Int =
    library.stories.sumOf { story ->
        val progress = library.progressOf(story.id)
        if (progress.evaluation == null) progress.listenCount.coerceAtLeast(0) else 0
    }

/** Mean of the per-story indicator. Empty when nothing was answered. */
fun meanIndicator(points: List<StatsPoint>): Double? =
    if (points.isEmpty()) null else points.map { it.indicator }.average()

fun statsAccounting(countedListens: Int, scoredStories: Int, unansweredListens: Int): String {
    val base = "Учтено ${listensLabel(countedListens)}. В расчёте ${storiesLabel(scoredStories)} с ответом."
    if (unansweredListens <= 0) return base
    return "$base Без ответа не учтено ${listensLabel(unansweredListens)}."
}

/** One decimal when needed, with a comma. Non-positive values become 0. */
fun formatStat(value: Double): String {
    val scaled = (value.coerceAtLeast(0.0) * 10.0).roundToInt()
    val whole = scaled / 10
    val frac = scaled % 10
    return if (frac == 0) whole.toString() else "$whole,$frac"
}

/**
 * Mean of [values] over the last [window] listens, ending at each index.
 * Every listen of a story carries that story’s value. The oldest story in the
 * window may contribute only some of its listens. The result stays empty until
 * [window] listens have accumulated.
 */
fun trailingListenWindow(listens: List<Int>, values: List<Double>, window: Int): List<Double?> {
    require(window >= 1) { "window must be at least 1" }
    require(listens.size == values.size) { "listens and values must match" }
    return List(values.size) { index ->
        var remaining = window
        var weighted = 0.0
        var covered = 0
        var cursor = index
        while (cursor >= 0 && remaining > 0) {
            val take = minOf(listens[cursor].coerceAtLeast(0), remaining)
            if (take > 0) {
                weighted += values[cursor] * take
                covered += take
                remaining -= take
            }
            cursor--
        }
        if (covered < window) null else weighted / window.toDouble()
    }
}

/** Highest value among the indicator and both windows. */
fun chartPeak(points: List<StatsPoint>): Double {
    var peak = 0.0
    for (point in points) {
        if (point.indicator > peak) peak = point.indicator
        val short = point.window5
        if (short != null && short > peak) peak = short
        val long = point.window10
        if (long != null && long > peak) peak = long
    }
    return peak
}

/** Nice integer scale that covers [peak]. A flat zero series still has a top of 1. */
fun listenAxis(peak: Double): ListenAxis {
    if (peak.isNaN() || peak <= 0.0) return ListenAxis(top = 1, ticks = listOf(0, 1))
    val raw = ceil(peak).toInt().coerceAtLeast(1)
    if (raw <= 4) return ListenAxis(top = raw, ticks = (0..raw).toList())
    val step = niceStep(raw / 4.0)
    val top = ((raw + step - 1) / step) * step
    return ListenAxis(top = top, ticks = (0..top step step).toList())
}

/** Indices (0-based) whose catalog numbers are labeled when the axis is crowded. */
fun catalogLabelIndices(count: Int): List<Int> {
    if (count <= 1) return List(count) { 0 }
    if (count <= 6) return (0 until count).toList()
    val step = ((count - 1) / 4).coerceAtLeast(1)
    val labels = mutableListOf(0)
    var index = step
    while (index < count - 1) {
        if (count - 1 - index > step / 2) labels += index
        index += step
    }
    labels += count - 1
    return labels
}

/** Inclusive runs of present values. A null splits the line. */
fun valueRuns(values: List<Double?>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    values.forEachIndexed { index, value ->
        if (value == null) {
            if (start >= 0) {
                runs += (start until index)
                start = -1
            }
        } else if (start < 0) {
            start = index
        }
    }
    if (start >= 0) runs += (start until values.size)
    return runs
}

fun statsChartDescription(points: List<StatsPoint>): String {
    val sentence = if (points.isEmpty()) {
        "Историй с ответом нет."
    } else {
        "Средний показатель: ${points.map { it.indicator }.average().roundToInt()}."
    }
    return "График показателя: процент верных ответов, делённый на число прослушиваний. " +
        "Окна 5 и 10 — среднее по последним прослушиваниям. $sentence"
}

private fun scoredStories(library: Library): List<ScoredStory> =
    library.stories.mapIndexedNotNull { index, story ->
        val progress = library.progressOf(story.id)
        val indicator = listenScore(progress) ?: return@mapIndexedNotNull null
        ScoredStory(
            catalogNumber = index + 1,
            listens = progress.listenCount,
            indicator = indicator,
        )
    }

private fun niceStep(rough: Double): Int {
    val safe = rough.coerceAtLeast(1.0)
    val magnitude = 10.0.pow(floor(log10(safe)))
    val fraction = safe / magnitude
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return (nice * magnitude).toInt().coerceAtLeast(1)
}
