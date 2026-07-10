package com.tepmex.paircompelo.feature.listcomparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.PreferenceList
import com.tepmex.paircompelo.domain.model.RankingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ListPairInfo(
    val list: PreferenceList,
    val activeItemCount: Int,
    val topItemName: String?,
)

data class ListComparisonUiState(
    val left: ListPairInfo? = null,
    val right: ListPairInfo? = null,
    val settings: RankingSettings = RankingSettings.Defaults,
    val lastWinnerId: UUID? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val unavailable: Boolean = false,
)

@HiltViewModel
class ListComparisonViewModel @Inject constructor(
    private val repository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ListComparisonUiState())
    val state: StateFlow<ListComparisonUiState> = _state.asStateFlow()
    private var lastSubmitAt = 0L

    init {
        viewModelScope.launch {
            _state.update { it.copy(settings = repository.observeSettings().first()) }
            loadNext()
        }
    }

    private suspend fun toInfo(list: PreferenceList): ListPairInfo {
        val summaries = repository.buildListSummaries()
        val match = summaries.firstOrNull { it.list.id == list.id }
        return ListPairInfo(list, match?.activeItemCount ?: 0, match?.topItemName)
    }

    fun loadNext() {
        viewModelScope.launch {
            val pair = repository.selectNextListPair()
            if (pair == null) {
                _state.update { it.copy(unavailable = true, left = null, right = null) }
            } else {
                _state.update {
                    it.copy(
                        left = toInfo(pair.first),
                        right = toInfo(pair.second),
                        unavailable = false,
                        lastWinnerId = null,
                        message = null,
                    )
                }
            }
        }
    }

    fun choose(outcome: ComparisonOutcome) {
        val now = System.currentTimeMillis()
        if (now - lastSubmitAt < 400) return
        lastSubmitAt = now
        val s = _state.value
        val left = s.left ?: return
        val right = s.right ?: return
        if (s.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val result = repository.recordListComparison(left.list.id, right.list.id, outcome)
                _state.update {
                    it.copy(
                        busy = false,
                        lastWinnerId = result.winnerListId,
                        message = when (outcome) {
                            ComparisonOutcome.LEFT_WINS -> "Chose ${left.list.name}"
                            ComparisonOutcome.RIGHT_WINS -> "Chose ${right.list.name}"
                            ComparisonOutcome.DRAW -> "Draw recorded"
                            ComparisonOutcome.SKIPPED -> "Skipped"
                        },
                    )
                }
                kotlinx.coroutines.delay(350)
                loadNext()
            }.onFailure { e ->
                _state.update { it.copy(busy = false, message = e.message) }
            }
        }
    }

    fun undo() {
        viewModelScope.launch {
            repository.undoLatestListComparison()
            loadNext()
        }
    }
}
