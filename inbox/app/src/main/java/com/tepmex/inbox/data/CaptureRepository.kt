package com.tepmex.inbox.data

import com.tepmex.inbox.domain.Capture
import com.tepmex.inbox.domain.CaptureActions
import com.tepmex.inbox.domain.CaptureCounts
import com.tepmex.inbox.domain.Place
import com.tepmex.inbox.domain.ScheduleAttempt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CaptureRepository(private val dao: CaptureDao) {
    fun observeInbox(): Flow<List<Capture>> = observe(Place.INBOX, CaptureActions::sortInbox)

    fun observeReminders(): Flow<List<Capture>> = observe(Place.SCHEDULED, CaptureActions::sortReminders)

    fun observeArchive(): Flow<List<Capture>> = observe(Place.ARCHIVE, CaptureActions::sortArchive)

    fun observeCounts(): Flow<CaptureCounts> = combine(
        observeInbox(),
        observeReminders(),
        observeArchive(),
    ) { inbox, reminders, archive ->
        CaptureCounts(inbox.size, reminders.size, archive.size)
    }

    suspend fun insert(capture: Capture) {
        dao.insert(capture.toEntity().copy(id = 0))
    }

    suspend fun markProcessed(id: Long, now: Long) {
        val current = dao.get(id)?.toDomain() ?: return
        val next = CaptureActions.markProcessed(current, now) ?: return
        dao.update(next.toEntity())
    }

    suspend fun schedule(id: Long, atMillis: Long, now: Long): ScheduleAttempt {
        val current = dao.get(id)?.toDomain() ?: return ScheduleAttempt.Missing
        val next = CaptureActions.schedule(current, atMillis, now)
            ?: return if (atMillis <= now) ScheduleAttempt.Past else ScheduleAttempt.Missing
        dao.update(next.toEntity())
        return ScheduleAttempt.Scheduled(next)
    }

    suspend fun cancelReminder(id: Long) {
        val current = dao.get(id)?.toDomain() ?: return
        val next = CaptureActions.cancelReminder(current) ?: return
        dao.update(next.toEntity())
    }

    suspend fun fire(id: Long, now: Long): Capture? {
        val current = dao.get(id)?.toDomain() ?: return null
        val next = CaptureActions.fire(current, now) ?: return null
        dao.update(next.toEntity())
        return next
    }

    suspend fun pendingReminders(): List<Capture> =
        dao.list(Place.SCHEDULED.name).map { it.toDomain() }

    suspend fun clearArchive() {
        dao.deletePlace(Place.ARCHIVE.name)
    }

    private fun observe(place: Place, sort: (List<Capture>) -> List<Capture>): Flow<List<Capture>> =
        dao.observe(place.name).map { rows -> sort(rows.map { it.toDomain() }) }
}
