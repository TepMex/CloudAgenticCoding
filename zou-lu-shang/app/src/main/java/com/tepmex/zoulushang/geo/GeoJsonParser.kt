package com.tepmex.zoulushang.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
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

    /** Nominatim boundingbox: [minLat, maxLat, minLon, maxLon] as strings. */
    fun rectangleFromBoundingBox(boundingBox: List<String>): String {
        require(boundingBox.size >= 4) { "Bounding box must have four values" }
        val minLat = boundingBox[0].toDouble()
        val maxLat = boundingBox[1].toDouble()
        val minLon = boundingBox[2].toDouble()
        val maxLon = boundingBox[3].toDouble()
        return """{"type":"Polygon","coordinates":[[[$minLon,$minLat],[$maxLon,$minLat],[$maxLon,$maxLat],[$minLon,$maxLat],[$minLon,$minLat]]]}"""
    }

    private fun extractRings(element: JsonElement): List<List<LatLng>> = when (element) {
        is JsonObject -> {
            when (element["type"]?.jsonPrimitive?.content?.lowercase()) {
                "polygon" -> {
                    val coords = element["coordinates"]?.asArray() ?: return emptyList()
                    parsePolygonCoordinates(coords)?.let { listOf(it) } ?: emptyList()
                }
                "multipolygon" -> {
                    val coords = element["coordinates"]?.asArray() ?: return emptyList()
                    coords.mapNotNull { polygon -> polygon.asArray()?.let(::parsePolygonCoordinates) }
                }
                "feature" -> element["geometry"]?.let { extractRings(it) } ?: emptyList()
                "featurecollection" -> {
                    element["features"]?.asArray()?.flatMap { extractRings(it) } ?: emptyList()
                }
                "geometrycollection" -> {
                    element["geometries"]?.asArray()?.flatMap { extractRings(it) } ?: emptyList()
                }
                else -> emptyList()
            }
        }
        else -> emptyList()
    }

    /** Exterior ring only (holes ignored). */
    private fun parsePolygonCoordinates(coords: JsonArray): List<LatLng>? {
        if (coords.isEmpty()) return null
        return when {
            isPositionArray(coords) -> parsePositionRing(coords)
            coords[0].asArray()?.let(::isPositionArray) == true -> {
                parsePositionRing(coords[0].asArray()!!)
            }
            else -> null
        }
    }

    private fun isPositionArray(array: JsonArray): Boolean =
        array.isNotEmpty() && array.all { element ->
            val position = element.asArray() ?: return@all false
            position.size >= 2 && position[0] is JsonPrimitive && position[1] is JsonPrimitive
        }

    private fun parsePositionRing(ring: JsonArray): List<LatLng> =
        ring.mapNotNull { position -> parsePosition(position) }

    private fun parsePosition(element: JsonElement): LatLng? {
        val pair = element.asArray() ?: return null
        if (pair.size < 2) return null
        val lng = pair[0].jsonPrimitive.content.toDouble()
        val lat = pair[1].jsonPrimitive.content.toDouble()
        return LatLng(lat = lat, lng = lng)
    }

    private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
}
