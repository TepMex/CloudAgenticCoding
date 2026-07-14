package com.tepmex.ankientertainer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.likedDataStore: DataStore<Preferences> by preferencesDataStore(name = "anki_entertainer_liked")

private val KEY_LIKED_JSON = stringPreferencesKey("liked_chunks_by_vocab")

data class StoredChunk(
    val id: String,
    val text: String,
    val modelName: String?,
)

class LikedChunksRepository(private val context: Context) {

    suspend fun getLiked(vocab: String): List<StoredChunk> {
        val map = readMap()
        return map[vocab].orEmpty()
    }

    suspend fun like(vocab: String, chunk: StoredChunk) {
        context.likedDataStore.edit { prefs ->
            val map = decodeMap(prefs[KEY_LIKED_JSON])
            val list = map.getOrDefault(vocab, mutableListOf()).toMutableList()
            if (list.none { it.id == chunk.id }) {
                list.add(chunk)
            }
            map[vocab] = list
            prefs[KEY_LIKED_JSON] = encodeMap(map)
        }
    }

    suspend fun unlike(vocab: String, chunkId: String) {
        context.likedDataStore.edit { prefs ->
            val map = decodeMap(prefs[KEY_LIKED_JSON])
            val list = map[vocab]?.filterNot { it.id == chunkId }.orEmpty()
            if (list.isEmpty()) {
                map.remove(vocab)
            } else {
                map[vocab] = list.toMutableList()
            }
            prefs[KEY_LIKED_JSON] = encodeMap(map)
        }
    }

    private suspend fun readMap(): Map<String, List<StoredChunk>> {
        val json = context.likedDataStore.data.first()[KEY_LIKED_JSON]
        return decodeMap(json)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

private fun decodeMap(json: String?): MutableMap<String, MutableList<StoredChunk>> {
    if (json.isNullOrBlank()) return mutableMapOf()
    return try {
        val root = JSONObject(json)
        val result = mutableMapOf<String, MutableList<StoredChunk>>()
        for (key in root.keys()) {
            val arr = root.optJSONArray(key) ?: continue
            val chunks = mutableListOf<StoredChunk>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                chunks.add(
                    StoredChunk(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        text = o.optString("text", ""),
                        modelName = o.optString("model").takeIf { it.isNotBlank() },
                    ),
                )
            }
            if (chunks.isNotEmpty()) {
                result[key] = chunks
            }
        }
        result
    } catch (_: Exception) {
        mutableMapOf()
    }
}

private fun encodeMap(map: Map<String, List<StoredChunk>>): String {
    val root = JSONObject()
    for ((vocab, chunks) in map) {
        if (chunks.isEmpty()) continue
        val arr = JSONArray()
        for (chunk in chunks) {
            arr.put(
                JSONObject()
                    .put("id", chunk.id)
                    .put("text", chunk.text)
                    .put("model", chunk.modelName.orEmpty()),
            )
        }
        root.put(vocab, arr)
    }
    return root.toString()
}
