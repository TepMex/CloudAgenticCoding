package com.tepmex.inbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureActionsTest {
    @Test
    fun blankAndWhitespaceAreRejected() {
        assertNull(CaptureActions.newCapture("", 1))
        assertNull(CaptureActions.newCapture(" \n\t ", 1))
    }

    @Test
    fun captureTrimsAndStartsInInbox() {
        val item = CaptureActions.newCapture("  hello\nworld  ", 10)!!
        assertEquals("hello\nworld", item.text)
        assertEquals(Place.INBOX, item.place)
        assertEquals(10L, item.createdAtMillis)
        assertNull(item.processedAtMillis)
        assertNull(item.reminderAtMillis)
        assertNull(item.notifiedAtMillis)
    }

    @Test
    fun processedMovesToArchive() {
        val item = sample(place = Place.INBOX)
        val done = CaptureActions.markProcessed(item, 50)!!
        assertEquals(Place.ARCHIVE, done.place)
        assertEquals(50L, done.processedAtMillis)
        assertEquals(ArchiveStampKind.Processed, CaptureActions.archiveStamp(done)!!.kind)
        assertNull(CaptureActions.markProcessed(done, 60))
    }

    @Test
    fun scheduleRequiresFutureInboxItem() {
        val item = sample(place = Place.INBOX)
        assertNull(CaptureActions.schedule(item, atMillis = 10, now = 10))
        assertNull(CaptureActions.schedule(item, atMillis = 9, now = 10))
        val scheduled = CaptureActions.schedule(item, atMillis = 80, now = 10)!!
        assertEquals(Place.SCHEDULED, scheduled.place)
        assertEquals(80L, scheduled.reminderAtMillis)
        assertNull(CaptureActions.schedule(scheduled, atMillis = 90, now = 10))
    }

    @Test
    fun cancelReturnsToInbox() {
        val scheduled = sample(place = Place.SCHEDULED, reminderAt = 80)
        val back = CaptureActions.cancelReminder(scheduled)!!
        assertEquals(Place.INBOX, back.place)
        assertNull(back.reminderAtMillis)
        assertNull(CaptureActions.cancelReminder(back))
    }

    @Test
    fun fireArchivesOnceWithDeliveryTime() {
        val scheduled = sample(place = Place.SCHEDULED, reminderAt = 80)
        val fired = CaptureActions.fire(scheduled, now = 95)!!
        assertEquals(Place.ARCHIVE, fired.place)
        assertEquals(95L, fired.notifiedAtMillis)
        assertEquals(80L, fired.reminderAtMillis)
        assertEquals(ArchiveStampKind.Notified, CaptureActions.archiveStamp(fired)!!.kind)
        assertEquals(95L, CaptureActions.archiveStamp(fired)!!.atMillis)
        assertNull(CaptureActions.fire(fired, now = 100))
        assertNull(CaptureActions.fire(sample(place = Place.INBOX), now = 100))
    }

    @Test
    fun sortsMatchTheScreens() {
        val older = sample(id = 1, created = 10)
        val newer = sample(id = 2, created = 20)
        assertEquals(listOf(2L, 1L), CaptureActions.sortInbox(listOf(older, newer)).map { it.id })

        val later = sample(id = 3, place = Place.SCHEDULED, reminderAt = 500)
        val sooner = sample(id = 4, place = Place.SCHEDULED, reminderAt = 100)
        assertEquals(listOf(4L, 3L), CaptureActions.sortReminders(listOf(later, sooner)).map { it.id })

        val processed = CaptureActions.markProcessed(sample(id = 5, created = 1), now = 40)!!
        val notified = CaptureActions.fire(
            sample(id = 6, place = Place.SCHEDULED, reminderAt = 10),
            now = 90,
        )!!
        assertEquals(listOf(6L, 5L), CaptureActions.sortArchive(listOf(processed, notified)).map { it.id })
        assertNull(CaptureActions.archiveStamp(sample(place = Place.INBOX)))
    }

    @Test
    fun centerAndTabsFollowTheMenu() {
        assertEquals(CenterAction.SendDisabled, centerAction(Tab.Capture, "  "))
        assertEquals(CenterAction.Send, centerAction(Tab.Capture, "a"))
        assertEquals(CenterAction.OpenCapture, centerAction(Tab.Inbox, "a"))
        assertEquals(CenterAction.OpenCapture, centerAction(Tab.Archive, ""))

        assertEquals(Tab.Capture, tabForRoute(null))
        assertEquals(Tab.Capture, tabForRoute(Routes.CAPTURE))
        assertEquals(Tab.Inbox, tabForRoute(Routes.INBOX))
        assertEquals(Tab.Inbox, tabForRoute(Routes.PROCESS))
        assertEquals(Tab.Inbox, tabForRoute("process/4"))
        assertEquals(Tab.Reminders, tabForRoute(Routes.REMINDERS))
        assertEquals(Tab.Archive, tabForRoute(Routes.ARCHIVE))
        assertEquals(Tab.Settings, tabForRoute(Routes.SETTINGS))
    }

    private fun sample(
        id: Long = 1,
        created: Long = 10,
        place: Place = Place.INBOX,
        reminderAt: Long? = null,
    ) = Capture(
        id = id,
        text = "note",
        createdAtMillis = created,
        place = place,
        processedAtMillis = null,
        reminderAtMillis = reminderAt,
        notifiedAtMillis = null,
    )
}
