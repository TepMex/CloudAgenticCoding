package com.tepmex.myweek.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tepmex.myweek.domain.SprintJoinRow
import kotlinx.coroutines.flow.Flow

private const val SPRINT_ROWS = """
    SELECT
        s.id AS sprintId,
        s.weekStartEpochDay AS weekStartEpochDay,
        s.archived AS archived,
        b.id AS blockId,
        b.title AS blockTitle,
        b.colorKey AS colorKey,
        b.emoji AS blockEmoji,
        b.position AS blockPosition,
        t.id AS taskId,
        t.emoji AS emoji,
        t.title AS taskTitle,
        t.done AS done,
        t.position AS taskPosition
    FROM sprints s
    LEFT JOIN blocks b ON b.sprintId = s.id
    LEFT JOIN tasks t ON t.blockId = b.id
"""

@Dao
interface WeekDao {
    @Query("SELECT id, weekStartEpochDay, archived FROM sprints")
    suspend fun headers(): List<SprintHeaderEntity>

    @Query("SELECT * FROM sprints WHERE id = :id")
    suspend fun sprint(id: Long): SprintEntity?

    @Query(
        """
        SELECT s.id AS id, s.weekStartEpochDay AS weekStartEpochDay, s.archived AS archived
        FROM sprints s
        INNER JOIN blocks b ON b.sprintId = s.id
        WHERE b.id = :blockId
        LIMIT 1
        """,
    )
    suspend fun headerForBlock(blockId: Long): SprintHeaderEntity?

    @Query(
        """
        SELECT s.id AS id, s.weekStartEpochDay AS weekStartEpochDay, s.archived AS archived
        FROM sprints s
        INNER JOIN blocks b ON b.sprintId = s.id
        INNER JOIN tasks t ON t.blockId = b.id
        WHERE t.id = :taskId
        LIMIT 1
        """,
    )
    suspend fun headerForTask(taskId: Long): SprintHeaderEntity?

    @Query(
        SPRINT_ROWS +
            " WHERE s.archived = 0 AND s.weekStartEpochDay = :day ORDER BY b.position, t.position",
    )
    fun observeWeek(day: Long): Flow<List<SprintJoinRow>>

    @Query(SPRINT_ROWS + " WHERE s.archived = 1 ORDER BY s.weekStartEpochDay, b.position, t.position")
    fun observeArchived(): Flow<List<SprintJoinRow>>

    @Query(SPRINT_ROWS + " WHERE s.id = :id ORDER BY b.position, t.position")
    suspend fun loadSprintRows(id: Long): List<SprintJoinRow>

    @Insert
    suspend fun insertSprint(entity: SprintEntity): Long

    @Insert
    suspend fun insertBlock(entity: BlockEntity): Long

    @Insert
    suspend fun insertTask(entity: TaskEntity): Long

    @Query("UPDATE sprints SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT COALESCE(MAX(position), -1) FROM blocks WHERE sprintId = :sprintId")
    suspend fun maxBlockPosition(sprintId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE blockId = :blockId")
    suspend fun maxTaskPosition(blockId: Long): Int

    @Query("UPDATE blocks SET title = :title, colorKey = :colorKey, emoji = :emoji WHERE id = :id")
    suspend fun updateBlock(id: Long, title: String, colorKey: String, emoji: String)

    @Query("SELECT emoji AS emoji, COUNT(*) AS uses FROM tasks GROUP BY emoji")
    fun observeEmojiUsage(): Flow<List<EmojiUsage>>

    @Query("SELECT emoji AS emoji, COUNT(*) AS uses FROM tasks GROUP BY emoji")
    suspend fun emojiUsage(): List<EmojiUsage>

    @Query("UPDATE tasks SET emoji = :emoji, title = :title WHERE id = :id")
    suspend fun updateTask(id: Long, emoji: String, title: String)

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM tasks WHERE blockId = :blockId")
    suspend fun deleteTasksForBlock(blockId: Long)

    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun deleteBlock(id: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM blocks")
    suspend fun deleteAllBlocks()

    @Query("DELETE FROM sprints")
    suspend fun deleteAllSprints()
}
