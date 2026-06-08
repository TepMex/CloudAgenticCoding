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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
                        takeoutLookup = uiState.visitedLookup,
                        liveLookup = uiState.liveLookup,
                        fitBounds = uiState.mapBounds,
                        enableMyLocation = locationPermissions.allGranted,
                        recenterMyLocationToken = uiState.recenterMyLocationToken,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!locationPermissions.allGranted) {
                        LocationPermissionBanner(
                            onRequest = locationPermissions.request,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            if (locationPermissions.allGranted) {
                                viewModel.recenterOnMyLocation()
                            } else {
                                locationPermissions.request()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My location")
                    }
                    FillMapControls(
                        isRunning = uiState.isFillMapRunning,
                        minutesRemaining = uiState.fillMapMinutesRemaining,
                        samplesTaken = uiState.fillMapSamplesTaken,
                        hasPermissions = locationPermissions.allGranted,
                        onRequestPermissions = locationPermissions.request,
                        onStart = viewModel::startFillMap,
                        onStop = viewModel::stopFillMap,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                    )
                    TileStats(
                        takeoutTileCount = uiState.visitedTileCount,
                        liveTileCount = uiState.liveTileCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp),
                    )
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
private fun LocationPermissionBanner(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Location access is needed to show your position and fill the map live.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FilledTonalButton(onClick = onRequest) {
            Text("Grant location access")
        }
    }
}

@Composable
private fun FillMapControls(
    isRunning: Boolean,
    minutesRemaining: Int,
    samplesTaken: Int,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isRunning) {
            Text(
                text = "Recording · $samplesTaken samples · ${minutesRemaining}m left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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

@Composable
private fun TileStats(
    takeoutTileCount: Int,
    liveTileCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$takeoutTileCount takeout · $liveTileCount live tiles (zoom ${com.tepmex.zoulushang.geo.TileMath.GRID_ZOOM})",
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
