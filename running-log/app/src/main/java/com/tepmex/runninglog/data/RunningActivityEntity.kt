package com.tepmex.runninglog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tepmex.runninglog.domain.ParsedSportRecord
import com.tepmex.runninglog.domain.RunningMetrics

@Entity(tableName = "running_activities")
data class RunningActivityEntity(
    @PrimaryKey val workoutId: String,
    val sportType: String,
    val startTimeEpochSec: Long,
    val endTimeEpochSec: Long,
    val durationSec: Int,
    val distanceMeters: Double,
    val paceSecPerKm: Double,
    val avgBpm: Int,
    val maxBpm: Int = 0,
    val cloudVo2Max: Int = 0,
    val cadenceSpm: Double,
    val calories: Double,
    val watermark: Long,
    val rawJson: String,
) {
    val heartbitsPerKm: Double
        get() = RunningMetrics.heartbitsPerKm(avgBpm, paceSecPerKm)

    /** Prefer cloud VO₂ max; fall back to ACSM pace + HR estimate. */
    val vo2MaxMlKgMin: Double
        get() = RunningMetrics.resolveVo2MaxMlKgMin(
            cloudVo2Max = cloudVo2Max,
            paceSecPerKm = paceSecPerKm,
            avgBpm = avgBpm,
            maxBpm = maxBpm,
        )

    companion object {
        fun fromParsed(p: ParsedSportRecord) = RunningActivityEntity(
            workoutId = p.workoutId,
            sportType = p.sportType,
            startTimeEpochSec = p.startTimeEpochSec,
            endTimeEpochSec = p.endTimeEpochSec,
            durationSec = p.durationSec,
            distanceMeters = p.distanceMeters,
            paceSecPerKm = p.paceSecPerKm,
            avgBpm = p.avgBpm,
            maxBpm = p.maxBpm,
            cloudVo2Max = p.cloudVo2Max,
            cadenceSpm = p.cadenceSpm,
            calories = p.calories,
            watermark = p.watermark,
            rawJson = p.rawJson,
        )
    }
}
