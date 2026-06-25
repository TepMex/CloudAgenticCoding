package com.tepmex.zoulushang2.geo

import com.tepmex.zoulushang2.data.PaintStroke

data class LocationApplyResult(
    val strokesAdded: Int,
    val accepted: Boolean,
)

object BrushEngine {
    const val MOVEMENT_THRESHOLD_METERS = 2.0

    fun applyLocation(
        latitude: Double,
        longitude: Double,
        lastLatitude: Double?,
        lastLongitude: Double?,
        lastTimestampMillis: Long?,
        timestampMillis: Long,
        maxSpeedKmh: Float,
        colorArgb: Int,
        thicknessMeters: Float,
        onStroke: (PaintStroke) -> Unit,
    ): LocationApplyResult {
        if (
            lastLatitude != null &&
            lastLongitude != null &&
            lastTimestampMillis != null &&
            !isSpeedRealistic(
                lastLatitude = lastLatitude,
                lastLongitude = lastLongitude,
                lastTimestampMillis = lastTimestampMillis,
                latitude = latitude,
                longitude = longitude,
                timestampMillis = timestampMillis,
                maxSpeedKmh = maxSpeedKmh,
            )
        ) {
            return LocationApplyResult(strokesAdded = 0, accepted = false)
        }

        val strokesAdded = when {
            lastLatitude == null || lastLongitude == null -> {
                onStroke(dotStroke(latitude, longitude, colorArgb, thicknessMeters))
                1
            }
            else -> {
                val distance = GeoMath.distanceMeters(lastLatitude, lastLongitude, latitude, longitude)
                if (distance >= MOVEMENT_THRESHOLD_METERS) {
                    onStroke(
                        PaintStroke(
                            latStart = lastLatitude,
                            lngStart = lastLongitude,
                            latEnd = latitude,
                            lngEnd = longitude,
                            colorArgb = colorArgb,
                            thicknessMeters = thicknessMeters,
                        ),
                    )
                    1
                } else {
                    onStroke(dotStroke(latitude, longitude, colorArgb, thicknessMeters))
                    1
                }
            }
        }
        return LocationApplyResult(strokesAdded = strokesAdded, accepted = true)
    }

    internal fun isSpeedRealistic(
        lastLatitude: Double,
        lastLongitude: Double,
        lastTimestampMillis: Long,
        latitude: Double,
        longitude: Double,
        timestampMillis: Long,
        maxSpeedKmh: Float,
    ): Boolean {
        val distance = GeoMath.distanceMeters(lastLatitude, lastLongitude, latitude, longitude)
        val elapsedMillis = timestampMillis - lastTimestampMillis
        val speedKmh = GeoMath.speedKmh(distance, elapsedMillis)
        return speedKmh <= maxSpeedKmh
    }

    private fun dotStroke(
        latitude: Double,
        longitude: Double,
        colorArgb: Int,
        thicknessMeters: Float,
    ): PaintStroke = PaintStroke(
        latStart = latitude,
        lngStart = longitude,
        latEnd = latitude,
        lngEnd = longitude,
        colorArgb = colorArgb,
        thicknessMeters = thicknessMeters,
    )
}
