package com.tepmex.zoulushang2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaintCellDao {
    @Query("SELECT cellKey, intensity FROM paint_cells")
    fun observeAll(): Flow<List<PaintCell>>

    @Query("SELECT cellKey, intensity FROM paint_cells")
    suspend fun getAll(): List<PaintCell>

    @Query("SELECT COUNT(*) FROM paint_cells")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cells: List<PaintCell>)

    @Query(
        """
        UPDATE paint_cells
        SET intensity = MIN(intensity + :delta, :maxIntensity)
        WHERE cellKey = :cellKey
        """,
    )
    suspend fun addIntensity(cellKey: Long, delta: Int, maxIntensity: Int): Int

    @Query("INSERT OR IGNORE INTO paint_cells (cellKey, intensity) VALUES (:cellKey, :initialIntensity)")
    suspend fun insertIfAbsent(cellKey: Long, initialIntensity: Int)

    @Query("DELETE FROM paint_cells")
    suspend fun deleteAll()
}
