package com.tepmex.zoulushang.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "imported_location_points")
data class ImportedLocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityId: Long,
    val ts: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)
