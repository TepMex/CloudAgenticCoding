package com.tepmex.idealtiming.domain

import java.time.Instant
import java.time.ZoneId

/**
 * One scheduled alert for a same-day cue (sector boundary, meal, or dog walk).
 */
data class SectionNotification(
    /** Unix seconds when the notification should fire. */
    val fireEpochSec: Long,
    val message: String,
    /** Stable id for alarm request codes (1–4 sections, 5–7 meals, 8 dog walk). */
    val alarmId: Int,
)

/**
 * Builds same-day notifications from a known wake time and the device [zoneId].
 *
 * Cues:
 * - wake+4h / +8h / +12h → "Наступило время для ${sector.labelRu}"
 * - wake+16h → "Пора спать"
 * - wake+30m / +6h / +11h → meal messages
 * - 19:00 **local wall-clock** on the current calendar day → dog-walk message
 *
 * Only future fires at or before [dayEndExclusiveEpochSec] are included
 * (nothing scheduled past the current local calendar day).
 */
object SectionNotificationPlanner {
    private const val SLEEP_MESSAGE = "Пора спать"

    fun plan(
        wakeEpochSec: Long,
        nowEpochSec: Long,
        dayEndExclusiveEpochSec: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<SectionNotification> {
        if (wakeEpochSec <= 0L) return emptyList()
        val out = ArrayList<SectionNotification>(DailyCues.ALARM_ID_MAX)
        val boundaries = listOf(
            Boundary(hoursAfterWake = 4, alarmId = 1, sleep = false, sector = DaySector.TacticsHandworkMorning),
            Boundary(hoursAfterWake = 8, alarmId = 2, sleep = false, sector = DaySector.TacticsHandworkAfternoon),
            Boundary(hoursAfterWake = 12, alarmId = 3, sleep = false, sector = DaySector.RestWindDown),
            Boundary(hoursAfterWake = 16, alarmId = 4, sleep = true, sector = null),
        )
        for (b in boundaries) {
            val fire = wakeEpochSec + b.hoursAfterWake * 3600L
            maybeAdd(out, fire, nowEpochSec, dayEndExclusiveEpochSec) {
                val message = if (b.sleep) {
                    SLEEP_MESSAGE
                } else {
                    "Наступило время для ${b.sector!!.labelRu}"
                }
                SectionNotification(fire, message, b.alarmId)
            }
        }
        maybeAdd(
            out,
            wakeEpochSec + DailyCues.BREAKFAST_AFTER_WAKE_SEC,
            nowEpochSec,
            dayEndExclusiveEpochSec,
        ) {
            SectionNotification(it, DailyCues.BREAKFAST_MESSAGE, DailyCues.ALARM_BREAKFAST)
        }
        maybeAdd(
            out,
            wakeEpochSec + DailyCues.LUNCH_AFTER_WAKE_SEC,
            nowEpochSec,
            dayEndExclusiveEpochSec,
        ) {
            SectionNotification(it, DailyCues.LUNCH_MESSAGE, DailyCues.ALARM_LUNCH)
        }
        maybeAdd(
            out,
            wakeEpochSec + DailyCues.DINNER_AFTER_WAKE_SEC,
            nowEpochSec,
            dayEndExclusiveEpochSec,
        ) {
            SectionNotification(it, DailyCues.DINNER_MESSAGE, DailyCues.ALARM_DINNER)
        }

        val today = Instant.ofEpochSecond(dayEndExclusiveEpochSec - 1).atZone(zoneId).toLocalDate()
        val dogWalk = today.atTime(DailyCues.DOG_WALK_LOCAL_TIME).atZone(zoneId).toEpochSecond()
        maybeAdd(out, dogWalk, nowEpochSec, dayEndExclusiveEpochSec) {
            SectionNotification(it, DailyCues.DOG_WALK_MESSAGE, DailyCues.ALARM_DOG_WALK)
        }

        out.sortBy { it.fireEpochSec }
        return out
    }

    private inline fun maybeAdd(
        out: MutableList<SectionNotification>,
        fire: Long,
        nowEpochSec: Long,
        dayEndExclusiveEpochSec: Long,
        build: (Long) -> SectionNotification,
    ) {
        if (fire <= nowEpochSec) return
        if (fire >= dayEndExclusiveEpochSec) return
        out += build(fire)
    }

    private data class Boundary(
        val hoursAfterWake: Int,
        val alarmId: Int,
        val sleep: Boolean,
        val sector: DaySector?,
    )
}
