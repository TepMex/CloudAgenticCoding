package com.tepmex.chesswatch

import org.json.JSONObject

data class TrackedActivity(
    val id: String,
    val name: String,
    val accumulatedMs: Long,
    val tileColorArgb: Int = PastelTileColors.fromSeed(id),
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("name", name)
            put("accumulatedMs", accumulatedMs)
            put("tileColorArgb", tileColorArgb)
        }

    companion object {
        fun fromJson(o: JSONObject): TrackedActivity {
            val id = o.getString("id")
            return TrackedActivity(
                id = id,
                name = o.getString("name"),
                accumulatedMs = o.getLong("accumulatedMs"),
                tileColorArgb =
                    if (o.has("tileColorArgb")) {
                        o.getInt("tileColorArgb")
                    } else {
                        PastelTileColors.fromSeed(id)
                    },
            )
        }
    }
}
