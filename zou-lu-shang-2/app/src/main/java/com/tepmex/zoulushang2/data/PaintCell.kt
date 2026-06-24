package com.tepmex.zoulushang2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paint_cells")
data class PaintCell(
    @PrimaryKey val cellKey: Long,
    val intensity: Int,
)
