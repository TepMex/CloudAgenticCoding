package com.tepmex.zoulushang.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveTileDao {
    @Query("SELECT * FROM live_tiles WHERE cityId = :cityId")
    suspend fun getTilesForCity(cityId: Long): List<LiveTile>

    @Query("SELECT * FROM live_tiles WHERE cityId = :cityId")
    fun observeTilesForCity(cityId: Long): Flow<List<LiveTile>>

    @Query("SELECT COUNT(*) FROM live_tiles WHERE cityId = :cityId")
    suspend fun countForCity(cityId: Long): Int

    @Query(
        """
        INSERT INTO live_tiles (cityId, tileKey, pointCount)
        VALUES (:cityId, :tileKey, 1)
        ON CONFLICT(cityId, tileKey) DO UPDATE SET pointCount = pointCount + 1
        """,
    )
    suspend fun recordVisit(cityId: Long, tileKey: Long)
}
