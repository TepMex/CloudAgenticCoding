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
    private var visitedLookup: HashMap<Long, Int>,
) : Overlay() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val rect = Rect()

    fun updateLookup(lookup: HashMap<Long, Int>) {
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
                val pointCount = visitedLookup[key] ?: continue
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
                ensureMinPixelSize(rect, minPixelSize(mapView))
                fillPaint.color = TileColorIntensity.fillColor(pointCount)
                strokePaint.color = TileColorIntensity.strokeColor(pointCount)
                canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, strokePaint)
            }
        }
    }

    private fun minPixelSize(mapView: MapView): Int {
        val zoomGap = TileMath.GRID_ZOOM - mapView.zoomLevelDouble
        return when {
            zoomGap <= 1.0 -> 0
            zoomGap <= 3.0 -> 6
            else -> 10
        }
    }

    private fun ensureMinPixelSize(rect: Rect, minPx: Int) {
        if (minPx <= 0) return
        if (rect.width() >= minPx && rect.height() >= minPx) return
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2
        val half = minPx / 2
        rect.set(centerX - half, centerY - half, centerX + half, centerY + half)
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
