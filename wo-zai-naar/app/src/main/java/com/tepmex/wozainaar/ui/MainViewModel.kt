package com.tepmex.wozainaar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.wozainaar.data.LocationPoint
import com.tepmex.wozainaar.data.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MainUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val permissionsReady: Boolean = false,
    val totalSamples: Int = 0,
)

class MainViewModel(
    private val repository: LocationRepository,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _uiState = MutableStateFlow(MainUiState())
    val screenState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val pointsForSelectedDay: StateFlow<List<LocationPoint>> = selectedDate
        .flatMapLatest { date -> repository.observeDay(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSelectedDate(date: LocalDate) {
        selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun setPermissionsReady(ready: Boolean) {
        _uiState.update { it.copy(permissionsReady = ready) }
    }

    fun refreshSampleCount() {
        viewModelScope.launch {
            val count = repository.countAll()
            _uiState.update { it.copy(totalSamples = count) }
        }
    }
}

class MainViewModelFactory(
    private val repository: LocationRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
