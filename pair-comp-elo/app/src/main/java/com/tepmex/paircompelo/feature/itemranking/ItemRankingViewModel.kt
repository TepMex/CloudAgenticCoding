package com.tepmex.paircompelo.feature.itemranking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.core.AppClock
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.elo.DecayCalculator
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.RankedEntry
import com.tepmex.paircompelo.domain.model.RankingSettings
import com.tepmex.paircompelo.domain.ranking.RankingRecalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

enum class ItemSort {
    ELO, NAME, MOST_COMPARED, LEAST_COMPARED, NEWEST, OLDEST
}

data class ItemRankingUiState(
    val entries: List<Pair<RankedEntry, PreferenceItem>> = emptyList(),
    val sort: ItemSort = ItemSort.ELO,
    val query: String = "",
    val settings: RankingSettings = RankingSettings.Defaults,
    val loading: Boolean = true,
)

@HiltViewModel
class ItemRankingViewModel @Inject constructor(
    repository: PreferenceRepository,
    clock: AppClock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val listId = UUID.fromString(checkNotNull(savedStateHandle["listId"]))
    private val sort = MutableStateFlow(ItemSort.ELO)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<ItemRankingUiState> = combine(
        repository.observeActiveItems(listId),
        repository.observeSettings(),
        sort,
        query,
    ) { items, settings, sortValue, q ->
        val now = clock.now()
        val decayed = items.associate { item ->
            item.id to DecayCalculator.decayRating(
                currentRating = item.rating,
                ratingUpdatedAt = item.ratingUpdatedAt,
                now = now,
                initialRating = settings.initialRating,
                decayEnabled = settings.decayEnabled,
                decayRatePerDay = settings.decayRatePerDay,
            ).decayedRating
        }
        val ranked = RankingRecalculator.assignRanks(
            entries = items.map { Triple(it.id, it.name, it.rating) },
            comparisonCounts = items.associate { it.id to it.comparisonCount },
            initialRating = settings.initialRating,
            minimumComparisons = settings.minimumComparisonsBeforeStable,
            nowDecayedRatings = decayed,
        )
        val byId = items.associateBy { it.id }
        var pairs = ranked.mapNotNull { entry -> byId[entry.id]?.let { entry to it } }
        if (q.isNotBlank()) {
            pairs = pairs.filter { it.second.name.contains(q, ignoreCase = true) }
        }
        pairs = when (sortValue) {
            ItemSort.ELO -> pairs
            ItemSort.NAME -> pairs.sortedBy { it.second.name.lowercase() }
            ItemSort.MOST_COMPARED -> pairs.sortedByDescending { it.second.comparisonCount }
            ItemSort.LEAST_COMPARED -> pairs.sortedBy { it.second.comparisonCount }
            ItemSort.NEWEST -> pairs.sortedByDescending { it.second.createdAt }
            ItemSort.OLDEST -> pairs.sortedBy { it.second.createdAt }
        }
        ItemRankingUiState(pairs, sortValue, q, settings, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemRankingUiState())

    fun setSort(value: ItemSort) = sort.update { value }
    fun setQuery(value: String) = query.update { value }
}
