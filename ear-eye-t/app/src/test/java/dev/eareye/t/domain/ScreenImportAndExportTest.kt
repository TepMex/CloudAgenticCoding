package dev.eareye.t.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scenario 18, plus export escaping so diagnostics cannot break the file. */
class ScreenImportAndExportTest {
    @Test
    fun repeatedUsageImportDoesNotDuplicateEvents() {
        val firstBatch = withStableOrdinals(
            listOf(
                1_000L to ScreenEventType.SCREEN_INTERACTIVE,
                1_000L to ScreenEventType.KEYGUARD_HIDDEN,
                2_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        )
        assertEquals(firstBatch, withStableOrdinals(
            listOf(
                1_000L to ScreenEventType.SCREEN_INTERACTIVE,
                1_000L to ScreenEventType.KEYGUARD_HIDDEN,
                2_000L to ScreenEventType.SCREEN_NON_INTERACTIVE,
            ),
        ))
        assertEquals(0, firstBatch[0].ordinal)
        assertEquals(1, firstBatch[1].ordinal)

        val range = TimeInterval(1_000, 5_000)
        val outside = ScreenEvent(500, ScreenEventType.DEVICE_STARTUP, 0)
        val once = applyScreenImport(listOf(outside), range, firstBatch)
        val twice = applyScreenImport(once, range, firstBatch)
        assertEquals(once, twice)
        assertEquals(4, twice.size)
        assertTrue(twice.contains(outside))

        val replacement = withStableOrdinals(listOf(1_500L to ScreenEventType.SCREEN_INTERACTIVE))
        val replaced = applyScreenImport(twice, range, replacement)
        assertEquals(listOf(outside, replacement.single()), replaced)
    }

    @Test
    fun jsonEscapesQuotesAndNewlines() {
        val document = ExportDocument(
            exportedAtMillis = 10,
            zoneId = "Asia/Omsk",
            manufacturer = "HONOR",
            model = "400 \"test\"",
            sdkInt = 36,
            release = "16",
            usageAccess = true,
            listenerEnabled = false,
            listenerConnected = false,
            audioActive = false,
            screenInteractive = true,
            keyguardLocked = false,
            lastConfirmationMillis = null,
            trustedUntilMillis = 10,
            lastSyncSuccessMillis = null,
            lastSyncError = "line\nnext",
            stats = null,
            sessions = emptyList(),
            transitions = emptyList(),
            screenEvents = emptyList(),
            diagnostics = listOf(ExportDiagnostic(10, "note", "кавычка \" и перевод\nстроки")),
        )
        val json = exportJson(document)
        assertTrue(json.contains("\\\"test\\\""))
        assertTrue(json.contains("line\\nnext"))
        assertFalse(json.contains("line\nnext"))
        val csv = exportCsv(document)
        assertTrue(csv.contains("\"line\nnext\""))
        assertEquals("0 ч 00 мин 03 с", formatDuration(3_000))
        assertEquals("1 ч 02 мин 03 с", formatDuration(3_723_000))
    }
}
