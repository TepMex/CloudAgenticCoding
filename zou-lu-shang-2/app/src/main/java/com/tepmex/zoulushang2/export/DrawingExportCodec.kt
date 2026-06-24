package com.tepmex.zoulushang2.export

import com.tepmex.zoulushang2.data.PaintStroke
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class DrawingExport(
    val version: Int = 2,
    val strokes: List<ExportedStroke>,
)

@Serializable
private data class ExportedStroke(
    val lat0: Double,
    val lng0: Double,
    val lat1: Double,
    val lng1: Double,
    val c: Int,
    val t: Float,
)

object DrawingExportCodec {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    fun encode(strokes: List<PaintStroke>): String {
        val export = DrawingExport(
            strokes = strokes.map {
                ExportedStroke(
                    lat0 = it.latStart,
                    lng0 = it.lngStart,
                    lat1 = it.latEnd,
                    lng1 = it.lngEnd,
                    c = it.colorArgb,
                    t = it.thicknessMeters,
                )
            },
        )
        return json.encodeToString(export)
    }

    fun decode(text: String): List<PaintStroke> {
        val export = json.decodeFromString<DrawingExport>(text.trim())
        require(export.version == 2) { "Unsupported export version: ${export.version}" }
        return export.strokes.map {
            PaintStroke(
                latStart = it.lat0,
                lngStart = it.lng0,
                latEnd = it.lat1,
                lngEnd = it.lng1,
                colorArgb = it.c,
                thicknessMeters = it.t.coerceAtLeast(0.5f),
            )
        }
    }
}
