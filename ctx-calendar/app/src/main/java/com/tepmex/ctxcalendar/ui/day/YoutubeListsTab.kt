package com.tepmex.ctxcalendar.ui.day

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tepmex.ctxcalendar.R
import com.tepmex.ctxcalendar.data.takeout.YoutubeSearchEvent
import com.tepmex.ctxcalendar.data.takeout.YoutubeWatchEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun YoutubeSearchTab(
    isLoading: Boolean,
    hasDatabase: Boolean,
    searches: List<YoutubeSearchEvent>,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    YoutubeListScaffold(
        isLoading = isLoading,
        hasDatabase = hasDatabase,
        isEmpty = searches.isEmpty(),
        emptyMessage = stringResource(R.string.no_youtube_searches_this_day),
        errorMessage = errorMessage,
        modifier = modifier,
    ) {
        items(searches, key = { it.ts }) { event ->
            YoutubeEventRow(
                time = formatTime(event.ts),
                title = event.query ?: "—",
                subtitle = null,
                url = event.url,
            )
        }
    }
}

@Composable
fun YoutubeWatchTab(
    isLoading: Boolean,
    hasDatabase: Boolean,
    watches: List<YoutubeWatchEvent>,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    YoutubeListScaffold(
        isLoading = isLoading,
        hasDatabase = hasDatabase,
        isEmpty = watches.isEmpty(),
        emptyMessage = stringResource(R.string.no_youtube_watches_this_day),
        errorMessage = errorMessage,
        modifier = modifier,
    ) {
        items(watches, key = { it.ts }) { event ->
            YoutubeEventRow(
                time = formatTime(event.ts),
                title = event.title ?: "—",
                subtitle = event.channel,
                url = event.url,
            )
        }
    }
}

@Composable
private fun YoutubeListScaffold(
    isLoading: Boolean,
    hasDatabase: Boolean,
    isEmpty: Boolean,
    emptyMessage: String,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    items: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    when {
        !hasDatabase -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.takeout_db_not_configured),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        isEmpty -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = items,
            )
        }
    }
}

@Composable
private fun YoutubeEventRow(
    time: String,
    title: String,
    subtitle: String?,
    url: String?,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (url != null) {
                    Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
