package com.tepmex.zoulushang2.geo

import com.tepmex.zoulushang2.data.PaintStroke

object BrushEngine {
    const val MOVEMENT_THRESHOLD_METERS = 2.0

    fun applyLocation(
        latitude: Double,
        longitude: Double,
        lastLatitude: Double?,
        lastLongitude: Double?,
        colorArgb: Int,
        thicknessMeters: Float,
        onStroke: (PaintStroke) -> Unit,
    ) {
        if (lastLatitude == null || lastLongitude == null) {
            onStroke(dotStroke(latitude, longitude, colorArgb, thicknessMeters))
            return
        }

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
        } else {
            onStroke(dotStroke(latitude, longitude, colorArgb, thicknessMeters))
        }
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
