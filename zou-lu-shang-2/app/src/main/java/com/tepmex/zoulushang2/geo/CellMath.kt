package com.tepmex.zoulushang2.geo

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

object CellMath {
    const val PAINT_ZOOM = 16

    fun latLngToCell(lat: Double, lng: Double, zoom: Int = PAINT_ZOOM): Pair<Int, Int> {
        val scale = 1 shl zoom
        val x = floor((lng + 180.0) / 360.0 * scale).toInt().coerceIn(0, scale - 1)
        val latRad = Math.toRadians(lat)
        val y = floor(
            (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * scale,
        ).toInt().coerceIn(0, scale - 1)
        return x to y
    }

    fun packCellKey(zoom: Int, x: Int, y: Int): Long {
        return (zoom.toLong() shl 58) or (x.toLong() shl 29) or y.toLong()
    }

    fun unpackCellKey(cellKey: Long): Triple<Int, Int, Int> {
        val zoom = (cellKey ushr 58).toInt()
        val x = ((cellKey shr 29) and 0x1FFFFFFF).toInt()
        val y = (cellKey and 0x1FFFFFFF).toInt()
        return Triple(zoom, x, y)
    }

    fun cellXToLng(x: Int, zoom: Int): Double = x / (1 shl zoom).toDouble() * 360.0 - 180.0

    fun cellYToLat(y: Int, zoom: Int): Double {
        val n = Math.PI - 2.0 * Math.PI * y / (1 shl zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

    fun cellBounds(zoom: Int, x: Int, y: Int): BoundingBox {
        val north = cellYToLat(y, zoom)
        val south = cellYToLat(y + 1, zoom)
        val west = cellXToLng(x, zoom)
        val east = cellXToLng(x + 1, zoom)
        return BoundingBox(north, east, south, west)
    }

    fun cellCenter(zoom: Int, x: Int, y: Int): GeoPoint {
        val bounds = cellBounds(zoom, x, y)
        return GeoPoint(
            (bounds.latNorth + bounds.latSouth) / 2.0,
            (bounds.lonEast + bounds.lonWest) / 2.0,
        )
    }

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

    fun boundsFromCellKeys(cellKeys: Collection<Long>): BoundingBox? {
        if (cellKeys.isEmpty()) return null
        var north = Double.NEGATIVE_INFINITY
        var south = Double.POSITIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        var west = Double.POSITIVE_INFINITY
        for (key in cellKeys) {
            val (zoom, x, y) = unpackCellKey(key)
            val bounds = cellBounds(zoom, x, y)
            north = maxOf(north, bounds.latNorth)
            south = minOf(south, bounds.latSouth)
            east = maxOf(east, bounds.lonEast)
            west = minOf(west, bounds.lonWest)
        }
        return BoundingBox(north, east, south, west)
    }
}
