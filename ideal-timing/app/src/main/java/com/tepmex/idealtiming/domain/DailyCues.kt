package com.tepmex.idealtiming.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Meal times are **hours after wake**. The dog walk is **19:00 local wall-clock**
 * (not 19 hours after wake).
 */
data class DialCueMarkers(
    val breakfastProgress: Float,
    val lunchProgress: Float,
    val dinnerProgress: Float,
    /** Null when today's 19:00 (or the 19:00 in the wake window) is outside `[wake, wake+16h]`. */
    val dogWalkProgress: Float?,
    val dogWalkEpochSec: Long?,
)

/**
 * Fixed daily cues drawn on the 16-hour dial and scheduled as notifications.
 */
object DailyCues {
    const val BREAKFAST_AFTER_WAKE_SEC = 30 * 60L
    const val LUNCH_AFTER_WAKE_SEC = 6 * 3600L
    const val DINNER_AFTER_WAKE_SEC = 11 * 3600L

    val DOG_WALK_LOCAL_TIME: LocalTime = LocalTime.of(19, 0)

    const val BREAKFAST_MESSAGE = "пора завтракать"
    const val LUNCH_MESSAGE = "пора обедать"
    const val DINNER_MESSAGE = "пора ужинать"
    const val DOG_WALK_MESSAGE = "время погулять с собакой"

    const val ALARM_BREAKFAST = 5
    const val ALARM_LUNCH = 6
    const val ALARM_DINNER = 7
    const val ALARM_DOG_WALK = 8
    const val ALARM_ID_MAX = 8

    fun breakfastProgress(daySeconds: Long = IdealClock.DAY_SECONDS): Float =
        BREAKFAST_AFTER_WAKE_SEC.toFloat() / daySeconds

    fun lunchProgress(daySeconds: Long = IdealClock.DAY_SECONDS): Float =
        LUNCH_AFTER_WAKE_SEC.toFloat() / daySeconds

    fun dinnerProgress(daySeconds: Long = IdealClock.DAY_SECONDS): Float =
        DINNER_AFTER_WAKE_SEC.toFloat() / daySeconds

    /**
     * Meal markers always sit on the 16-hour dial (0.5h / 6h / 11h after wake).
     * Dog-walk marker uses the 19:00 local instant that falls inside `[wake, wake+16h]`,
     * if any — otherwise the icon is omitted (no clamp to the rim).
     */
    fun markers(
        wakeEpochSec: Long,
        zoneId: ZoneId,
        daySeconds: Long = IdealClock.DAY_SECONDS,
    ): DialCueMarkers {
        val dog = dogWalkOnDial(wakeEpochSec, zoneId, daySeconds)
        return DialCueMarkers(
            breakfastProgress = breakfastProgress(daySeconds),
            lunchProgress = lunchProgress(daySeconds),
            dinnerProgress = dinnerProgress(daySeconds),
            dogWalkProgress = dog?.second,
            dogWalkEpochSec = dog?.first,
        )
    }

    /**
     * The 19:00 local wall-clock instant that lands on the 16-hour dial, if any.
     */
    fun dogWalkOnDial(
        wakeEpochSec: Long,
        zoneId: ZoneId,
        daySeconds: Long = IdealClock.DAY_SECONDS,
    ): Pair<Long, Float>? {
        val wake = Instant.ofEpochSecond(wakeEpochSec)
        val end = Instant.ofEpochSecond(wakeEpochSec + daySeconds)
        var day = wake.atZone(zoneId).toLocalDate()
        val endDate = end.atZone(zoneId).toLocalDate()
        while (!day.isAfter(endDate)) {
            val epoch = day.atTime(DOG_WALK_LOCAL_TIME).atZone(zoneId).toEpochSecond()
            val progress = SunCalculator.progressOnDial(wakeEpochSec, epoch, daySeconds)
            if (progress != null) return epoch to progress
            day = day.plusDays(1)
        }
        return null
    }
}
