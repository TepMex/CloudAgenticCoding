package com.tepmex.myweek.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRulesTest {
    @Test
    fun weekStartIsMondayOnEveryWeekday() {
        assertEquals(date("2026-10-19"), WeekRules.weekStart(date("2026-10-19")))
        assertEquals(date("2026-10-19"), WeekRules.weekStart(date("2026-10-21")))
        assertEquals(date("2026-10-19"), WeekRules.weekStart(date("2026-10-25")))
    }

    @Test
    fun planningIsMondayUntilTheDateChanges() {
        val monday = date("2026-10-19")
        val tuesday = date("2026-10-20")
        assertTrue(WeekRules.canPlan(monday, monday, archived = false))
        assertFalse(WeekRules.canPlan(tuesday, monday, archived = false))
        assertFalse(WeekRules.canPlan(monday, monday, archived = true))
        assertFalse(WeekRules.canPlan(monday, date("2026-10-12"), archived = false))
        assertTrue(WeekRules.canCheck(tuesday, monday, archived = false))
        assertFalse(WeekRules.canCheck(tuesday, monday, archived = true))
        assertFalse(WeekRules.canCheck(tuesday, date("2026-10-26"), archived = false))
    }

    @Test
    fun mondayWithNoHistoryCreatesAnEmptySprint() {
        val plan = WeekRules.plan(emptyList(), date("2026-10-19"))
        assertEquals(date("2026-10-19"), plan.create?.weekStart)
        assertNull(plan.create?.copyFromId)
        assertTrue(plan.archiveIds.isEmpty())
    }

    @Test
    fun wednesdayWithNoHistoryCreatesNothing() {
        assertTrue(WeekRules.plan(emptyList(), date("2026-10-21")).isEmpty)
    }

    @Test
    fun lateOpenAfterMondayArchivesAndCopiesButPlanningStaysClosed() {
        val plan = WeekRules.plan(
            listOf(header(1, "2026-10-12")),
            date("2026-10-21"),
        )
        assertEquals(listOf(1L), plan.archiveIds)
        assertEquals(date("2026-10-19"), plan.create?.weekStart)
        assertEquals(1L, plan.create?.copyFromId)
        assertFalse(WeekRules.canPlan(date("2026-10-21"), date("2026-10-19"), archived = false))
        assertTrue(WeekRules.canCheck(date("2026-10-21"), date("2026-10-19"), archived = false))
    }

    @Test
    fun nextMondayCopiesTheLatestOpenSprint() {
        val plan = WeekRules.plan(
            listOf(header(1, "2026-10-05"), header(2, "2026-10-12")),
            date("2026-10-19"),
        )
        assertEquals(listOf(1L, 2L), plan.archiveIds)
        assertEquals(2L, plan.create?.copyFromId)
        assertEquals(date("2026-10-19"), plan.create?.weekStart)
    }

    @Test
    fun planIsIdempotentOnceTheCurrentWeekExists() {
        val monday = date("2026-10-19")
        val created = listOf(header(7, "2026-10-19"))
        assertTrue(WeekRules.plan(created, monday).isEmpty)
        assertTrue(WeekRules.plan(created, date("2026-10-21")).isEmpty)
    }

    @Test
    fun futureSprintIsLeftAloneUntilItsMonday() {
        val headers = listOf(
            header(1, "2026-10-26", archived = true),
            header(2, "2026-11-02"),
        )
        assertTrue(WeekRules.plan(headers, date("2026-10-21")).isEmpty)
        val onThatMonday = WeekRules.plan(headers, date("2026-10-26"))
        assertEquals(1L, onThatMonday.unarchiveId)
        assertTrue(onThatMonday.archiveIds.isEmpty())
        assertNull(onThatMonday.create)
    }

    @Test
    fun carryKeepsStructureAndDropsTicks() {
        val sprint = Sprint(
            id = 3,
            weekStart = date("2026-10-12"),
            archived = true,
            blocks = listOf(
                Block(
                    id = 10,
                    title = "Чтение",
                    colorKey = "teal",
                    emoji = "📚",
                    position = 1,
                    tasks = listOf(
                        Task(1, "📚", "Бродель", done = true, position = 0),
                        Task(2, "📚", "Ещё", done = false, position = 1),
                    ),
                ),
                Block(
                    id = 11,
                    title = "Дом",
                    colorKey = "nope",
                    emoji = " ",
                    position = 0,
                    tasks = emptyList(),
                ),
            ),
        )
        val drafts = WeekRules.carry(sprint)
        assertEquals(listOf("Дом", "Чтение"), drafts.map { it.title })
        assertEquals("blue", drafts[0].colorKey)
        assertEquals(TaskText.DEFAULT_EMOJI, drafts[0].emoji)
        assertEquals("teal", drafts[1].colorKey)
        assertEquals("📚", drafts[1].emoji)
        assertEquals(listOf("📚" to "Бродель", "📚" to "Ещё"), drafts[1].tasks.map { it.emoji to it.title })
        assertTrue(WeekRules.carry(null).isEmpty())
    }

    @Test
    fun archiveFeedIsOldestFirst() {
        val older = Sprint(1, date("2026-10-12"), archived = true, blocks = emptyList())
        val newer = Sprint(2, date("2026-10-19"), archived = true, blocks = emptyList())
        val open = Sprint(3, date("2026-10-26"), archived = false, blocks = emptyList())
        val feed = WeekRules.archiveFeed(listOf(newer, open, older))
        assertEquals(listOf(1L, 2L), feed.map { it.id })
    }

    @Test
    fun phaseFollowsTheClock() {
        val sprint = Sprint(1, date("2026-10-19"), archived = false, blocks = emptyList())
        assertEquals(WeekPhase.Planning, WeekRules.phase(date("2026-10-19"), sprint))
        assertEquals(WeekPhase.Tracking, WeekRules.phase(date("2026-10-21"), sprint))
        assertEquals(WeekPhase.NoSprint, WeekRules.phase(date("2026-10-21"), null))
    }

    @Test
    fun debugCalendarMovesForwardOnly() {
        assertEquals(date("2026-10-26"), DebugCalendar.nextMonday(date("2026-10-21")))
        assertEquals(date("2026-10-26"), DebugCalendar.nextMonday(date("2026-10-19")))
        assertEquals(date("2026-10-20"), DebugCalendar.ordinaryDay(date("2026-10-19")))
        assertEquals(date("2026-10-21"), DebugCalendar.ordinaryDay(date("2026-10-21")))
        assertEquals(date("2026-10-26"), DebugCalendar.nextDay(date("2026-10-25")))
    }

    @Test
    fun taskTextTrimsAndRejectsBlankDescriptions() {
        assertNull(TaskText.task("📚", "   "))
        assertEquals(TaskDraft(TaskText.DEFAULT_EMOJI, "Бродель"), TaskText.task("  ", " Бродель "))
        assertEquals("Чтение", TaskText.blockTitle("  Чтение "))
        assertNull(TaskText.blockTitle("  "))
        assertEquals(80, TaskText.blockTitle("я".repeat(90))?.length)
        assertEquals(TaskText.DEFAULT_EMOJI, TaskText.emoji("  "))
        assertEquals("🦄", TaskText.emoji(" 🦄 "))
        assertEquals(TaskText.EMOJI_LIMIT, TaskText.emoji("я".repeat(40)).length)
    }

    @Test
    fun paletteSkipsColorsAlreadyUsed() {
        assertEquals("blue", Palette.next(emptyList()))
        assertEquals("teal", Palette.next(listOf("blue")))
        assertEquals("blue", Palette.next(Palette.ids))
        assertEquals("blue", Palette.sanitize("nope"))
    }

    @Test
    fun joinRowsBecomeBlocksInPositionOrder() {
        val monday = date("2026-10-19").toEpochDay()
        val next = date("2026-10-26").toEpochDay()
        val rows = listOf(
            row(sprintId = 2, day = next, blockId = 8, blockTitle = "Дом", blockPosition = 0, blockEmoji = "🏠"),
            row(
                sprintId = 1,
                day = monday,
                blockId = 4,
                blockTitle = "Чтение",
                blockEmoji = "📚",
                blockPosition = 1,
                taskId = 2,
                title = "Вторая",
                done = false,
                taskPosition = 1,
            ),
            row(
                sprintId = 1,
                day = monday,
                blockId = 3,
                blockTitle = "Пустой",
                blockPosition = 0,
            ),
            row(
                sprintId = 1,
                day = monday,
                blockId = 4,
                blockTitle = "Чтение",
                blockEmoji = "📚",
                blockPosition = 1,
                taskId = 1,
                title = "Первая",
                done = true,
                taskPosition = 0,
            ),
        )
        val sprints = rows.toSprints()
        assertEquals(listOf(1L, 2L), sprints.map { it.id })
        assertEquals(listOf("Пустой", "Чтение"), sprints[0].blocks.map { it.title })
        assertEquals(TaskText.DEFAULT_EMOJI, sprints[0].blocks[0].emoji)
        assertEquals("📚", sprints[0].blocks[1].emoji)
        assertEquals("🏠", sprints[1].blocks[0].emoji)
        assertTrue(sprints[0].blocks[0].tasks.isEmpty())
        assertEquals(listOf("Первая", "Вторая"), sprints[0].blocks[1].tasks.map { it.title })
        assertTrue(sprints[0].blocks[1].tasks[0].done)
        assertFalse(sprints[0].blocks[1].tasks[1].done)
        assertTrue(sprints[1].blocks[0].tasks.isEmpty())
    }

    private fun date(iso: String): LocalDate = LocalDate.parse(iso)

    private fun header(id: Long, iso: String, archived: Boolean = false) =
        SprintHeader(id, date(iso), archived)

    private fun row(
        sprintId: Long,
        day: Long,
        blockId: Long? = null,
        blockTitle: String? = null,
        blockEmoji: String? = null,
        blockPosition: Int? = null,
        taskId: Long? = null,
        title: String? = null,
        done: Boolean? = null,
        taskPosition: Int? = null,
    ) = SprintJoinRow(
        sprintId = sprintId,
        weekStartEpochDay = day,
        archived = false,
        blockId = blockId,
        blockTitle = blockTitle,
        colorKey = "blue",
        blockEmoji = blockEmoji,
        blockPosition = blockPosition,
        taskId = taskId,
        emoji = "📚",
        taskTitle = title,
        done = done,
        taskPosition = taskPosition,
    )
}
