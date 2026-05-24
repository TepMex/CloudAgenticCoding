package com.tepmex.ankidashboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.tepmex.ankidashboard.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * GitHub-style calendar heatmap for review intensity / mistakes.
 */
class CalendarHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var valuesByDay: Map<String, Int> = emptyMap()
    private var maxCount: Int = 1
    private var colorScheme: ColorScheme = ColorScheme.ANKI
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyColor = context.getColor(R.color.heatmap_empty)
    private val ankiColors = intArrayOf(
        context.getColor(R.color.heatmap_anki_1),
        context.getColor(R.color.heatmap_anki_2),
        context.getColor(R.color.heatmap_anki_3),
        context.getColor(R.color.heatmap_anki_4),
        context.getColor(R.color.heatmap_anki_5),
        context.getColor(R.color.heatmap_anki_6),
        context.getColor(R.color.heatmap_anki_7),
        context.getColor(R.color.heatmap_anki_8),
        context.getColor(R.color.heatmap_anki_9),
        context.getColor(R.color.heatmap_anki_10),
    )
    private val mistakeColors = intArrayOf(
        context.getColor(R.color.heatmap_mistake_1),
        context.getColor(R.color.heatmap_mistake_2),
        context.getColor(R.color.heatmap_mistake_3),
        context.getColor(R.color.heatmap_mistake_4),
        context.getColor(R.color.heatmap_mistake_5),
    )

    enum class ColorScheme { ANKI, MISTAKE }

    fun setData(dayCounts: List<Pair<String, Int>>, scheme: ColorScheme) {
        colorScheme = scheme
        valuesByDay = dayCounts.toMap()
        maxCount = max(1, valuesByDay.values.maxOrNull() ?: 1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        canvas.drawColor(emptyColor)

        val end = LocalDate.now()
        val start = end.minusMonths(12)
        val weeks = ChronoUnit.WEEKS.between(
            start.with(DayOfWeek.MONDAY),
            end,
        ).toInt() + 2
        val rows = 7
        val gap = 2f
        val cellW = (width - gap * (weeks - 1)) / weeks
        val cellH = (height - gap * (rows - 1)) / rows
        val size = minOf(cellW, cellH).coerceAtLeast(4f)

        var date = start.with(DayOfWeek.MONDAY)
        var week = 0
        while (!date.isAfter(end)) {
            for (dow in 0 until 7) {
                val current = date.plusDays(dow.toLong())
                if (current.isAfter(end)) break
                val key = current.toString()
                val count = valuesByDay[key] ?: 0
                cellPaint.color = colorForCount(count)
                val x = week * (size + gap)
                val y = dow * (size + gap)
                canvas.drawRoundRect(x, y, x + size, y + size, 2f, 2f, cellPaint)
            }
            date = date.plusWeeks(1)
            week++
        }
    }

    private fun colorForCount(count: Int): Int {
        if (count <= 0) return emptyColor
        val palette = if (colorScheme == ColorScheme.ANKI) ankiColors else mistakeColors
        if (maxCount <= 0) return palette.first()
        val thresholds = if (colorScheme == ColorScheme.ANKI) {
            (9 downTo 1).map { maxCount * it / 10.0 }.toList()
        } else {
            listOf(
                maxCount * 0.8,
                maxCount * 0.6,
                maxCount * 0.4,
                maxCount * 0.2,
                1.0,
            )
        }
        for (i in thresholds.indices) {
            if (count >= thresholds[i]) {
                return palette[minOf(i, palette.size - 1)]
            }
        }
        return palette.last()
    }
}
