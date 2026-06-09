package com.tepmex.zoulushang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.zoulushang.geo.TileMath
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val draft = uiState.settingsDraft ?: return

    val referenceLatitude = uiState.selectedCity?.let { (it.minLat + it.maxLat) / 2.0 } ?: 0.0
    val tileWidthMeters = TileMath.approximateTileWidthMeters(referenceLatitude, draft.gridZoom)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setShowSettings(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Control which tile grids appear on the map and how large each square tile is.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsToggleRow(
                title = "Takeout grid",
                description = "Show tiles from imported Google Takeout location history.",
                checked = draft.showTakeoutGrid,
                onCheckedChange = { viewModel.updateSettingsDraft(showTakeoutGrid = it) },
                enabled = !uiState.isSavingSettings,
            )

            SettingsToggleRow(
                title = "Fill-the-map grid",
                description = "Show tiles recorded during live GPS fill-the-map sessions.",
                checked = draft.showLiveGrid,
                onCheckedChange = { viewModel.updateSettingsDraft(showLiveGrid = it) },
                enabled = !uiState.isSavingSettings,
            )

            HorizontalDivider()

            Text(
                text = "Tile size",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Zoom ${draft.gridZoom} · ~${tileWidthMeters.roundToInt()} m per side",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Higher zoom means smaller tiles. Changing tile size recalculates stored tiles on save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = draft.gridZoom.toFloat(),
                onValueChange = { value ->
                    viewModel.updateSettingsDraft(gridZoom = value.roundToInt())
                },
                valueRange = TileMath.MIN_GRID_ZOOM.toFloat()..TileMath.MAX_GRID_ZOOM.toFloat(),
                steps = TileMath.MAX_GRID_ZOOM - TileMath.MIN_GRID_ZOOM - 1,
                enabled = !uiState.isSavingSettings,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Larger tiles (z${TileMath.MIN_GRID_ZOOM})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Smaller tiles (z${TileMath.MAX_GRID_ZOOM})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.isSavingSettings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = if (uiState.isImporting) "Recalculating tiles…" else "Saving…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = viewModel::saveSettings,
                enabled = !uiState.isSavingSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
