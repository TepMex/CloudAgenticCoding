package com.tepmex.runninglog.domain

import com.tepmex.runninglog.mi.MiConstants
import org.json.JSONObject
import kotlin.math.roundToInt

data class ParsedSportRecord(
    val workoutId: String,
    val sportType: String,
    val startTimeEpochSec: Long,
    val endTimeEpochSec: Long,
    val durationSec: Int,
    val distanceMeters: Double,
    val paceSecPerKm: Double,
    val avgBpm: Int,
    val cadenceSpm: Double,
    val calories: Double,
    val watermark: Long,
    val rawJson: String,
    val steps: Int,
)

object SportRecordParser {
    fun isRunning(sportType: String): Boolean =
        sportType.lowercase() in MiConstants.RUNNING_SPORT_KEYS

    fun parseCloudRecord(rec: JSONObject): ParsedSportRecord? {
        val rawVal = rec.opt("value")
        val value: JSONObject = when (rawVal) {
            is JSONObject -> rawVal
            is String -> try {
                JSONObject(rawVal)
            } catch (_: Exception) {
                JSONObject()
            }
            else -> JSONObject()
        }

        val sid = rec.optString("sid").trim().ifBlank { rec.optString("did").trim() }
        val watermark = rec.optLong("watermark", 0L)
        val timeHint = rec.optLong("time", 0L)
        // Watermark is unique in Xiaomi's sync stream; include it so shared device
        // sids cannot collapse many workouts into one Room row.
        val workoutId = resolveWorkoutId(sid = sid, watermark = watermark, time = timeHint)
            ?: return null

        val sportType = rec.optString("key").ifBlank {
            rec.optString("category", "unknown")
        }

        val start = value.optLong("start_time", 0L).takeIf { it > 0 }
            ?: timeHint
        val duration = value.optInt("duration", 0)
        val end = value.optLong("end_time", 0L).takeIf { it > 0 }
            ?: (start + duration)

        val distance = value.optDouble("corrected_distance", 0.0)
            .takeIf { it > 0 }
            ?: value.optDouble("distance", 0.0)

        val avgBpm = value.optInt("avg_hrm", 0)
        val steps = value.optInt("steps", 0)
        val calories = value.optDouble("calories", 0.0).takeIf { it > 0 }
            ?: value.optDouble("total_cal", 0.0)

        val pace = RunningMetrics.resolvePaceSecPerKm(
            avgPaceSec = value.optDouble("avg_pace", 0.0),
            durationSec = duration,
            distanceMeters = distance,
        )
        val cadence = RunningMetrics.resolveCadenceSpm(
            avgCadence = value.optDouble("avg_cadence", 0.0),
            steps = steps,
            durationSec = duration,
        )

        return ParsedSportRecord(
            workoutId = workoutId,
            sportType = sportType,
            startTimeEpochSec = start,
            endTimeEpochSec = end,
            durationSec = duration,
            distanceMeters = distance,
            paceSecPerKm = pace,
            avgBpm = avgBpm,
            cadenceSpm = cadence,
            calories = calories,
            watermark = watermark,
            rawJson = value.toString(),
            steps = steps,
        )
    }

    /** Stable unique key for a cloud sport record. */
    fun resolveWorkoutId(sid: String, watermark: Long, time: Long): String? = when {
        watermark > 0L && sid.isNotBlank() -> "$sid#$watermark"
        watermark > 0L -> "wm:$watermark"
        sid.isNotBlank() && time > 0L -> "$sid#$time"
        sid.isNotBlank() -> sid
        else -> null
    }
}

object RunningMetrics {
    fun resolvePaceSecPerKm(
        avgPaceSec: Double,
        durationSec: Int,
        distanceMeters: Double,
    ): Double {
        if (avgPaceSec > 0) return avgPaceSec
        val km = distanceMeters / 1000.0
        if (km <= 0 || durationSec <= 0) return 0.0
        return durationSec / km
    }

    fun resolveCadenceSpm(
        avgCadence: Double,
        steps: Int,
        durationSec: Int,
    ): Double {
        if (avgCadence > 0) return avgCadence
        if (steps <= 0 || durationSec <= 0) return 0.0
        return steps / (durationSec / 60.0)
    }

    /** Fractional minutes per km (temp). */
    fun tempoMinutes(paceSecPerKm: Double): Double =
        if (paceSecPerKm <= 0) 0.0 else paceSecPerKm / 60.0

    fun heartbitsPerKm(avgBpm: Int, paceSecPerKm: Double): Double =
        if (avgBpm <= 0 || paceSecPerKm <= 0) 0.0 else avgBpm * tempoMinutes(paceSecPerKm)

    fun formatPace(paceSecPerKm: Double): String {
        if (paceSecPerKm <= 0) return "—"
        val total = paceSecPerKm.roundToInt().coerceAtLeast(0)
        val min = total / 60
        val sec = total % 60
        return "%d:%02d".format(min, sec)
    }

    fun formatDistanceKm(meters: Double): String {
        if (meters <= 0) return "—"
        return "%.2f km".format(meters / 1000.0)
    }
}
