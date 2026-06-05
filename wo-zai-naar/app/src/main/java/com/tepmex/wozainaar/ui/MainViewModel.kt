package com.tepmex.wozainaar.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tepmex.wozainaar.LocationPermissions
import com.tepmex.wozainaar.data.LocationPoint
import com.tepmex.wozainaar.data.LocationRepository
import com.tepmex.wozainaar.work.LocationWorkScheduler
import com.tepmex.wozainaar.work.LocationWorker
import com.tepmex.wozainaar.work.TrackingLogger
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
    val periodicWorkState: String = "—",
    val manualWorkState: String = "—",
    val manualCaptureInFlight: Boolean = false,
)

class MainViewModel(
    private val repository: LocationRepository,
    private val appContext: Context,
) : ViewModel() {
    private val workManager = WorkManager.getInstance(appContext)
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private var periodicHealInFlight = false

    private val _uiState = MutableStateFlow(MainUiState())
    val screenState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val trackingLogs: StateFlow<List<String>> = TrackingLogger.lines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pointsForSelectedDay: StateFlow<List<LocationPoint>> = selectedDate
        .flatMapLatest { date -> repository.observeDay(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        observePeriodicWork()
        observeManualWork()
    }

    fun setSelectedDate(date: LocalDate) {
        selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun setPermissionsReady(ready: Boolean) {
        _uiState.update { it.copy(permissionsReady = ready) }
    }

    fun onPermissionsGranted() {
        TrackingLogger.log("Permissions ready; ensuring periodic work is scheduled")
        LocationWorkScheduler.schedule(appContext)
        refreshSampleCount()
    }

    private fun healPeriodicWorkIfNeeded(stateLabel: String) {
        if (!LocationPermissions.hasAll(appContext) || periodicHealInFlight) return
        if (
            !stateLabel.contains("failed", ignoreCase = true) &&
            !stateLabel.contains("cancelled", ignoreCase = true)
        ) {
            return
        }
        periodicHealInFlight = true
        TrackingLogger.log("Periodic work is $stateLabel — re-scheduling with cancel + re-enqueue")
        LocationWorkScheduler.schedule(appContext)
    }

    fun captureLocationNow() {
        if (_uiState.value.manualCaptureInFlight) {
            TrackingLogger.log("Manual capture already in progress")
            return
        }
        LocationWorkScheduler.runNow(appContext)
    }

    fun refreshSampleCount() {
        viewModelScope.launch {
            val count = repository.countAll()
            _uiState.update { it.copy(totalSamples = count) }
        }
    }

    private fun observePeriodicWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(LocationWorker.UNIQUE_PERIODIC_WORK)
                .collect { infos ->
                    val state = formatWorkStates(infos)
                    if (state != _uiState.value.periodicWorkState) {
                        TrackingLogger.log("Periodic work state: $state")
                    }
                    _uiState.update { it.copy(periodicWorkState = state) }
                    if (
                        infos.any {
                            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                        }
                    ) {
                        periodicHealInFlight = false
                    } else {
                        healPeriodicWorkIfNeeded(state)
                    }
                }
        }
    }

    private fun observeManualWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(LocationWorker.UNIQUE_ONE_TIME_WORK)
                .collect { infos ->
                    val state = formatWorkStates(infos)
                    val inFlight = infos.any {
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                    }
                    if (state != _uiState.value.manualWorkState) {
                        TrackingLogger.log("Manual capture work state: $state")
                    }
                    _uiState.update {
                        it.copy(
                            manualWorkState = state,
                            manualCaptureInFlight = inFlight,
                        )
                    }
                    if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                        refreshSampleCount()
                    }
                }
        }
    }

    private fun formatWorkStates(infos: List<WorkInfo>): String {
        if (infos.isEmpty()) return "not scheduled"
        return infos.joinToString { info ->
            val detail = listOfNotNull(
                info.outputData.getString(LocationWorker.KEY_ERROR),
                info.outputData.getString(LocationWorker.KEY_SKIP_REASON),
            ).joinToString()
            buildString {
                append(info.state.name.lowercase())
                if (detail.isNotEmpty()) append(" ($detail)")
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: LocationRepository,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
