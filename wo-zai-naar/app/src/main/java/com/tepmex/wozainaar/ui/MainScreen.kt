package com.tepmex.wozainaar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.wozainaar.R
import com.tepmex.wozainaar.data.LocationPoint
import com.tepmex.wozainaar.ui.maps.DailyMovementMap
import org.osmdroid.util.GeoPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.screenState.collectAsStateWithLifecycle()
    val points by viewModel.pointsForSelectedDay.collectAsStateWithLifecycle()
    val permissions = rememberLocationPermissionsState()

    LaunchedEffect(permissions.allGranted) {
        viewModel.setPermissionsReady(permissions.allGranted)
        if (permissions.allGranted) {
            viewModel.refreshSampleCount()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!permissions.allGranted) {
                PermissionPrompt(
                    onRequest = permissions.request,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            } else {
                DateNavigator(
                    date = uiState.selectedDate,
                    onPrevious = { viewModel.setSelectedDate(uiState.selectedDate.minusDays(1)) },
                    onNext = { viewModel.setSelectedDate(uiState.selectedDate.plusDays(1)) },
                    sampleCount = points.size,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )

                Text(
                    text = stringResource(R.string.tracking_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                if (points.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_points_today),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    MovementMapSection(
                        points = points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.55f),
                    )
                    SampleList(
                        points = points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                            .padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_rationale_location),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.permission_rationale_background),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.permission_rationale_notifications),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant_permissions))
        }
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    sampleCount: Int,
    modifier: Modifier = Modifier,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = date.format(formatter), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.points_count, sampleCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onNext,
            enabled = date.isBefore(LocalDate.now()),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
    }
}

@Composable
private fun MovementMapSection(
    points: List<LocationPoint>,
    modifier: Modifier = Modifier,
) {
    val pathPoints = remember(points) {
        points.map { GeoPoint(it.latitude, it.longitude) }
    }
    var didFitBounds by remember(points) { mutableStateOf(false) }

    DailyMovementMap(
        pathPoints = pathPoints,
        fitBounds = !didFitBounds,
        onFitBoundsApplied = { didFitBounds = true },
        modifier = modifier.padding(8.dp),
    )
}

@Composable
private fun SampleList(
    points: List<LocationPoint>,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
    }
    val zone = remember { ZoneId.systemDefault() }

    LazyColumn(modifier = modifier) {
        items(points, key = { it.id }) { point ->
            val time = Instant.ofEpochMilli(point.recordedAt)
                .atZone(zone)
                .format(timeFormatter)
            val accuracy = point.accuracyMeters?.let { "±%.0f m".format(it) } ?: "—"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Text(text = time, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "%.5f, %.5f · %s".format(point.latitude, point.longitude, accuracy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
