package com.tepmex.myweek.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanTextTest {
    @Test
    fun copiesEmojiAndStatusForTheWholeWeek() {
        val sprint = Sprint(
            id = 1,
            weekStart = LocalDate.of(2026, 10, 19),
            archived = true,
            blocks = listOf(
                Block(
                    id = 1,
                    title = "Дом",
                    colorKey = "blue",
                    emoji = "🏠",
                    position = 0,
                    tasks = listOf(
                        Task(1, "🧹", "Уборка", done = true, position = 0),
                        Task(2, "🛒", "Продукты", done = false, position = 1),
                    ),
                ),
                Block(
                    id = 2,
                    title = "Спорт",
                    colorKey = "green",
                    emoji = "💪",
                    position = 1,
                    tasks = listOf(
                        Task(3, "🚶", "20к шагов", done = false, position = 0),
                    ),
                ),
            ),
        )
        assertEquals(
            """
            19 октября 2026
            🏠 Дом
            🧹 Уборка — выполнено
            🛒 Продукты — не выполнено

            💪 Спорт
            🚶 20к шагов — не выполнено
            """.trimIndent(),
            PlanText.render(sprint),
        )
    }
}
