package com.tepmex.zoulushang.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "city_boundaries")
data class CityBoundary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val osmPlaceId: Long,
    val displayName: String,
    val geoJson: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)
