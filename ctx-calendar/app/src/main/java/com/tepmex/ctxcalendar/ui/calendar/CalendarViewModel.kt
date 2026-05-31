package com.tepmex.ctxcalendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.ctxcalendar.data.GalleryPhoto
import com.tepmex.ctxcalendar.data.PhotoRepository
import com.tepmex.ctxcalendar.util.PerformanceLog
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val photosByDay: Map<LocalDate, List<GalleryPhoto>> = emptyMap(),
    val hasMediaPermission: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
)

class CalendarViewModel(
    private val photoRepository: PhotoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasMediaPermission = granted) }
        if (granted) {
            refreshPhotos()
        }
    }

    fun refreshPhotos() {
        if (!_uiState.value.hasMediaPermission) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching { photoRepository.loadPhotosByDay() }
                .onSuccess { photos ->
                    _uiState.update {
                        it.copy(photosByDay = photos, isLoading = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = error.message ?: "Failed to load photos",
                        )
                    }
                }
        }
    }

    fun previousMonth() {
        PerformanceLog.trace("calendar previousMonth") {
            _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
        }
    }

    fun nextMonth() {
        PerformanceLog.trace("calendar nextMonth") {
            _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
        }
    }

    fun photosFor(date: LocalDate): List<GalleryPhoto> =
        _uiState.value.photosByDay[date].orEmpty()

    fun photoById(photoId: Long): GalleryPhoto? =
        photoRepository.findPhoto(_uiState.value.photosByDay, photoId)
}

class CalendarViewModelFactory(
    private val photoRepository: PhotoRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            return CalendarViewModel(photoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
