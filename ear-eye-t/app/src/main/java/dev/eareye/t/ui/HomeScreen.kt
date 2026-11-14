package dev.eareye.t.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.eareye.t.R
import dev.eareye.t.domain.DailyStatistics
import dev.eareye.t.domain.DaySelection
import dev.eareye.t.domain.TimeInterval
import dev.eareye.t.domain.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelect: (DaySelection) -> Unit,
    onPickDate: () -> Unit,
    onOpenAccess: () -> Unit,
) {
    if (state.loading && state.stats == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val zone = runCatching { ZoneId.of(state.zoneId) }.getOrElse { ZoneId.systemDefault() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.screen_title), style = MaterialTheme.typography.headlineMedium)
        Text(text = state.dateLabel, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selection is DaySelection.Today,
                onClick = { onSelect(DaySelection.Today) },
                label = { Text(stringResource(R.string.chip_today)) },
            )
            FilterChip(
                selected = state.selection is DaySelection.Yesterday,
                onClick = { onSelect(DaySelection.Yesterday) },
                label = { Text(stringResource(R.string.chip_yesterday)) },
            )
            FilterChip(
                selected = state.selection is DaySelection.Specific,
                onClick = onPickDate,
                label = { Text(stringResource(R.string.chip_date)) },
            )
        }
        StatusLine(state, zone)
        if (!state.usageGranted || !state.listenerEnabled) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.need_access))
                    TextButton(onClick = onOpenAccess) { Text(stringResource(R.string.open_access)) }
                }
            }
        }
        val stats = state.stats
        if (stats == null || stats.window.isEmpty()) {
            Text(stringResource(R.string.future_day))
        } else {
            if (stats.incomplete) IncompleteCard(stats)
            Metric(stringResource(R.string.eye_time), formatDuration(stats.eyeMillis), stringResource(R.string.confirmed_eye))
            Metric(stringResource(R.string.ear_time), formatDuration(stats.earMillis), stringResource(R.string.confirmed_ear))
            Metric(stringResource(R.string.overlap_time), formatDuration(stats.overlapMillis), null)
            Metric(stringResource(R.string.union_time), formatDuration(stats.unionMillis), null)
            Metric(stringResource(R.string.eye_only), formatDuration(stats.eyeOnlyMillis), null)
            Metric(stringResource(R.string.ear_only), formatDuration(stats.earOnlyMillis), null)
            Metric(
                stringResource(R.string.coverage),
                String.format(Locale.forLanguageTag("ru"), "%.1f%%", stats.coverageFraction * 100.0),
                null,
            )
            Metric(stringResource(R.string.unknown_time), formatDuration(stats.unknownMillis), null)
            if (stats.possiblyUndercountedEarMillis > 0L) {
                Text("Возможно не учтено ушное время: ${formatDuration(stats.possiblyUndercountedEarMillis)}.")
            }
            if (stats.eyeDuringAudioUnknownMillis > 0L) {
                Text(
                    "Глазное время вне наблюдения аудио: ${formatDuration(stats.eyeDuringAudioUnknownMillis)}. " +
                        "Оно не входит в «только экран», пересечение и общее время.",
                )
            }
            TimelineBlock(state, zone)
        }
        Text(text = stringResource(R.string.player_note), style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun StatusLine(state: HomeUiState, zone: ZoneId) {
    val monitor = if (state.listenerConnected) {
        stringResource(R.string.monitor_on)
    } else {
        stringResource(R.string.monitor_off)
    }
    val audio = if (!state.listenerConnected) {
        ""
    } else if (state.audioActive) {
        " · ${stringResource(R.string.audio_now_on)}"
    } else {
        " · ${stringResource(R.string.audio_now_off)}"
    }
    Text(text = monitor + audio, style = MaterialTheme.typography.bodyLarge)
    val confirmation = state.lastConfirmationMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(zone).format(STAMP)
    } ?: "нет данных"
    Text(text = "Последнее подтверждение работы: $confirmation", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun IncompleteCard(stats: DailyStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.incomplete), style = MaterialTheme.typography.titleMedium)
            Text(
                "Подтверждённые числа выше не дополняются оценкой. Неизвестные ${formatDuration(stats.unknownMillis)} " +
                    "не называются тишиной и не входят в формулу общего времени.",
            )
        }
    }
}

@Composable
private fun Metric(title: String, value: String, caption: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        if (caption != null) {
            Text(text = caption, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TimelineBlock(state: HomeUiState, zone: ZoneId) {
    val timeline = state.timeline ?: return
    val window = timeline.window
    if (window.isEmpty()) return
    Text(text = "Шкала", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "${formatClock(window.startMillis, zone)} – ${formatClock(window.endMillis, zone)}",
        style = MaterialTheme.typography.bodySmall,
    )
    IntervalTrack(
        title = stringResource(R.string.timeline_eye),
        window = window,
        spans = colored(timeline.eyeUnknown, TrackColors.unknown) + colored(timeline.eye, TrackColors.eye),
    )
    IntervalTrack(
        title = stringResource(R.string.timeline_ear),
        window = window,
        spans = colored(timeline.audioUnknown, TrackColors.unknown) +
            colored(timeline.poweredOff, TrackColors.poweredOff) +
            colored(timeline.ear, TrackColors.ear),
    )
    IntervalTrack(
        title = stringResource(R.string.timeline_overlap),
        window = window,
        spans = colored(timeline.overlap, TrackColors.overlap),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendRow(TrackColors.eye, stringResource(R.string.timeline_eye))
        LegendRow(TrackColors.ear, stringResource(R.string.timeline_ear))
        LegendRow(TrackColors.overlap, stringResource(R.string.timeline_overlap))
        LegendRow(TrackColors.unknown, stringResource(R.string.timeline_unknown))
        LegendRow(TrackColors.poweredOff, stringResource(R.string.timeline_off))
    }
}

private fun colored(intervals: List<TimeInterval>, color: androidx.compose.ui.graphics.Color) =
    intervals.map { it to color }

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss", Locale.forLanguageTag("ru"))
