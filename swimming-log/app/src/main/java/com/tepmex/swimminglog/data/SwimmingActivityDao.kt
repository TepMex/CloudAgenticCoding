package com.tepmex.swimminglog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SwimmingActivityDao {
    @Query("SELECT * FROM swimming_activities ORDER BY startTimeEpochSec DESC")
    fun observeAll(): Flow<List<SwimmingActivityEntity>>

    @Query("SELECT COALESCE(MAX(watermark), 0) FROM swimming_activities")
    suspend fun maxWatermark(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SwimmingActivityEntity>)

    @Query("DELETE FROM swimming_activities")
    suspend fun clearAll()
}
