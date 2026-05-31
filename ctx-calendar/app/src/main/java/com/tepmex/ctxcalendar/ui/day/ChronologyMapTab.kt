package com.tepmex.ctxcalendar.ui.day

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.tepmex.ctxcalendar.R
import com.tepmex.ctxcalendar.data.takeout.ChronologyActivity
import com.tepmex.ctxcalendar.data.takeout.ChronologyVisit
import com.tepmex.ctxcalendar.data.takeout.GeoTrackPoint
import com.tepmex.ctxcalendar.ui.maps.MapsApiKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChronologyMapTab(
    isLoading: Boolean,
    hasDatabase: Boolean,
    track: List<GeoTrackPoint>,
    visits: List<ChronologyVisit>,
    activities: List<ChronologyActivity>,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapsAvailable = remember { MapsApiKey.isConfigured(context) }

    when {
        !hasDatabase -> {
            EmptyTakeoutMessage(
                message = stringResource(R.string.takeout_db_not_configured),
                modifier = modifier,
            )
        }
        isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            EmptyTakeoutMessage(message = errorMessage, modifier = modifier)
        }
        track.isEmpty() && visits.isEmpty() && activities.isEmpty() -> {
            EmptyTakeoutMessage(
                message = stringResource(R.string.no_chronology_this_day),
                modifier = modifier,
            )
        }
        !mapsAvailable -> {
            ChronologyFallbackList(
                track = track,
                visits = visits,
                activities = activities,
                onOpenMaps = { lat, lng ->
                    val uri = Uri.parse("geo:$lat,$lng")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                modifier = modifier,
            )
        }
        else -> {
            ChronologyMapContent(
                track = track,
                visits = visits,
                activities = activities,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ChronologyMapContent(
    track: List<GeoTrackPoint>,
    visits: List<ChronologyVisit>,
    activities: List<ChronologyActivity>,
    modifier: Modifier = Modifier,
) {
    val pathPoints = remember(track) {
        track.map { LatLng(it.lat, it.lng) }
    }
    val cameraPositionState = rememberCameraPositionState()
    var didFitBounds by remember { mutableStateOf(false) }

    LaunchedEffect(pathPoints, visits, activities) {
        if (didFitBounds) return@LaunchedEffect
        val all = buildList {
            addAll(pathPoints)
            visits.forEach { v ->
                if (v.lat != null && v.lng != null) add(LatLng(v.lat, v.lng))
            }
            activities.forEach { a ->
                if (a.lat != null && a.lng != null) add(LatLng(a.lat, a.lng))
            }
        }
        if (all.isEmpty()) return@LaunchedEffect
        if (all.size == 1) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(all.first(), 14f)
        } else {
            val bounds = LatLngBounds.builder()
            all.forEach { bounds.include(it) }
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(
                    bounds.build(),
                    64,
                ),
            )
        }
        didFitBounds = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
        ) {
            if (pathPoints.size >= 2) {
                Polyline(points = pathPoints)
            }
            visits.forEach { visit ->
                if (visit.lat != null && visit.lng != null) {
                    Marker(
                        state = MarkerState(LatLng(visit.lat, visit.lng)),
                        title = visit.semanticType ?: "Visit",
                        snippet = visit.placeId,
                    )
                }
            }
        }

        if (visits.isNotEmpty() || activities.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                items(visits, key = { "v-${it.ts}" }) { visit ->
                    TimelineRow(
                        time = formatTime(visit.ts),
                        title = visit.semanticType ?: stringResource(R.string.visit),
                        subtitle = visit.placeId,
                    )
                }
                items(activities, key = { "a-${it.ts}" }) { activity ->
                    val distance = activity.distanceMeters?.let { "%.0f m".format(it) }
                    TimelineRow(
                        time = formatTime(activity.ts),
                        title = activity.activityType ?: stringResource(R.string.activity),
                        subtitle = distance,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChronologyFallbackList(
    track: List<GeoTrackPoint>,
    visits: List<ChronologyVisit>,
    activities: List<ChronologyActivity>,
    onOpenMaps: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.maps_api_key_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(track.take(50), key = { "t-${it.ts}" }) { point ->
                TimelineRow(
                    time = formatTime(point.ts),
                    title = point.kind,
                    subtitle = "%.5f, %.5f".format(point.lat, point.lng),
                    onClick = { onOpenMaps(point.lat, point.lng) },
                )
            }
            items(visits, key = { "v-${it.ts}" }) { visit ->
                TimelineRow(
                    time = formatTime(visit.ts),
                    title = visit.semanticType ?: stringResource(R.string.visit),
                    subtitle = visit.placeId,
                )
            }
            items(activities, key = { "a-${it.ts}" }) { activity ->
                TimelineRow(
                    time = formatTime(activity.ts),
                    title = activity.activityType ?: stringResource(R.string.activity),
                    subtitle = activity.distanceMeters?.let { "%.0f m".format(it) },
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    time: String,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
    Column(modifier = rowModifier) {
        Text(text = "$time · $title", style = MaterialTheme.typography.bodyMedium)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyTakeoutMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun formatTime(epochMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
