package com.tepmex.tinglistories.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    private val story = Story(
        id = 1,
        title = "换工作",
        text = "李红换了工作。",
        questions = listOf(Question("以前做什么？", "会计")),
        hasStoryAudio = true,
        hasQuestionsAudio = true,
    )

    @Test
    fun mergeKeepsGradeWhenContentMatchesAndDropsItWhenQuestionsChange() {
        val progress = mapOf(
            1 to StoryProgress(
                listenCount = 4,
                answers = listOf("会计"),
                evaluation = Evaluation(1, 1, "Верно", emptyList()),
            ),
        )
        val same = mergeProgress(listOf(story), progress, listOf(story))
        assertEquals(4, same.getValue(1).listenCount)
        assertTrue(same.getValue(1).completed)

        val edited = story.copy(questions = listOf(Question("以前做什么？", "老师")))
        val changed = mergeProgress(listOf(story), progress, listOf(edited))
        assertEquals(4, changed.getValue(1).listenCount)
        assertFalse(changed.getValue(1).completed)
        assertTrue(changed.getValue(1).answers.isEmpty())

        val removed = mergeProgress(listOf(story), progress, emptyList())
        assertTrue(removed.isEmpty())
    }

    @Test
    fun playCountsOnlyWhenThatAudioExists() {
        assertTrue(shouldCountPlay(story, AudioKind.STORY))
        assertTrue(shouldCountPlay(story, AudioKind.QUESTIONS))
        val silent = story.copy(hasStoryAudio = false, hasQuestionsAudio = false)
        assertFalse(shouldCountPlay(silent, AudioKind.STORY))
        assertFalse(shouldCountPlay(silent, AudioKind.QUESTIONS))
        assertTrue(showTextBeforeAnswer(silent))
        assertFalse(showTextBeforeAnswer(story))
    }

    @Test
    fun nextStoryStopsAtTheEnd() {
        val second = story.copy(id = 2, title = "雨")
        assertEquals(2, nextStoryId(listOf(story, second), 1))
        assertNull(nextStoryId(listOf(story, second), 2))
        assertNull(nextStoryId(listOf(story), 9))
    }

    @Test
    fun listenLabelsFollowRussianPlural() {
        assertEquals("1 прослушивание", listensLabel(1))
        assertEquals("2 прослушивания", listensLabel(2))
        assertEquals("5 прослушиваний", listensLabel(5))
        assertEquals("11 прослушиваний", listensLabel(11))
        assertEquals("21 прослушивание", listensLabel(21))
        assertEquals("22 прослушивания", listensLabel(22))
    }

    @Test
    fun partialAudioNoteCountsStoriesThatHaveEitherFile() {
        val silent = story.copy(id = 2, hasStoryAudio = false, hasQuestionsAudio = false)
        val library = Library(stories = listOf(story, silent))
        assertEquals(
            "Аудио в архиве: 1 из 2. Истории без аудио открываются текстом.",
            audioCoverageNote(library),
        )
        assertNull(audioCoverageNote(Library(stories = listOf(story))))
    }
}
