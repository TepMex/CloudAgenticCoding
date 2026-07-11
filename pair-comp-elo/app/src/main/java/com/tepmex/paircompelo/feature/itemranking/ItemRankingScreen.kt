package com.tepmex.paircompelo.feature.itemranking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.EmptyState

@Composable
fun ItemRankingScreen(
    onBack: () -> Unit,
    viewModel: ItemRankingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppScaffold(title = "Item ranking", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ItemSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { viewModel.setSort(sort) },
                        label = { Text(sort.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            if (state.entries.isEmpty()) {
                EmptyState(
                    title = "No active items",
                    message = "Add items and compare them to build a ranking.",
                )
            } else {
                LazyColumn {
                    items(state.entries, key = { it.first.id }) { (entry, item) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text("#${entry.rank}  ${entry.name}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append("Rating ${"%.1f".format(entry.rating)}")
                                    append(" (${"%+.1f".format(entry.ratingDeltaFromInitial)} from start)")
                                    append(" · ${entry.comparisonCount} comps")
                                    append(" · ${item.winCount}–${item.lossCount}")
                                    if (!entry.isStable) append(" · Not enough comparisons yet")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.isStable) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
