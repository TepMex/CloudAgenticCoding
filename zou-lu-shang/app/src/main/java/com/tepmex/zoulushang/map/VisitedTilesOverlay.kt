package com.tepmex.zoulushang.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.tepmex.zoulushang.geo.TileMath
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

class VisitedTilesOverlay(
    private var visitedLookup: HashMap<Long, Boolean>,
) : Overlay() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x6600C853.toInt()
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x9900A040.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val rect = Rect()

    fun updateLookup(lookup: HashMap<Long, Boolean>) {
        visitedLookup = lookup
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || visitedLookup.isEmpty()) return
        val projection: Projection = mapView.projection
        val bbox: BoundingBox = mapView.boundingBox
        val (xRange, yRange) = visibleRanges(bbox)
        val zoom = TileMath.GRID_ZOOM

        for (x in xRange) {
            for (y in yRange) {
                val key = TileMath.packTileKey(zoom, x, y)
                if (visitedLookup[key] != true) continue
                val bounds = TileMath.tileBounds(zoom, x, y)
                val topLeft = projection.toPixels(
                    org.osmdroid.util.GeoPoint(bounds.latNorth, bounds.lonWest),
                    null,
                )
                val bottomRight = projection.toPixels(
                    org.osmdroid.util.GeoPoint(bounds.latSouth, bounds.lonEast),
                    null,
                )
                rect.set(
                    minOf(topLeft.x, bottomRight.x),
                    minOf(topLeft.y, bottomRight.y),
                    maxOf(topLeft.x, bottomRight.x),
                    maxOf(topLeft.y, bottomRight.y),
                )
                canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, strokePaint)
            }
        }
    }

    private fun visibleRanges(bbox: BoundingBox): Pair<IntRange, IntRange> {
        val scale = 1 shl TileMath.GRID_ZOOM
        val minX = TileMath.latLngToTile(bbox.latNorth, bbox.lonWest).first
        val maxX = TileMath.latLngToTile(bbox.latSouth, bbox.lonEast).first
        val minY = TileMath.latLngToTile(bbox.latNorth, bbox.lonEast).second
        val maxY = TileMath.latLngToTile(bbox.latSouth, bbox.lonWest).second
        val xRange = minOf(minX, maxX).coerceAtLeast(0)..maxOf(minX, maxX).coerceAtMost(scale - 1)
        val yRange = minOf(minY, maxY).coerceAtLeast(0)..maxOf(minY, maxY).coerceAtMost(scale - 1)
        return xRange to yRange
    }
}
