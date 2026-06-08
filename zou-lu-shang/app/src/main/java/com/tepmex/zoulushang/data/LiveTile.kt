package com.tepmex.zoulushang.data

import androidx.room.Entity

@Entity(
    tableName = "live_tiles",
    primaryKeys = ["cityId", "tileKey"],
)
data class LiveTile(
    val cityId: Long,
    val tileKey: Long,
    val pointCount: Int = 1,
)
