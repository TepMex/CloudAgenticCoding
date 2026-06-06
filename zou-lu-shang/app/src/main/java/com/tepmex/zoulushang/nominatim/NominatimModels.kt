package com.tepmex.zoulushang.nominatim

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NominatimSearchResult(
    @SerialName("place_id") val placeId: Long,
    @SerialName("display_name") val displayName: String,
    val lat: String,
    val lon: String,
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("geojson") val geoJson: JsonElement? = null,
    val boundingbox: List<String>? = null,
)

@Serializable
data class NominatimDetailsResult(
    @SerialName("place_id") val placeId: Long,
    val geometry: JsonElement? = null,
)
