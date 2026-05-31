package com.tepmex.ctxcalendar.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.ctxcalendar.data.takeout.TakeoutDayTimeline
import com.tepmex.ctxcalendar.data.takeout.TakeoutRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DayTakeoutUiState(
    val isLoading: Boolean = false,
    val hasDatabase: Boolean = false,
    val timeline: TakeoutDayTimeline? = null,
    val errorMessage: String? = null,
)

class DayDetailViewModel(
    private val takeoutRepository: TakeoutRepository,
) : ViewModel() {

    private val _takeoutState = MutableStateFlow(DayTakeoutUiState())
    val takeoutState: StateFlow<DayTakeoutUiState> = _takeoutState.asStateFlow()

    fun loadForDate(date: LocalDate) {
        viewModelScope.launch {
            if (!takeoutRepository.isOpen()) {
                _takeoutState.value = DayTakeoutUiState(hasDatabase = false)
                return@launch
            }
            _takeoutState.update { it.copy(isLoading = true, hasDatabase = true, errorMessage = null) }
            runCatching { takeoutRepository.loadDayTimeline(date) }
                .onSuccess { timeline ->
                    _takeoutState.update {
                        it.copy(isLoading = false, timeline = timeline)
                    }
                }
                .onFailure { error ->
                    _takeoutState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load timeline",
                        )
                    }
                }
        }
    }
}

class DayDetailViewModelFactory(
    private val takeoutRepository: TakeoutRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DayDetailViewModel::class.java)) {
            return DayDetailViewModel(takeoutRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
