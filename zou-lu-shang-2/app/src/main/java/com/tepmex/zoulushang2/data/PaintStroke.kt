package com.tepmex.zoulushang2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paint_strokes")
data class PaintStroke(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latStart: Double,
    val lngStart: Double,
    val latEnd: Double,
    val lngEnd: Double,
    val colorArgb: Int,
    val thicknessMeters: Float,
)
