package com.tepmex.zoulushang.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ImportedLocationPointDao {
    @Insert
    suspend fun insertAll(points: List<ImportedLocationPoint>)

    @Query("SELECT * FROM imported_location_points WHERE cityId = :cityId")
    suspend fun getForCity(cityId: Long): List<ImportedLocationPoint>

    @Query("DELETE FROM imported_location_points WHERE cityId = :cityId")
    suspend fun deleteForCity(cityId: Long)
}
