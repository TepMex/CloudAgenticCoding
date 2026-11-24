package com.tepmex.assumptions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tepmex.assumptions.domain.EmotionEntry
import com.tepmex.assumptions.domain.Emotions
import com.tepmex.assumptions.domain.WheelHit
import com.tepmex.assumptions.domain.WheelState
import com.tepmex.assumptions.domain.clockLabel
import com.tepmex.assumptions.domain.dayLabel
import com.tepmex.assumptions.ui.theme.InkSoft
import com.tepmex.assumptions.ui.theme.argb
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun EmotionsScreen(
    wheel: WheelState,
    emotions: List<EmotionEntry>,
    onHit: (WheelHit) -> Unit,
    onRecord: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val selected = wheel.selectedId?.let { Emotions.find(it) }
    var pendingDelete by remember { mutableStateOf<EmotionEntry?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Эмоции",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight).coerceAtMost(460.dp)
            EmotionWheel(
                state = wheel,
                onHit = onHit,
                modifier = Modifier.size(side),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected == null) {
                Text(
                    text = if (wheel.focusedIndex == null) {
                        "Нажмите сектор. Палец на открытом уровне открывает следующий."
                    } else {
                        "Центр сворачивает колесо. Записать можно выбранную эмоцию."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(argb(selected.colorArgb), CircleShape),
                    )
                    Text(
                        text = selected.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onRecord) {
                        Text("Записать")
                    }
                }
            }
        }
        Text(
            text = "Записи",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (emotions.isEmpty()) {
            Text(
                text = "Пока пусто — выберите эмоцию на колесе.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        } else {
            val grouped = emotions.groupBy { dayLabel(it.createdAtEpochMs, zone, today) }
            LazyColumn(modifier = Modifier.weight(0.9f)) {
                grouped.forEach { (day, rows) ->
                    item(key = "day-$day") {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSoft,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(rows, key = { it.id }) { entry ->
                        EmotionRow(
                            entry = entry,
                            zone = zone,
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        val name = Emotions.find(entry.emotionId)?.name ?: "запись"
        ConfirmDelete(
            title = "Удалить запись?",
            body = name,
            onConfirm = {
                onDelete(entry.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun EmotionRow(
    entry: EmotionEntry,
    zone: ZoneId,
    onDelete: () -> Unit,
) {
    val emotion = Emotions.find(entry.emotionId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(argb(emotion?.colorArgb ?: 0xFF9E9E9E), CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = emotion?.name ?: entry.emotionId,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = clockLabel(entry.createdAtEpochMs, zone),
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
        }
    }
}
