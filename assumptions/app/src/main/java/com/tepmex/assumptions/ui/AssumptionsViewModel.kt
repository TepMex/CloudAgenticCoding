package com.tepmex.assumptions.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.assumptions.AssumptionsApp
import com.tepmex.assumptions.data.AssumptionsRepository
import com.tepmex.assumptions.domain.EmotionEntry
import com.tepmex.assumptions.domain.Expectation
import com.tepmex.assumptions.domain.ExpectationField
import com.tepmex.assumptions.domain.PleasureScale
import com.tepmex.assumptions.domain.Quadrant
import com.tepmex.assumptions.domain.WheelHit
import com.tepmex.assumptions.domain.WheelReducer
import com.tepmex.assumptions.domain.WheelState
import com.tepmex.assumptions.domain.expectationErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Section { Emotions, Expectations, Report }

sealed interface DraftMode {
    data object Create : DraftMode

    data class Resolve(val id: Long) : DraftMode
}

data class ExpectationDraft(
    val mode: DraftMode,
    val event: String,
    val outcome: String,
    val score: Int?,
    val note: String,
    val errors: Set<ExpectationField>,
)

class AssumptionsViewModel(
    private val repository: AssumptionsRepository,
) : ViewModel() {
    val emotions: StateFlow<List<EmotionEntry>> = repository.emotions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expectations: StateFlow<List<Expectation>> = repository.expectations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val section = MutableStateFlow(Section.Emotions)
    val wheel = MutableStateFlow(WheelState())
    val draft = MutableStateFlow<ExpectationDraft?>(null)
    val quadrant = MutableStateFlow<Quadrant?>(null)

    fun open(section: Section) {
        this.section.value = section
    }

    fun onWheelHit(hit: WheelHit) {
        wheel.update { WheelReducer.reduce(it, hit) }
    }

    fun recordSelectedEmotion() {
        val emotionId = wheel.value.selectedId ?: return
        viewModelScope.launch {
            repository.recordEmotion(emotionId)
            wheel.value = WheelState()
        }
    }

    fun deleteEmotion(id: Long) {
        viewModelScope.launch { repository.deleteEmotion(id) }
    }

    fun beginCreate() {
        draft.value = ExpectationDraft(
            mode = DraftMode.Create,
            event = "",
            outcome = "",
            score = null,
            note = "",
            errors = emptySet(),
        )
    }

    fun beginResolve(item: Expectation) {
        if (item.afterScore != null) return
        draft.value = ExpectationDraft(
            mode = DraftMode.Resolve(item.id),
            event = item.eventText,
            outcome = item.expectedOutcome,
            score = null,
            note = "",
            errors = emptySet(),
        )
    }

    fun updateEvent(text: String) = updateDraft { it.copy(event = text, errors = it.errors - ExpectationField.Event) }

    fun updateOutcome(text: String) =
        updateDraft { it.copy(outcome = text, errors = it.errors - ExpectationField.Outcome) }

    fun updateScore(score: Int) {
        if (!PleasureScale.contains(score)) return
        updateDraft { it.copy(score = score, errors = it.errors - ExpectationField.Score) }
    }

    fun updateNote(text: String) = updateDraft { it.copy(note = text) }

    fun cancelDraft() {
        draft.value = null
    }

    private var saving = false

    fun saveDraft() {
        if (saving) return
        val current = draft.value ?: return
        when (val mode = current.mode) {
            DraftMode.Create -> {
                val errors = expectationErrors(current.event, current.outcome, current.score)
                if (errors.isNotEmpty()) {
                    draft.value = current.copy(errors = errors)
                    return
                }
                saving = true
                viewModelScope.launch {
                    try {
                        repository.addExpectation(current.event, current.outcome, current.score!!)
                        draft.value = null
                    } finally {
                        saving = false
                    }
                }
            }
            is DraftMode.Resolve -> {
                val score = current.score
                if (score == null || !PleasureScale.contains(score)) {
                    draft.value = current.copy(errors = setOf(ExpectationField.Score))
                    return
                }
                saving = true
                viewModelScope.launch {
                    try {
                        repository.resolve(mode.id, score, current.note)
                        draft.value = null
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    fun deleteExpectation(id: Long) {
        viewModelScope.launch { repository.deleteExpectation(id) }
    }

    fun toggleQuadrant(next: Quadrant) {
        quadrant.update { current -> if (current == next) null else next }
    }

    fun clearQuadrant() {
        quadrant.value = null
    }

    private fun updateDraft(block: (ExpectationDraft) -> ExpectationDraft) {
        draft.update { current -> current?.let(block) }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val assumptions = app as AssumptionsApp
                    return AssumptionsViewModel(assumptions.repository) as T
                }
            }
    }
}
