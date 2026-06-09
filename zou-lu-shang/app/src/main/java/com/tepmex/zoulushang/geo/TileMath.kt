package com.tepmex.zoulushang.geo

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

object TileMath {
    const val DEFAULT_GRID_ZOOM = 15
    const val MIN_GRID_ZOOM = 12
    const val MAX_GRID_ZOOM = 18

    @Deprecated("Use DEFAULT_GRID_ZOOM or pass zoom explicitly", ReplaceWith("DEFAULT_GRID_ZOOM"))
    const val GRID_ZOOM = DEFAULT_GRID_ZOOM

    fun approximateTileWidthMeters(latitude: Double, zoom: Int): Double {
        val metersPerPixel = 156543.03392 * kotlin.math.cos(Math.toRadians(latitude)) / (1 shl zoom)
        return metersPerPixel * 256.0
    }

    fun latLngToTile(lat: Double, lng: Double, zoom: Int = DEFAULT_GRID_ZOOM): Pair<Int, Int> {
        val scale = 1 shl zoom
        val x = floor((lng + 180.0) / 360.0 * scale).toInt().coerceIn(0, scale - 1)
        val latRad = Math.toRadians(lat)
        val y = floor(
            (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * scale
        ).toInt().coerceIn(0, scale - 1)
        return x to y
    }

    fun packTileKey(zoom: Int, x: Int, y: Int): Long {
        return (zoom.toLong() shl 58) or (x.toLong() shl 29) or y.toLong()
    }

    fun unpackTileKey(tileKey: Long): Triple<Int, Int, Int> {
        val zoom = (tileKey ushr 58).toInt()
        val x = ((tileKey shr 29) and 0x1FFFFFFF).toInt()
        val y = (tileKey and 0x1FFFFFFF).toInt()
        return Triple(zoom, x, y)
    }

    fun tileXToLng(x: Int, zoom: Int): Double = x / (1 shl zoom).toDouble() * 360.0 - 180.0

    fun tileYToLat(y: Int, zoom: Int): Double {
        val n = Math.PI - 2.0 * Math.PI * y / (1 shl zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

    fun tileBounds(zoom: Int, x: Int, y: Int): BoundingBox {
        val north = tileYToLat(y, zoom)
        val south = tileYToLat(y + 1, zoom)
        val west = tileXToLng(x, zoom)
        val east = tileXToLng(x + 1, zoom)
        return BoundingBox(north, east, south, west)
    }

    fun tileCenter(zoom: Int, x: Int, y: Int): GeoPoint {
        val bounds = tileBounds(zoom, x, y)
        return GeoPoint(
            (bounds.latNorth + bounds.latSouth) / 2.0,
            (bounds.lonEast + bounds.lonWest) / 2.0,
        )
    }

    fun boundsFromTileKeys(tileKeys: Collection<Long>): BoundingBox? {
        if (tileKeys.isEmpty()) return null
        var north = Double.NEGATIVE_INFINITY
        var south = Double.POSITIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        var west = Double.POSITIVE_INFINITY
        for (key in tileKeys) {
            val (zoom, x, y) = unpackTileKey(key)
            val bounds = tileBounds(zoom, x, y)
            north = maxOf(north, bounds.latNorth)
            south = minOf(south, bounds.latSouth)
            east = maxOf(east, bounds.lonEast)
            west = minOf(west, bounds.lonWest)
        }
        return BoundingBox(north, east, south, west)
    }

}
