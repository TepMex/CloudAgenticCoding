package com.tepmex.tinglistories.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

const val LISTEN_AVERAGE_SHORT = 5
const val LISTEN_AVERAGE_LONG = 10

/** One catalog position on the statistics chart. Averages and the percent may be absent. */
data class StatsPoint(
    val listens: Int,
    val average5: Double?,
    val average10: Double?,
    val correctPercent: Double?,
)

/** Integer ticks for the listen axis. [top] is the highest tick. */
data class ListenAxis(
    val top: Int,
    val ticks: List<Int>,
)

/**
 * Catalog order, one point per story.
 * [StatsPoint.average5] and [StatsPoint.average10] are trailing means of the listen
 * count and stay absent until the window is full. [StatsPoint.correctPercent] is
 * absent when the story has no usable grade.
 */
fun storyStats(library: Library): List<StatsPoint> {
    val listens = library.stories.map { library.progressOf(it.id).listenCount.coerceAtLeast(0) }
    val short = trailingAverage(listens, LISTEN_AVERAGE_SHORT)
    val long = trailingAverage(listens, LISTEN_AVERAGE_LONG)
    return library.stories.indices.map { index ->
        StatsPoint(
            listens = listens[index],
            average5 = short[index],
            average10 = long[index],
            correctPercent = correctPercent(library.progressOf(library.stories[index].id)),
        )
    }
}

/** Mean of this value and the ones before it. The first `window - 1` places stay empty. */
fun trailingAverage(values: List<Int>, window: Int): List<Double?> {
    require(window >= 1) { "window must be at least 1" }
    return List(values.size) { index ->
        if (index + 1 < window) {
            null
        } else {
            var sum = 0
            for (offset in index - window + 1..index) sum += values[offset]
            sum.toDouble() / window.toDouble()
        }
    }
}

/** `score / max × 100`. A missing grade, or a max of zero, has no percent. */
fun correctPercent(progress: StoryProgress): Double? {
    val grade = progress.evaluation ?: return null
    if (grade.max <= 0) return null
    return grade.score.coerceIn(0, grade.max) * 100.0 / grade.max.toDouble()
}

/** Highest listen value among the raw counts and both averages. */
fun chartPeak(points: List<StatsPoint>): Double {
    var peak = 0.0
    for (point in points) {
        if (point.listens > peak) peak = point.listens.toDouble()
        val short = point.average5
        if (short != null && short > peak) peak = short
        val long = point.average10
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
    val graded = points.mapNotNull { it.correctPercent }
    val percentSentence = if (graded.isEmpty()) {
        "Проверенных историй нет."
    } else {
        "Средний процент верных: ${graded.average().roundToInt()}."
    }
    return "График прослушиваний, средних по 5 и 10 историям и процента верных ответов. $percentSentence"
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
