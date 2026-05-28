package com.tepmex.zuotasks.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RegularTaskDao {

    @Query("SELECT * FROM regular_tasks ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<RegularTaskEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM regular_tasks")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: RegularTaskEntity): Long

    @Query("UPDATE regular_tasks SET lastPerformedAt = :timestamp WHERE id = :id")
    suspend fun setLastPerformed(id: Long, timestamp: Long)

    @Query("DELETE FROM regular_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM regular_tasks ORDER BY id ASC")
    suspend fun getAll(): List<RegularTaskEntity>

    @Query("DELETE FROM regular_tasks")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<RegularTaskEntity>)
}
