package com.tepmex.zoulushang2.map

import android.graphics.Canvas
import android.graphics.Paint
import com.tepmex.zoulushang2.data.PaintStroke
import com.tepmex.zoulushang2.geo.GeoMath
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class StrokeOverlay(
    private var strokes: List<PaintStroke> = emptyList(),
) : Overlay() {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun updateStrokes(newStrokes: List<PaintStroke>) {
        strokes = newStrokes
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || strokes.isEmpty()) return
        val projection = mapView.projection

        for (stroke in strokes) {
            val widthPx = GeoMath.metersToPixels(
                stroke.thicknessMeters.toDouble(),
                stroke.latStart,
                stroke.lngStart,
                projection,
            )
            strokePaint.color = stroke.colorArgb
            strokePaint.strokeWidth = widthPx

            val start = projection.toPixels(GeoPoint(stroke.latStart, stroke.lngStart), null)
            val end = projection.toPixels(GeoPoint(stroke.latEnd, stroke.lngEnd), null)

            if (stroke.latStart == stroke.latEnd && stroke.lngStart == stroke.lngEnd) {
                fillPaint.color = stroke.colorArgb
                canvas.drawCircle(start.x.toFloat(), start.y.toFloat(), widthPx / 2f, fillPaint)
            } else {
                canvas.drawLine(
                    start.x.toFloat(),
                    start.y.toFloat(),
                    end.x.toFloat(),
                    end.y.toFloat(),
                    strokePaint,
                )
            }
        }
    }
}
