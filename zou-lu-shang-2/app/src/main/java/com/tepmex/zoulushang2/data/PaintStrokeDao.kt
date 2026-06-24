package com.tepmex.zoulushang2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaintStrokeDao {
    @Query("SELECT * FROM paint_strokes ORDER BY id ASC")
    fun observeAll(): Flow<List<PaintStroke>>

    @Query("SELECT * FROM paint_strokes ORDER BY id ASC")
    suspend fun getAll(): List<PaintStroke>

    @Query("SELECT COUNT(*) FROM paint_strokes")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(strokes: List<PaintStroke>)

    @Insert
    suspend fun insert(stroke: PaintStroke)

    @Query("DELETE FROM paint_strokes")
    suspend fun deleteAll()
}
