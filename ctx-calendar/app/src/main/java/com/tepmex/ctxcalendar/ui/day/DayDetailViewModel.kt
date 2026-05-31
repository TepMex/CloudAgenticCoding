package com.tepmex.ctxcalendar.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.ctxcalendar.data.takeout.TakeoutDayTimeline
import com.tepmex.ctxcalendar.data.takeout.TakeoutRepository
import com.tepmex.ctxcalendar.util.PerformanceLog
import java.time.LocalDate
import kotlinx.coroutines.Job
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

    private val timelineCache = mutableMapOf<LocalDate, TakeoutDayTimeline>()
    private var loadedDate: LocalDate? = null
    private var loadJob: Job? = null
    private var cachedDbGeneration: Int = -1

    fun loadForDate(date: LocalDate, force: Boolean = false) {
        val dbGeneration = takeoutRepository.openGeneration()
        if (dbGeneration != cachedDbGeneration) {
            timelineCache.clear()
            loadedDate = null
            cachedDbGeneration = dbGeneration
        }

        if (!takeoutRepository.isOpen()) {
            timelineCache.clear()
            loadedDate = null
            _takeoutState.value = DayTakeoutUiState(hasDatabase = false)
            return
        }

        if (!force) {
            timelineCache[date]?.let { cached ->
                loadedDate = date
                _takeoutState.update {
                    it.copy(
                        isLoading = false,
                        hasDatabase = takeoutRepository.isOpen(),
                        timeline = cached,
                        errorMessage = null,
                    )
                }
                PerformanceLog.log("loadForDate($date) cache hit")
                return
            }
            if (loadedDate == date && _takeoutState.value.isLoading) {
                PerformanceLog.log("loadForDate($date) already in flight")
                return
            }
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _takeoutState.update { it.copy(isLoading = true, hasDatabase = true, errorMessage = null) }
            PerformanceLog.traceSuspend("takeout loadDayTimeline($date)") {
                runCatching { takeoutRepository.loadDayTimeline(date) }
            }
                .onSuccess { timeline ->
                    if (timeline != null) {
                        timelineCache[date] = timeline
                    }
                    loadedDate = date
                    _takeoutState.update {
                        it.copy(isLoading = false, timeline = timeline)
                    }
                }
                .onFailure { error ->
                    loadedDate = date
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
