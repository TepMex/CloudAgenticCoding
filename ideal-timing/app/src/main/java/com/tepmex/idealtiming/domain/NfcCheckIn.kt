package com.tepmex.idealtiming.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/**
 * Physical NFC check-in: a stamp of the 16-hour dial progress at the moment any
 * NFC tag was scanned while the app was in the foreground.
 *
 * The stamp belongs to a **wake day** — the local calendar date of the wake
 * epoch it was made against. First tap for that wake day wins. A later Mi Fitness
 * sync keeps the stamp when the new wake is still that same local date, and drops
 * it when wake data arrives for a different day.
 */
data class NfcCheckIn(
    val wakeLocalDate: LocalDate,
    val progress: Float,
    val checkedInEpochSec: Long,
) {
    fun toJson(): String = JSONObject()
        .put("local_date", wakeLocalDate.toString())
        .put("progress", progress.toDouble())
        .put("checked_in_epoch_sec", checkedInEpochSec)
        .toString()

    companion object {
        fun fromJson(raw: String?): NfcCheckIn? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                val date = LocalDate.parse(o.getString("local_date"))
                val progress = o.getDouble("progress").toFloat()
                val epoch = o.optLong("checked_in_epoch_sec", 0L)
                if (epoch <= 0L) null
                else NfcCheckIn(wakeLocalDate = date, progress = progress, checkedInEpochSec = epoch)
            } catch (_: Exception) {
                null
            }
        }
    }
}

object NfcCheckInStamp {
    fun wakeLocalDate(wakeEpochSec: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochSecond(wakeEpochSec).atZone(zoneId).toLocalDate()

    /**
     * Stamp the current pointer progress. Returns [existing] unchanged when it
     * already belongs to [wakeEpochSec]'s local date (first check-in for this
     * wake day is fixed, including after a same-day re-sync).
     */
    fun apply(
        existing: NfcCheckIn?,
        wakeEpochSec: Long,
        nowEpochSec: Long,
        zoneId: ZoneId,
    ): NfcCheckIn {
        val wakeDate = wakeLocalDate(wakeEpochSec, zoneId)
        if (existing != null && existing.wakeLocalDate == wakeDate) return existing
        val reading = IdealClock.reading(wakeEpochSec, nowEpochSec)
        return NfcCheckIn(
            wakeLocalDate = wakeDate,
            progress = reading.progress,
            checkedInEpochSec = nowEpochSec,
        )
    }

    /**
     * Dial progress to draw for the current wake, or null when the stamp belongs
     * to a different wake day (or there is none).
     */
    fun progressForWake(
        checkIn: NfcCheckIn?,
        wakeEpochSec: Long,
        zoneId: ZoneId,
    ): Float? {
        val wakeDate = wakeLocalDate(wakeEpochSec, zoneId)
        return checkIn?.takeIf { it.wakeLocalDate == wakeDate }?.progress
    }
}
