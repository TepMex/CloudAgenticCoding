package com.tepmex.zoulushang.importing

import kotlin.math.cos
import kotlin.math.sqrt

object PointClusterer {
    private const val MAX_TIME_GAP_MS = 5 * 60 * 1000L
    private const val MAX_DISTANCE_METERS = 30.0

    fun cluster(points: List<LocationPoint>): List<LocationPoint> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.ts }
        val result = mutableListOf<LocationPoint>()
        var cluster = mutableListOf(sorted.first())

        fun flushCluster() {
            if (cluster.isEmpty()) return
            val centroid = centroidOf(cluster)
            result += centroid
            cluster = mutableListOf()
        }

        for (i in 1 until sorted.size) {
            val prev = cluster.last()
            val current = sorted[i]
            val dt = current.ts - prev.ts
            val dist = haversineMeters(prev.lat, prev.lng, current.lat, current.lng)
            if (dt <= MAX_TIME_GAP_MS && dist <= MAX_DISTANCE_METERS) {
                cluster += current
            } else {
                flushCluster()
                cluster += current
            }
        }
        flushCluster()
        return result
    }

    private fun centroidOf(points: List<LocationPoint>): LocationPoint {
        val lat = points.map { it.lat }.average()
        val lng = points.map { it.lng }.average()
        val ts = points.last().ts
        val accuracy = points.mapNotNull { it.accuracyMeters }.average().takeIf { !it.isNaN() }?.toFloat()
        return LocationPoint(ts = ts, lat = lat, lng = lng, accuracyMeters = accuracy)
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sinSq(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinSq(dLng / 2)
        return 2 * r * kotlin.math.asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun sinSq(x: Double) = kotlin.math.sin(x) * kotlin.math.sin(x)
}
