package com.tepmex.swimminglog.domain

import com.tepmex.swimminglog.mi.MiConstants
import kotlin.math.roundToInt
import org.json.JSONObject

data class ParsedSportRecord(
    val workoutId: String,
    val sportType: String,
    val startTimeEpochSec: Long,
    val endTimeEpochSec: Long,
    val durationSec: Int,
    val distanceMeters: Double,
    val paceSecPer100m: Double,
    val avgBpm: Int,
    val maxBpm: Int,
    val swolf: Double,
    val calories: Double,
    val watermark: Long,
    val rawJson: String,
)

object SportRecordParser {
    fun isSwimming(sportType: String): Boolean =
        sportType.lowercase() in MiConstants.SWIMMING_SPORT_KEYS

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
        val maxBpm = value.optInt("max_hrm", 0)
        val calories = value.optDouble("calories", 0.0).takeIf { it > 0 }
            ?: value.optDouble("total_cal", 0.0)

        val pace = SwimmingMetrics.resolvePaceSecPer100m(
            avgPace = firstPositiveDouble(value, "avg_pace"),
            durationSec = duration,
            distanceMeters = distance,
        )
        val swolf = SwimmingMetrics.resolveSwolf(
            cloudAvgSwolf = firstPositiveDouble(value, "avg_swolf", "swolf"),
            strokeCount = firstPositiveDouble(
                value,
                "stroke_count",
                "strokes",
                "total_strokes",
                "swim_strokes",
            ).roundToInt(),
            avgStrokeFreqPerMin = SwimmingMetrics.normalizeStrokeFreqPerMin(
                firstPositiveDouble(value, "avg_stroke_freq", "avg_stroke_rate", "stroke_rate"),
            ),
            poolLengthMeters = SwimmingMetrics.normalizePoolLengthMeters(
                firstPositiveDouble(
                    value,
                    "pool_length",
                    "pool_len",
                    "lane_length",
                    "swimming_pool_length",
                ),
            ),
            durationSec = duration,
            distanceMeters = distance,
        )

        return ParsedSportRecord(
            workoutId = workoutId,
            sportType = sportType,
            startTimeEpochSec = start,
            endTimeEpochSec = end,
            durationSec = duration,
            distanceMeters = distance,
            paceSecPer100m = pace,
            avgBpm = avgBpm,
            maxBpm = maxBpm,
            swolf = swolf,
            calories = calories,
            watermark = watermark,
            rawJson = value.toString(),
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

    private fun firstPositiveDouble(value: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (!value.has(key) || value.isNull(key)) continue
            when (val raw = value.opt(key)) {
                is Number -> {
                    val n = raw.toDouble()
                    if (n > 0) return n
                }
                is String -> raw.toDoubleOrNull()?.takeIf { it > 0 }?.let { return it }
                is JSONObject -> {
                    val avg = raw.optDouble("avg", 0.0)
                    if (avg > 0) return avg
                }
            }
        }
        return 0.0
    }
}

/** One swim’s inputs for trailing-year averages (domain seam; not a Room entity). */
data class SwimMetricSample(
    val startTimeEpochSec: Long,
    val swolf: Double,
    val heartbitsPer100m: Double,
)

data class TrailingYearAverages(
    val avgSwolf: Double?,
    val avgHeartbitsPer100m: Double?,
)

enum class MetricVsAverage {
    Better,
    Worse,
    Equal,
    Unavailable,
}

object SwimmingMetrics {
    const val TRAILING_YEAR_DAYS = 365

    /**
     * Cloud `avg_pace` at or above this value is seconds per kilometre.
     * A human swimming pace cannot be faster than about 45 s/100 m, so a
     * sec/km figure below 450 is not a real kilometre pace.
     */
    const val CLOUD_PACE_AS_SEC_PER_KM_FROM = 450.0

    /** Pool-length numbers above this are centimeters. */
    const val POOL_LENGTH_CENTIMETERS_ABOVE = 200.0

    /** Stroke-frequency numbers below this are strokes per second. */
    const val STROKE_FREQ_PER_SECOND_BELOW = 5.0

    private const val SECONDS_PER_DAY = 86_400L

    /**
     * Seconds per 100 m. Duration and distance win when both are present.
     * Cloud `avg_pace` is only the fallback — see [paceFromCloudAvgPace].
     */
    fun resolvePaceSecPer100m(
        avgPace: Double,
        durationSec: Int,
        distanceMeters: Double,
    ): Double {
        val hundreds = distanceMeters / 100.0
        if (hundreds > 0 && durationSec > 0) {
            return durationSec / hundreds
        }
        return paceFromCloudAvgPace(avgPace)
    }

    fun paceFromCloudAvgPace(avgPace: Double): Double {
        if (avgPace <= 0) return 0.0
        return if (avgPace >= CLOUD_PACE_AS_SEC_PER_KM_FROM) avgPace / 10.0 else avgPace
    }

    fun normalizePoolLengthMeters(raw: Double): Double {
        if (raw <= 0) return 0.0
        return if (raw > POOL_LENGTH_CENTIMETERS_ABOVE) raw / 100.0 else raw
    }

    fun normalizeStrokeFreqPerMin(raw: Double): Double {
        if (raw <= 0) return 0.0
        return if (raw < STROKE_FREQ_PER_SECOND_BELOW) raw * 60.0 else raw
    }

    /**
     * Prefer cloud average SWOLF. Otherwise seconds per length + strokes per length.
     */
    fun resolveSwolf(
        cloudAvgSwolf: Double,
        strokeCount: Int,
        avgStrokeFreqPerMin: Double,
        poolLengthMeters: Double,
        durationSec: Int,
        distanceMeters: Double,
    ): Double {
        if (cloudAvgSwolf > 0) return cloudAvgSwolf
        return estimateSwolf(
            strokeCount = strokeCount,
            avgStrokeFreqPerMin = avgStrokeFreqPerMin,
            poolLengthMeters = poolLengthMeters,
            durationSec = durationSec,
            distanceMeters = distanceMeters,
        )
    }

    fun estimateSwolf(
        strokeCount: Int,
        avgStrokeFreqPerMin: Double,
        poolLengthMeters: Double,
        durationSec: Int,
        distanceMeters: Double,
    ): Double {
        if (poolLengthMeters <= 0 || distanceMeters <= 0 || durationSec <= 0) return 0.0
        val lengths = distanceMeters / poolLengthMeters
        if (lengths <= 0) return 0.0
        val secondsPerLength = durationSec / lengths
        val strokesPerLength = when {
            strokeCount > 0 -> strokeCount / lengths
            avgStrokeFreqPerMin > 0 -> avgStrokeFreqPerMin * (secondsPerLength / 60.0)
            else -> return 0.0
        }
        if (strokesPerLength <= 0) return 0.0
        return secondsPerLength + strokesPerLength
    }

    /** Fractional minutes per 100 m, times average heart rate. */
    fun heartbitsPer100m(avgBpm: Int, paceSecPer100m: Double): Double =
        if (avgBpm <= 0 || paceSecPer100m <= 0) 0.0 else avgBpm * (paceSecPer100m / 60.0)

    fun formatPace(paceSecPer100m: Double): String {
        if (paceSecPer100m <= 0) return "—"
        val total = paceSecPer100m.roundToInt().coerceAtLeast(0)
        val min = total / 60
        val sec = total % 60
        return "%d:%02d".format(min, sec)
    }

    fun formatSwolf(swolf: Double): String {
        if (swolf <= 0) return "—"
        return "%.0f".format(swolf)
    }

    /** Single-swim distance. Meters, because pace is per 100 m. */
    fun formatDistance(meters: Double): String {
        if (meters <= 0) return "—"
        return "${meters.roundToInt()} m"
    }

    /** Prompt snapshot total. Kilometres once the sum reaches 1 km. */
    fun formatDistanceTotal(meters: Double): String {
        if (meters <= 0) return "—"
        return if (meters >= 1000.0) {
            "%.2f km".format(meters / 1000.0)
        } else {
            formatDistance(meters)
        }
    }

    /**
     * Arithmetic means of SWOLF and heartbits/100 m for swims started in the last
     * [TRAILING_YEAR_DAYS] days ending at [nowEpochSec]. Zero/missing metrics are skipped.
     */
    fun trailingYearAverages(
        samples: List<SwimMetricSample>,
        nowEpochSec: Long,
        windowDays: Int = TRAILING_YEAR_DAYS,
    ): TrailingYearAverages {
        val windowStart = nowEpochSec - windowDays.toLong() * SECONDS_PER_DAY
        val inWindow = samples.filter {
            it.startTimeEpochSec in windowStart..nowEpochSec
        }
        val swolfs = inWindow.map { it.swolf }.filter { it > 0 }
        val heartbits = inWindow.map { it.heartbitsPer100m }.filter { it > 0 }
        return TrailingYearAverages(
            avgSwolf = swolfs.takeIf { it.isNotEmpty() }?.average(),
            avgHeartbitsPer100m = heartbits.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** SWOLF: lower than the trailing-year average is better. */
    fun compareSwolf(value: Double, average: Double?): MetricVsAverage =
        compareMetric(value, average, higherIsBetter = false)

    /** Heartbits/100 m: lower than the trailing-year average is better. */
    fun compareHeartbitsPer100m(value: Double, average: Double?): MetricVsAverage =
        compareMetric(value, average, higherIsBetter = false)

    private fun compareMetric(
        value: Double,
        average: Double?,
        higherIsBetter: Boolean,
    ): MetricVsAverage {
        if (value <= 0 || average == null) return MetricVsAverage.Unavailable
        val cmp = value.compareTo(average)
        if (cmp == 0) return MetricVsAverage.Equal
        val better = if (higherIsBetter) cmp > 0 else cmp < 0
        return if (better) MetricVsAverage.Better else MetricVsAverage.Worse
    }
}
