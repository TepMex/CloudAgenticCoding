package com.tepmex.tinglistories.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsSeriesTest {
    @Test
    fun listenScoreDividesPercentByListensAndSkipsAnUnansweredStory() {
        assertNull(listenScore(StoryProgress(listenCount = 4)))
        assertNull(listenScore(StoryProgress(evaluation = Evaluation(1, 0, "ok", emptyList()), listenCount = 2)))
        assertNull(listenScore(graded(2, 4, listens = 0)))
        assertEquals(50.0 / 3.0, listenScore(graded(2, 4, listens = 3))!!, 0.0001)
        assertEquals(0.0, listenScore(graded(0, 4, listens = 4))!!, 0.0001)
        assertEquals(50.0, listenScore(graded(9, 4, listens = 2))!!, 0.0001)
        assertEquals(100.0, listenScore(graded(4, 4, listens = 1))!!, 0.0001)
    }

    @Test
    fun windowOfFiveListensSkipsAStoryWithNoAnswer() {
        val library = libraryOf(
            listens = listOf(3, 2, 10, 4),
            grades = listOf(graded(2, 4), graded(4, 4), null, graded(0, 4)),
        )
        val points = storyStats(library)
        assertEquals(listOf(1, 2, 4), points.map { it.catalogNumber })
        assertEquals(50.0 / 3.0, points[0].indicator, 0.0001)
        assertEquals(50.0, points[1].indicator, 0.0001)
        assertEquals(0.0, points[2].indicator, 0.0001)

        assertNull(points[0].window5)
        assertEquals(30.0, points[1].window5!!, 0.0001)
        assertEquals(10.0, points[2].window5!!, 0.0001)
        assertNull(points[0].window10)
        assertNull(points[1].window10)
        assertNull(points[2].window10)

        val runs = valueRuns(points.map { it.window5 })
        assertEquals(listOf(1..2), runs)

        assertEquals(9, countedListenCount(library))
        assertEquals(10, unansweredListenCount(library))
        assertEquals(
            "Учтено 9 прослушиваний. В расчёте 3 истории с ответом. Без ответа не учтено 10 прослушиваний.",
            statsAccounting(9, 3, 10),
        )
    }

    @Test
    fun windowOfTenUsesOnlyCountedListensAndAPartialOlderStory() {
        val listens = List(12) { 1 }
        val grades = listens.indices.map { index ->
            if (index == 4) null else graded(4, 4, listens = 1)
        }
        val points = storyStats(libraryOf(listens, grades))
        assertEquals(11, points.size)
        assertEquals(listOf(1, 2, 3, 4, 6), points.take(5).map { it.catalogNumber })
        assertNull(points[8].window10)
        assertEquals(100.0, points[9].window10!!, 0.0001)
        assertEquals(100.0, points[10].window10!!, 0.0001)
        assertNull(points[3].window5)
        assertEquals(100.0, points[4].window5!!, 0.0001)
    }

    @Test
    fun aGradeWithoutQuestionsOrListensAddsNoPoint() {
        val library = libraryOf(
            listens = listOf(5, 0, 2),
            grades = listOf(
                StoryProgress(listenCount = 5, evaluation = Evaluation(1, 0, "ok", emptyList())),
                graded(4, 4, listens = 0),
                graded(2, 4, listens = 2),
            ),
        )
        val points = storyStats(library)
        assertEquals(1, points.size)
        assertEquals(3, points[0].catalogNumber)
        assertEquals(25.0, points[0].indicator, 0.0001)
        assertEquals(2, countedListenCount(library))
        assertEquals(0, unansweredListenCount(library))
    }

    @Test
    fun trailingWindowWeightsEachListenByItsStoryIndicator() {
        val values = listOf(10.0, 40.0)
        val listens = listOf(8, 2)
        val short = trailingListenWindow(listens, values, 5)
        assertEquals(10.0, short[0]!!, 0.0001)
        assertEquals((10.0 * 3 + 40.0 * 2) / 5.0, short[1]!!, 0.0001)
        val long = trailingListenWindow(listens, values, 10)
        assertNull(long[0])
        assertEquals((10.0 * 8 + 40.0 * 2) / 10.0, long[1]!!, 0.0001)
    }

    @Test
    fun listenAxisCoversThePeakOfTheIndicatorAndTheWindows() {
        assertEquals(ListenAxis(1, listOf(0, 1)), listenAxis(0.0))
        assertEquals(ListenAxis(4, listOf(0, 1, 2, 3, 4)), listenAxis(3.2))
        assertEquals(ListenAxis(10, listOf(0, 5, 10)), listenAxis(10.0))
        val axis = listenAxis(31.0)
        assertEquals(40, axis.top)
        assertEquals(0, axis.ticks.first())
        assertEquals(40, axis.ticks.last())

        val points = listOf(
            StatsPoint(catalogNumber = 1, indicator = 2.0, window5 = 6.0, window10 = null),
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
    fun chartDescriptionMentionsTheIndicatorAndSkipsAnEmptyLibrary() {
        val empty = storyStats(Library())
        assertEquals(
            "График показателя: процент верных ответов, делённый на число прослушиваний. " +
                "Окна 5 и 10 — среднее по последним прослушиваниям. Историй с ответом нет.",
            statsChartDescription(empty),
        )
        val graded = storyStats(
            libraryOf(
                listens = listOf(3, 2, 10, 4),
                grades = listOf(graded(2, 4), graded(4, 4), null, graded(0, 4)),
            ),
        )
        assertEquals(
            "График показателя: процент верных ответов, делённый на число прослушиваний. " +
                "Окна 5 и 10 — среднее по последним прослушиваниям. Средний показатель: 22.",
            statsChartDescription(graded),
        )
        assertEquals("16,7", formatStat(50.0 / 3.0))
        assertEquals("50", formatStat(50.0))
        assertEquals("22,2", formatStat(meanIndicator(storyStats(
            libraryOf(
                listens = listOf(3, 2, 10, 4),
                grades = listOf(graded(2, 4), graded(4, 4), null, graded(0, 4)),
            ),
        ))!!))
    }

    private fun graded(score: Int, max: Int, listens: Int = 1) = StoryProgress(
        listenCount = listens,
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
