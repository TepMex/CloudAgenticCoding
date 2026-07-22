package com.tepmex.ankientertainer.data.hanzi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMnemonicFallbackTest {

    @Test
    fun returnsUpToFiveStoriesInCharacterThenScoreOrder() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            data = mapOf(
                "休" to meta(
                    "休",
                    mnemonics = listOf(
                        mnemo("休 high", 100.0),
                        mnemo("休 mid", 80.0),
                        mnemo("休 low", 60.0),
                    ),
                ),
                "明" to meta(
                    "明",
                    mnemonics = listOf(
                        mnemo("明 high", 90.0),
                        mnemo("明 mid", 70.0),
                        mnemo("明 low", 50.0),
                    ),
                ),
            ),
        )
        val fallback = OfflineMnemonicFallback(repo)

        val stories = fallback.loadStories("休明")

        assertEquals(5, stories.size)
        assertEquals(
            listOf(
                // Contiguous compound run "休明" has no stories; then per-char.
                "休 — 休 high",
                "休 — 休 mid",
                "休 — 休 low",
                "明 — 明 high",
                "明 — 明 mid",
            ),
            stories.map { it.text },
        )
        assertEquals(listOf("休", "休", "休", "明", "明"), stories.map { it.character })
    }

    @Test
    fun prefersCompoundWordMnemonicsBeforePerCharacter() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            data = mapOf(
                "休息" to meta(
                    "休息",
                    mnemonics = listOf(
                        mnemo("compound high", 100.0),
                        mnemo("compound mid", 70.0),
                    ),
                ),
                "休" to meta(
                    "休",
                    mnemonics = listOf(
                        mnemo("char high", 90.0),
                        mnemo("char mid", 50.0),
                    ),
                ),
                "息" to meta("息", mnemonics = emptyList()),
            ),
        )
        val stories = OfflineMnemonicFallback(repo).loadStories("休息")
        assertEquals(
            listOf(
                "休息 — compound high",
                "休息 — compound mid",
                "休 — char high",
                "休 — char mid",
            ),
            stories.map { it.text },
        )
    }

    @Test
    fun compoundQueryUsesPerCharacterWhenNoCompoundEntry() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            data = mapOf(
                "休" to meta("休", mnemonics = listOf(mnemo("rest", 100.0))),
            ),
        )
        val stories = OfflineMnemonicFallback(repo).loadStories("休息")
        assertEquals(listOf("休 — rest"), stories.map { it.text })
    }

    @Test
    fun returnsFewerThanFiveWhenDatabaseHasFewer() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            data = mapOf(
                "休" to meta("休", mnemonics = listOf(mnemo("only one", 100.0))),
            ),
        )
        val stories = OfflineMnemonicFallback(repo).loadStories("休")
        assertEquals(1, stories.size)
        assertEquals("休 — only one", stories[0].text)
    }

    @Test
    fun returnsEmptyWhenNoHanCharacters() = runBlocking {
        val repo = FakeHanziMetadataRepository(data = emptyMap())
        val stories = OfflineMnemonicFallback(repo).loadStories("hello")
        assertTrue(stories.isEmpty())
    }

    @Test
    fun returnsEmptyWhenDatasetUnavailable() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            data = mapOf("休" to meta("休", mnemonics = listOf(mnemo("x", 1.0)))),
            available = false,
            statusMessage = "db missing",
        )
        val stories = OfflineMnemonicFallback(repo).loadStories("休")
        assertTrue(stories.isEmpty())
    }

    @Test
    fun skipsCharactersWithoutMnemonics() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            data = mapOf(
                "无" to meta("无", mnemonics = emptyList()),
                "休" to meta("休", mnemonics = listOf(mnemo("rest", 100.0))),
            ),
        )
        val stories = OfflineMnemonicFallback(repo).loadStories("无休")
        assertEquals(listOf("休 — rest"), stories.map { it.text })
    }

    private fun mnemo(story: String, score: Double) = MnemonicInfo(
        story = story,
        normalizedScore = score,
        sourcePriority = 1,
        source = "seed",
        sourceRecordId = story,
    )
}
