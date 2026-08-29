package com.tepmex.ankientertainer.data.hanzi

import com.tepmex.ankientertainer.ui.TextChunk
import com.tepmex.ankientertainer.ui.mergeSessionChunks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCompositionTest {

    @Test
    fun formatsGreedyPictophoneticBeforeAnyStoryText() {
        val meta = meta(
            "清",
            greedyComponents = listOf("氵", "丰", "月"),
            isPhoneticSemantic = true,
            greedyPhonetic = "青",
        )
        val text = HanziMetadataFormatter.formatCompositionCard("清", meta)
        assertEquals(
            """
            清
            Composition: 氵 + 丰 + 月
            Phonetic-semantic: yes
            Phonetic: 青
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun formatsGreedyNonPictophoneticWithoutPhoneticLine() {
        val meta = meta(
            "休",
            etymologyType = "pictophonetic",
            phonetic = "木",
            greedyComponents = listOf("人", "木"),
            isPhoneticSemantic = false,
        )
        val text = HanziMetadataFormatter.formatCompositionCard("休", meta)
        assertEquals(
            """
            休
            Composition: 人 + 木
            Phonetic-semantic: no
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun conservativeDatasetCanRejectClassicalPictophonetic() {
        val meta = meta(
            "吗",
            etymologyType = "pictophonetic",
            semantic = "口",
            phonetic = "马",
            greedyComponents = listOf("口", "马"),
            isPhoneticSemantic = false,
        )
        val text = HanziMetadataFormatter.formatCompositionCard("吗", meta)!!
        assertTrue(text.contains("Phonetic-semantic: no"))
        assertTrue(!text.contains("Phonetic:"))
    }

    @Test
    fun phoneticMayDifferFromVisibleComponents() {
        val meta = meta(
            "亿",
            greedyComponents = listOf("人", "乙"),
            isPhoneticSemantic = true,
            greedyPhonetic = "意",
        )
        val text = HanziMetadataFormatter.formatCompositionCard("亿", meta)!!
        assertTrue(text.contains("Composition: 人 + 乙"))
        assertTrue(text.contains("Phonetic: 意"))
    }

    @Test
    fun fallsBackToMmahWhenGreedyRowMissing() {
        val meta = meta(
            "清",
            etymologyType = "pictophonetic",
            phonetic = "青",
            decomposition = "⿰氵青",
        )
        val text = HanziMetadataFormatter.formatCompositionCard("清", meta)
        assertEquals(
            """
            清
            Composition: ⿰氵青
            Phonetic-semantic: yes
            Phonetic: 青
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun returnsNullWhenNoCompositionData() {
        assertNull(HanziMetadataFormatter.formatCompositionCard("休", meta("休")))
    }

    @Test
    fun loaderEmitsOneCardPerUniqueHanInFirstOccurrenceOrder() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            mapOf(
                "你" to meta("你", greedyComponents = listOf("人", "尔"), isPhoneticSemantic = false),
                "好" to meta(
                    "好",
                    greedyComponents = listOf("女", "子"),
                    isPhoneticSemantic = false,
                ),
            ),
        )
        val cards = CharacterCompositionLoader(repo).loadCards("你好好!")
        assertEquals(listOf("你", "好"), cards.map { it.character })
        assertTrue(cards[0].text.startsWith("你\n"))
        assertTrue(cards[1].text.contains("Composition: 女 + 子"))
        assertEquals(listOf("你", "好"), repo.lastLoadedCharacters)
    }

    @Test
    fun loaderSkipsCharactersWithoutCompositionData() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            mapOf("好" to meta("好", greedyComponents = listOf("女", "子"), isPhoneticSemantic = false)),
        )
        val cards = CharacterCompositionLoader(repo).loadCards("A好B")
        assertEquals(listOf("好"), cards.map { it.character })
    }

    @Test
    fun loaderReturnsEmptyWhenDatasetUnavailable() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            data = mapOf("休" to meta("休", greedyComponents = listOf("人", "木"))),
            available = false,
        )
        assertTrue(CharacterCompositionLoader(repo).loadCards("休").isEmpty())
    }

    @Test
    fun sessionPutsCompositionAheadOfLikedAndStories() {
        val composition = listOf(chunk("c", "composition"))
        val posters = listOf(chunk("p", "poster"))
        val liked = listOf(chunk("l", "liked", liked = true))
        val stories = listOf(chunk("s", "story"))
        val merged = mergeSessionChunks(composition, posters, liked, stories)
        assertEquals(listOf("c", "p", "l", "s"), merged.map { it.id })
        assertEquals("composition", merged.first().text)
        assertEquals("story", merged.last().text)
    }

    private fun chunk(id: String, text: String, liked: Boolean = false) = TextChunk(
        id = id,
        text = text,
        isLiked = liked,
        modelName = "test",
    )
}
