package com.tepmex.zoulushang2.export

import com.tepmex.zoulushang2.data.PaintCell
import com.tepmex.zoulushang2.geo.CellMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class DrawingExport(
    val version: Int = 1,
    val zoom: Int = CellMath.PAINT_ZOOM,
    val cells: List<ExportedCell>,
)

@Serializable
private data class ExportedCell(
    val k: Long,
    val i: Int,
)

object DrawingExportCodec {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    fun encode(cells: List<PaintCell>): String {
        val export = DrawingExport(
            cells = cells.map { ExportedCell(k = it.cellKey, i = it.intensity) },
        )
        return json.encodeToString(export)
    }

    fun decode(text: String): List<PaintCell> {
        val export = json.decodeFromString<DrawingExport>(text.trim())
        require(export.version == 1) { "Unsupported export version: ${export.version}" }
        require(export.zoom == CellMath.PAINT_ZOOM) {
            "Unsupported zoom level: ${export.zoom} (expected ${CellMath.PAINT_ZOOM})"
        }
        return export.cells.map { PaintCell(cellKey = it.k, intensity = it.i.coerceIn(0, 1000)) }
    }
}
