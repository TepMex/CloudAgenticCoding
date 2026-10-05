package com.tepmex.cornegame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tepmex.cornegame.data.SessionRecord

@Composable
fun ProgressScreen(
    history: List<SessionRecord>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = history.takeLast(20)
    val record = history.maxByOrNull { it.wpm }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Назад") }
            Text("Прогресс", style = MaterialTheme.typography.titleLarge)
        }
        if (history.isEmpty()) {
            Text("Пока нет завершённых сессий. Недописанная тренировка сюда не попадает.")
            return@Column
        }
        Text("Сессий: ${history.size}")
        if (record != null) {
            Text(
                "Рекорд: ${formatWpm(record.wpm)} WPM · ${record.language.name} · ${formatWhen(record.finishedAtEpochMs)}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "WPM последних ${recent.size}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WpmChart(recent, Modifier.fillMaxWidth().height(180.dp))
        history.asReversed().forEach { item ->
            Text(
                "${formatWhen(item.finishedAtEpochMs)}   ${item.language.name}   ${formatWpm(item.wpm)} WPM   ${formatAccuracy(item.accuracy)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WpmChart(sessions: List<SessionRecord>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier) {
        if (sessions.isEmpty()) return@Canvas
        val pad = 12.dp.toPx()
        val plotW = (size.width - pad * 2).coerceAtLeast(1f)
        val plotH = (size.height - pad * 2).coerceAtLeast(1f)
        val maxWpm = sessions.maxOf { it.wpm }.coerceAtLeast(1.0)
        drawLine(
            color = grid,
            start = Offset(pad, pad + plotH),
            end = Offset(pad + plotW, pad + plotH),
            strokeWidth = 2f,
        )
        val points = sessions.mapIndexed { index, item ->
            val x = if (sessions.size == 1) {
                pad + plotW / 2f
            } else {
                pad + plotW * index / (sessions.size - 1)
            }
            val y = pad + plotH - (item.wpm / maxWpm * plotH).toFloat()
            Offset(x, y)
        }
        val path = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        drawPath(
            path = path,
            color = line,
            style = Stroke(width = 4f, cap = StrokeCap.Round),
        )
        points.forEach { point ->
            drawCircle(color = line, radius = 6f, center = point)
        }
    }
}
