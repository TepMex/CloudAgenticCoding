package com.tepmex.ankidashboard.ui

import android.content.Context
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.tepmex.ankidashboard.R

object ChartTheme {

    fun apply(context: Context, chart: CombinedChart) {
        applyCommon(context, chart)
        chart.axisRight.isEnabled = true
        chart.axisLeft.isEnabled = true
        chart.drawOrder = arrayOf(
            CombinedChart.DrawOrder.BAR,
            CombinedChart.DrawOrder.LINE,
        )
    }

    fun apply(context: Context, chart: BarChart) {
        applyCommon(context, chart)
        chart.axisRight.isEnabled = false
        chart.axisLeft.isEnabled = true
    }

    private fun applyCommon(
        context: Context,
        chart: BarLineChartBase<*>,
    ) {
        val axisText = context.getColor(R.color.chart_axis_text)
        val grid = context.getColor(R.color.chart_grid)
        val surface = context.getColor(R.color.dashboard_surface)
        val legendText = context.getColor(R.color.chart_legend_text)

        chart.setBackgroundColor(surface)
        chart.setDrawGridBackground(false)
        chart.setNoDataTextColor(context.getColor(R.color.dashboard_text_muted))
        chart.setBorderColor(grid)
        chart.setExtraOffsets(8f, 8f, 12f, 8f)
        chart.description.isEnabled = false

        styleYAxis(chart.axisLeft, axisText, grid, drawGrid = true)
        styleYAxis(chart.axisRight, axisText, grid, drawGrid = false)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = axisText
            axisLineColor = grid
            gridColor = grid
            setDrawGridLines(false)
            textSize = 11f
        }

        chart.legend.apply {
            isEnabled = true
            textColor = legendText
            textSize = 12f
            formSize = 10f
            form = Legend.LegendForm.SQUARE
            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            yOffset = 8f
        }
    }

    private fun styleYAxis(axis: YAxis, textColor: Int, gridColor: Int, drawGrid: Boolean) {
        axis.textColor = textColor
        axis.axisLineColor = gridColor
        axis.gridColor = gridColor
        axis.textSize = 11f
        axis.setDrawGridLines(drawGrid)
    }
}
