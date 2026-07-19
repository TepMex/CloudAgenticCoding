package com.tepmex.ankientertainer.data.hanzi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateEngineTest {

    private fun engine(repo: HanziMetadataRepository = FakeHanziMetadataRepository()) =
        DefaultPromptTemplateEngine(repo)

    @Test
    fun queryOnlyBackwardCompatible() = runBlocking {
        val result = engine().expand("Word: {QUERY}", "你好")
        assertEquals("Word: 你好", result.prompt)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun everyPlaceholderAndRepeatedReuse() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            mapOf(
                "清" to meta(
                    "清",
                    etymologyType = "pictophonetic",
                    semantic = "氵",
                    phonetic = "青",
                    opposites = listOf(OppositeTarget("清", "s2t", false, "unihan", true)),
                    simplification = SimplificationInfo(
                        "清", "清", "UNCHANGED",
                        "清 → 清\nNo standard simplified/traditional difference found.",
                        "derived", 1.0,
                    ),
                    mnemonics = listOf(
                        MnemonicInfo("clear water", 10.0, 1, "seed", "1"),
                    ),
                ),
            ),
        )
        val template = """
            Q={QUERY}
            O={OPPOSITE}
            O2={OPPOSITE}
            S={SEMANTIC}
            P={PHONETIC}
            M={MNEMO_EXAMPLES}
            H={SIMPL_HISTORY}
        """.trimIndent()
        val result = engine(repo).expand(template, "清")
        assertTrue(result.prompt.contains("Q=清"))
        assertEquals(1, repo.loadCount)
        val firstO = result.prompt.substringAfter("O=").substringBefore("\nO2=")
        val secondO = result.prompt.substringAfter("O2=").substringBefore("\nS=")
        assertEquals(firstO, secondO)
        assertTrue(result.prompt.contains("semantic component 氵"))
        assertTrue(result.prompt.contains("phonetic component 青"))
        assertTrue(result.prompt.contains("1. clear water"))
    }

    @Test
    fun emptyMetadataPlaceholders() = runBlocking {
        val result = engine(FakeHanziMetadataRepository(emptyMap())).expand(
            "{OPPOSITE}|{SEMANTIC}|{PHONETIC}|{MNEMO_EXAMPLES}",
            "xyz",
        )
        assertEquals("|||", result.prompt.replace("\n", ""))
    }

    @Test
    fun unknownPlaceholdersRemainWithWarning() = runBlocking {
        val result = engine().expand("A {QUERY} B {FOO} C {BAR}", "x")
        assertEquals("A x B {FOO} C {BAR}", result.prompt)
        assertTrue(result.warnings.any { it.contains("{FOO}") && it.contains("{BAR}") })
    }

    @Test
    fun literalBracesUnmatchedLeftAlone() = runBlocking {
        val result = engine().expand("keep { alone and {QUERY}", "q")
        assertEquals("keep { alone and q", result.prompt)
    }

    @Test
    fun excessBlankLineNormalization() = runBlocking {
        val result = engine(FakeHanziMetadataRepository(emptyMap())).expand(
            "A\n\n\n{SEMANTIC}\n\n\nB",
            "清",
        )
        assertFalse(result.prompt.contains("\n\n\n"))
        assertTrue(result.prompt.contains("A\n\nB") || result.prompt == "A\n\nB")
    }

    @Test
    fun punctuationLatinAndRepeatedHanzi() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            mapOf(
                "好" to meta("好", opposites = emptyList()),
                "说" to meta(
                    "说",
                    opposites = listOf(
                        OppositeTarget("說", "s2t", true, "unihan", true),
                        OppositeTarget("説", "s2t", true, "unihan", true),
                    ),
                ),
            ),
        )
        val result = engine(repo).expand("{QUERY}\n{OPPOSITE}", "hello 好好说!")
        assertTrue(result.prompt.startsWith("hello 好好说!"))
        val opposite = result.prompt.substringAfter("\n")
        assertTrue(opposite.indexOf("好") < opposite.indexOf("说"))
        assertEquals(1, opposite.lines().count { it.startsWith("好") })
    }

    @Test
    fun supplementaryPlaneHanInQuery() = runBlocking {
        val rare = String(Character.toChars(0x20000))
        val repo = FakeHanziMetadataRepository(
            mapOf(rare to meta(rare, opposites = emptyList())),
        )
        val result = engine(repo).expand("{QUERY}|{OPPOSITE}", rare)
        assertTrue(result.prompt.startsWith("$rare|"))
        assertTrue(result.prompt.contains("$rare → $rare (same in both)"))
    }

    @Test
    fun metadataTruncationLine() = runBlocking {
        val chars = (0 until 25).map { Character.toString(0x4E00 + it) }
        val data = chars.associateWith { meta(it, opposites = emptyList()) }
        val result = engine(FakeHanziMetadataRepository(data)).expand(
            "{OPPOSITE}",
            chars.joinToString(""),
        )
        assertTrue(result.prompt.contains(HanziMetadataFormatter.TRUNCATION_LINE))
        assertEquals(20, result.prompt.lines().count { it.contains("→") })
    }

    @Test
    fun queryStillWorksWhenMetadataUnavailable() = runBlocking {
        val repo = FakeHanziMetadataRepository(
            available = false,
            statusMessage = "DB missing",
        )
        val result = engine(repo).expand("Q={QUERY}\nO={OPPOSITE}\nS={SEMANTIC}", "你好")
        assertTrue(result.prompt.contains("Q=你好"))
        assertEquals("", result.prompt.substringAfter("O=").substringBefore("\n"))
        assertTrue(result.warnings.any { it.contains("DB missing") })
    }

    @Test
    fun doesNotFetchWhenOnlyQueryPresent() = runBlocking {
        val repo = FakeHanziMetadataRepository(mapOf("好" to meta("好")))
        engine(repo).expand("only {QUERY}", "好")
        assertEquals(0, repo.loadCount)
    }
}
