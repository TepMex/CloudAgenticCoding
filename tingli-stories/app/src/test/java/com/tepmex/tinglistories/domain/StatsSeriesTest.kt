package com.tepmex.tinglistories.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun barsSkipAStoryWithNoAnswerAndPaintOnlyThoseBelowTheMeanGray() {
        val library = libraryOf(
            listens = listOf(3, 2, 10, 4),
            grades = listOf(graded(2, 4), graded(4, 4), null, graded(0, 4)),
        )
        val points = storyStats(library)
        assertEquals(listOf(1, 2, 4), points.map { it.catalogNumber })
        assertEquals(50.0 / 3.0, points[0].indicator, 0.0001)
        assertEquals(50.0, points[1].indicator, 0.0001)
        assertEquals(0.0, points[2].indicator, 0.0001)
        assertEquals("22,2", formatStat(meanIndicator(points)!!))
        assertFalse(points[0].green)
        assertTrue(points[1].green)
        assertFalse(points[2].green)
        assertEquals(
            "Столбцы: процент верных ответов, делённый на число прослушиваний. " +
                "Среднее 22,2. Зелёный — не ниже среднего, серый — ниже. " +
                "01 16,7 серый; 02 50 зелёный; 04 0 серый",
            statsChartDescription(points),
        )
    }

    @Test
    fun aGradeWithoutQuestionsOrListensAddsNoBar() {
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
        assertTrue(points[0].green)
        assertEquals("Пройденных историй нет.", statsChartDescription(emptyList()))
    }

    @Test
    fun equalIndicatorsStayGreenAndOnlyAStrictlyLowerBarIsGray() {
        assertTrue(
            storyStats(libraryOf(listOf(2, 2), listOf(graded(4, 4), graded(4, 4)))).all { it.green },
        )
        assertTrue(storyStats(libraryOf(listOf(3), listOf(graded(2, 4)))).single().green)
        val mixed = storyStats(
            libraryOf(
                listOf(1, 1, 1),
                listOf(graded(4, 4), graded(0, 4), graded(2, 4)),
            ),
        )
        assertEquals(listOf(true, false, true), mixed.map { it.green })
        assertTrue(atOrAboveMean(50.0, 50.0))
        assertFalse(atOrAboveMean(49.9, 50.0))
    }

    @Test
    fun circleStaysBrightAtTheMeanAndWithoutAnIndicator() {
        assertEquals(
            setOf(1, 4),
            dimmedStoryIds(
                libraryOf(
                    listens = listOf(3, 2, 10, 4),
                    grades = listOf(graded(2, 4), graded(4, 4), null, graded(0, 4)),
                ),
            ),
        )
        assertEquals(
            emptySet<Int>(),
            dimmedStoryIds(libraryOf(listOf(2, 2), listOf(graded(4, 4), graded(4, 4)))),
        )
        assertEquals(
            emptySet<Int>(),
            dimmedStoryIds(libraryOf(listOf(3), listOf(graded(2, 4)))),
        )
        assertEquals(
            emptySet<Int>(),
            dimmedStoryIds(libraryOf(listOf(0, 2), listOf(graded(4, 4), graded(0, 4)))),
        )
        assertEquals(
            setOf(2),
            dimmedStoryIds(
                libraryOf(
                    listOf(1, 1, 1),
                    listOf(graded(4, 4), graded(0, 4), graded(2, 4)),
                ),
            ),
        )
        assertEquals(emptySet<Int>(), dimmedStoryIds(Library()))
    }

    @Test
    fun listenAxisCoversThePeakOfTheIndicator() {
        assertEquals(ListenAxis(1, listOf(0, 1)), listenAxis(0.0))
        assertEquals(ListenAxis(4, listOf(0, 1, 2, 3, 4)), listenAxis(3.2))
        assertEquals(ListenAxis(10, listOf(0, 5, 10)), listenAxis(10.0))
        val axis = listenAxis(31.0)
        assertEquals(40, axis.top)
        assertEquals(0, axis.ticks.first())
        assertEquals(40, axis.ticks.last())

        val points = listOf(StatsPoint(catalogNumber = 1, indicator = 6.0, green = true))
        assertEquals(6.0, chartPeak(points), 0.0001)
        assertEquals(listOf(0, 2, 4, 6), listenAxis(chartPeak(points)).ticks)
        assertEquals(0.0, chartPeak(emptyList()), 0.0001)
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
    fun formatStatUsesAComma() {
        assertEquals("16,7", formatStat(50.0 / 3.0))
        assertEquals("50", formatStat(50.0))
        assertEquals("0", formatStat(-1.0))
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
