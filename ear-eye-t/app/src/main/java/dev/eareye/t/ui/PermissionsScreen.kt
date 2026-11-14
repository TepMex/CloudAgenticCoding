package dev.eareye.t.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.eareye.t.R
import dev.eareye.t.domain.AudioCategorySelection

@Composable
fun PermissionsScreen(
    state: PermissionsUiState,
    onUsage: () -> Unit,
    onListener: () -> Unit,
    onAppDetails: () -> Unit,
    onBattery: () -> Unit,
    onCategories: (AudioCategorySelection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.access_title), style = MaterialTheme.typography.headlineMedium)
        Section(
            title = stringResource(R.string.usage_title),
            body = stringResource(R.string.usage_body),
            status = stringResource(if (state.usageGranted) R.string.granted else R.string.missing),
        )
        Button(onClick = onUsage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_usage))
        }
        Section(
            title = stringResource(R.string.listener_title),
            body = stringResource(R.string.listener_body),
            status = buildString {
                append(stringResource(if (state.listenerEnabled) R.string.granted else R.string.missing))
                append(". ")
                append(if (state.listenerConnected) stringResource(R.string.monitor_on) else stringResource(R.string.monitor_off))
            },
        )
        Button(onClick = onListener, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_listener))
        }
        Section(stringResource(R.string.restricted_title), stringResource(R.string.restricted_body), null)
        Button(onClick = onAppDetails, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_app))
        }
        Section(stringResource(R.string.magicos_title), stringResource(R.string.magicos_body), null)
        Button(onClick = onBattery, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_battery))
        }
        Text(stringResource(R.string.accuracy_body))
        Text(stringResource(R.string.audible_body))
        Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.categories_body))
        CategorySwitch(stringResource(R.string.cat_media), state.categories.media) {
            onCategories(state.categories.copy(media = it))
        }
        CategorySwitch(stringResource(R.string.cat_game), state.categories.game) {
            onCategories(state.categories.copy(game = it))
        }
        CategorySwitch(stringResource(R.string.cat_assistant), state.categories.assistant) {
            onCategories(state.categories.copy(assistant = it))
        }
        CategorySwitch(stringResource(R.string.cat_navigation), state.categories.navigation) {
            onCategories(state.categories.copy(navigation = it))
        }
        CategorySwitch(stringResource(R.string.cat_accessibility), state.categories.accessibility) {
            onCategories(state.categories.copy(accessibility = it))
        }
    }
}

@Composable
private fun Section(title: String, body: String, status: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (status != null) Text(status, style = MaterialTheme.typography.labelLarge)
        Text(body)
    }
}

@Composable
private fun CategorySwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
