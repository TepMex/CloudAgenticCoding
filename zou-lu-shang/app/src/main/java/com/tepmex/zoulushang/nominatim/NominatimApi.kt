package com.tepmex.zoulushang.nominatim

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class NominatimApi(private val userAgent: String) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchCities(query: String): List<NominatimSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, Charsets.UTF_8)
        val url = "https://nominatim.openstreetmap.org/search" +
            "?q=$encoded&format=json&polygon_geojson=1&limit=10&featuretype=city"
        getJsonArray(url)
    }

    suspend fun lookupBoundary(result: NominatimSearchResult): NominatimSearchResult = withContext(Dispatchers.IO) {
        val osmIds = result.osmIdsParam()
            ?: error("No OSM id for ${result.displayName}")
        val url = "https://nominatim.openstreetmap.org/lookup" +
            "?osm_ids=$osmIds&format=json&polygon_geojson=1"
        val results = getJsonArray(url)
        results.firstOrNull { it.geoJson != null }
            ?: error("No boundary polygon returned for ${result.displayName}")
    }

    suspend fun detailsGeometry(placeId: Long): JsonElement? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/details" +
            "?place_id=$placeId&format=json&polygon_geojson=1"
        val connection = openConnection(url)
        try {
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            json.decodeFromString<NominatimDetailsResult>(body).geometry
        } finally {
            connection.disconnect()
        }
    }

    private fun getJsonArray(url: String): List<NominatimSearchResult> {
        val connection = openConnection(url)
        return try {
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            json.decodeFromString<List<NominatimSearchResult>>(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            connectTimeout = 15_000
            readTimeout = 30_000
        }

    private fun NominatimSearchResult.osmIdsParam(): String? {
        val type = osmType ?: return null
        val id = osmId ?: return null
        val prefix = when (type.lowercase()) {
            "node" -> "N"
            "way" -> "W"
            "relation" -> "R"
            else -> return null
        }
        return "$prefix$id"
    }
}
