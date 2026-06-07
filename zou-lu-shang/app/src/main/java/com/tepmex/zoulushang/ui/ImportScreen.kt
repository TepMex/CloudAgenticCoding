package com.tepmex.zoulushang.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tepmex.zoulushang.importing.ImportProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val dbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        viewModel.importTakeoutDb(uri)
    }

    val jsonPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importTakeoutJson(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Import location history") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setShowImport(false) }) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Import Google Takeout location data for ${uiState.selectedCity?.displayName ?: "the selected city"}. " +
                    "Pick takeout.db once — switching to another city will import from the same file automatically.",
                style = MaterialTheme.typography.bodyMedium,
            )

            uiState.takeoutDbUri?.let { uri ->
                Text(
                    text = "Saved takeout.db:\n$uri",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { viewModel.clearTakeoutDb() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isImporting,
                ) {
                    Text("Clear saved takeout.db")
                }
            }

            if (uiState.selectedCity == null) {
                Text(
                    text = "Choose a city first — import maps points to tiles inside that city's boundary.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { viewModel.setShowCityPicker(true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose city")
                }
            }

            if (uiState.isImporting) {
                ImportProgressBar(uiState)
            } else if (uiState.selectedCity != null) {
                Button(
                    onClick = { dbPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isImporting,
                ) {
                    Text(if (uiState.takeoutDbUri == null) "Choose takeout.db" else "Choose different takeout.db")
                }
                OutlinedButton(
                    onClick = { jsonPicker.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose Records.json")
                }
            }

            uiState.importProgress?.takeIf { it.stage == ImportProgress.Stage.DONE && !uiState.isImporting }?.let {
                Text(
                    text = "Imported ${it.tilesFound} unique visited tiles.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
