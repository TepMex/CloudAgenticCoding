package com.tepmex.paircompelo.feature.itemcomparison

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.PreferenceItem
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

data class ItemComparisonUiState(
    val left: PreferenceItem? = null,
    val right: PreferenceItem? = null,
    val settings: RankingSettings = RankingSettings.Defaults,
    val lastWinnerId: UUID? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val unavailable: Boolean = false,
)

@HiltViewModel
class ItemComparisonViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val listId = UUID.fromString(checkNotNull(savedStateHandle["listId"]))
    private val _state = MutableStateFlow(ItemComparisonUiState())
    val state: StateFlow<ItemComparisonUiState> = _state.asStateFlow()
    private var lastSubmitAt = 0L

    init {
        viewModelScope.launch {
            val settings = repository.observeSettings().first()
            _state.update { it.copy(settings = settings) }
            loadNext()
        }
    }

    fun loadNext() {
        viewModelScope.launch {
            val pair = repository.selectNextItemPair(listId)
            if (pair == null) {
                _state.update { it.copy(left = null, right = null, unavailable = true) }
            } else {
                _state.update {
                    it.copy(
                        left = pair.first,
                        right = pair.second,
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
                val result = repository.recordItemComparison(listId, left.id, right.id, outcome)
                _state.update {
                    it.copy(
                        busy = false,
                        lastWinnerId = result.winnerItemId,
                        message = when (outcome) {
                            ComparisonOutcome.LEFT_WINS -> "Chose ${left.name}"
                            ComparisonOutcome.RIGHT_WINS -> "Chose ${right.name}"
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
            repository.undoLatestItemComparison(listId)
            loadNext()
        }
    }
}
