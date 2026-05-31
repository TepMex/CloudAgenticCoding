package com.tepmex.ctxcalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.ctxcalendar.data.AppPreferences
import com.tepmex.ctxcalendar.data.takeout.TakeoutDbInfo
import com.tepmex.ctxcalendar.data.takeout.TakeoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val takeoutDbUri: String? = null,
    val dbInfo: TakeoutDbInfo? = null,
    val isOpening: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val takeoutRepository: TakeoutRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.takeoutDbUri.collect { uri ->
                _uiState.update { it.copy(takeoutDbUri = uri) }
                if (uri != null && !takeoutRepository.isOpen()) {
                    reopenDatabase(uri)
                }
            }
        }
    }

    fun onDatabasePicked(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOpening = true, errorMessage = null, statusMessage = null) }
            preferences.setTakeoutDbUri(uri)
            reopenDatabase(uri)
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            takeoutRepository.close()
            preferences.setTakeoutDbUri(null)
            _uiState.update {
                it.copy(
                    takeoutDbUri = null,
                    dbInfo = null,
                    statusMessage = null,
                    errorMessage = null,
                )
            }
        }
    }

    private suspend fun reopenDatabase(uri: String) {
        _uiState.update { it.copy(isOpening = true, errorMessage = null) }
        val result = takeoutRepository.openFromUri(uri)
        _uiState.update { state ->
            result.fold(
                onSuccess = { info ->
                    state.copy(
                        isOpening = false,
                        dbInfo = info,
                        statusMessage = buildStatusMessage(info),
                        errorMessage = null,
                    )
                },
                onFailure = { error ->
                    state.copy(
                        isOpening = false,
                        dbInfo = null,
                        statusMessage = null,
                        errorMessage = error.message ?: "Failed to open database",
                    )
                },
            )
        }
    }

    private fun buildStatusMessage(info: TakeoutDbInfo): String {
        val parts = buildList {
            info.schemaVersion?.let { add("schema v$it") }
            info.eventCount?.let { add("$it events") }
            info.builtAt?.let { add("built $it") }
        }
        return if (parts.isEmpty()) "Database connected" else parts.joinToString(" · ")
    }
}

class SettingsViewModelFactory(
    private val preferences: AppPreferences,
    private val takeoutRepository: TakeoutRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(preferences, takeoutRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
