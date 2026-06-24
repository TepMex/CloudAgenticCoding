package com.tepmex.zoulushang2.data

import com.tepmex.zoulushang2.brush.BrushSettingsStore
import com.tepmex.zoulushang2.export.DrawingExportCodec
import com.tepmex.zoulushang2.geo.BrushEngine
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val database: ZouLuShang2Database,
) {
    private val dao = database.paintStrokeDao()

    fun observeStrokes(): Flow<List<PaintStroke>> = dao.observeAll()

    suspend fun getStrokes(): List<PaintStroke> = dao.getAll()

    suspend fun getStrokeCount(): Int = dao.count()

    suspend fun recordLocation(
        latitude: Double,
        longitude: Double,
        lastLatitude: Double?,
        lastLongitude: Double?,
    ): Int {
        val brush = BrushSettingsStore.settings.value
        val strokes = mutableListOf<PaintStroke>()
        BrushEngine.applyLocation(
            latitude = latitude,
            longitude = longitude,
            lastLatitude = lastLatitude,
            lastLongitude = lastLongitude,
            colorArgb = brush.colorArgb,
            thicknessMeters = brush.thicknessMeters,
        ) { stroke ->
            strokes.add(stroke)
        }
        for (stroke in strokes) {
            dao.insert(stroke)
        }
        return strokes.size
    }

    suspend fun exportDrawingText(): String {
        val strokes = dao.getAll()
        return DrawingExportCodec.encode(strokes)
    }

    suspend fun importDrawingText(text: String) {
        val strokes = DrawingExportCodec.decode(text)
        dao.deleteAll()
        if (strokes.isNotEmpty()) {
            dao.insertAll(strokes)
        }
    }

    suspend fun clearDrawing() {
        dao.deleteAll()
    }
}
