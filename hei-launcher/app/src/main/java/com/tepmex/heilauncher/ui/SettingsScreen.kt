package com.tepmex.heilauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.heilauncher.R
import com.tepmex.heilauncher.domain.ClockFormat
import com.tepmex.heilauncher.domain.NameSize

private val Ink = Color.White
private val Muted = Color(0xFF8A8A8A)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestHomeRole: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.back),
            color = Ink,
            fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.settings_title), color = Ink, fontSize = 36.sp)
        Spacer(Modifier.height(28.dp))

        SectionLabel(stringResource(R.string.clock))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Choice(stringResource(R.string.clock_system), state.prefs.clockFormat == ClockFormat.SYSTEM) {
                viewModel.setClock(ClockFormat.SYSTEM)
            }
            Choice(stringResource(R.string.clock_12), state.prefs.clockFormat == ClockFormat.H12) {
                viewModel.setClock(ClockFormat.H12)
            }
            Choice(stringResource(R.string.clock_24), state.prefs.clockFormat == ClockFormat.H24) {
                viewModel.setClock(ClockFormat.H24)
            }
        }
        Spacer(Modifier.height(22.dp))

        ToggleRow(stringResource(R.string.show_year), state.prefs.showYearProgress, viewModel::setShowYear)
        ToggleRow(stringResource(R.string.show_day), state.prefs.showDayProgress, viewModel::setShowDay)
        ToggleRow(stringResource(R.string.show_battery), state.prefs.showBattery, viewModel::setShowBattery)
        ToggleRow(stringResource(R.string.show_weather), state.prefs.showWeather, viewModel::setShowWeather)

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.name_size))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Choice(stringResource(R.string.size_s), state.prefs.nameSize == NameSize.S) {
                viewModel.setNameSize(NameSize.S)
            }
            Choice(stringResource(R.string.size_m), state.prefs.nameSize == NameSize.M) {
                viewModel.setNameSize(NameSize.M)
            }
            Choice(stringResource(R.string.size_l), state.prefs.nameSize == NameSize.L) {
                viewModel.setNameSize(NameSize.L)
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(R.string.favourites))
        Text(
            text = stringResource(R.string.favourites_hint),
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (state.favourites.isEmpty()) {
            Text(
                text = stringResource(R.string.favourites_empty),
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        state.favourites.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    color = Ink,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Action(stringResource(R.string.move_up), enabled = index > 0) {
                    viewModel.move(row.component, -1)
                }
                Action(stringResource(R.string.move_down), enabled = index < state.favourites.lastIndex) {
                    viewModel.move(row.component, 1)
                }
                Action(stringResource(R.string.remove), enabled = true) {
                    viewModel.remove(row.component)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        LinkRow(
            title = stringResource(R.string.location_access),
            detail = stringResource(if (state.locationGranted) R.string.location_on else R.string.location_off),
            onClick = onRequestLocation,
        )
        LinkRow(
            title = stringResource(R.string.set_default),
            detail = if (state.homeRoleHeld) stringResource(R.string.default_on) else "",
            onClick = onRequestHomeRole,
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.version, state.versionName),
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Ink else DimChoice,
        fontSize = 18.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

private val DimChoice = Color(0xFF666666)

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Ink, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Ink,
                uncheckedThumbColor = Ink,
                uncheckedTrackColor = Color(0xFF2A2A2A),
                uncheckedBorderColor = Color(0xFF3A3A3A),
            ),
        )
    }
}

@Composable
private fun Action(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (enabled) Ink else Color(0xFF444444),
        fontSize = 14.sp,
        modifier = Modifier
            .padding(start = 10.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun LinkRow(title: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(text = title, color = Ink, fontSize = 18.sp)
        if (detail.isNotEmpty()) {
            Text(text = detail, color = Muted, fontSize = 14.sp, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
