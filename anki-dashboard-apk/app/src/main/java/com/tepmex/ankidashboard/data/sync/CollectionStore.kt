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
        if (!dir.exists() && !dir.mkdirs()) {
            throw SyncException(
                message = "Cannot create storage directory: ${dir.absolutePath}",
                phase = "saving",
            )
        }
        val collection = collectionFile(context)
        try {
            collection.writeBytes(data)
        } catch (e: Exception) {
            throw SyncException(
                message = "Cannot write collection file: ${e.message ?: e.javaClass.simpleName}",
                phase = "saving",
                cause = e,
            )
        }
        val metaJson = JSONObject()
        meta.forEach { (key, value) -> metaJson.put(key, value) }
        metaJson.put("savedAt", System.currentTimeMillis())
        try {
            File(dir, META_FILE).writeText(metaJson.toString())
        } catch (e: Exception) {
            throw SyncException(
                message = "Collection saved but metadata write failed: ${e.message ?: e.javaClass.simpleName}",
                phase = "saving",
                cause = e,
            )
        }
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
