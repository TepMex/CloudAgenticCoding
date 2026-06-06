@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.tepmex.zoulushang.importing

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TakeoutJsonReader(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun readPoints(uri: Uri, onProgress: (Int) -> Unit): List<LocationPoint> {
        val result = ArrayList<LocationPoint>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffered = input.buffered()
            buffered.mark(4096)
            val header = ByteArray(4096)
            val read = buffered.read(header)
            buffered.reset()
            val prefix = String(header, 0, read.coerceAtLeast(0)).trimStart()

            if (prefix.startsWith("[")) {
                json.decodeToSequence<JsonObject>(buffered).forEach { obj ->
                    parseLocationObject(obj)?.let { result += it }
                    if (result.size % 500 == 0) onProgress(result.size)
                }
            } else {
                JsonReader(buffered.reader()).use { reader ->
                    streamLocationsObject(reader, result, onProgress)
                }
            }
        } ?: error("Cannot read JSON file")
        onProgress(result.size)
        return result
    }

    private fun streamLocationsObject(
        reader: JsonReader,
        out: MutableList<LocationPoint>,
        onProgress: (Int) -> Unit,
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "locations" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        parseLocationFromAndroidReader(reader)?.let { out += it }
                        if (out.size % 500 == 0) onProgress(out.size)
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun parseLocationFromAndroidReader(reader: JsonReader): LocationPoint? {
        var lat: Double? = null
        var lng: Double? = null
        var ts: Long? = null
        var accuracy: Float? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "latitudeE7" -> lat = readNumericAsLong(reader)?.div(1e7)
                "longitudeE7" -> lng = readNumericAsLong(reader)?.div(1e7)
                "latitude" -> lat = readNumericAsDouble(reader)
                "longitude" -> lng = readNumericAsDouble(reader)
                "timestampMs" -> ts = readNumericAsLong(reader)
                "timestamp" -> ts = readNumericAsLong(reader)
                "accuracy" -> accuracy = readNumericAsDouble(reader)?.toFloat()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val latitude = lat ?: return null
        val longitude = lng ?: return null
        val timestamp = ts ?: return null
        return LocationPoint(ts = timestamp, lat = latitude, lng = longitude, accuracyMeters = accuracy)
    }

    private fun readNumericAsLong(reader: JsonReader): Long? = when (reader.peek()) {
        JsonToken.NUMBER, JsonToken.STRING -> reader.nextString().toLongOrNull()
        else -> {
            reader.skipValue()
            null
        }
    }

    private fun readNumericAsDouble(reader: JsonReader): Double? = when (reader.peek()) {
        JsonToken.NUMBER, JsonToken.STRING -> reader.nextString().toDoubleOrNull()
        else -> {
            reader.skipValue()
            null
        }
    }

    private fun parseLocationObject(obj: JsonObject): LocationPoint? {
        val lat = obj.doubleField("latitude")
            ?: obj.longField("latitudeE7")?.div(1e7)
            ?: return null
        val lng = obj.doubleField("longitude")
            ?: obj.longField("longitudeE7")?.div(1e7)
            ?: return null
        val ts = obj.longField("timestampMs")
            ?: obj.longField("timestamp")
            ?: return null
        val accuracy = obj.floatField("accuracy")
        return LocationPoint(ts = ts, lat = lat, lng = lng, accuracyMeters = accuracy)
    }

    private fun JsonObject.doubleField(name: String): Double? =
        this[name]?.jsonPrimitive?.content?.toDoubleOrNull()

    private fun JsonObject.longField(name: String): Long? =
        this[name]?.jsonPrimitive?.content?.toLongOrNull()

    private fun JsonObject.floatField(name: String): Float? =
        this[name]?.jsonPrimitive?.content?.toFloatOrNull()
}
