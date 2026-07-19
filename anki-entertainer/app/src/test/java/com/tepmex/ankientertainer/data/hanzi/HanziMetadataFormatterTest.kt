package com.tepmex.ankientertainer.data.hanzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HanziMetadataFormatterTest {

    @Test
    fun oppositeSimplifiedToTraditionalAndSameAndOneToMany() {
        val data = mapOf(
            "说" to meta(
                "说",
                opposites = listOf(
                    OppositeTarget("說", "s2t", true, "unihan", true),
                    OppositeTarget("説", "s2t", true, "unihan", true),
                ),
            ),
            "好" to meta("好", opposites = emptyList()),
            "发" to meta(
                "发",
                opposites = listOf(
                    OppositeTarget("發", "s2t", true, "unihan", true),
                    OppositeTarget("髮", "s2t", true, "unihan", true),
                ),
            ),
        )
        val text = HanziMetadataFormatter.formatOpposite(listOf("说", "好", "发"), data, truncated = false)
        assertTrue(text.contains("说 → 說 / 説 (context-dependent)"))
        assertTrue(text.contains("好 → 好 (same in both)"))
        assertTrue(text.contains("发 → 發 / 髮 (context-dependent)"))
    }

    @Test
    fun oppositeTraditionalToSimplifiedStableOrder() {
        val data = mapOf(
            "說" to meta(
                "說",
                opposites = listOf(
                    OppositeTarget("说", "t2s", false, "unihan", true),
                ),
            ),
        )
        val text = HanziMetadataFormatter.formatOpposite(listOf("說"), data, false)
        assertEquals("說 → 说", text)
    }

    @Test
    fun semanticAndPhoneticOnlyForPictophoneticWithComponents() {
        val data = mapOf(
            "清" to meta("清", etymologyType = "pictophonetic", semantic = "氵", phonetic = "青"),
            "休" to meta("休", etymologyType = "ideographic", semantic = null, phonetic = null),
            "缺义" to meta("缺义", etymologyType = "pictophonetic", semantic = null, phonetic = "X"),
            "缺音" to meta("缺音", etymologyType = "pictophonetic", semantic = "Y", phonetic = null),
        )
        // Use single-codepoint keys in real usage; keep fixture chars simple:
        val ordered = listOf("清", "休")
        val semantic = HanziMetadataFormatter.formatSemantic(ordered, data, false)
        val phonetic = HanziMetadataFormatter.formatPhonetic(ordered, data, false)
        assertEquals("清: semantic component 氵", semantic)
        assertEquals("清: phonetic component 青", phonetic)
        assertFalse(semantic.contains("休"))
        assertFalse(phonetic.contains("休"))
    }

    @Test
    fun semanticOmitsNullComponentEvenIfPictophonetic() {
        val data = mapOf(
            "清" to meta("清", etymologyType = "pictophonetic", semantic = null, phonetic = "青"),
        )
        assertEquals("", HanziMetadataFormatter.formatSemantic(listOf("清"), data, false))
        assertEquals("清: phonetic component 青", HanziMetadataFormatter.formatPhonetic(listOf("清"), data, false))
    }

    @Test
    fun mnemonicsTopFiveRankingAndOmitMissing() {
        val stories = (1..7).map {
            MnemonicInfo("story $it", normalizedScore = it.toDouble(), sourcePriority = 1, source = "s", sourceRecordId = "$it")
        }.reversed() // unsorted input
        val data = mapOf(
            "休" to meta("休", mnemonics = stories.sortedByDescending { it.normalizedScore }),
            "无" to meta("无", mnemonics = emptyList()),
        )
        val text = HanziMetadataFormatter.formatMnemonics(listOf("休", "无"), data, false)
        assertTrue(text.startsWith("休:"))
        assertFalse(text.contains("无"))
        assertEquals(5, text.lines().count { it.matches(Regex("""\d+\. .*""")) })
        assertTrue(text.contains("1. story 7"))
        assertTrue(text.contains("5. story 3"))
        assertFalse(text.contains("story 2"))
    }

    @Test
    fun simplHistoryUsesExplanationAndTruncationLine() {
        val data = mapOf(
            "好" to meta(
                "好",
                simplification = SimplificationInfo(
                    simplifiedCharacter = "好",
                    traditionalCharacter = "好",
                    classification = "UNCHANGED",
                    explanation = "好 → 好\nNo standard simplified/traditional difference found.",
                    evidenceType = "derived",
                    confidence = 1.0,
                ),
            ),
        )
        val text = HanziMetadataFormatter.formatSimplHistory(listOf("好"), data, truncated = true)
        assertTrue(text.contains("No standard simplified/traditional difference found."))
        assertTrue(text.contains(HanziMetadataFormatter.TRUNCATION_LINE))
    }
}
