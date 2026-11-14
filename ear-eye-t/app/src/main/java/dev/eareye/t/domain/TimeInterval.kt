package dev.eareye.t.domain

/**
 * Half-open instant range [startMillis, endMillis).
 * Instants are Unix epoch milliseconds. Duration is the difference of those
 * instants and does not depend on local clock labels.
 */
data class TimeInterval(
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(0L)

    fun isEmpty(): Boolean = endMillis <= startMillis
}

fun totalDuration(intervals: List<TimeInterval>): Long =
    intervals.sumOf { it.durationMillis }

fun mergeIntervals(intervals: List<TimeInterval>): List<TimeInterval> {
    val sorted = intervals.filter { !it.isEmpty() }.sortedBy { it.startMillis }
    if (sorted.isEmpty()) return emptyList()
    val merged = mutableListOf<TimeInterval>()
    var start = sorted[0].startMillis
    var end = sorted[0].endMillis
    for (index in 1 until sorted.size) {
        val next = sorted[index]
        if (next.startMillis <= end) {
            if (next.endMillis > end) end = next.endMillis
        } else {
            merged += TimeInterval(start, end)
            start = next.startMillis
            end = next.endMillis
        }
    }
    merged += TimeInterval(start, end)
    return merged
}

fun intersectIntervals(left: List<TimeInterval>, right: List<TimeInterval>): List<TimeInterval> {
    val a = mergeIntervals(left)
    val b = mergeIntervals(right)
    val out = mutableListOf<TimeInterval>()
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        val start = maxOf(a[i].startMillis, b[j].startMillis)
        val end = minOf(a[i].endMillis, b[j].endMillis)
        if (end > start) out += TimeInterval(start, end)
        if (a[i].endMillis <= b[j].endMillis) i++ else j++
    }
    return out
}

fun subtractIntervals(source: List<TimeInterval>, removing: List<TimeInterval>): List<TimeInterval> {
    val holes = mergeIntervals(removing)
    val out = mutableListOf<TimeInterval>()
    for (interval in mergeIntervals(source)) {
        var cursor = interval.startMillis
        for (hole in holes) {
            if (hole.endMillis <= cursor) continue
            if (hole.startMillis >= interval.endMillis) break
            if (hole.startMillis > cursor) {
                out += TimeInterval(cursor, minOf(hole.startMillis, interval.endMillis))
            }
            cursor = maxOf(cursor, hole.endMillis)
            if (cursor >= interval.endMillis) break
        }
        if (cursor < interval.endMillis) out += TimeInterval(cursor, interval.endMillis)
    }
    return out
}

fun clipIntervals(intervals: List<TimeInterval>, window: TimeInterval): List<TimeInterval> {
    if (window.isEmpty()) return emptyList()
    return intervals.mapNotNull { interval ->
        val start = maxOf(interval.startMillis, window.startMillis)
        val end = minOf(interval.endMillis, window.endMillis)
        if (end > start) TimeInterval(start, end) else null
    }
}
