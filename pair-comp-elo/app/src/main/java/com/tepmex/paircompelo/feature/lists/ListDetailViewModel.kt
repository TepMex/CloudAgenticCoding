package com.tepmex.paircompelo.feature.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.ItemComparison
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.PreferenceList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ListDetailUiState(
    val list: PreferenceList? = null,
    val items: List<PreferenceItem> = emptyList(),
    val comparisonCount: Int = 0,
    val topItem: PreferenceItem? = null,
    val recentComparisons: List<ItemComparison> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val listId = UUID.fromString(checkNotNull(savedStateHandle["listId"]))

    val uiState: StateFlow<ListDetailUiState> = combine(
        repository.observeList(listId),
        repository.observeItems(listId),
        repository.observeItemComparisonCount(listId),
        repository.observeTopItem(listId),
        repository.observeItemComparisons(listId, limit = 20),
    ) { list, items, count, top, history ->
        ListDetailUiState(
            list = list,
            items = items,
            comparisonCount = count,
            topItem = top,
            recentComparisons = history,
            loading = list == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    fun archiveItem(id: UUID) = viewModelScope.launch { repository.archiveItem(id) }
    fun restoreItem(id: UUID) = viewModelScope.launch { repository.restoreItem(id) }
    fun deleteItem(id: UUID) = viewModelScope.launch { repository.deleteItem(id) }
    fun reorder(ordered: List<UUID>) = viewModelScope.launch { repository.reorderItems(listId, ordered) }
}
