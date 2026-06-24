package com.tepmex.zoulushang2.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.zoulushang2.data.AppRepository
import com.tepmex.zoulushang2.location.PaintForegroundService
import com.tepmex.zoulushang2.location.PaintSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val paintLookup: HashMap<Long, Int> = hashMapOf(),
    val paintedCellCount: Int = 0,
    val isPainting: Boolean = false,
    val paintStrokesApplied: Int = 0,
    val recenterMyLocationToken: Int = 0,
    val showSettings: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

sealed interface AppEvent {
    data class ExportReady(val text: String) : AppEvent
    data object ImportRequest : AppEvent
}

class AppViewModel(
    private val repository: AppRepository,
    private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observePaintLookup().collect { lookup ->
                _uiState.update {
                    it.copy(
                        paintLookup = lookup,
                        paintedCellCount = lookup.size,
                    )
                }
            }
        }
        viewModelScope.launch {
            PaintSession.state.collect { session ->
                _uiState.update {
                    it.copy(
                        isPainting = session.isRunning,
                        paintStrokesApplied = session.strokesApplied,
                    )
                }
            }
        }
    }

    fun startPainting() {
        PaintForegroundService.start(appContext)
    }

    fun stopPainting() {
        PaintForegroundService.stop(appContext)
    }

    fun recenterOnMyLocation() {
        _uiState.update { it.copy(recenterMyLocationToken = it.recenterMyLocationToken + 1) }
    }

    fun setShowSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show, statusMessage = null) }
    }

    fun onExportClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching { repository.exportDrawingText() }
                .onSuccess { text ->
                    _events.emit(AppEvent.ExportReady(text))
                    _uiState.update { it.copy(isBusy = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "Export failed",
                        )
                    }
                }
        }
    }

    fun onImportClicked() {
        viewModelScope.launch {
            _events.emit(AppEvent.ImportRequest)
        }
    }

    fun importFromText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null, errorMessage = null) }
            runCatching { repository.importDrawingText(text) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Drawing imported",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "Import failed",
                        )
                    }
                }
        }
    }

    fun clearDrawing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            runCatching { repository.clearDrawing() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Drawing cleared",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "Clear failed",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class AppViewModelFactory(
    private val repository: AppRepository,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(repository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
