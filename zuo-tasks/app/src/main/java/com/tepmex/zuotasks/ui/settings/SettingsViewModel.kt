package com.tepmex.zuotasks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.zuotasks.data.ZuoTasksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val statusMessage: String? = null,
    val isBusy: Boolean = false,
)

sealed interface SettingsEvent {
    data class ExportReady(val text: String) : SettingsEvent
    data class ImportRequest(val text: String? = null) : SettingsEvent
}

class SettingsViewModel(
    private val repository: ZuoTasksRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<SettingsEvent?>(null)
    val events: StateFlow<SettingsEvent?> = _events.asStateFlow()

    fun onBackupClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                repository.exportBackup()
            }.onSuccess { text ->
                _events.value = SettingsEvent.ExportReady(text)
                _uiState.update { it.copy(statusMessage = "Backup ready to save", isBusy = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Backup failed", isBusy = false)
                }
            }
        }
    }

    fun onImportClicked() {
        _events.value = SettingsEvent.ImportRequest()
    }

    fun importFromText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                repository.importBackup(text)
            }.onSuccess {
                _uiState.update { it.copy(statusMessage = "Import completed", isBusy = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Import failed", isBusy = false)
                }
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }
}
