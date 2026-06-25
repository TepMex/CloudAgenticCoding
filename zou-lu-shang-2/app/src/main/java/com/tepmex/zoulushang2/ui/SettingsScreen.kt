package com.tepmex.zoulushang2.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tepmex.zoulushang2.paint.PaintSettings
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val pending = pendingExportText ?: return@rememberLauncherForActivityResult
        pendingExportText = null
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(pending.toByteArray(Charsets.UTF_8))
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: ""
        viewModel.importFromText(text)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AppEvent.ExportReady -> {
                    pendingExportText = event.text
                    createDocumentLauncher.launch("zou-lu-shang-2-drawing.json")
                }
                AppEvent.ImportRequest -> {
                    openDocumentLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"),
                    )
                }
            }
        }
    }

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Export your painted map as compact JSON, or import a previous drawing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Maximum Speed",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "GPS points faster than this are ignored as unrealistic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = uiState.maxSpeedKmh,
                    onValueChange = viewModel::setMaxSpeedKmh,
                    valueRange = PaintSettings.MIN_MAX_SPEED_KMH..PaintSettings.MAX_MAX_SPEED_KMH,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${uiState.maxSpeedKmh.toInt()} km/h",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Button(
                onClick = viewModel::onExportClicked,
                enabled = !uiState.isBusy && uiState.strokeCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export drawing")
            }
            OutlinedButton(
                onClick = viewModel::onImportClicked,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import drawing")
            }
            OutlinedButton(
                onClick = viewModel::clearDrawing,
                enabled = !uiState.isBusy && uiState.strokeCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear drawing")
            }
            if (uiState.isBusy) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            uiState.statusMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
