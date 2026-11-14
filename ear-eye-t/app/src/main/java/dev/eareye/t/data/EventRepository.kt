package dev.eareye.t.data

import androidx.room.withTransaction
import dev.eareye.t.data.db.AudioSettingsEntity
import dev.eareye.t.data.db.AudioTransitionEntity
import dev.eareye.t.data.db.DataGenerationEntity
import dev.eareye.t.data.db.DiagnosticEventEntity
import dev.eareye.t.data.db.EarEyeDatabase
import dev.eareye.t.data.db.MonitoringSessionEntity
import dev.eareye.t.data.db.ScreenEventEntity
import dev.eareye.t.data.db.SyncStateEntity
import dev.eareye.t.domain.AudioCategorySelection
import dev.eareye.t.domain.AudioTransitionRecord
import dev.eareye.t.domain.DayComputation
import dev.eareye.t.domain.ExportDiagnostic
import dev.eareye.t.domain.ExportDocument
import dev.eareye.t.domain.LiveObservation
import dev.eareye.t.domain.ObservationPolicy
import dev.eareye.t.domain.ScreenEvent
import dev.eareye.t.domain.ScreenEventType
import dev.eareye.t.domain.SessionRecord
import dev.eareye.t.domain.calculateDailyStatistics
import dev.eareye.t.domain.periodWindow
import dev.eareye.t.domain.poweredOffIntervals
import dev.eareye.t.domain.reconstructEar
import dev.eareye.t.domain.reconstructEye
import dev.eareye.t.domain.timelineFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class EventRepository(
    private val database: EarEyeDatabase,
) {
    private val dao = database.dao()

    fun observeGeneration(): Flow<Long> = dao.observeGeneration().map { it ?: 0L }

    fun observeDiagnostics(): Flow<List<DiagnosticEventEntity>> =
        dao.observeDiagnostics(ObservationPolicy.DIAGNOSTIC_LIMIT)

    suspend fun ensureSingletons() {
        dao.insertSettings(AudioSettingsEntity())
        dao.insertGeneration(DataGenerationEntity(version = 0))
        dao.insertSyncState(SyncStateEntity(lastSuccessWallMillis = null, trustedUntilMillis = null, lastError = null))
    }

    suspend fun syncState(): SyncStateEntity? = dao.syncState()

    suspend fun audioSettings(): AudioCategorySelection {
        val row = dao.settings() ?: AudioSettingsEntity()
        return row.toSelection()
    }

    suspend fun updateAudioSettings(selection: AudioCategorySelection) {
        database.withTransaction {
            dao.updateSettings(
                media = selection.media,
                game = selection.game,
                assistant = selection.assistant,
                navigation = selection.navigation,
                accessibility = selection.accessibility,
            )
            dao.bumpGeneration()
        }
    }

    suspend fun replaceScreenEvents(
        fromMillis: Long,
        toMillis: Long,
        events: List<ScreenEvent>,
        trustedUntilMillis: Long,
        successWallMillis: Long,
    ) {
        database.withTransaction {
            dao.deleteScreenEventsInRange(fromMillis, toMillis)
            if (events.isNotEmpty()) {
                dao.insertScreenEvents(
                    events.map { event ->
                        ScreenEventEntity(
                            timestampMillis = event.timestampMillis,
                            eventType = event.type.code,
                            ordinal = event.ordinal,
                        )
                    },
                )
            }
            dao.markSyncSuccess(successWallMillis, trustedUntilMillis)
            dao.bumpGeneration()
        }
    }

    suspend fun markSyncFailure(message: String) {
        database.withTransaction {
            dao.markSyncFailure(message.take(300))
            dao.bumpGeneration()
        }
    }

    suspend fun openSessions(): List<MonitoringSessionEntity> = dao.openSessions()

    suspend fun insertSession(session: MonitoringSessionEntity): Long = dao.insertSession(session)

    suspend fun updateSession(session: MonitoringSessionEntity) {
        dao.updateSession(session)
    }

    suspend fun latestSession(): MonitoringSessionEntity? = dao.latestSession()

    suspend fun insertTransition(transition: AudioTransitionEntity) {
        database.withTransaction {
            dao.insertTransition(transition)
            dao.bumpGeneration()
        }
    }

    suspend fun addDiagnostic(kind: String, message: String, wallMillis: Long, bump: Boolean = true) {
        database.withTransaction {
            dao.insertDiagnostic(
                DiagnosticEventEntity(
                    wallMillis = wallMillis,
                    kind = kind,
                    message = message.take(500),
                ),
            )
            dao.pruneDiagnostics(ObservationPolicy.DIAGNOSTIC_LIMIT)
            if (bump) dao.bumpGeneration()
        }
    }

    suspend fun computeDay(
        date: LocalDate,
        zone: ZoneId,
        nowMillis: Long,
        live: LiveObservation?,
    ): DayComputation {
        val window = periodWindow(date, zone, nowMillis)
        val loaded = loadSources()
        val trustedUntil = loaded.trustedUntilMillis ?: Long.MIN_VALUE
        val eye = reconstructEye(loaded.screenEvents, window, trustedUntil)
        val poweredOff = poweredOffIntervals(loaded.screenEvents, window, trustedUntil)
        val ear = reconstructEar(loaded.sessions, loaded.transitions, live)
        val stats = calculateDailyStatistics(
            eyeActive = eye.active,
            eyeUnknown = eye.unknown,
            earActive = ear.active,
            listenerCoverage = ear.coverage,
            poweredOff = poweredOff,
            window = window,
        )
        val timeline = timelineFor(
            eyeActive = eye.active,
            eyeUnknown = eye.unknown,
            earActive = ear.active,
            listenerCoverage = ear.coverage,
            poweredOff = poweredOff,
            window = window,
        )
        return DayComputation(stats, timeline)
    }

    suspend fun buildExport(
        date: LocalDate,
        zone: ZoneId,
        nowMillis: Long,
        live: LiveObservation?,
        manufacturer: String,
        model: String,
        sdkInt: Int,
        release: String,
        usageAccess: Boolean,
        listenerEnabled: Boolean,
        listenerConnected: Boolean,
        audioActive: Boolean,
        screenInteractive: Boolean,
        keyguardLocked: Boolean,
    ): ExportDocument {
        val loaded = loadSources()
        val computation = computeDay(date, zone, nowMillis, live)
        return ExportDocument(
            exportedAtMillis = nowMillis,
            zoneId = zone.id,
            manufacturer = manufacturer,
            model = model,
            sdkInt = sdkInt,
            release = release,
            usageAccess = usageAccess,
            listenerEnabled = listenerEnabled,
            listenerConnected = listenerConnected,
            audioActive = audioActive,
            screenInteractive = screenInteractive,
            keyguardLocked = keyguardLocked,
            lastConfirmationMillis = loaded.sessions.maxOfOrNull { it.lastConfirmedWallMillis },
            trustedUntilMillis = loaded.trustedUntilMillis,
            lastSyncSuccessMillis = loaded.lastSyncSuccessMillis,
            lastSyncError = loaded.lastSyncError,
            stats = computation.stats,
            sessions = loaded.sessions,
            transitions = loaded.transitions,
            screenEvents = loaded.screenEvents,
            diagnostics = dao.latestDiagnostics(ObservationPolicy.DIAGNOSTIC_LIMIT).map { row ->
                ExportDiagnostic(row.wallMillis, row.kind, row.message)
            },
        )
    }

    private suspend fun loadSources(): LoadedSources {
        val sync = dao.syncState()
        val sessions = dao.allSessions().map { it.toRecord() }
        val transitions = dao.allTransitions().map { it.toRecord() }
        val events = dao.allScreenEvents().mapNotNull { it.toEvent() }
        return LoadedSources(
            screenEvents = events,
            sessions = sessions,
            transitions = transitions,
            trustedUntilMillis = sync?.trustedUntilMillis,
            lastSyncSuccessMillis = sync?.lastSuccessWallMillis,
            lastSyncError = sync?.lastError,
        )
    }

    private data class LoadedSources(
        val screenEvents: List<ScreenEvent>,
        val sessions: List<SessionRecord>,
        val transitions: List<AudioTransitionRecord>,
        val trustedUntilMillis: Long?,
        val lastSyncSuccessMillis: Long?,
        val lastSyncError: String?,
    )
}

private fun AudioSettingsEntity.toSelection(): AudioCategorySelection = AudioCategorySelection(
    media = media,
    game = game,
    assistant = assistant,
    navigation = navigation,
    accessibility = accessibility,
)

private fun ScreenEventEntity.toEvent(): ScreenEvent? {
    val type = ScreenEventType.fromCode(eventType) ?: return null
    return ScreenEvent(timestampMillis, type, ordinal)
}

private fun MonitoringSessionEntity.toRecord(): SessionRecord = SessionRecord(
    sessionId = id,
    bootCount = bootCount,
    connectedWallMillis = connectedWallMillis,
    connectedElapsedMillis = connectedElapsedMillis,
    lastConfirmedWallMillis = lastConfirmedWallMillis,
    lastConfirmedElapsedMillis = lastConfirmedElapsedMillis,
)

private fun AudioTransitionEntity.toRecord(): AudioTransitionRecord = AudioTransitionRecord(
    sessionId = sessionId,
    wallMillis = wallMillis,
    elapsedMillis = elapsedMillis,
    bootCount = bootCount,
    active = active,
)
