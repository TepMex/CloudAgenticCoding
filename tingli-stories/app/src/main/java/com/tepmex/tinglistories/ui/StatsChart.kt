package com.tepmex.tinglistories.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.tepmex.tinglistories.domain.valueRuns

internal const val StatsChartTag = "stats-chart"

@Immutable
internal data class StatsChartPalette(
    val listens: Color,
    val average5: Color,
    val average10: Color,
    val percent: Color,
)

@Composable
internal fun statsChartPalette(): StatsChartPalette {
    val dark = isSystemInDarkTheme()
    return StatsChartPalette(
        listens = MaterialTheme.colorScheme.primary,
        average5 = MaterialTheme.colorScheme.secondary,
        average10 = if (dark) Color(0xFF8FB8DC) else Color(0xFF24557A),
        percent = if (dark) Color(0xFFE4C07A) else Color(0xFFB86A14),
    )
}

@Composable
fun StatsChart(points: List<StatsPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val palette = statsChartPalette()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val description = statsChartDescription(points)
    Column(modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            LegendItem(palette.listens, "Прослушивания")
            LegendItem(palette.average5, "Среднее 5")
            LegendItem(palette.average10, "Среднее 10")
            LegendItem(palette.percent, "Верные, %")
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(228.dp)
                .testTag(StatsChartTag)
                .semantics { contentDescription = description },
        ) {
            drawStatsPlot(points, palette, axisColor, gridColor)
        }
        Text(
            "Левая ось — число прослушиваний. Правая — процент верных ответов. Среднее считается по текущей истории и предыдущим, когда в окне уже 5 или 10 историй.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawStatsPlot(
    points: List<StatsPoint>,
    palette: StatsChartPalette,
    axisColor: Color,
    gridColor: Color,
) {
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = axisColor.toArgb()
        textSize = 11.sp.toPx()
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val axis = listenAxis(chartPeak(points))
    val leftReserve = axis.ticks.maxOf { labelPaint.measureText(it.toString()) } + 8.dp.toPx()
    val rightReserve = labelPaint.measureText("100%") + 8.dp.toPx()
    val labelHeight = labelPaint.fontMetrics.let { it.descent - it.ascent }
    val plotLeft = leftReserve
    val plotRight = size.width - rightReserve
    val plotTop = labelHeight / 2f + 4.dp.toPx()
    val plotBottom = size.height - labelHeight - 8.dp.toPx()
    if (plotRight <= plotLeft || plotBottom <= plotTop) return

    val listenTop = axis.top.toFloat()
    fun yListen(value: Float): Float = plotBottom - (plotBottom - plotTop) * (value / listenTop)
    fun yPercent(value: Float): Float = plotBottom - (plotBottom - plotTop) * (value / 100f)
    fun xAt(index: Int): Float {
        if (points.size <= 1) return (plotLeft + plotRight) / 2f
        return plotLeft + (plotRight - plotLeft) * index / (points.size - 1)
    }

    val grid = gridColor.copy(alpha = 0.85f)
    for (tick in axis.ticks) {
        val y = yListen(tick.toFloat())
        drawLine(grid, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
        drawAxisLabel(tick.toString(), 0f, y, labelPaint, Align.LEFT)
    }
    for (tick in listOf(0, 50, 100)) {
        drawAxisLabel("$tick%", size.width, yPercent(tick.toFloat()), labelPaint, Align.RIGHT)
    }

    val percentValues = points.map { it.correctPercent }
    val shortValues = points.map { it.average5 }
    val longValues = points.map { it.average10 }
    drawSeries(percentValues, palette.percent, 2.dp.toPx(), ::xAt, ::yPercent)
    drawSeries(longValues, palette.average10, 2.dp.toPx(), ::xAt) { yListen(it) }
    drawSeries(shortValues, palette.average5, 2.dp.toPx(), ::xAt) { yListen(it) }
    drawSeries(
        points.map<StatsPoint, Double?> { it.listens.toDouble() },
        palette.listens,
        2.5.dp.toPx(),
        ::xAt,
    ) { yListen(it) }

    labelPaint.textAlign = Paint.Align.CENTER
    val baseline = size.height - labelPaint.fontMetrics.descent
    for (index in catalogLabelIndices(points.size)) {
        val label = (index + 1).toString().padStart(2, '0')
        val width = labelPaint.measureText(label)
        val x = xAt(index).coerceIn(width / 2f, size.width - width / 2f)
        drawContext.canvas.nativeCanvas.drawText(label, x, baseline, labelPaint)
    }
}

private enum class Align { LEFT, RIGHT }

private fun DrawScope.drawAxisLabel(
    text: String,
    anchorX: Float,
    centerY: Float,
    paint: Paint,
    align: Align,
) {
    val previous = paint.textAlign
    paint.textAlign = if (align == Align.RIGHT) Paint.Align.RIGHT else Paint.Align.LEFT
    val baseline = centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, anchorX, baseline, paint)
    paint.textAlign = previous
}

private fun DrawScope.drawSeries(
    values: List<Double?>,
    color: Color,
    strokePx: Float,
    xOf: (Int) -> Float,
    yOf: (Float) -> Float,
) {
    val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (run in valueRuns(values)) {
        if (run.first == run.last) {
            val value = values[run.first] ?: continue
            drawCircle(color, radius = strokePx, center = Offset(xOf(run.first), yOf(value.toFloat())))
        } else {
            val path = Path()
            val first = values[run.first] ?: continue
            path.moveTo(xOf(run.first), yOf(first.toFloat()))
            for (index in run.first + 1..run.last) {
                val value = values[index] ?: continue
                path.lineTo(xOf(index), yOf(value.toFloat()))
            }
            drawPath(path, color, style = stroke)
        }
    }
}
