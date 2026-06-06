package com.tepmex.zoulushang.importing

data class LocationPoint(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float?,
)
