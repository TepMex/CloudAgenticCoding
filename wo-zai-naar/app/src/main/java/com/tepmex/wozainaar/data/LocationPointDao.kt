package com.tepmex.wozainaar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {
    @Insert
    suspend fun insert(point: LocationPoint): Long

    @Query(
        """
        SELECT * FROM location_points
        WHERE recordedAt >= :dayStartMs AND recordedAt < :dayEndMs
        ORDER BY recordedAt ASC
        """,
    )
    fun observeForDay(dayStartMs: Long, dayEndMs: Long): Flow<List<LocationPoint>>

    @Query(
        """
        SELECT * FROM location_points
        WHERE recordedAt >= :dayStartMs AND recordedAt < :dayEndMs
        ORDER BY recordedAt ASC
        """,
    )
    suspend fun getForDay(dayStartMs: Long, dayEndMs: Long): List<LocationPoint>

    @Query("SELECT COUNT(*) FROM location_points")
    suspend fun countAll(): Int
}
