package com.tepmex.idealtiming.domain

/**
 * One scheduled alert for a sector boundary (or the 16h sleep cue).
 */
data class SectionNotification(
    /** Unix seconds when the notification should fire. */
    val fireEpochSec: Long,
    val message: String,
    /** Stable id for alarm request codes (1 = first sector change, …, 4 = sleep). */
    val alarmId: Int,
)

/**
 * Builds same-day section-change notifications from a known wake time.
 *
 * Boundaries:
 * - wake+4h / +8h / +12h → "Наступило время для ${sector.labelRu}"
 * - wake+16h → "Пора спать"
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
    ): List<SectionNotification> {
        if (wakeEpochSec <= 0L) return emptyList()
        val out = ArrayList<SectionNotification>(4)
        // Sector starts after wake: sectors 2, 3, 4 at 4h / 8h / 12h; sleep at 16h.
        val boundaries = listOf(
            Boundary(hoursAfterWake = 4, alarmId = 1, sleep = false, sector = DaySector.TacticsHandworkMorning),
            Boundary(hoursAfterWake = 8, alarmId = 2, sleep = false, sector = DaySector.TacticsHandworkAfternoon),
            Boundary(hoursAfterWake = 12, alarmId = 3, sleep = false, sector = DaySector.RestWindDown),
            Boundary(hoursAfterWake = 16, alarmId = 4, sleep = true, sector = null),
        )
        for (b in boundaries) {
            val fire = wakeEpochSec + b.hoursAfterWake * 3600L
            if (fire <= nowEpochSec) continue
            if (fire >= dayEndExclusiveEpochSec) continue
            val message = if (b.sleep) {
                SLEEP_MESSAGE
            } else {
                "Наступило время для ${b.sector!!.labelRu}"
            }
            out += SectionNotification(
                fireEpochSec = fire,
                message = message,
                alarmId = b.alarmId,
            )
        }
        return out
    }

    private data class Boundary(
        val hoursAfterWake: Int,
        val alarmId: Int,
        val sleep: Boolean,
        val sector: DaySector?,
    )
}
