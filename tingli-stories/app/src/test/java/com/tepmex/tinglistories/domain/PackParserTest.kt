package com.tepmex.tinglistories.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class PackParserTest {
    @Test
    fun bundledAssetMatchesMonorepoZip() {
        val asset = File("src/main/assets/hsk4_sample.zip")
        assertTrue(asset.isFile)
        assertEquals(sha256(repoSampleZip()), sha256(asset))
    }

    @Test
    fun sampleZipMatchesHsk4Layout() {
        val file = repoSampleZip()
        ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.filter { !it.endsWith("/") }.toSet()
            val jsonName = names.first { it.endsWith(".json") }
            val text = zip.getInputStream(zip.getEntry(jsonName)).bufferedReader().use { it.readText() }
            val pack = parseCatalog(text, names)
            assertEquals("HSK 4", pack.level)
            assertEquals(100, pack.stories.size)
            val first = pack.stories.first()
            assertEquals(1, first.id)
            assertEquals("换工作", first.title)
            assertEquals(4, first.questions.size)
            assertEquals("李红以前的工作是什么？", first.questions.first().prompt)
            assertEquals("story_001.mp3", first.storyAudioEntry)
            assertEquals("questions_001.mp3", first.questionsAudioEntry)
            assertNull(pack.stories[1].storyAudioEntry)
            assertNull(pack.stories[1].questionsAudioEntry)
            assertEquals(100, pack.stories.last().id)
        }
    }

    @Test
    fun audioReferenceFallsBackToFileNameAndAudioFolder() {
        val names = setOf("story_001.mp3", "notes.txt")
        assertEquals("story_001.mp3", resolveAudio(names, "audio/story_001.mp3"))
        val nested = setOf("audio/story_002.mp3")
        assertEquals("audio/story_002.mp3", resolveAudio(nested, "story_002.mp3"))
        val both = setOf("story_003.mp3", "audio/story_003.mp3")
        assertEquals("audio/story_003.mp3", resolveAudio(both, "audio/story_003.mp3"))
        assertEquals("Story_004.MP3", resolveAudio(setOf("Story_004.MP3"), "audio/story_004.mp3"))
        assertNull(resolveAudio(names, "missing.mp3"))
        assertNull(resolveAudio(setOf("__MACOSX/story_001.mp3"), "story_001.mp3"))
    }

    @Test
    fun macosMetadataJsonIsIgnored() {
        val chosen = selectCatalogName(
            listOf(
                "__MACOSX/._pack.json" to "{not json",
                "readme.json" to """{"hello":1}""",
                "pack/hsk4_stories_100_audio.json" to """{"level":"HSK 4","exercises":[{"id":7,"title":"雨","text":"下雨了。","questions":[{"q":"什么？","a":"雨"}]}]}""",
            ),
        )
        assertEquals("pack/hsk4_stories_100_audio.json", chosen)
    }

    @Test
    fun bareArrayIsACatalog() {
        val pack = parseCatalog(
            """[{"id":"2","name":"猫","passage":"有一只猫。","questions":[{"question":"几只？","answer":"一只"}]}]""",
            emptySet(),
        )
        assertEquals(1, pack.stories.size)
        assertEquals(2, pack.stories.single().id)
        assertEquals("猫", pack.stories.single().title)
        assertEquals("有一只猫。", pack.stories.single().text)
        assertEquals("几只？", pack.stories.single().questions.single().prompt)
        assertFalse(pack.stories.single().stored().hasStoryAudio)
        assertTrue(showTextBeforeAnswer(pack.stories.single().stored()))
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun repoSampleZip(): File {
    val candidates = listOf(
        File("../../hsk4_sample.zip"),
        File("../hsk4_sample.zip"),
        File("hsk4_sample.zip"),
        File("/workspace/hsk4_sample.zip"),
    )
    return candidates.firstOrNull { it.isFile }
        ?: error("hsk4_sample.zip not found from ${File(".").canonicalPath}")
}
