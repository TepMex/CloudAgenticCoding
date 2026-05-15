package com.tepmex.chesswatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ActivityStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): PersistedState {
        val raw = prefs.getString(KEY_ACTIVITIES, null) ?: return PersistedState.default()
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("items")
            val items = buildList {
                for (i in 0 until arr.length()) {
                    add(TrackedActivity.fromJson(arr.getJSONObject(i)))
                }
            }
            if (items.isEmpty()) {
                PersistedState.default()
            } else {
                var selectedId = root.optString("selectedId", items.first().id)
                if (items.none { it.id == selectedId }) {
                    selectedId = items.first().id
                }
                val segmentStart = root.optLong("segmentStartMs", System.currentTimeMillis())
                PersistedState(items, selectedId, segmentStart)
            }
        } catch (_: Exception) {
            PersistedState.default()
        }
    }

    fun save(state: PersistedState) {
        val arr = JSONArray()
        state.items.forEach { arr.put(it.toJson()) }
        val root =
            JSONObject().apply {
                put("items", arr)
                put("selectedId", state.selectedId)
                put("segmentStartMs", state.segmentStartMs)
            }
        prefs.edit().putString(KEY_ACTIVITIES, root.toString()).apply()
    }

    data class PersistedState(
        val items: List<TrackedActivity>,
        val selectedId: String?,
        val segmentStartMs: Long,
    ) {
        companion object {
            fun default(): PersistedState {
                val idle =
                    TrackedActivity(
                        id = IDLE_ID,
                        name = "idle",
                        accumulatedMs = 0L,
                    )
                val now = System.currentTimeMillis()
                return PersistedState(listOf(idle), idle.id, now)
            }
        }
    }

    companion object {
        private const val PREFS = "chesswatch_state"
        private const val KEY_ACTIVITIES = "activities_v1"
        const val IDLE_ID = "idle"
    }
}
