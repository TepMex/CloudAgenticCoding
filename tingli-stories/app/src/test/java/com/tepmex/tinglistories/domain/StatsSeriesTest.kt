package com.tepmex.tinglistories.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsSeriesTest {
    @Test
    fun trailingMeansStartOnlyWhenTheWindowIsFull() {
        val values = listOf(1, 2, 3, 4, 5, 6)
        val short = trailingAverage(values, 5)
        assertEquals(listOf(null, null, null, null, 3.0, 4.0), short)
        assertEquals(List(6) { null }, trailingAverage(values, 10))

        val ten = (1..10).toList()
        val long = trailingAverage(ten, 10)
        assertNull(long[8])
        assertEquals(5.5, long[9]!!, 0.0001)
    }

    @Test
    fun correctPercentFollowsTheGradeAndSkipsAMissingOne() {
        assertNull(correctPercent(StoryProgress(listenCount = 4)))
        assertNull(correctPercent(StoryProgress(evaluation = Evaluation(1, 0, "ok", emptyList()))))
        assertEquals(50.0, correctPercent(graded(2, 4))!!, 0.0001)
        assertEquals(0.0, correctPercent(graded(0, 4))!!, 0.0001)
        assertEquals(100.0, correctPercent(graded(9, 4))!!, 0.0001)
    }

    @Test
    fun storyStatsKeepsCatalogOrderAndBreaksThePercentLine() {
        val library = libraryOf(
            listens = listOf(1, 2, 3, 4, 5, 6),
            grades = listOf(graded(2, 4), null, graded(4, 4), null, null, graded(1, 0)),
        )
        val points = storyStats(library)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), points.map { it.listens })
        assertEquals(3.0, points[4].average5!!, 0.0001)
        assertEquals(4.0, points[5].average5!!, 0.0001)
        assertNull(points[3].average5)
        assertNull(points[5].average10)
        assertEquals(50.0, points[0].correctPercent!!, 0.0001)
        assertNull(points[1].correctPercent)
        assertEquals(100.0, points[2].correctPercent!!, 0.0001)
        assertNull(points[5].correctPercent)

        val runs = valueRuns(points.map { it.correctPercent })
        assertEquals(listOf(0..0, 2..2), runs)
    }

    @Test
    fun listenAxisCoversThePeakWithRoomForBothAverages() {
        assertEquals(ListenAxis(1, listOf(0, 1)), listenAxis(0.0))
        assertEquals(ListenAxis(4, listOf(0, 1, 2, 3, 4)), listenAxis(3.2))
        assertEquals(ListenAxis(10, listOf(0, 5, 10)), listenAxis(10.0))
        val axis = listenAxis(31.0)
        assertEquals(40, axis.top)
        assertEquals(0, axis.ticks.first())
        assertEquals(40, axis.ticks.last())

        val points = listOf(
            StatsPoint(listens = 2, average5 = 6.0, average10 = null, correctPercent = 100.0),
        )
        assertEquals(6.0, chartPeak(points), 0.0001)
        assertEquals(listOf(0, 2, 4, 6), listenAxis(chartPeak(points)).ticks)
    }

    @Test
    fun catalogLabelsKeepTheEndsAndSkipACrowd() {
        assertEquals(listOf(0, 1, 2), catalogLabelIndices(3))
        val labels = catalogLabelIndices(100)
        assertEquals(0, labels.first())
        assertEquals(99, labels.last())
        assertEquals(labels, labels.distinct().sorted())
        assertTrue(labels.size in 4..6)
        labels.zipWithNext().forEach { (left, right) -> assertTrue(right - left >= 12) }
        val dozen = catalogLabelIndices(12)
        assertEquals(listOf(0, 2, 4, 6, 8, 11), dozen)
    }

    @Test
    fun chartDescriptionMentionsTheSeriesAndTheMeanPercent() {
        val empty = storyStats(Library())
        assertEquals(
            "График прослушиваний, средних по 5 и 10 историям и процента верных ответов. Проверенных историй нет.",
            statsChartDescription(empty),
        )
        val graded = storyStats(
            libraryOf(listOf(1, 3), listOf(graded(1, 4), graded(3, 4))),
        )
        assertEquals(
            "График прослушиваний, средних по 5 и 10 историям и процента верных ответов. Средний процент верных: 50.",
            statsChartDescription(graded),
        )
    }

    private fun graded(score: Int, max: Int) = StoryProgress(
        listenCount = 1,
        evaluation = Evaluation(score, max, "ok", emptyList()),
    )

    private fun libraryOf(listens: List<Int>, grades: List<StoryProgress?>): Library {
        val stories = listens.mapIndexed { index, _ ->
            Story(
                id = index + 1,
                title = "故事",
                text = "文",
                questions = listOf(Question("问", "答")),
                hasStoryAudio = true,
                hasQuestionsAudio = true,
            )
        }
        val progress = listens.mapIndexedNotNull { index, count ->
            val grade = grades.getOrNull(index)
            val stored = when {
                grade != null -> grade.copy(listenCount = count)
                count > 0 -> StoryProgress(listenCount = count)
                else -> null
            }
            stored?.let { index + 1 to it }
        }.toMap()
        return Library(stories = stories, progress = progress)
    }
}
