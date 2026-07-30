package com.tepmex.runninglog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningActivityDao {
    @Query("SELECT * FROM running_activities ORDER BY startTimeEpochSec DESC")
    fun observeAll(): Flow<List<RunningActivityEntity>>

    @Query("SELECT COALESCE(MAX(watermark), 0) FROM running_activities")
    suspend fun maxWatermark(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RunningActivityEntity>)

    @Query("DELETE FROM running_activities")
    suspend fun clearAll()
}
