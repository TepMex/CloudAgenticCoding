package com.tepmex.myweek.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepGroupsTest {
    @Test
    fun numberedTasksCollapseAndAFinishedSiblingDoesNotRemoveTheGroup() {
        val tasks = listOf(
            task(1, "🚶", "20к шагов", done = true),
            task(2, "🚶", "40к шагов", done = false),
            task(3, "📚", "Книга 1", done = true),
            task(4, "📚", "Книга 2", done = false),
            task(5, "🏃", "40к шагов", done = false),
        )
        val active = StepGroups.active(tasks)
        assertEquals(setOf("к шагов", "Книга"), active.map { it.label }.toSet())
        val steps = active.first { it.emoji == "🚶" }
        assertEquals("к шагов", steps.label)
        assertEquals(1, steps.openCount)
        assertEquals(StepGroups.key("🚶", "20к шагов"), steps.key)
        val book = active.first { it.label == "Книга" }
        assertEquals("📚", book.emoji)
        assertEquals(1, book.openCount)
        assertEquals("🏃", active.first { it.emoji == "🏃" }.emoji)
    }

    @Test
    fun higherRatingIsListedFirst() {
        val active = StepGroups.active(
            listOf(task(1, "🧹", "Уборка", done = false), task(2, "📚", "Книга 1", done = false)),
            mapOf(
                StepGroups.key("🧹", "Уборка") to StepStanding(rating = 900.0, comparisonCount = 2),
                StepGroups.key("📚", "Книга 1") to StepStanding(rating = 1200.0, comparisonCount = 3),
            ),
        )
        assertEquals(listOf("Книга", "Уборка"), active.map { it.label })
    }

    @Test
    fun aGroupOfOnlyFinishedTasksIsHidden() {
        val tasks = listOf(
            task(1, "📚", "Книга 1", done = true),
            task(2, "📚", "Книга 2", done = true),
        )
        assertTrue(StepGroups.active(tasks).isEmpty())
    }

    @Test
    fun standingFollowsTheNormalizedKey() {
        val key = StepGroups.key("📚", "Книга 2")
        val active = StepGroups.active(
            listOf(task(1, "📚", "Книга 1", done = true), task(2, "📚", "Книга 2", done = false)),
            mapOf(key to StepStanding(rating = 1100.0, comparisonCount = 4)),
        )
        assertEquals(1100.0, active.single().rating, 0.001)
        assertEquals(4, active.single().comparisonCount)
    }

    private fun task(id: Long, emoji: String, title: String, done: Boolean) =
        Task(id = id, emoji = emoji, title = title, done = done, position = 0)
}
