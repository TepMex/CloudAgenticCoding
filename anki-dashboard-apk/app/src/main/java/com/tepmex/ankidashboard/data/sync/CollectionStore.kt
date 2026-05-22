package com.tepmex.ankidashboard.data.sync

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Local cache for collection.anki2 downloaded from AnkiWeb.
 */
object CollectionStore {
    private const val DIR_NAME = "ankiweb"
    private const val COLLECTION_FILE = "collection.anki2"
    private const val META_FILE = "collection-meta.json"

    fun collectionFile(context: Context): File =
        File(File(context.filesDir, DIR_NAME), COLLECTION_FILE)

    fun hasCollection(context: Context): Boolean =
        collectionFile(context).isFile

    fun saveCollection(context: Context, data: ByteArray, meta: Map<String, Any?> = emptyMap()) {
        val dir = File(context.filesDir, DIR_NAME)
        dir.mkdirs()
        collectionFile(context).writeBytes(data)
        val metaJson = JSONObject()
        meta.forEach { (key, value) -> metaJson.put(key, value) }
        metaJson.put("savedAt", System.currentTimeMillis())
        File(dir, META_FILE).writeText(metaJson.toString())
    }

    fun loadMeta(context: Context): JSONObject? {
        val metaFile = File(File(context.filesDir, DIR_NAME), META_FILE)
        if (!metaFile.isFile) return null
        return try {
            JSONObject(metaFile.readText())
        } catch (_: Exception) {
            null
        }
    }
}
