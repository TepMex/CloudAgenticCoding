package dev.eareye.t.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eareye.t.EarEyeApp
import dev.eareye.t.R
import dev.eareye.t.collect.ScreenSyncScheduler
import dev.eareye.t.collect.openAppDetailsSettings
import dev.eareye.t.collect.openBatterySettings
import dev.eareye.t.collect.openNotificationListenerSettings
import dev.eareye.t.collect.openUsageAccessSettings
import dev.eareye.t.domain.DaySelection
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EarEyeTheme {
                EarEyeAppScreen()
            }
        }
    }
}

private enum class Destination { Home, Diagnostics, Access }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarEyeAppScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current.applicationContext as EarEyeApp)),
) {
    val context = LocalContext.current
    val home by viewModel.home.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(Destination.Home) }
    var showDatePicker by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsFail = stringResource(R.string.settings_fail)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ScreenSyncScheduler.enqueueSoon(context)
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val jsonExport = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { writeExport(context, uri, viewModel.buildJson(), snackbar) }
    }
    val csvExport = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { writeExport(context, uri, viewModel.buildCsv(), snackbar) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == Destination.Home,
                    onClick = { destination = Destination.Home },
                    icon = { Text("Г") },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.nav_today)) },
                )
                NavigationBarItem(
                    selected = destination == Destination.Diagnostics,
                    onClick = { destination = Destination.Diagnostics },
                    icon = { Text("Д") },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.nav_diagnostics)) },
                )
                NavigationBarItem(
                    selected = destination == Destination.Access,
                    onClick = { destination = Destination.Access },
                    icon = { Text("А") },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.nav_access)) },
                )
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            when (destination) {
                Destination.Home -> HomeScreen(
                    state = home,
                    onSelect = viewModel::select,
                    onPickDate = { showDatePicker = true },
                    onOpenAccess = { destination = Destination.Access },
                )
                Destination.Diagnostics -> DiagnosticsScreen(
                    state = diagnostics,
                    onExportJson = { jsonExport.launch("ear-eye-diagnostics.json") },
                    onExportCsv = { csvExport.launch("ear-eye-diagnostics.csv") },
                )
                Destination.Access -> PermissionsScreen(
                    state = permissions,
                    onUsage = {
                        if (!openUsageAccessSettings(context)) {
                            scope.launch { snackbar.showSnackbar(settingsFail) }
                        }
                    },
                    onListener = {
                        if (!openNotificationListenerSettings(context)) {
                            scope.launch { snackbar.showSnackbar(settingsFail) }
                        }
                    },
                    onAppDetails = {
                        if (!openAppDetailsSettings(context)) {
                            scope.launch { snackbar.showSnackbar(settingsFail) }
                        }
                    },
                    onBattery = {
                        if (!openBatterySettings(context)) {
                            scope.launch { snackbar.showSnackbar(settingsFail) }
                        }
                    },
                    onCategories = viewModel::setCategories,
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.select(DaySelection.Specific(date))
                    }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private suspend fun writeExport(
    context: android.content.Context,
    uri: android.net.Uri,
    text: String,
    snackbar: SnackbarHostState,
) {
    val wrote = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("no stream")
    }
    snackbar.showSnackbar(
        context.getString(if (wrote.isSuccess) R.string.export_ok else R.string.export_fail),
    )
}
