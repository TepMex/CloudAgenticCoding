package com.tepmex.tinglistories.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One answered story on the statistics chart.
 * [indicator] is the percent of correct answers divided by the listen count.
 * [green] is true when that indicator is not strictly below the mean of the chart.
 * The library circle uses the same split: green stays bright, gray is below the mean.
 */
data class StatsPoint(
    val catalogNumber: Int,
    val indicator: Double,
    val green: Boolean,
)

/** Integer ticks for the indicator axis. [top] is the highest tick. */
data class ListenAxis(
    val top: Int,
    val ticks: List<Int>,
)

private data class ScoredStory(
    val catalogNumber: Int,
    val indicator: Double,
)

/**
 * Not strictly below [mean]. A story that equals the mean stays green,
 * matching a library circle that is muted only when its indicator is lower.
 */
fun atOrAboveMean(indicator: Double, mean: Double): Boolean = indicator >= mean

/**
 * Answered stories only, in catalog order.
 * A story with no grade, a grade max of 0, or no listens is left out.
 */
fun storyStats(library: Library): List<StatsPoint> {
    val scored = scoredStories(library)
    if (scored.isEmpty()) return emptyList()
    val mean = scored.map { it.indicator }.average()
    return scored.map { story ->
        StatsPoint(
            catalogNumber = story.catalogNumber,
            indicator = story.indicator,
            green = atOrAboveMean(story.indicator, mean),
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

/** Mean of the per-story indicator. Empty when nothing was answered. */
fun meanIndicator(points: List<StatsPoint>): Double? =
    if (points.isEmpty()) null else points.map { it.indicator }.average()

/**
 * Story ids whose library circle is muted.
 * A graded story is muted when its indicator is strictly below the mean of
 * every story that has one. No indicator keeps the ordinary circle, including
 * a grade with no listens. A story at the mean stays bright.
 */
fun dimmedStoryIds(library: Library): Set<Int> {
    val mean = meanIndicator(storyStats(library)) ?: return emptySet()
    return library.stories.mapNotNull { story ->
        val score = listenScore(library.progressOf(story.id)) ?: return@mapNotNull null
        if (atOrAboveMean(score, mean)) null else story.id
    }.toSet()
}

/** One decimal when needed, with a comma. Non-positive values become 0. */
fun formatStat(value: Double): String {
    val scaled = (value.coerceAtLeast(0.0) * 10.0).roundToInt()
    val whole = scaled / 10
    val frac = scaled % 10
    return if (frac == 0) whole.toString() else "$whole,$frac"
}

/** Highest indicator. A chart with no bars has a peak of zero. */
fun chartPeak(points: List<StatsPoint>): Double =
    points.maxOfOrNull { it.indicator.coerceAtLeast(0.0) } ?: 0.0

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

fun statsChartDescription(points: List<StatsPoint>): String {
    if (points.isEmpty()) return "Пройденных историй нет."
    val mean = points.map { it.indicator }.average()
    val bars = points.joinToString("; ") { point ->
        val tone = if (point.green) "зелёный" else "серый"
        "${point.catalogNumber.toString().padStart(2, '0')} ${formatStat(point.indicator)} $tone"
    }
    return "Столбцы: процент верных ответов, делённый на число прослушиваний. " +
        "Среднее ${formatStat(mean)}. Зелёный — не ниже среднего, серый — ниже. $bars"
}

private fun scoredStories(library: Library): List<ScoredStory> =
    library.stories.mapIndexedNotNull { index, story ->
        val progress = library.progressOf(story.id)
        val indicator = listenScore(progress) ?: return@mapIndexedNotNull null
        ScoredStory(
            catalogNumber = index + 1,
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
