package com.tepmex.heilauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.heilauncher.R
import com.tepmex.heilauncher.domain.LifeSpan
import java.time.LocalDate
import java.time.LocalDateTime

private val Filled = Color(0xFFF2F2F2)
private val Near = Color(0xFF8A8A8A)
private val Far = Color(0xFF3A3A3A)

@Composable
fun LifeSpanRow(now: LocalDateTime, modifier: Modifier = Modifier) {
    val hours = LifeSpan.hoursUntilNearEnd(now)
    val hoursText = hours.toString()
    val description = stringResource(R.string.life_span, hoursText)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LifeSpanBar(
            today = now.toLocalDate(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = hoursText,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun LifeSpanBar(today: LocalDate, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.toInt()
        if (widthPx <= 0) return@BoxWithConstraints
        val grid = LifeSpan.grid(widthPx)
        Canvas(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val cap = constraints.maxHeight
                    val height = if (cap == Constraints.Infinity) {
                        grid.rows.coerceAtLeast(constraints.minHeight)
                    } else {
                        grid.rows.coerceIn(constraints.minHeight, cap.coerceAtLeast(constraints.minHeight))
                    }
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = height, maxHeight = height),
                    )
                    layout(placeable.width, height) { placeable.place(0, 0) }
                },
        ) {
            val columns = grid.columns
            val cellW = size.width / columns
            val cellH = size.height / grid.rows
            if (cellW <= 0f || cellH <= 0f) return@Canvas
            val filledThrough = LifeSpan.todayIndex(today)
            val nearEnd = LifeSpan.nearEndIndex
            for (index in 0 until LifeSpan.dayCount) {
                val color = when {
                    index <= filledThrough -> Filled
                    index <= nearEnd -> Near
                    else -> Far
                }
                val cell = LifeSpan.cell(index, grid)
                drawRect(
                    color = color,
                    topLeft = Offset(cell.column * cellW, cell.row * cellH),
                    size = Size(cellW, cellH),
                )
            }
        }
    }
}
