package com.tepmex.tinglistories.data

import com.tepmex.tinglistories.domain.AudioKind
import com.tepmex.tinglistories.domain.Evaluation
import com.tepmex.tinglistories.domain.EvaluationItem
import com.tepmex.tinglistories.domain.repoSampleZip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryStoreTest {
    @Test
    fun sampleImportWritesStoryOneAudioAndSurvivesASecondImport() {
        val root = tempRoot()
        val store = LibraryStore(root)
        val outcome = store.importZip(repoSampleZip())
        assertEquals(100, outcome.storyCount)
        assertEquals(1, outcome.storyAudioCount)
        assertEquals(1, outcome.questionAudioCount)
        assertEquals("HSK 4", outcome.library.level)
        val first = outcome.library.story(1)!!
        val storyAudio = store.audioFile(first, AudioKind.STORY)!!
        val questionAudio = store.audioFile(first, AudioKind.QUESTIONS)!!
        assertTrue(storyAudio.isFile && storyAudio.length() > 1000)
        assertTrue(questionAudio.isFile && questionAudio.length() > 1000)
        assertNull(store.audioFile(outcome.library.story(2)!!, AudioKind.STORY))

        val listened = store.recordListen(1)
        assertEquals(1, listened.progressOf(1).listenCount)
        assertFalse(listened.progressOf(1).completed)
        val grade = Evaluation(
            score = 1,
            max = 4,
            summary = "Один верный.",
            items = listOf(EvaluationItem(1, true, "Да")),
        )
        store.saveAttempt(1, listOf("会计", "", "", ""), grade)
        store.importZip(repoSampleZip())
        val reloaded = LibraryStore(root).load()
        assertEquals(1, reloaded.progressOf(1).listenCount)
        assertEquals("Один верный.", reloaded.progressOf(1).evaluation?.summary)
        assertEquals("会计", reloaded.progressOf(1).answers.first())

        val reset = store.resetAnswer(1)
        assertEquals(1, reset.progressOf(1).listenCount)
        assertFalse(reset.progressOf(1).completed)
        assertTrue(reset.progressOf(1).answers.isEmpty())
        assertEquals("换工作", LibraryStore(root).load().story(1)?.title)
    }

    @Test
    fun changedQuestionsKeepTheListenCountOnly() {
        val root = tempRoot()
        val store = LibraryStore(root)
        store.importZip(pack(question = "以前做什么？"))
        store.recordListen(3)
        store.recordListen(3)
        store.saveAttempt(
            3,
            listOf("会计"),
            Evaluation(1, 1, "Да", emptyList()),
        )
        store.importZip(pack(question = "现在做什么？"))
        val library = store.load()
        assertEquals(2, library.progressOf(3).listenCount)
        assertFalse(library.progressOf(3).completed)
        assertTrue(library.progressOf(3).answers.isEmpty())
        assertEquals("story.mp3", store.audioFile(library.story(3)!!, AudioKind.STORY)?.name)
        assertTrue(store.audioFile(library.story(3)!!, AudioKind.STORY)!!.isFile)
    }

    @Test
    fun chineseRoundTripAndUnknownIdDoesNotListen() {
        val root = tempRoot()
        val store = LibraryStore(root)
        store.importZip(pack(question = "几只猫？"))
        val before = store.recordListen(99)
        assertEquals(0, before.totalListens)
        val again = LibraryJson.decode(File(root, "library.json").readText())
        assertEquals("猫", again.story(3)?.title)
        assertEquals("几只猫？", again.story(3)?.questions?.single()?.prompt)
    }

    private fun pack(question: String): File {
        val json = """
            {"level":"HSK 4","type":"听力","exercises":[{"id":3,"title":"猫","text":"有一只猫。","questions":[{"q":"$question","a":"一只"}],"audio_story":"audio/cat.mp3","audio_questions":"missing.mp3"}]}
        """.trimIndent()
        val file = File.createTempFile("pack", ".zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("stories.json"))
            zip.write(json.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("audio/cat.mp3"))
            zip.write(byteArrayOf(0x49, 0x44, 0x33, 1, 2, 3))
            zip.closeEntry()
        }
        return file
    }

    private fun tempRoot(): File = File.createTempFile("tingli", "").apply {
        delete()
        mkdirs()
    }
}
