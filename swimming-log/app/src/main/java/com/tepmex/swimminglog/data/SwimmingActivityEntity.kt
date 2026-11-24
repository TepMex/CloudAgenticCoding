package com.tepmex.swimminglog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tepmex.swimminglog.domain.ParsedSportRecord
import com.tepmex.swimminglog.domain.SwimmingMetrics

@Entity(tableName = "swimming_activities")
data class SwimmingActivityEntity(
    @PrimaryKey val workoutId: String,
    val sportType: String,
    val startTimeEpochSec: Long,
    val endTimeEpochSec: Long,
    val durationSec: Int,
    val distanceMeters: Double,
    val paceSecPer100m: Double,
    val avgBpm: Int,
    val maxBpm: Int = 0,
    val swolf: Double,
    val calories: Double,
    val watermark: Long,
    val rawJson: String,
) {
    val heartbitsPer100m: Double
        get() = SwimmingMetrics.heartbitsPer100m(avgBpm, paceSecPer100m)

    companion object {
        fun fromParsed(p: ParsedSportRecord) = SwimmingActivityEntity(
            workoutId = p.workoutId,
            sportType = p.sportType,
            startTimeEpochSec = p.startTimeEpochSec,
            endTimeEpochSec = p.endTimeEpochSec,
            durationSec = p.durationSec,
            distanceMeters = p.distanceMeters,
            paceSecPer100m = p.paceSecPer100m,
            avgBpm = p.avgBpm,
            maxBpm = p.maxBpm,
            swolf = p.swolf,
            calories = p.calories,
            watermark = p.watermark,
            rawJson = p.rawJson,
        )
    }
}
