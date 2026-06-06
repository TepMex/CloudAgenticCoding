package com.tepmex.zoulushang.ui

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
import androidx.compose.ui.unit.dp
import com.tepmex.zoulushang.importing.ImportProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    val dbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importTakeoutDb(it) }
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
                    "Points are filtered (accuracy < 50 m), clustered to remove stationary duplicates, " +
                    "mapped to zoom-15 tiles inside the city boundary, and stored locally.",
                style = MaterialTheme.typography.bodyMedium,
            )

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
                ) {
                    Text("Choose takeout.db")
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
