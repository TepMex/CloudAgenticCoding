package com.tepmex.zoulushang.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.util.BoundingBox

data class ParsedPolygon(
    val rings: List<List<LatLng>>,
    val boundingBox: BoundingBox,
)

object GeoJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePolygon(geoJson: String): ParsedPolygon {
        val root = json.parseToJsonElement(geoJson)
        val rings = extractRings(root)
        require(rings.isNotEmpty()) { "No polygon coordinates in GeoJSON" }
        val allPoints = rings.flatten()
        val minLat = allPoints.minOf { it.lat }
        val maxLat = allPoints.maxOf { it.lat }
        val minLng = allPoints.minOf { it.lng }
        val maxLng = allPoints.maxOf { it.lng }
        return ParsedPolygon(
            rings = rings,
            boundingBox = BoundingBox(maxLat, maxLng, minLat, minLng),
        )
    }

    private fun extractRings(element: JsonElement): List<List<LatLng>> = when (element) {
        is JsonObject -> {
            when (element["type"]?.jsonPrimitive?.content) {
                "Polygon" -> listOf(parseRing(element["coordinates"]!!.jsonArray[0].jsonArray))
                "MultiPolygon" -> element["coordinates"]!!.jsonArray.flatMap { polygon ->
                    polygon.jsonArray.map { ring -> parseRing(ring.jsonArray[0].jsonArray) }
                }
                "Feature" -> extractRings(element["geometry"]!!)
                "FeatureCollection" -> element["features"]!!.jsonArray.flatMap { extractRings(it) }
                else -> emptyList()
            }
        }
        else -> emptyList()
    }

    private fun parseRing(array: JsonArray): List<LatLng> =
        array.map { coord ->
            val pair = coord.jsonArray
            LatLng(lat = pair[1].jsonPrimitive.content.toDouble(), lng = pair[0].jsonPrimitive.content.toDouble())
        }
}
