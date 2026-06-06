package com.tepmex.zoulushang.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CityBoundaryDao {
    @Query("SELECT * FROM city_boundaries ORDER BY displayName")
    suspend fun getAll(): List<CityBoundary>

    @Query("SELECT * FROM city_boundaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CityBoundary?

    @Query("SELECT * FROM city_boundaries WHERE osmPlaceId = :placeId LIMIT 1")
    suspend fun getByOsmPlaceId(placeId: Long): CityBoundary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: CityBoundary): Long

    @Query("DELETE FROM city_boundaries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
