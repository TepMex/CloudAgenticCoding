package com.tepmex.paircompelo.feature.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.ConfirmDialog
import com.tepmex.paircompelo.ui.components.EmptyState
import com.tepmex.paircompelo.ui.components.LoadingState
import com.tepmex.paircompelo.ui.components.SectionLabel
import java.util.UUID

@Composable
fun ListDetailScreen(
    onBack: () -> Unit,
    onEditList: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onEditItem: (String, String) -> Unit,
    onCompare: (String) -> Unit,
    onRanking: (String) -> Unit,
    onHistory: (String) -> Unit,
    viewModel: ListDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val list = state.list
    var deleteId by remember { mutableStateOf<UUID?>(null) }

    AppScaffold(
        title = list?.name ?: "List",
        onBack = onBack,
        actions = {
            if (list != null) {
                IconButton(onClick = { onEditList(list.id.toString()) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit list")
                }
            }
        },
        floatingActionButton = {
            if (list != null) {
                FloatingActionButton(onClick = { onAddItem(list.id.toString()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add item")
                }
            }
        },
    ) { padding ->
        when {
            state.loading || list == null -> LoadingState()
            else -> {
                val active = state.items.filter { !it.isArchived }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        list.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${active.size} active items · ${state.comparisonCount} comparisons",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        state.topItem?.let {
                            Text("Current top: ${it.name}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onCompare(list.id.toString()) },
                                enabled = active.size >= 2,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Text(" Compare")
                            }
                            OutlinedButton(
                                onClick = { onRanking(list.id.toString()) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Leaderboard, null)
                                Text(" Ranking")
                            }
                        }
                        TextButton(onClick = { onHistory(list.id.toString()) }) {
                            Text("Comparison history")
                        }
                        SectionLabel("Items")
                    }
                    if (state.items.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No items",
                                message = "Add at least two items to start comparing.",
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { item ->
                        ItemRow(
                            item = item,
                            onEdit = { onEditItem(list.id.toString(), item.id.toString()) },
                            onArchive = { viewModel.archiveItem(item.id) },
                            onRestore = { viewModel.restoreItem(item.id) },
                            onDelete = { deleteId = item.id },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        ConfirmDialog(
            title = "Delete item?",
            message = "Comparisons involving this item will be removed and rankings recalculated.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteItem(id)
                deleteId = null
            },
            onDismiss = { deleteId = null },
            destructive = true,
        )
    }
}

@Composable
private fun ItemRow(
    item: PreferenceItem,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name + if (item.isArchived) " (archived)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Rating ${"%.0f".format(item.rating)} · ${item.winCount}–${item.lossCount} · ${item.comparisonCount} comps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { menu = true }) { Text("⋯") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
            if (item.isArchived) {
                DropdownMenuItem(text = { Text("Restore") }, onClick = { menu = false; onRestore() })
            } else {
                DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onArchive() })
            }
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}
