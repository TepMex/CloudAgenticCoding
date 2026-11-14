package dev.eareye.t.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.eareye.t.domain.TimeInterval
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun IntervalTrack(
    title: String,
    window: TimeInterval,
    spans: List<Pair<TimeInterval, Color>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(top = 4.dp),
        ) {
            drawRect(TrackColors.idle.copy(alpha = 0.45f))
            if (window.isEmpty()) return@Canvas
            val span = (window.endMillis - window.startMillis).coerceAtLeast(1L).toFloat()
            spans.forEach { (interval, color) ->
                val start = ((interval.startMillis - window.startMillis).coerceAtLeast(0L)).toFloat() / span
                val end = ((interval.endMillis - window.startMillis).coerceAtMost(window.durationMillis)).toFloat() / span
                val left = start * size.width
                val right = end * size.width
                if (right > left) {
                    drawRect(
                        color = color,
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height),
                    )
                }
            }
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) { drawRect(color) }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

fun formatClock(millis: Long, zone: ZoneId): String {
    return Instant.ofEpochMilli(millis).atZone(zone).format(CLOCK)
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
