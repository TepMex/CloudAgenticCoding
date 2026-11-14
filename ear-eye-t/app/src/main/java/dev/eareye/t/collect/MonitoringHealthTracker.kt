package dev.eareye.t.collect

import dev.eareye.t.data.EventRepository
import dev.eareye.t.data.db.AudioTransitionEntity
import dev.eareye.t.data.db.MonitoringSessionEntity
import dev.eareye.t.domain.AudioAggregateReducer
import dev.eareye.t.domain.ClockSample
import dev.eareye.t.domain.SessionEndReason
import dev.eareye.t.domain.describeUsages
import dev.eareye.t.domain.observationSplit
import dev.eareye.t.domain.qualifyingUsageCount
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MonitoringHealthTracker(
    private val repository: EventRepository,
    private val device: DeviceStatus,
    private val categories: CategoryCache,
) {
    private val mutex = Mutex()
    private val reducer = AudioAggregateReducer()
    private var session: MonitoringSessionEntity? = null

    suspend fun closeDanglingUnexpected() = mutex.withLock {
        closeDanglingLocked()
    }

    suspend fun onListenerConnected() = mutex.withLock {
        closeDanglingLocked()
        val sample = device.sample()
        val created = openSession(sample)
        reducer.reset()
        MonitoringRuntime.update {
            it.copy(listenerConnected = true, audioActive = false, sessionId = created.id)
        }
        note(
            "listener_connected",
            "Мониторинг аудио подключён.",
            sample.wallMillis,
        )
    }

    suspend fun onCleanDisconnect() = mutex.withLock {
        val current = session
        if (current == null) {
            MonitoringRuntime.update { it.copy(listenerConnected = false, audioActive = false, sessionId = null) }
            return@withLock
        }
        val sample = device.sample()
        val split = observationSplit(current.toSample(), sample)
        if (split == null) {
            repository.updateSession(
                current.copy(
                    lastConfirmedWallMillis = sample.wallMillis,
                    lastConfirmedElapsedMillis = sample.elapsedMillis,
                    endReason = SessionEndReason.CLEAN.name,
                    unexpected = false,
                ),
            )
            note("listener_disconnected", "Мониторинг аудио отключён.", sample.wallMillis)
        } else {
            closeAtLastConfirmation(current, split)
        }
        session = null
        reducer.reset()
        MonitoringRuntime.update { it.copy(listenerConnected = false, audioActive = false, sessionId = null) }
    }

    /** @return true when the caller must read a fresh playback snapshot. */
    suspend fun onHeartbeat(): Boolean = mutex.withLock {
        val current = session ?: return@withLock false
        val sample = device.sample()
        val split = observationSplit(current.toSample(), sample)
        if (split != null) return@withLock true
        val touched = current.copy(
            lastConfirmedWallMillis = sample.wallMillis,
            lastConfirmedElapsedMillis = sample.elapsedMillis,
            lastHeartbeatWallMillis = sample.wallMillis,
        )
        repository.updateSession(touched)
        session = touched
        false
    }

    suspend fun onPlaybackCount(usages: List<Int>, source: String) = mutex.withLock {
        val current = session ?: return@withLock
        val sample = device.sample()
        val split = observationSplit(current.toSample(), sample)
        val activeSession = if (split != null) {
            closeAtLastConfirmation(current, split)
            reducer.reset()
            val opened = openSession(sample).copy(
                lastCallbackWallMillis = if (source == SOURCE_CALLBACK) sample.wallMillis else null,
                lastSnapshotWallMillis = if (source == SOURCE_SNAPSHOT) sample.wallMillis else null,
            )
            repository.updateSession(opened)
            session = opened
            opened
        } else {
            val touched = current.copy(
                lastConfirmedWallMillis = sample.wallMillis,
                lastConfirmedElapsedMillis = sample.elapsedMillis,
                lastCallbackWallMillis = if (source == SOURCE_CALLBACK) sample.wallMillis else current.lastCallbackWallMillis,
                lastSnapshotWallMillis = if (source == SOURCE_SNAPSHOT) sample.wallMillis else current.lastSnapshotWallMillis,
            )
            repository.updateSession(touched)
            session = touched
            touched
        }
        val selection = categories.selection
        val count = qualifyingUsageCount(usages, selection)
        val change = reducer.onCount(count)
        if (change != null) {
            repository.insertTransition(
                AudioTransitionEntity(
                    sessionId = activeSession.id,
                    wallMillis = sample.wallMillis,
                    elapsedMillis = sample.elapsedMillis,
                    bootCount = sample.bootCount,
                    active = change,
                ),
            )
            val message = if (change) {
                "Воспроизведение подходящей категории началось. ${describeUsages(usages, selection)}"
            } else {
                "Воспроизведение подходящей категории закончилось. ${describeUsages(usages, selection)}"
            }
            note(if (change) "audio_active" else "audio_inactive", message, sample.wallMillis)
        } else if (source == SOURCE_SNAPSHOT) {
            note(
                "audio_snapshot",
                "Снимок аудио. ${describeUsages(usages, selection)}",
                sample.wallMillis,
            )
        }
        MonitoringRuntime.update {
            it.copy(
                listenerConnected = true,
                audioActive = reducer.isActive(),
                sessionId = session?.id,
            )
        }
    }

    suspend fun onObservationFailure(reason: String) = mutex.withLock {
        note(
            "audio_read_failed",
            "Не удалось прочитать активные конфигурации аудио ($reason). Состояние не изменено.",
            device.sample().wallMillis,
        )
    }

    private suspend fun closeDanglingLocked() {
        val open = repository.openSessions()
        if (open.isEmpty()) return
        val wall = device.sample().wallMillis
        for (row in open) {
            repository.updateSession(
                row.copy(
                    endReason = SessionEndReason.UNEXPECTED.name,
                    unexpected = true,
                ),
            )
        }
        session = null
        reducer.reset()
        note(
            "listener_unexpected",
            "Сессия мониторинга прервана. Время после последнего подтверждения неизвестно и не считается тишиной.",
            wall,
        )
    }

    private suspend fun closeAtLastConfirmation(current: MonitoringSessionEntity, reason: SessionEndReason) {
        repository.updateSession(
            current.copy(
                endReason = reason.name,
                unexpected = reason != SessionEndReason.CLOCK && reason != SessionEndReason.CLEAN,
            ),
        )
        val message = when (reason) {
            SessionEndReason.GAP ->
                "Большой промежуток без подтверждения мониторинга. Промежуток неизвестен и не считается тишиной."
            SessionEndReason.CLOCK ->
                "Обнаружено изменение системных часов. Сессия наблюдения разделена, состояние аудио будет считано заново."
            SessionEndReason.REBOOT ->
                "Обнаружена другая загрузка устройства. Старое наблюдение не продлевается."
            SessionEndReason.UNEXPECTED ->
                "Сессия мониторинга прервана. Время после последнего подтверждения неизвестно."
            SessionEndReason.CLEAN -> "Мониторинг аудио отключён."
        }
        note(reason.name.lowercase(), message, current.lastConfirmedWallMillis)
    }

    private suspend fun openSession(sample: ClockSample): MonitoringSessionEntity {
        val draft = MonitoringSessionEntity(
            bootCount = sample.bootCount,
            connectedWallMillis = sample.wallMillis,
            connectedElapsedMillis = sample.elapsedMillis,
            lastConfirmedWallMillis = sample.wallMillis,
            lastConfirmedElapsedMillis = sample.elapsedMillis,
            lastCallbackWallMillis = null,
            lastSnapshotWallMillis = null,
            lastHeartbeatWallMillis = null,
            endReason = null,
            unexpected = false,
        )
        val id = repository.insertSession(draft)
        val created = draft.copy(id = id)
        session = created
        return created
    }

    private suspend fun note(kind: String, message: String, wallMillis: Long) {
        repository.addDiagnostic(kind, message, wallMillis, bump = false)
    }
}

private fun MonitoringSessionEntity.toSample(): ClockSample = ClockSample(
    wallMillis = lastConfirmedWallMillis,
    elapsedMillis = lastConfirmedElapsedMillis,
    bootCount = bootCount,
)

const val SOURCE_CALLBACK: String = "callback"
const val SOURCE_SNAPSHOT: String = "snapshot"
