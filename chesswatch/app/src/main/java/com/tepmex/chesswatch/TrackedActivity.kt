package com.tepmex.chesswatch

import org.json.JSONObject

data class TrackedActivity(
    val id: String,
    val name: String,
    val accumulatedMs: Long,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("name", name)
            put("accumulatedMs", accumulatedMs)
        }

    companion object {
        fun fromJson(o: JSONObject): TrackedActivity =
            TrackedActivity(
                id = o.getString("id"),
                name = o.getString("name"),
                accumulatedMs = o.getLong("accumulatedMs"),
            )
    }
}
