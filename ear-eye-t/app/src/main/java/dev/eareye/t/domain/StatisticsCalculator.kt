package dev.eareye.t.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyStatistics(
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val eyeMillis: Long,
    val earMillis: Long,
    val eyeOnlyMillis: Long,
    val earOnlyMillis: Long,
    val overlapMillis: Long,
    val unionMillis: Long,
    val bothKnownEyeMillis: Long,
    val bothKnownEarMillis: Long,
    val eyeDuringAudioUnknownMillis: Long,
    val earDuringEyeUnknownMillis: Long,
    val possiblyUndercountedEarMillis: Long,
    val listenerCoverageMillis: Long,
    val poweredOffMillis: Long,
    val knownAudioMillis: Long,
    val coverageFraction: Double,
    val unknownMillis: Long,
    val incomplete: Boolean,
) {
    val window: TimeInterval get() = TimeInterval(windowStartMillis, windowEndMillis)

    companion object {
        fun empty(window: TimeInterval): DailyStatistics = DailyStatistics(
            windowStartMillis = window.startMillis,
            windowEndMillis = window.endMillis,
            eyeMillis = 0L,
            earMillis = 0L,
            eyeOnlyMillis = 0L,
            earOnlyMillis = 0L,
            overlapMillis = 0L,
            unionMillis = 0L,
            bothKnownEyeMillis = 0L,
            bothKnownEarMillis = 0L,
            eyeDuringAudioUnknownMillis = 0L,
            earDuringEyeUnknownMillis = 0L,
            possiblyUndercountedEarMillis = 0L,
            listenerCoverageMillis = 0L,
            poweredOffMillis = 0L,
            knownAudioMillis = 0L,
            coverageFraction = 0.0,
            unknownMillis = 0L,
            incomplete = false,
        )
    }
}

data class TimelineModel(
    val window: TimeInterval,
    val eye: List<TimeInterval>,
    val eyeUnknown: List<TimeInterval>,
    val ear: List<TimeInterval>,
    val audioUnknown: List<TimeInterval>,
    val overlap: List<TimeInterval>,
    val poweredOff: List<TimeInterval>,
)

data class DayComputation(
    val stats: DailyStatistics,
    val timeline: TimelineModel,
)

/**
 * Union is Eye + Ear − Overlap only inside the region where both dimensions
 * are known. Confirmed eye time that falls into an audio gap is reported
 * separately and is not labelled "eye only".
 */
fun calculateDailyStatistics(
    eyeActive: List<TimeInterval>,
    eyeUnknown: List<TimeInterval>,
    earActive: List<TimeInterval>,
    listenerCoverage: List<TimeInterval>,
    poweredOff: List<TimeInterval>,
    window: TimeInterval,
): DailyStatistics {
    if (window.isEmpty()) return DailyStatistics.empty(window)

    val eye = mergeIntervals(clipIntervals(eyeActive, window))
    val eyeUnk = mergeIntervals(clipIntervals(eyeUnknown, window))
    val ear = mergeIntervals(clipIntervals(earActive, window))
    val listened = mergeIntervals(clipIntervals(listenerCoverage, window))
    val off = mergeIntervals(clipIntervals(poweredOff, window))
    val knownAudio = mergeIntervals(listened + off)
    val audioUnknown = subtractIntervals(listOf(window), knownAudio)
    val eyeKnown = subtractIntervals(listOf(window), eyeUnk)
    val bothKnown = intersectIntervals(knownAudio, eyeKnown)

    val eyeConfirmed = intersectIntervals(eye, eyeKnown)
    val earConfirmed = intersectIntervals(ear, knownAudio)
    val overlap = intersectIntervals(intersectIntervals(eyeConfirmed, earConfirmed), bothKnown)
    val eyeInBoth = intersectIntervals(eyeConfirmed, bothKnown)
    val earInBoth = intersectIntervals(earConfirmed, bothKnown)
    val eyeOnly = subtractIntervals(eyeInBoth, earConfirmed)
    val earOnly = subtractIntervals(earInBoth, eyeConfirmed)
    val union = mergeIntervals(eyeInBoth + earInBoth)
    val eyeOutsideAudio = subtractIntervals(eyeConfirmed, knownAudio)
    val earOutsideEye = subtractIntervals(earConfirmed, eyeKnown)
    val unknown = mergeIntervals(eyeUnk + audioUnknown)
    val windowMillis = window.durationMillis
    val knownAudioMillis = totalDuration(knownAudio)

    return DailyStatistics(
        windowStartMillis = window.startMillis,
        windowEndMillis = window.endMillis,
        eyeMillis = totalDuration(eyeConfirmed),
        earMillis = totalDuration(earConfirmed),
        eyeOnlyMillis = totalDuration(eyeOnly),
        earOnlyMillis = totalDuration(earOnly),
        overlapMillis = totalDuration(overlap),
        unionMillis = totalDuration(union),
        bothKnownEyeMillis = totalDuration(eyeInBoth),
        bothKnownEarMillis = totalDuration(earInBoth),
        eyeDuringAudioUnknownMillis = totalDuration(eyeOutsideAudio),
        earDuringEyeUnknownMillis = totalDuration(earOutsideEye),
        possiblyUndercountedEarMillis = totalDuration(audioUnknown),
        listenerCoverageMillis = totalDuration(listened),
        poweredOffMillis = totalDuration(subtractIntervals(off, listened)),
        knownAudioMillis = knownAudioMillis,
        coverageFraction = if (windowMillis <= 0L) 0.0 else knownAudioMillis.toDouble() / windowMillis.toDouble(),
        unknownMillis = totalDuration(unknown),
        incomplete = totalDuration(unknown) > 0L,
    )
}

fun timelineFor(
    eyeActive: List<TimeInterval>,
    eyeUnknown: List<TimeInterval>,
    earActive: List<TimeInterval>,
    listenerCoverage: List<TimeInterval>,
    poweredOff: List<TimeInterval>,
    window: TimeInterval,
): TimelineModel {
    val knownAudio = mergeIntervals(
        clipIntervals(listenerCoverage, window) + clipIntervals(poweredOff, window),
    )
    val audioUnknown = subtractIntervals(listOf(window), knownAudio)
    val eye = mergeIntervals(clipIntervals(eyeActive, window))
    val ear = mergeIntervals(clipIntervals(earActive, window))
    return TimelineModel(
        window = window,
        eye = eye,
        eyeUnknown = mergeIntervals(clipIntervals(eyeUnknown, window)),
        ear = ear,
        audioUnknown = audioUnknown,
        overlap = intersectIntervals(eye, ear),
        poweredOff = mergeIntervals(clipIntervals(poweredOff, window)),
    )
}

fun localDayBounds(date: LocalDate, zone: ZoneId): TimeInterval {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return TimeInterval(start, end)
}

/** Selected period. Today ends at [nowMillis]; a future day is empty. */
fun periodWindow(date: LocalDate, zone: ZoneId, nowMillis: Long): TimeInterval {
    val day = localDayBounds(date, zone)
    val end = minOf(day.endMillis, nowMillis)
    return if (end <= day.startMillis) TimeInterval(day.startMillis, day.startMillis) else TimeInterval(day.startMillis, end)
}

fun splitByLocalDays(interval: TimeInterval, zone: ZoneId): List<Pair<LocalDate, TimeInterval>> {
    if (interval.isEmpty()) return emptyList()
    val parts = mutableListOf<Pair<LocalDate, TimeInterval>>()
    var cursor = interval.startMillis
    while (cursor < interval.endMillis) {
        val zoned = Instant.ofEpochMilli(cursor).atZone(zone)
        val date = zoned.toLocalDate()
        val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        if (nextMidnight <= cursor) break
        val end = minOf(interval.endMillis, nextMidnight)
        if (end > cursor) parts += date to TimeInterval(cursor, end)
        cursor = end
    }
    return parts
}

fun resolveSelectionDate(selection: DaySelection, zone: ZoneId, nowMillis: Long): LocalDate {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (selection) {
        DaySelection.Today -> today
        DaySelection.Yesterday -> today.minusDays(1)
        is DaySelection.Specific -> selection.date
    }
}

sealed interface DaySelection {
    data object Today : DaySelection
    data object Yesterday : DaySelection
    data class Specific(val date: LocalDate) : DaySelection
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return "%d ч %02d мин %02d с".format(hours, minutes, seconds)
}
