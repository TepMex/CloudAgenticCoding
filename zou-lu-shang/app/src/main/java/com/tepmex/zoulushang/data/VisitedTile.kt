package com.tepmex.zoulushang.data

import androidx.room.Entity

@Entity(
    tableName = "visited_tiles",
    primaryKeys = ["cityId", "tileKey"],
)
data class VisitedTile(
    val cityId: Long,
    val tileKey: Long,
    val pointCount: Int = 1,
)
