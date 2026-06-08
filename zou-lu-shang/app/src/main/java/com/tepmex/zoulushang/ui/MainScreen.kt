package com.tepmex.zoulushang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        when {
            uiState.showCityPicker -> CitySearchScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(scaffoldPadding),
            )
            uiState.showImport -> ImportScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(scaffoldPadding),
            )
            else -> MainMapScaffold(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier.padding(scaffoldPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainMapScaffold(
    viewModel: AppViewModel,
    uiState: AppUiState,
    modifier: Modifier = Modifier,
) {
    val locationPermissions = rememberLocationPermissionsState()
    val hasCity = uiState.selectedCity != null

    Scaffold(
        modifier = modifier,
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
                    if (hasCity) {
                        IconButton(
                            onClick = {
                                if (locationPermissions.allGranted) {
                                    viewModel.recenterOnMyLocation()
                                } else {
                                    locationPermissions.request()
                                }
                            },
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "My location")
                        }
                    }
                    IconButton(onClick = { viewModel.setShowCityPicker(true) }) {
                        Icon(Icons.Default.AddLocation, contentDescription = "Choose city")
                    }
                    IconButton(onClick = { viewModel.setShowImport(true) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import data")
                    }
                },
            )
        },
        bottomBar = {
            if (hasCity) {
                MapControlBottomBar(
                    takeoutTileCount = uiState.visitedTileCount,
                    liveTileCount = uiState.liveTileCount,
                    isRunning = uiState.isFillMapRunning,
                    minutesRemaining = uiState.fillMapMinutesRemaining,
                    samplesTaken = uiState.fillMapSamplesTaken,
                    hasPermissions = locationPermissions.allGranted,
                    onRequestPermissions = locationPermissions.request,
                    onStart = viewModel::startFillMap,
                    onStop = viewModel::stopFillMap,
                )
            }
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
            if (hasCity && !locationPermissions.allGranted) {
                LocationPermissionBanner(
                    onRequest = locationPermissions.request,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (!hasCity) {
                EmptyState(onChooseCity = { viewModel.setShowCityPicker(true) })
            } else {
                VisitedTilesMap(
                    takeoutLookup = uiState.visitedLookup,
                    liveLookup = uiState.liveLookup,
                    fitBounds = uiState.mapBounds,
                    enableMyLocation = locationPermissions.allGranted,
                    recenterMyLocationToken = uiState.recenterMyLocationToken,
                    modifier = Modifier.fillMaxSize(),
                )
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
private fun LocationPermissionBanner(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Grant location access to show your position and fill the map live.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onRequest,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun MapControlBottomBar(
    takeoutTileCount: Int,
    liveTileCount: Int,
    isRunning: Boolean,
    minutesRemaining: Int,
    samplesTaken: Int,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$takeoutTileCount takeout · $liveTileCount live tiles (zoom ${com.tepmex.zoulushang.geo.TileMath.GRID_ZOOM})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isRunning) {
                        "Recording · $samplesTaken samples · ${minutesRemaining}m left"
                    } else {
                        "Fill the map — record your path for up to 30 minutes"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (isRunning) {
                    FilledTonalButton(onClick = onStop) {
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = {
                            if (hasPermissions) onStart() else onRequestPermissions()
                        },
                    ) {
                        Text("Start")
                    }
                }
            }
        }
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
