package com.tepmex.ctxcalendar.ui.day

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tepmex.ctxcalendar.R
import com.tepmex.ctxcalendar.data.takeout.ChronologyActivity
import com.tepmex.ctxcalendar.data.takeout.ChronologyVisit
import com.tepmex.ctxcalendar.data.takeout.GeoTrackPoint
import com.tepmex.ctxcalendar.ui.maps.OsmMapMarker
import com.tepmex.ctxcalendar.ui.maps.OsmMapView
import org.osmdroid.util.GeoPoint
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
        track.map { GeoPoint(it.lat, it.lng) }
    }
    val visitMarkers = remember(visits) {
        visits.mapNotNull { visit ->
            if (visit.lat == null || visit.lng == null) return@mapNotNull null
            OsmMapMarker(
                position = GeoPoint(visit.lat, visit.lng),
                title = visit.semanticType ?: "Visit",
                snippet = visit.placeId,
            )
        }
    }
    var didFitBounds by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        OsmMapView(
            pathPoints = pathPoints,
            visitMarkers = visitMarkers,
            fitBounds = !didFitBounds,
            onFitBoundsApplied = { didFitBounds = true },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

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
private fun TimelineRow(
    time: String,
    title: String,
    subtitle: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
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
