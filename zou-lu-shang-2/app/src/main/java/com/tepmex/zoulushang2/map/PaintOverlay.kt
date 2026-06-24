package com.tepmex.zoulushang2.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.tepmex.zoulushang2.geo.CellMath
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

class PaintOverlay(
    private var paintLookup: HashMap<Long, Int>,
    private var gridZoom: Int = CellMath.PAINT_ZOOM,
) : Overlay() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = Rect()

    fun updateLookup(lookup: HashMap<Long, Int>, zoom: Int = CellMath.PAINT_ZOOM) {
        paintLookup = lookup
        gridZoom = zoom
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || paintLookup.isEmpty()) return
        val projection: Projection = mapView.projection
        val bbox: BoundingBox = mapView.boundingBox
        val (xRange, yRange) = visibleRanges(bbox)

        for (x in xRange) {
            for (y in yRange) {
                val key = CellMath.packCellKey(gridZoom, x, y)
                val intensity = paintLookup[key] ?: continue

                val bounds = CellMath.cellBounds(gridZoom, x, y)
                val topLeft = projection.toPixels(GeoPoint(bounds.latNorth, bounds.lonWest), null)
                val bottomRight = projection.toPixels(GeoPoint(bounds.latSouth, bounds.lonEast), null)
                rect.set(
                    minOf(topLeft.x, bottomRight.x),
                    minOf(topLeft.y, bottomRight.y),
                    maxOf(topLeft.x, bottomRight.x),
                    maxOf(topLeft.y, bottomRight.y),
                )
                ensureMinPixelSize(rect, minPixelSize(mapView))

                fillPaint.color = PaintColorIntensity.fillColor(intensity)
                canvas.drawRect(rect, fillPaint)
            }
        }
    }

    private fun minPixelSize(mapView: MapView): Int {
        val zoomGap = gridZoom - mapView.zoomLevelDouble
        if (zoomGap > 1.0) return 0
        return when {
            zoomGap <= 0.5 -> 0
            zoomGap <= 1.0 -> 4
            else -> 0
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
        val scale = 1 shl gridZoom
        val minX = CellMath.latLngToCell(bbox.latNorth, bbox.lonWest, gridZoom).first
        val maxX = CellMath.latLngToCell(bbox.latSouth, bbox.lonEast, gridZoom).first
        val minY = CellMath.latLngToCell(bbox.latNorth, bbox.lonEast, gridZoom).second
        val maxY = CellMath.latLngToCell(bbox.latSouth, bbox.lonWest, gridZoom).second
        val xRange = minOf(minX, maxX).coerceAtLeast(0)..maxOf(minX, maxX).coerceAtMost(scale - 1)
        val yRange = minOf(minY, maxY).coerceAtLeast(0)..maxOf(minY, maxY).coerceAtMost(scale - 1)
        return xRange to yRange
    }
}
