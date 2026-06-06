package com.tepmex.zoulushang.geo

data class LatLng(val lat: Double, val lng: Double)

object PointInPolygon {
    fun contains(point: LatLng, ring: List<LatLng>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val xi = ring[i].lng
            val yi = ring[i].lat
            val xj = ring[j].lng
            val yj = ring[j].lat
            val intersects = ((yi > point.lat) != (yj > point.lat)) &&
                (point.lng < (xj - xi) * (point.lat - yi) / (yj - yi + 0.0) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    fun containsInAnyRing(point: LatLng, rings: List<List<LatLng>>): Boolean =
        rings.any { contains(point, it) }
}
