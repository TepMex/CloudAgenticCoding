package com.tepmex.zoulushang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.zoulushang.map.VisitedTilesMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    when {
        uiState.showCityPicker -> CitySearchScreen(viewModel = viewModel, modifier = modifier)
        uiState.showImport -> ImportScreen(viewModel = viewModel, modifier = modifier)
        else -> Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("zou-lu-shang")
                            uiState.selectedCity?.let { city ->
                                Text(
                                    text = city.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setShowCityPicker(true) }) {
                            Icon(Icons.Default.AddLocation, contentDescription = "Choose city")
                        }
                        IconButton(onClick = { viewModel.setShowImport(true) }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import data")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (uiState.isImporting) {
                    ImportProgressBar(uiState)
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.selectedCity == null) {
                        EmptyState(onChooseCity = { viewModel.setShowCityPicker(true) })
                    } else {
                        VisitedTilesMap(
                            visitedLookup = uiState.visitedLookup,
                            fitBounds = uiState.mapBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                        TileStats(
                            tileCount = uiState.visitedTileCount,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onChooseCity: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Map,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Choose a city to start exploring your location history grid.")
        Text(
            text = "Search uses Nominatim once; maps and tiles work offline after import.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TileStats(tileCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$tileCount visited tiles (zoom ${com.tepmex.zoulushang.geo.TileMath.GRID_ZOOM})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun ImportProgressBar(uiState: AppUiState) {
    val progress = uiState.importProgress
    val label = when (progress?.stage) {
        null -> "Starting import…"
        com.tepmex.zoulushang.importing.ImportProgress.Stage.READING -> "Reading location points…"
        com.tepmex.zoulushang.importing.ImportProgress.Stage.FILTERING -> "Filtering by accuracy…"
        com.tepmex.zoulushang.importing.ImportProgress.Stage.CLUSTERING -> "Clustering stationary points…"
        com.tepmex.zoulushang.importing.ImportProgress.Stage.MAPPING -> "Mapping to tiles… (${progress.tilesFound} so far)"
        com.tepmex.zoulushang.importing.ImportProgress.Stage.SAVING -> "Saving tiles…"
        com.tepmex.zoulushang.importing.ImportProgress.Stage.DONE -> "Done"
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { progress?.fraction?.coerceIn(0f, 1f) ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}
