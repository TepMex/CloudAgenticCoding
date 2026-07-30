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
    val cadenceSpm: Double,
    val calories: Double,
    val watermark: Long,
    val rawJson: String,
) {
    val heartbitsPerKm: Double
        get() = RunningMetrics.heartbitsPerKm(avgBpm, paceSecPerKm)

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
            cadenceSpm = p.cadenceSpm,
            calories = p.calories,
            watermark = p.watermark,
            rawJson = p.rawJson,
        )
    }
}
