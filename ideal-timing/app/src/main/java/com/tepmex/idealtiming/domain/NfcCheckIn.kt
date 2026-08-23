package com.tepmex.idealtiming.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/**
 * Physical NFC check-in: a stamp of the 16-hour dial progress at the moment any
 * NFC tag was scanned while the app was in the foreground.
 *
 * The stamp is keyed by **local calendar date** ([zoneId] at [checkedInEpochSec]).
 * First tap of the day wins; later taps that day keep the original angle.
 */
data class NfcCheckIn(
    val localDate: LocalDate,
    val progress: Float,
    val checkedInEpochSec: Long,
) {
    fun toJson(): String = JSONObject()
        .put("local_date", localDate.toString())
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
                else NfcCheckIn(localDate = date, progress = progress, checkedInEpochSec = epoch)
            } catch (_: Exception) {
                null
            }
        }
    }
}

object NfcCheckInStamp {
    fun localDate(nowEpochSec: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochSecond(nowEpochSec).atZone(zoneId).toLocalDate()

    /**
     * Stamp the current pointer progress. Returns [existing] unchanged when it
     * already belongs to today's local date (first check-in of the day is fixed).
     */
    fun apply(
        existing: NfcCheckIn?,
        wakeEpochSec: Long,
        nowEpochSec: Long,
        zoneId: ZoneId,
    ): NfcCheckIn {
        val today = localDate(nowEpochSec, zoneId)
        if (existing != null && existing.localDate == today) return existing
        val reading = IdealClock.reading(wakeEpochSec, nowEpochSec)
        return NfcCheckIn(
            localDate = today,
            progress = reading.progress,
            checkedInEpochSec = nowEpochSec,
        )
    }

    /** Dial progress to draw today, or null when there is no stamp for this local date. */
    fun progressForToday(
        checkIn: NfcCheckIn?,
        nowEpochSec: Long,
        zoneId: ZoneId,
    ): Float? {
        val today = localDate(nowEpochSec, zoneId)
        return checkIn?.takeIf { it.localDate == today }?.progress
    }
}
