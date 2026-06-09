package com.tepmex.zoulushang.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LiveLocationSampleDao {
    @Insert
    suspend fun insert(sample: LiveLocationSample)

    @Query("SELECT * FROM live_location_samples WHERE cityId = :cityId")
    suspend fun getSamplesForCity(cityId: Long): List<LiveLocationSample>

    @Query("DELETE FROM live_location_samples WHERE cityId = :cityId")
    suspend fun deleteForCity(cityId: Long)
}
