package com.tepmex.inbox.domain

enum class Place {
    INBOX,
    SCHEDULED,
    ARCHIVE,
}

data class Capture(
    val id: Long,
    val text: String,
    val createdAtMillis: Long,
    val place: Place,
    val processedAtMillis: Long?,
    val reminderAtMillis: Long?,
    val notifiedAtMillis: Long?,
)

enum class ArchiveStampKind {
    Processed,
    Notified,
}

data class ArchiveStamp(
    val kind: ArchiveStampKind,
    val atMillis: Long,
)

data class CaptureCounts(
    val inbox: Int,
    val reminders: Int,
    val archive: Int,
)

sealed interface ScheduleAttempt {
    data class Scheduled(val capture: Capture) : ScheduleAttempt
    data object Past : ScheduleAttempt
    data object Missing : ScheduleAttempt
}

object CaptureActions {
    fun newCapture(text: String, now: Long): Capture? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return Capture(
            id = 0,
            text = trimmed,
            createdAtMillis = now,
            place = Place.INBOX,
            processedAtMillis = null,
            reminderAtMillis = null,
            notifiedAtMillis = null,
        )
    }

    fun markProcessed(item: Capture, now: Long): Capture? {
        if (item.place != Place.INBOX) return null
        return item.copy(place = Place.ARCHIVE, processedAtMillis = now)
    }

    fun schedule(item: Capture, atMillis: Long, now: Long): Capture? {
        if (atMillis <= now) return null
        if (item.place != Place.INBOX) return null
        return item.copy(
            place = Place.SCHEDULED,
            reminderAtMillis = atMillis,
            processedAtMillis = null,
            notifiedAtMillis = null,
        )
    }

    fun cancelReminder(item: Capture): Capture? {
        if (item.place != Place.SCHEDULED) return null
        return item.copy(place = Place.INBOX, reminderAtMillis = null)
    }

    fun fire(item: Capture, now: Long): Capture? {
        if (item.place != Place.SCHEDULED) return null
        return item.copy(place = Place.ARCHIVE, notifiedAtMillis = now)
    }

    fun sortInbox(items: List<Capture>): List<Capture> =
        items.sortedWith(compareByDescending<Capture> { it.createdAtMillis }.thenByDescending { it.id })

    fun sortReminders(items: List<Capture>): List<Capture> =
        items.sortedWith(compareBy<Capture> { it.reminderAtMillis ?: Long.MAX_VALUE }.thenBy { it.id })

    fun sortArchive(items: List<Capture>): List<Capture> =
        items.sortedWith(compareByDescending<Capture> { closeAt(it) }.thenByDescending { it.id })

    fun closeAt(item: Capture): Long =
        item.processedAtMillis ?: item.notifiedAtMillis ?: item.createdAtMillis

    fun archiveStamp(item: Capture): ArchiveStamp? {
        if (item.place != Place.ARCHIVE) return null
        item.notifiedAtMillis?.let { return ArchiveStamp(ArchiveStampKind.Notified, it) }
        item.processedAtMillis?.let { return ArchiveStamp(ArchiveStampKind.Processed, it) }
        return null
    }
}
