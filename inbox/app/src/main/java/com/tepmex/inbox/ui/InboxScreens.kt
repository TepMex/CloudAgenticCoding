package com.tepmex.inbox.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.inbox.R
import com.tepmex.inbox.domain.ArchiveStampKind
import com.tepmex.inbox.domain.Capture
import com.tepmex.inbox.domain.CaptureActions
import java.util.Calendar

@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.inbox.collectAsStateWithLifecycle()
    ItemList(
        title = stringResource(R.string.tab_inbox),
        items = items,
        empty = stringResource(R.string.inbox_empty),
        modifier = modifier,
    ) { item ->
        CaptureCard(
            item = item,
            onClick = { onOpen(item.id) },
            onCopy = {
                viewModel.copied()
            },
        )
    }
}

@Composable
fun RemindersScreen(
    viewModel: InboxViewModel,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ItemList(
        title = stringResource(R.string.tab_reminders),
        items = items,
        empty = stringResource(R.string.reminders_empty),
        modifier = modifier,
    ) { item ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = formatStamp(context, item.reminderAtMillis ?: item.createdAtMillis),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { viewModel.cancelReminder(item.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.cancel_reminder), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ArchiveScreen(
    viewModel: InboxViewModel,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.archive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ItemList(
        title = stringResource(R.string.tab_archive),
        items = items,
        empty = stringResource(R.string.archive_empty),
        modifier = modifier,
    ) { item ->
        val stamp = CaptureActions.archiveStamp(item)
        val second = when (stamp?.kind) {
            ArchiveStampKind.Processed -> stringResource(
                R.string.archive_processed,
                formatStamp(context, stamp.atMillis),
            )
            ArchiveStampKind.Notified -> stringResource(
                R.string.archive_notified,
                formatStamp(context, stamp.atMillis),
            )
            null -> null
        }
        val copy = rememberCopy()
        Column(
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    copy(item.text)
                    viewModel.copied()
                },
                onLongClickLabel = stringResource(R.string.copy),
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.archive_created, formatStamp(context, item.createdAtMillis)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (second != null) {
                Text(
                    text = second,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ProcessScreen(
    viewModel: InboxViewModel,
    id: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val inboxReady by viewModel.inboxReady.collectAsStateWithLifecycle()
    val item = inbox.find { it.id == id }
    val context = LocalContext.current
    if (!inboxReady) {
        Text(
            text = stringResource(R.string.process_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = modifier.padding(16.dp).semantics { heading() },
        )
        return
    }
    if (item == null) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(R.string.process_missing),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onFinished, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.back))
            }
        }
        return
    }
    val pickTime = rememberReminderPicker { atMillis ->
        if (viewModel.schedule(item.id, atMillis)) onFinished()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pickTime()
    }
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.process_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { heading() },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
        }
        Button(
            onClick = {
                viewModel.markProcessed(item.id)
                onFinished()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.mark_processed), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) pickTime() else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.remind), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: InboxViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic by viewModel.haptic.collectAsStateWithLifecycle()
    val keyboard by viewModel.openKeyboard.collectAsStateWithLifecycle()
    val lockScreen by viewModel.showOnLockScreen.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var notificationsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var exact by remember {
        mutableStateOf(
            context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true,
        )
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                exact = context.getSystemService(android.app.AlarmManager::class.java)
                    ?.canScheduleExactAlarms() == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val version = remember {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        ).versionName.orEmpty()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_archive_title)) },
            text = { Text(stringResource(R.string.clear_archive_body, counts.archive)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearArchive()
                    confirmClear = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.dismiss)) }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.settings_speed), style = MaterialTheme.typography.titleLarge)
        ToggleRow(stringResource(R.string.settings_haptic), haptic) { viewModel.setHaptic(it) }
        ToggleRow(stringResource(R.string.settings_keyboard), keyboard) { viewModel.setOpenKeyboard(it) }
        ToggleRow(stringResource(R.string.settings_lock_screen), lockScreen) { viewModel.setShowOnLockScreen(it) }

        Text(
            text = stringResource(R.string.settings_notifications),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(
                if (notificationsGranted) R.string.notifications_on else R.string.notifications_off,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!notificationsGranted) {
            Button(
                onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.allow_notifications), style = MaterialTheme.typography.titleMedium) }
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.open_notification_settings), style = MaterialTheme.typography.titleMedium) }
        Text(
            text = stringResource(if (exact) R.string.exact_on else R.string.exact_off),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!exact) {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.allow_exact), style = MaterialTheme.typography.titleMedium) }
        }

        Text(
            text = stringResource(R.string.settings_data),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(stringResource(R.string.count_inbox, counts.inbox), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.count_reminders, counts.reminders), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.count_archive, counts.archive), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.cancel_hint), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { confirmClear = true },
            enabled = counts.archive > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.clear_archive), style = MaterialTheme.typography.titleMedium) }
        Text(
            text = stringResource(R.string.version_line, version),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ItemList(
    title: String,
    items: List<Capture>,
    empty: String,
    modifier: Modifier = Modifier,
    card: @Composable (Capture) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { heading() },
        )
        if (items.isEmpty()) {
            Text(
                text = empty,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) { card(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(
    item: Capture,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val copy = rememberCopy()
    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                copy(item.text)
                onCopy()
            },
            onClickLabel = stringResource(R.string.process_title),
            onLongClickLabel = stringResource(R.string.copy),
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.archive_created, formatStamp(context, item.createdAtMillis)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("inbox", text)))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun rememberReminderPicker(onPicked: (Long) -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        val initial = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val chosen = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPicked(chosen.timeInMillis)
                    },
                    initial.get(Calendar.HOUR_OF_DAY),
                    initial.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context),
                ).show()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1_000L
        }.show()
    }
}
