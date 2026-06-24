package com.tepmex.zoulushang2.geo

object BrushEngine {
    const val MAX_INTENSITY = 1000
    const val MOVEMENT_THRESHOLD_METERS = 4.0
    const val STAY_INTENSITY = 10
    const val BRUSH_RADIUS_CELLS = 1
    private const val ZOOM = CellMath.PAINT_ZOOM

    fun applyLocation(
        latitude: Double,
        longitude: Double,
        lastLatitude: Double?,
        lastLongitude: Double?,
        onCellPaint: (cellKey: Long, delta: Int) -> Unit,
    ) {
        val (x, y) = CellMath.latLngToCell(latitude, longitude, ZOOM)
        if (lastLatitude == null || lastLongitude == null) {
            paintSpot(x, y, onCellPaint)
            return
        }

        val distance = CellMath.distanceMeters(lastLatitude, lastLongitude, latitude, longitude)
        if (distance >= MOVEMENT_THRESHOLD_METERS) {
            val (lastX, lastY) = CellMath.latLngToCell(lastLatitude, lastLongitude, ZOOM)
            paintLine(lastX, lastY, x, y, onCellPaint)
        } else {
            paintSpot(x, y, onCellPaint)
        }
    }

    private fun paintSpot(
        centerX: Int,
        centerY: Int,
        onCellPaint: (cellKey: Long, delta: Int) -> Unit,
    ) {
        val scale = 1 shl ZOOM
        for (dx in -BRUSH_RADIUS_CELLS..BRUSH_RADIUS_CELLS) {
            for (dy in -BRUSH_RADIUS_CELLS..BRUSH_RADIUS_CELLS) {
                val x = centerX + dx
                val y = centerY + dy
                if (x !in 0 until scale || y !in 0 until scale) continue
                val falloff = when {
                    dx == 0 && dy == 0 -> 1.0
                    kotlin.math.abs(dx) + kotlin.math.abs(dy) == 1 -> 0.6
                    else -> 0.35
                }
                val delta = (STAY_INTENSITY * falloff).toInt().coerceAtLeast(1)
                onCellPaint(CellMath.packCellKey(ZOOM, x, y), delta)
            }
        }
    }

    private fun paintLine(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        onCellPaint: (cellKey: Long, delta: Int) -> Unit,
    ) {
        var x = x0
        var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            paintSpot(x, y, onCellPaint)
            if (x == x1 && y == y1) break
            val err2 = err * 2
            if (err2 > -dy) {
                err -= dy
                x += sx
            }
            if (err2 < dx) {
                err += dx
                y += sy
            }
        }
    }
}
