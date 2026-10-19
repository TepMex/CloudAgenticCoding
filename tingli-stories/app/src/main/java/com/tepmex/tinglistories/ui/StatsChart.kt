package com.tepmex.tinglistories.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.tinglistories.domain.StatsPoint
import com.tepmex.tinglistories.domain.catalogLabelIndices
import com.tepmex.tinglistories.domain.chartPeak
import com.tepmex.tinglistories.domain.listenAxis
import com.tepmex.tinglistories.domain.statsChartDescription
import kotlin.math.min

internal const val StatsChartTag = "stats-chart"

@Composable
fun StatsChart(points: List<StatsPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val green = MaterialTheme.colorScheme.secondary
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline
    val description = statsChartDescription(points)
    Canvas(
        modifier
            .fillMaxWidth()
            .testTag(StatsChartTag)
            .semantics { contentDescription = description },
    ) {
        drawBarChart(points, green, gray, labelColor, gridColor)
    }
}

private fun DrawScope.drawBarChart(
    points: List<StatsPoint>,
    green: Color,
    gray: Color,
    labelColor: Color,
    gridColor: Color,
) {
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.toArgb()
        textSize = 11.sp.toPx()
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val axis = listenAxis(chartPeak(points))
    val leftReserve = axis.ticks.maxOf { labelPaint.measureText(it.toString()) } + 10.dp.toPx()
    val rightPad = 8.dp.toPx()
    val labelHeight = labelPaint.fontMetrics.let { it.descent - it.ascent }
    val plotLeft = leftReserve
    val plotRight = size.width - rightPad
    val plotTop = labelHeight / 2f + 4.dp.toPx()
    val plotBottom = size.height - labelHeight - 10.dp.toPx()
    if (plotRight <= plotLeft || plotBottom <= plotTop) return

    val valueTop = axis.top.toFloat().coerceAtLeast(1f)
    fun yOf(value: Float): Float =
        plotBottom - (plotBottom - plotTop) * (value / valueTop).coerceIn(0f, 1f)

    val grid = gridColor.copy(alpha = 0.85f)
    for (tick in axis.ticks) {
        val y = yOf(tick.toFloat())
        drawLine(grid, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
        drawAxisLabel(tick.toString(), 0f, y, labelPaint)
    }

    val slot = (plotRight - plotLeft) / points.size
    val barWidth = min(slot * 0.62f, 48.dp.toPx())
    val minHeight = 2.dp.toPx()
    points.forEachIndexed { index, point ->
        val center = plotLeft + slot * (index + 0.5f)
        val height = (plotBottom - yOf(point.indicator.toFloat())).coerceAtLeast(minHeight)
        val radius = minOf(3.dp.toPx(), barWidth / 2f, height / 2f)
        drawRoundRect(
            color = if (point.green) green else gray,
            topLeft = Offset(center - barWidth / 2f, plotBottom - height),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }

    labelPaint.textAlign = Paint.Align.CENTER
    val baseline = size.height - labelPaint.fontMetrics.descent
    for (index in catalogLabelIndices(points.size)) {
        val label = points[index].catalogNumber.toString().padStart(2, '0')
        val width = labelPaint.measureText(label)
        val x = (plotLeft + slot * (index + 0.5f)).coerceIn(width / 2f, size.width - width / 2f)
        drawContext.canvas.nativeCanvas.drawText(label, x, baseline, labelPaint)
    }
}

private fun DrawScope.drawAxisLabel(
    text: String,
    anchorX: Float,
    centerY: Float,
    paint: Paint,
) {
    val previous = paint.textAlign
    paint.textAlign = Paint.Align.LEFT
    val baseline = centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, anchorX, baseline, paint)
    paint.textAlign = previous
}
