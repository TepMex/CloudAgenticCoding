package com.tepmex.inbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures WHERE place = :place")
    fun observe(place: String): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE place = :place")
    suspend fun list(place: String): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun get(id: Long): CaptureEntity?

    @Insert
    suspend fun insert(entity: CaptureEntity): Long

    @Update
    suspend fun update(entity: CaptureEntity)

    @Query("DELETE FROM captures WHERE place = :place")
    suspend fun deletePlace(place: String)
}
