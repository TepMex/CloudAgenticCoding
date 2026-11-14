package dev.eareye.t.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.eareye.t.EarEyeApp
import dev.eareye.t.collect.CategoryCache
import dev.eareye.t.collect.DeviceStatus
import dev.eareye.t.collect.MonitorBridge
import dev.eareye.t.collect.RuntimeSnapshot
import dev.eareye.t.data.EventRepository
import dev.eareye.t.data.db.DiagnosticEventEntity
import dev.eareye.t.data.db.MonitoringSessionEntity
import dev.eareye.t.domain.AudioCategorySelection
import dev.eareye.t.domain.DailyStatistics
import dev.eareye.t.domain.DaySelection
import dev.eareye.t.domain.ExportDocument
import dev.eareye.t.domain.LiveObservation
import dev.eareye.t.domain.TimelineModel
import dev.eareye.t.domain.exportCsv
import dev.eareye.t.domain.exportJson
import dev.eareye.t.domain.resolveSelectionDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val selection: DaySelection = DaySelection.Today,
    val date: LocalDate? = null,
    val dateLabel: String = "",
    val stats: DailyStatistics? = null,
    val timeline: TimelineModel? = null,
    val listenerConnected: Boolean = false,
    val audioActive: Boolean = false,
    val usageGranted: Boolean = false,
    val listenerEnabled: Boolean = false,
    val lastConfirmationMillis: Long? = null,
    val zoneId: String = "",
)

data class DiagnosticsUiState(
    val loading: Boolean = true,
    val manufacturer: String = "",
    val model: String = "",
    val release: String = "",
    val sdk: Int = 0,
    val usageGranted: Boolean = false,
    val listenerEnabled: Boolean = false,
    val listenerConnected: Boolean = false,
    val screenInteractive: Boolean = false,
    val keyguardLocked: Boolean = false,
    val audioActive: Boolean = false,
    val lastCallbackMillis: Long? = null,
    val lastHeartbeatMillis: Long? = null,
    val lastSnapshotMillis: Long? = null,
    val lastConfirmationMillis: Long? = null,
    val lastSyncMillis: Long? = null,
    val syncError: String? = null,
    val trustedUntilMillis: Long? = null,
    val events: List<DiagnosticEventEntity> = emptyList(),
)

data class PermissionsUiState(
    val usageGranted: Boolean = false,
    val listenerEnabled: Boolean = false,
    val listenerConnected: Boolean = false,
    val categories: AudioCategorySelection = AudioCategorySelection(),
)

class MainViewModel(
    private val repository: EventRepository,
    private val device: DeviceStatus,
    private val categories: CategoryCache,
    runtime: StateFlow<RuntimeSnapshot>,
) : ViewModel() {
    private val selection = MutableStateFlow<DaySelection>(DaySelection.Today)
    private val resumeTick = MutableStateFlow(0)
    @Volatile private var latestSnap: RuntimeSnapshot = RuntimeSnapshot()
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(30_000)
        }
    }

    private val homeState = MutableStateFlow(HomeUiState())
    private val diagnosticsState = MutableStateFlow(DiagnosticsUiState())
    private val permissionsState = MutableStateFlow(PermissionsUiState())

    val home: StateFlow<HomeUiState> = homeState.asStateFlow()
    val diagnostics: StateFlow<DiagnosticsUiState> = diagnosticsState.asStateFlow()
    val permissions: StateFlow<PermissionsUiState> = permissionsState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                combine(selection, repository.observeGeneration(), ticker, runtime, resumeTick) { selected, _, now, snap, _ ->
                    selected to (now to snap)
                },
                repository.observeDiagnostics(),
            ) { refresh, diagnostics ->
                refresh to diagnostics
            }.collect { (refresh, diagnostics) ->
                val (selected, clockAndSnap) = refresh
                val (now, snap) = clockAndSnap
                refresh(selected, now, snap, diagnostics)
            }
        }
    }

    fun onResume() {
        resumeTick.value += 1
    }

    fun select(selection: DaySelection) {
        this.selection.value = selection
    }

    fun setCategories(selection: AudioCategorySelection) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAudioSettings(selection)
            categories.selection = selection
            MonitorBridge.resnapshot?.invoke()
        }
    }

    suspend fun buildJson(): String = withContext(Dispatchers.IO) { exportJson(buildExport()) }

    suspend fun buildCsv(): String = withContext(Dispatchers.IO) { exportCsv(buildExport()) }

    private suspend fun refresh(
        selected: DaySelection,
        now: Long,
        snap: RuntimeSnapshot,
        diagnostics: List<DiagnosticEventEntity>,
    ) {
        latestSnap = snap
        val zone = ZoneId.systemDefault()
        val loaded = withContext(Dispatchers.IO) {
            val date = resolveSelectionDate(selected, zone, now)
            val live = liveObservation(snap)
            val computation = repository.computeDay(date, zone, now, live)
            val session = repository.latestSession()
            val sync = repository.syncState()
            Loaded(
                date = date,
                computation = computation,
                session = session,
                lastSync = sync?.lastSuccessWallMillis,
                syncError = sync?.lastError,
                trustedUntil = sync?.trustedUntilMillis,
                settings = repository.audioSettings(),
                usageGranted = device.usageAccessGranted(),
                listenerEnabled = device.notificationListenerEnabled(),
                screenInteractive = device.screenInteractive(),
                keyguardLocked = device.keyguardLocked(),
            )
        }
        homeState.value = HomeUiState(
            loading = false,
            selection = selected,
            date = loaded.date,
            dateLabel = loaded.date.format(DATE_LABEL),
            stats = loaded.computation.stats,
            timeline = loaded.computation.timeline,
            listenerConnected = snap.listenerConnected,
            audioActive = snap.audioActive,
            usageGranted = loaded.usageGranted,
            listenerEnabled = loaded.listenerEnabled,
            lastConfirmationMillis = loaded.session?.lastConfirmedWallMillis,
            zoneId = zone.id,
        )
        diagnosticsState.value = DiagnosticsUiState(
            loading = false,
            manufacturer = device.manufacturer(),
            model = device.model(),
            release = device.release(),
            sdk = device.sdkInt(),
            usageGranted = loaded.usageGranted,
            listenerEnabled = loaded.listenerEnabled,
            listenerConnected = snap.listenerConnected,
            screenInteractive = loaded.screenInteractive,
            keyguardLocked = loaded.keyguardLocked,
            audioActive = snap.audioActive,
            lastCallbackMillis = loaded.session?.lastCallbackWallMillis,
            lastHeartbeatMillis = loaded.session?.lastHeartbeatWallMillis,
            lastSnapshotMillis = loaded.session?.lastSnapshotWallMillis,
            lastConfirmationMillis = loaded.session?.lastConfirmedWallMillis,
            lastSyncMillis = loaded.lastSync,
            syncError = loaded.syncError,
            trustedUntilMillis = loaded.trustedUntil,
            events = diagnostics,
        )
        permissionsState.value = PermissionsUiState(
            usageGranted = loaded.usageGranted,
            listenerEnabled = loaded.listenerEnabled,
            listenerConnected = snap.listenerConnected,
            categories = loaded.settings,
        )
    }

    private fun liveObservation(snap: RuntimeSnapshot): LiveObservation? {
        val sessionId = snap.sessionId ?: return null
        if (!snap.listenerConnected) return null
        val sample = device.sample()
        return LiveObservation(sessionId, sample.wallMillis, sample.elapsedMillis)
    }

    private suspend fun buildExport(): ExportDocument {
        val zone = ZoneId.systemDefault()
        val now = device.sample().wallMillis
        val date = resolveSelectionDate(selection.value, zone, now)
        val snap = latestSnap
        return repository.buildExport(
            date = date,
            zone = zone,
            nowMillis = now,
            live = liveObservation(snap),
            manufacturer = device.manufacturer(),
            model = device.model(),
            sdkInt = device.sdkInt(),
            release = device.release(),
            usageAccess = device.usageAccessGranted(),
            listenerEnabled = device.notificationListenerEnabled(),
            listenerConnected = snap.listenerConnected,
            audioActive = snap.audioActive,
            screenInteractive = device.screenInteractive(),
            keyguardLocked = device.keyguardLocked(),
        )
    }

    private data class Loaded(
        val date: LocalDate,
        val computation: dev.eareye.t.domain.DayComputation,
        val session: MonitoringSessionEntity?,
        val lastSync: Long?,
        val syncError: String?,
        val trustedUntil: Long?,
        val settings: AudioCategorySelection,
        val usageGranted: Boolean,
        val listenerEnabled: Boolean,
        val screenInteractive: Boolean,
        val keyguardLocked: Boolean,
    )

    companion object {
        private val DATE_LABEL: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
    }
}

class MainViewModelFactory(
    private val app: EarEyeApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val graph = app.graph
        return MainViewModel(
            repository = graph.repository,
            device = graph.device,
            categories = graph.categories,
            runtime = dev.eareye.t.collect.MonitoringRuntime.state,
        ) as T
    }
}
