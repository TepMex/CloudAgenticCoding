package com.tepmex.zoulushang.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VisitedTileDao {
    @Query("SELECT tileKey FROM visited_tiles WHERE cityId = :cityId")
    suspend fun getTileKeysForCity(cityId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM visited_tiles WHERE cityId = :cityId")
    suspend fun countForCity(cityId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tiles: List<VisitedTile>)

    @Query("DELETE FROM visited_tiles WHERE cityId = :cityId")
    suspend fun deleteForCity(cityId: Long)
}
