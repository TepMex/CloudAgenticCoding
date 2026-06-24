package com.tepmex.zoulushang2.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tepmex.zoulushang2.map.PaintedMap

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
        if (uiState.showSettings) {
            SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(scaffoldPadding),
            )
        } else {
            MainMapScaffold(
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
                title = { Text("zou-lu-shang-2") },
                actions = {
                    IconButton(onClick = { viewModel.setShowSettings(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PaintedMap(
                paintLookup = uiState.paintLookup,
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

            PaintControls(
                isPainting = uiState.isPainting,
                strokesApplied = uiState.paintStrokesApplied,
                paintedCellCount = uiState.paintedCellCount,
                hasPermissions = locationPermissions.allGranted,
                onRequestPermissions = locationPermissions.request,
                onStart = viewModel::startPainting,
                onStop = viewModel::stopPainting,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }
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
            text = "Location access is needed to show your position and paint the map.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FilledTonalButton(onClick = onRequest) {
            Text("Grant location access")
        }
    }
}

@Composable
private fun PaintControls(
    isPainting: Boolean,
    strokesApplied: Int,
    paintedCellCount: Int,
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
        Text(
            text = if (isPainting) {
                "Painting · $strokesApplied strokes · $paintedCellCount cells"
            } else {
                "$paintedCellCount painted cells"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isPainting) {
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
