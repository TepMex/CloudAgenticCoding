package com.tepmex.zoulushang.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live_location_samples")
data class LiveLocationSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityId: Long,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long = System.currentTimeMillis(),
)
