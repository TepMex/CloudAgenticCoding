package com.tepmex.zoulushang.importing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.json.JSONObject
import java.io.File

class TakeoutDbReader(private val context: Context) {
    fun readPoints(
        uri: Uri,
        onProgress: (Int) -> Unit,
    ): List<LocationPoint> {
        val tmp = File(context.cacheDir, "takeout-import.db")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read database file")

        val database = SQLiteDatabase.openDatabase(tmp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            validateSchema(database)
            val hasAccuracyColumn = columnExists(database, "events", "accuracy")
            queryAllLocationPoints(database, hasAccuracyColumn, onProgress)
        } finally {
            database.close()
        }
    }

    private fun validateSchema(database: SQLiteDatabase) {
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='events'",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) error("Not a takeout.db file (missing events table)")
        }
    }

    private fun columnExists(database: SQLiteDatabase, table: String, column: String): Boolean {
        database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameCol) == column) return true
            }
        }
        return false
    }

    private fun queryAllLocationPoints(
        database: SQLiteDatabase,
        hasAccuracyColumn: Boolean,
        onProgress: (Int) -> Unit,
    ): List<LocationPoint> {
        val count = database.rawQuery(
            """
            SELECT COUNT(*) FROM events
            WHERE kind IN ('path_point', 'position') AND lat IS NOT NULL
            """.trimIndent(),
            null,
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

        val accuracySelect = if (hasAccuracyColumn) "accuracy" else "payload"
        val sql = """
            SELECT ts, lat, lng, $accuracySelect
            FROM events
            WHERE kind IN ('path_point', 'position') AND lat IS NOT NULL
            ORDER BY ts
        """.trimIndent()

        return database.rawQuery(sql, null).use { cursor ->
            val tsCol = cursor.getColumnIndexOrThrow("ts")
            val latCol = cursor.getColumnIndexOrThrow("lat")
            val lngCol = cursor.getColumnIndexOrThrow("lng")
            val accCol = cursor.getColumnIndexOrThrow(if (hasAccuracyColumn) "accuracy" else "payload")
            val result = ArrayList<LocationPoint>(minOf(count, 50_000))
            var processed = 0
            while (cursor.moveToNext()) {
                val accuracy = if (hasAccuracyColumn) {
                    if (cursor.isNull(accCol)) null else cursor.getFloat(accCol)
                } else {
                    parseAccuracyFromPayload(cursor.getString(accCol))
                }
                result += LocationPoint(
                    ts = cursor.getLong(tsCol),
                    lat = cursor.getDouble(latCol),
                    lng = cursor.getDouble(lngCol),
                    accuracyMeters = accuracy,
                )
                processed++
                if (processed % 500 == 0) onProgress(processed)
            }
            onProgress(processed)
            result
        }
    }

    private fun parseAccuracyFromPayload(payload: String?): Float? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            JSONObject(payload).optDouble("accuracy").takeIf { !it.isNaN() }?.toFloat()
        }.getOrNull()
    }
}
