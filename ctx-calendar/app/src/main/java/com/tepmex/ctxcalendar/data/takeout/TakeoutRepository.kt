package com.tepmex.ctxcalendar.data.takeout

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class TakeoutRepository(private val context: Context) {

    private var db: SQLiteDatabase? = null
    private var openedUri: String? = null

    fun isOpen(): Boolean = db != null

    fun openedUriString(): String? = openedUri

    suspend fun openFromUri(uriString: String): Result<TakeoutDbInfo> = withContext(Dispatchers.IO) {
        close()
        runCatching {
            val uri = Uri.parse(uriString)
            val tmp = File(context.cacheDir, "takeout-copy.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot read database file")
            val database = SQLiteDatabase.openDatabase(
                tmp.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            validateSchema(database)
            db = database
            openedUri = uriString
            readDbInfo(database)
        }.onFailure { e ->
            Log.e(TAG, "openFromUri failed", e)
            close()
        }
    }

    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {
        }
        db = null
        openedUri = null
    }

    suspend fun loadDayTimeline(date: LocalDate): TakeoutDayTimeline? = withContext(Dispatchers.IO) {
        val database = db ?: return@withContext null
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        TakeoutDayTimeline(
            track = queryTrack(database, dayStart, dayEnd),
            visits = queryVisits(database, dayStart, dayEnd),
            activities = queryActivities(database, dayStart, dayEnd),
            searches = queryYoutubeSearches(database, dayStart, dayEnd),
            watches = queryYoutubeWatches(database, dayStart, dayEnd),
        )
    }

    private fun validateSchema(database: SQLiteDatabase) {
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='events'",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                error("Not a takeout.db file (missing events table)")
            }
        }
    }

    private fun readDbInfo(database: SQLiteDatabase): TakeoutDbInfo {
        val meta = mutableMapOf<String, String>()
        database.rawQuery("SELECT key, value FROM meta", null).use { cursor ->
            val keyCol = cursor.getColumnIndexOrThrow("key")
            val valueCol = cursor.getColumnIndexOrThrow("value")
            while (cursor.moveToNext()) {
                meta[cursor.getString(keyCol)] = cursor.getString(valueCol)
            }
        }
        return TakeoutDbInfo(
            schemaVersion = meta["schema_version"],
            eventCount = meta["event_count"]?.toLongOrNull(),
            builtAt = meta["built_at"],
        )
    }

    private fun queryTrack(
        database: SQLiteDatabase,
        dayStart: Long,
        dayEnd: Long,
    ): List<GeoTrackPoint> {
        val sql = """
            SELECT ts, lat, lng, kind
            FROM events
            WHERE ts >= ? AND ts < ?
              AND kind IN ('path_point', 'position')
              AND lat IS NOT NULL
            ORDER BY ts
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(dayStart.toString(), dayEnd.toString())).use { cursor ->
            val tsCol = cursor.getColumnIndexOrThrow("ts")
            val latCol = cursor.getColumnIndexOrThrow("lat")
            val lngCol = cursor.getColumnIndexOrThrow("lng")
            val kindCol = cursor.getColumnIndexOrThrow("kind")
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GeoTrackPoint(
                            ts = cursor.getLong(tsCol),
                            lat = cursor.getDouble(latCol),
                            lng = cursor.getDouble(lngCol),
                            kind = cursor.getString(kindCol),
                        ),
                    )
                }
            }
        }
    }

    private fun queryVisits(
        database: SQLiteDatabase,
        dayStart: Long,
        dayEnd: Long,
    ): List<ChronologyVisit> {
        val sql = """
            SELECT e.ts, e.ts_end, e.lat, e.lng, s1.value AS place_id, s2.value AS semantic_type
            FROM events e
            LEFT JOIN strings s1 ON s1.id = e.str1_id
            LEFT JOIN strings s2 ON s2.id = e.str2_id
            WHERE e.kind = 'visit'
              AND e.ts >= ? AND e.ts < ?
            ORDER BY e.ts
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(dayStart.toString(), dayEnd.toString())).use { cursor ->
            val tsCol = cursor.getColumnIndexOrThrow("ts")
            val tsEndCol = cursor.getColumnIndexOrThrow("ts_end")
            val latCol = cursor.getColumnIndexOrThrow("lat")
            val lngCol = cursor.getColumnIndexOrThrow("lng")
            val placeCol = cursor.getColumnIndexOrThrow("place_id")
            val typeCol = cursor.getColumnIndexOrThrow("semantic_type")
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ChronologyVisit(
                            ts = cursor.getLong(tsCol),
                            tsEnd = cursor.getLongOrNull(tsEndCol),
                            placeId = cursor.getString(placeCol),
                            semanticType = cursor.getString(typeCol),
                            lat = cursor.getDoubleOrNull(latCol),
                            lng = cursor.getDoubleOrNull(lngCol),
                        ),
                    )
                }
            }
        }
    }

    private fun queryActivities(
        database: SQLiteDatabase,
        dayStart: Long,
        dayEnd: Long,
    ): List<ChronologyActivity> {
        val sql = """
            SELECT e.ts, e.ts_end, e.lat, e.lng, e.payload, s1.value AS activity_type
            FROM events e
            LEFT JOIN strings s1 ON s1.id = e.str1_id
            WHERE e.kind = 'activity'
              AND e.ts >= ? AND e.ts < ?
            ORDER BY e.ts
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(dayStart.toString(), dayEnd.toString())).use { cursor ->
            val tsCol = cursor.getColumnIndexOrThrow("ts")
            val tsEndCol = cursor.getColumnIndexOrThrow("ts_end")
            val latCol = cursor.getColumnIndexOrThrow("lat")
            val lngCol = cursor.getColumnIndexOrThrow("lng")
            val payloadCol = cursor.getColumnIndexOrThrow("payload")
            val typeCol = cursor.getColumnIndexOrThrow("activity_type")
            buildList {
                while (cursor.moveToNext()) {
                    val payload = runCatching {
                        JSONObject(cursor.getString(payloadCol))
                    }.getOrNull()
                    add(
                        ChronologyActivity(
                            ts = cursor.getLong(tsCol),
                            tsEnd = cursor.getLongOrNull(tsEndCol),
                            activityType = cursor.getString(typeCol),
                            distanceMeters = payload?.optDouble("distanceMeters")?.takeIf { !it.isNaN() },
                            lat = cursor.getDoubleOrNull(latCol),
                            lng = cursor.getDoubleOrNull(lngCol),
                        ),
                    )
                }
            }
        }
    }

    private fun queryYoutubeSearches(
        database: SQLiteDatabase,
        dayStart: Long,
        dayEnd: Long,
    ): List<YoutubeSearchEvent> {
        val sql = """
            SELECT e.ts, e.payload, s1.value AS query
            FROM events e
            LEFT JOIN strings s1 ON s1.id = e.str1_id
            WHERE e.source = 'youtube' AND e.kind = 'search'
              AND e.ts >= ? AND e.ts < ?
            ORDER BY e.ts DESC
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(dayStart.toString(), dayEnd.toString())).use { cursor ->
            val tsCol = cursor.getColumnIndexOrThrow("ts")
            val payloadCol = cursor.getColumnIndexOrThrow("payload")
            val queryCol = cursor.getColumnIndexOrThrow("query")
            buildList {
                while (cursor.moveToNext()) {
                    val payload = runCatching {
                        JSONObject(cursor.getString(payloadCol))
                    }.getOrNull()
                    add(
                        YoutubeSearchEvent(
                            ts = cursor.getLong(tsCol),
                            query = cursor.getString(queryCol),
                            url = payload?.optString("url")?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
    }

    private fun queryYoutubeWatches(
        database: SQLiteDatabase,
        dayStart: Long,
        dayEnd: Long,
    ): List<YoutubeWatchEvent> {
        val sql = """
            SELECT e.ts, e.payload, s1.value AS title, s2.value AS channel
            FROM events e
            LEFT JOIN strings s1 ON s1.id = e.str1_id
            LEFT JOIN strings s2 ON s2.id = e.str2_id
            WHERE e.source = 'youtube' AND e.kind = 'watch'
              AND e.ts >= ? AND e.ts < ?
            ORDER BY e.ts DESC
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(dayStart.toString(), dayEnd.toString())).use { cursor ->
            val tsCol = cursor.getColumnIndexOrThrow("ts")
            val payloadCol = cursor.getColumnIndexOrThrow("payload")
            val titleCol = cursor.getColumnIndexOrThrow("title")
            val channelCol = cursor.getColumnIndexOrThrow("channel")
            buildList {
                while (cursor.moveToNext()) {
                    val payload = runCatching {
                        JSONObject(cursor.getString(payloadCol))
                    }.getOrNull()
                    add(
                        YoutubeWatchEvent(
                            ts = cursor.getLong(tsCol),
                            title = cursor.getString(titleCol),
                            channel = cursor.getString(channelCol),
                            url = payload?.optString("url")?.takeIf { it.isNotBlank() },
                            videoId = payload?.optString("video_id")?.takeIf { it.isNotBlank() },
                            subtype = payload?.optString("subtype")?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? =
        if (isNull(columnIndex)) null else getLong(columnIndex)

    private fun android.database.Cursor.getDoubleOrNull(columnIndex: Int): Double? =
        if (isNull(columnIndex)) null else getDouble(columnIndex)

    companion object {
        private const val TAG = "TakeoutRepository"
    }
}
