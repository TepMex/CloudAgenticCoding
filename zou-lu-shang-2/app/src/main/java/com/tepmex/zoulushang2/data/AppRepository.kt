package com.tepmex.zoulushang2.data

import com.tepmex.zoulushang2.export.DrawingExportCodec
import com.tepmex.zoulushang2.geo.BrushEngine
import com.tepmex.zoulushang2.geo.CellMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(
    private val database: ZouLuShang2Database,
) {
    private val dao = database.paintCellDao()

    fun observePaintLookup(): Flow<HashMap<Long, Int>> =
        dao.observeAll().map { cells ->
            HashMap<Long, Int>(cells.size).apply {
                for (cell in cells) {
                    put(cell.cellKey, cell.intensity)
                }
            }
        }

    suspend fun getPaintLookup(): HashMap<Long, Int> {
        val cells = dao.getAll()
        return HashMap<Long, Int>(cells.size).apply {
            for (cell in cells) {
                put(cell.cellKey, cell.intensity)
            }
        }
    }

    suspend fun getCellCount(): Int = dao.count()

    suspend fun recordLocation(
        latitude: Double,
        longitude: Double,
        lastLatitude: Double?,
        lastLongitude: Double?,
    ): Int {
        val updates = mutableListOf<Pair<Long, Int>>()
        BrushEngine.applyLocation(
            latitude = latitude,
            longitude = longitude,
            lastLatitude = lastLatitude,
            lastLongitude = lastLongitude,
        ) { cellKey, delta ->
            updates.add(cellKey to delta)
        }
        for ((cellKey, delta) in updates) {
            addCellIntensity(cellKey, delta)
        }
        return updates.size
    }

    private suspend fun addCellIntensity(cellKey: Long, delta: Int) {
        val updated = dao.addIntensity(cellKey, delta, BrushEngine.MAX_INTENSITY)
        if (updated == 0) {
            dao.insertIfAbsent(cellKey, delta.coerceAtMost(BrushEngine.MAX_INTENSITY))
        }
    }

    suspend fun exportDrawingText(): String {
        val cells = dao.getAll()
        return DrawingExportCodec.encode(cells)
    }

    suspend fun importDrawingText(text: String) {
        val cells = DrawingExportCodec.decode(text)
        dao.deleteAll()
        if (cells.isNotEmpty()) {
            dao.upsertAll(cells)
        }
    }

    suspend fun clearDrawing() {
        dao.deleteAll()
    }
}
