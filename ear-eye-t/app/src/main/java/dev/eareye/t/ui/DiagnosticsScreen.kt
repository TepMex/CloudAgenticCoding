package dev.eareye.t.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.eareye.t.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
) {
    if (state.loading && state.events.isEmpty() && state.model.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }
        return
    }
    val zone = ZoneId.systemDefault()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.headlineMedium)
        }
        item { Fact("Android", "${state.release} (API ${state.sdk})") }
        item { Fact("Устройство", "${state.manufacturer} ${state.model}") }
        item { Fact("Статистика использования", yesNo(state.usageGranted)) }
        item { Fact("Доступ к уведомлениям", yesNo(state.listenerEnabled)) }
        item { Fact("Служба подключена", yesNo(state.listenerConnected)) }
        item { Fact("Экран интерактивен", yesNo(state.screenInteractive)) }
        item { Fact("Экран блокировки показан", yesNo(state.keyguardLocked)) }
        item { Text(stringResource(R.string.keyguard_note), style = MaterialTheme.typography.bodySmall) }
        item {
            Fact(
                "Звук сейчас",
                if (!state.listenerConnected) {
                    stringResource(R.string.monitor_off)
                } else if (state.audioActive) {
                    stringResource(R.string.audio_now_on)
                } else {
                    stringResource(R.string.audio_now_off)
                },
            )
        }
        item { Fact("Последний callback", stamp(state.lastCallbackMillis, zone)) }
        item { Fact("Последний heartbeat", stamp(state.lastHeartbeatMillis, zone)) }
        item { Fact("Последний снимок", stamp(state.lastSnapshotMillis, zone)) }
        item { Fact("Последнее подтверждение", stamp(state.lastConfirmationMillis, zone)) }
        item { Fact("Успешная синхронизация экрана", stamp(state.lastSyncMillis, zone)) }
        item { Fact("Доверенный горизонт экрана", stamp(state.trustedUntilMillis, zone)) }
        state.syncError?.let { error -> item { Fact("Ошибка синхронизации", error) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_json))
                }
                Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_csv))
                }
            }
        }
        item { Text("Последние события", style = MaterialTheme.typography.titleMedium) }
        if (state.events.isEmpty()) {
            item { Text(stringResource(R.string.no_events)) }
        } else {
            items(state.events, key = { it.id }) { event ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${stamp(event.wallMillis, zone)} · ${event.kind}", style = MaterialTheme.typography.labelMedium)
                    Text(event.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.yes else R.string.no)

private fun stamp(millis: Long?, zone: ZoneId): String {
    if (millis == null) return "нет данных"
    return Instant.ofEpochMilli(millis).atZone(zone).format(STAMP)
}

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss", Locale.forLanguageTag("ru"))
