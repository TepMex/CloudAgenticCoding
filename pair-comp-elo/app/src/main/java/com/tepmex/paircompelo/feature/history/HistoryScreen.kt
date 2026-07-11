package com.tepmex.paircompelo.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.ItemComparison
import com.tepmex.paircompelo.domain.model.ListComparison
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.ConfirmDialog
import com.tepmex.paircompelo.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val listId: UUID? = savedStateHandle.get<String>("listId")?.let(UUID::fromString)

    val itemComparisons: StateFlow<List<ItemComparison>> =
        (if (listId != null) {
            repository.observeItemComparisons(listId, 200)
        } else {
            repository.observeAllItemComparisons(200)
        }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listComparisons: StateFlow<List<ListComparison>> =
        repository.observeListComparisons(200)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteItemComparison(id: UUID) = viewModelScope.launch {
        repository.deleteItemComparison(id)
    }

    fun deleteListComparison(id: UUID) = viewModelScope.launch {
        repository.deleteListComparison(id)
    }

    fun undoItem() = viewModelScope.launch {
        listId?.let { repository.undoLatestItemComparison(it) }
    }

    fun undoList() = viewModelScope.launch {
        repository.undoLatestListComparison()
    }
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    listScoped: Boolean,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val itemHistory by viewModel.itemComparisons.collectAsStateWithLifecycle()
    val listHistory by viewModel.listComparisons.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var deleteItemId by remember { mutableStateOf<UUID?>(null) }
    var deleteListId by remember { mutableStateOf<UUID?>(null) }

    AppScaffold(title = "Comparison history", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!listScoped) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Items") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Lists") })
                }
            }
            val showItems = listScoped || tab == 0
            if (showItems) {
                TextButton(
                    onClick = viewModel::undoItem,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("Undo latest item comparison")
                }
                if (itemHistory.isEmpty()) {
                    EmptyState(
                        title = "No item comparisons",
                        message = "Choices you make will appear here.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        items(itemHistory, key = { it.id }) { c ->
                            HistoryRow(
                                timestamp = c.comparedAt.toString(),
                                detail = "${c.outcome} · left ${c.leftItemId.toString().take(8)}… vs " +
                                    "right ${c.rightItemId.toString().take(8)}… · " +
                                    "ΔL ${"%+.1f".format(c.leftRatingAfter - c.leftRatingBefore)} / " +
                                    "ΔR ${"%+.1f".format(c.rightRatingAfter - c.rightRatingBefore)}",
                                onDelete = { deleteItemId = c.id },
                            )
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = viewModel::undoList,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("Undo latest list comparison")
                }
                if (listHistory.isEmpty()) {
                    EmptyState(
                        title = "No list comparisons",
                        message = "Compare lists to see history here.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        items(listHistory, key = { it.id }) { c ->
                            HistoryRow(
                                timestamp = c.comparedAt.toString(),
                                detail = "${c.outcome} · " +
                                    "ΔL ${"%+.1f".format(c.leftRatingAfter - c.leftRatingBefore)} / " +
                                    "ΔR ${"%+.1f".format(c.rightRatingAfter - c.rightRatingBefore)}",
                                onDelete = { deleteListId = c.id },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteItemId?.let { id ->
        ConfirmDialog(
            title = "Delete comparison?",
            message = "Rankings will be recalculated from remaining history.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteItemComparison(id)
                deleteItemId = null
            },
            onDismiss = { deleteItemId = null },
            destructive = true,
        )
    }
    deleteListId?.let { id ->
        ConfirmDialog(
            title = "Delete comparison?",
            message = "List rankings will be recalculated from remaining history.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteListComparison(id)
                deleteListId = null
            },
            onDismiss = { deleteListId = null },
            destructive = true,
        )
    }
}

@Composable
private fun HistoryRow(
    timestamp: String,
    detail: String,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(timestamp, style = MaterialTheme.typography.labelMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDelete) { Text("Delete & recalculate") }
    }
}
