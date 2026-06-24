package com.tepmex.zoulushang2.geo

import kotlin.math.cos
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection

object GeoMath {
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadius * c
    }

    fun offsetLngMeters(latitude: Double, longitude: Double, metersEast: Double): Double {
        val earthRadius = 6_371_000.0
        val dLng = Math.toDegrees(metersEast / (earthRadius * cos(Math.toRadians(latitude))))
        return longitude + dLng
    }

    fun metersToPixels(
        meters: Double,
        latitude: Double,
        longitude: Double,
        projection: Projection,
    ): Float {
        val eastLng = offsetLngMeters(latitude, longitude, meters)
        val p0 = projection.toPixels(GeoPoint(latitude, longitude), null)
        val p1 = projection.toPixels(GeoPoint(latitude, eastLng), null)
        return kotlin.math.hypot(
            (p1.x - p0.x).toDouble(),
            (p1.y - p0.y).toDouble(),
        ).toFloat().coerceAtLeast(1f)
    }
}
