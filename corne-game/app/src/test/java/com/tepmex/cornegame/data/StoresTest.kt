package com.tepmex.cornegame.data

import com.tepmex.cornegame.domain.Language
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoresTest {
    @Test
    fun settingsRoundTripAndBrokenFile() {
        val dir = tempDir()
        val file = File(dir, "settings.json")
        val store = FileSettingsStore(file)
        assertEquals(AppSettings(), store.load())
        store.save(
            AppSettings(
                highlight = false,
                fingerColors = true,
                vibrate = false,
                keyScale = 0.8f,
                language = Language.RU,
            ),
        )
        assertEquals(Language.RU, FileSettingsStore(file).load().language)
        assertEquals(0.8f, FileSettingsStore(file).load().keyScale, 0.001f)
        file.writeText("""{"keyScale":9,"language":"NOPE"}""")
        val recovered = FileSettingsStore(file).load()
        assertEquals(1f, recovered.keyScale, 0.001f)
        assertEquals(Language.EN, recovered.language)
        file.writeText("not-json")
        assertEquals(AppSettings(), FileSettingsStore(file).load())
    }

    @Test
    fun historyKeepsTheLatestTwoHundred() {
        val file = File(tempDir(), "sessions.json")
        val store = FileSessionHistory(file)
        repeat(205) { index ->
            store.append(
                SessionRecord(
                    finishedAtEpochMs = index.toLong(),
                    language = if (index % 2 == 0) Language.EN else Language.RU,
                    wpm = index.toDouble(),
                    accuracy = 90.0,
                    errors = 1,
                    durationMs = 1_000,
                ),
            )
        }
        val loaded = store.load()
        assertEquals(200, loaded.size)
        assertEquals(5L, loaded.first().finishedAtEpochMs)
        assertEquals(204L, loaded.last().finishedAtEpochMs)
        assertEquals(204.0, loaded.maxOf { it.wpm }, 0.001)
        file.writeText("{")
        assertTrue(store.load().isEmpty())
    }

    private fun tempDir(): File = File.createTempFile("corne", "dir").apply {
        delete()
        mkdirs()
    }
}
