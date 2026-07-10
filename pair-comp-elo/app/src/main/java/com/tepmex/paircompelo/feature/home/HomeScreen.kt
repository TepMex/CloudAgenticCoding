package com.tepmex.paircompelo.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.paircompelo.data.repository.ListSummary
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.EmptyState
import com.tepmex.paircompelo.ui.components.LoadingState

@Composable
fun HomeScreen(
    onOpenList: (String) -> Unit,
    onCreateList: () -> Unit,
    onCompareItems: (String) -> Unit,
    onListRanking: () -> Unit,
    onCompareLists: () -> Unit,
    onSettings: () -> Unit,
    onArchived: () -> Unit,
    onHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val summaries by viewModel.richSummaries.collectAsStateWithLifecycle()

    AppScaffold(
        title = "Pair Comp Elo",
        actions = {
            IconButton(onClick = onHistory) {
                Icon(Icons.Default.History, contentDescription = "Comparison history")
            }
            IconButton(onClick = onArchived) {
                Icon(Icons.Default.Archive, contentDescription = "Archived lists")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateList) {
                Icon(Icons.Default.Add, contentDescription = "Add list")
            }
        },
    ) { padding ->
        if (summaries.isEmpty()) {
            EmptyState(
                title = "No lists yet",
                message = "Create a list of anything you want to rank — books, films, restaurants, tasks.",
                modifier = Modifier.padding(padding),
                action = {
                    Button(onClick = onCreateList) { Text("Add a list") }
                },
            )
            return@AppScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = "Your preference lists",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Text(
                    text = "Ratings reflect your current preferences — not objective quality.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onListRanking, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Leaderboard, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("List rankings")
                    }
                    OutlinedButton(onClick = onCompareLists, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Compare lists")
                    }
                }
                HorizontalDivider()
            }
            items(summaries, key = { it.list.id }) { summary ->
                ListSummaryRow(
                    summary = summary,
                    onOpen = { onOpenList(summary.list.id.toString()) },
                    onCompare = { onCompareItems(summary.list.id.toString()) },
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ListSummaryRow(
    summary: ListSummary,
    onOpen: () -> Unit,
    onCompare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "List ${summary.list.name}" }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.list.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = buildString {
                    append("${summary.activeItemCount} items · ")
                    append("${summary.itemComparisonCount} comparisons · ")
                    append("rating ${"%.0f".format(summary.list.rating)}")
                    summary.topItemName?.let { append(" · top: $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.activeItemCount >= 2) {
            TextButton(onClick = onCompare) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start comparison")
                Text("Compare")
            }
        }
    }
}
