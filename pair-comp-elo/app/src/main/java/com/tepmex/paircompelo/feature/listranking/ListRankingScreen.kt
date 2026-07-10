package com.tepmex.paircompelo.feature.listranking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.core.AppClock
import com.tepmex.paircompelo.data.repository.ListSummary
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.elo.DecayCalculator
import com.tepmex.paircompelo.domain.model.RankingSettings
import com.tepmex.paircompelo.domain.ranking.RankingRecalculator
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ListRankRow(
    val rank: Int,
    val summary: ListSummary,
    val decayedRating: Double,
    val isStable: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListRankingViewModel @Inject constructor(
    repository: PreferenceRepository,
    clock: AppClock,
) : ViewModel() {
    val rows: StateFlow<List<ListRankRow>> = combine(
        repository.observeActiveLists(),
        repository.observeSettings(),
    ) { lists, settings -> lists to settings }
        .mapLatest { (_, settings) ->
            val summaries = repository.buildListSummaries()
            val now = clock.now()
            val decayed = summaries.associate { s ->
                s.list.id to DecayCalculator.decayRating(
                    s.list.rating,
                    s.list.ratingUpdatedAt,
                    now,
                    settings.initialRating,
                    settings.decayEnabled,
                    settings.decayRatePerDay,
                ).decayedRating
            }
            val ranked = RankingRecalculator.assignRanks(
                entries = summaries.map { Triple(it.list.id, it.list.name, it.list.rating) },
                comparisonCounts = summaries.associate { it.list.id to it.list.comparisonCount },
                initialRating = settings.initialRating,
                minimumComparisons = settings.minimumComparisonsBeforeStable,
                nowDecayedRatings = decayed,
            )
            val byId = summaries.associateBy { it.list.id }
            ranked.mapNotNull { entry ->
                byId[entry.id]?.let { summary ->
                    ListRankRow(entry.rank, summary, entry.rating, entry.isStable)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ListRankingScreen(
    onBack: () -> Unit,
    viewModel: ListRankingViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    AppScaffold(title = "List rankings", onBack = onBack) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                title = "No lists yet",
                message = "Create lists and compare them to build a ranking of the lists themselves.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                items(rows, key = { it.summary.list.id }) { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            "#${row.rank}  ${row.summary.list.name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            buildString {
                                append("Rating ${"%.1f".format(row.decayedRating)}")
                                append(" · ${row.summary.activeItemCount} items")
                                append(" · ${row.summary.list.comparisonCount} list comps")
                                row.summary.topItemName?.let { append(" · top: $it") }
                                if (!row.isStable) append(" · Not enough comparisons yet")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.isStable) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }
}
