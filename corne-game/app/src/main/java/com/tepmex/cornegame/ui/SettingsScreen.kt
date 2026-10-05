package com.tepmex.cornegame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tepmex.cornegame.data.AppSettings
import com.tepmex.cornegame.domain.Finger

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Назад") }
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Сессия не сбрасывается.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        SettingSwitch(
            label = "Подсветка следующей клавиши",
            checked = settings.highlight,
            onChecked = { value -> onChange { it.copy(highlight = value) } },
        )
        SettingSwitch(
            label = "Режим «по пальцам»",
            checked = settings.fingerColors,
            onChecked = { value -> onChange { it.copy(fingerColors = value) } },
        )
        if (settings.fingerColors) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Finger.entries.forEach { finger ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(finger.zoneColor()),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(fingerLabel(finger), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        SettingSwitch(
            label = "Вибрация при ошибке",
            checked = settings.vibrate,
            onChecked = { value -> onChange { it.copy(vibrate = value) } },
        )
        Spacer(Modifier.height(8.dp))
        Text("Размер клавиш")
        Slider(
            value = settings.keyScale,
            onValueChange = { value -> onChange { it.copy(keyScale = value) } },
            valueRange = 0.65f..1f,
        )
        Text(
            "Клавиатура всегда вписывается в экран. Ползунок только уменьшает клавиши.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
