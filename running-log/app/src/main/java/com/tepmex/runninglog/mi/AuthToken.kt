package com.tepmex.runninglog.mi

import org.json.JSONObject

data class AuthToken(
    val userId: String = "",
    val cUserId: String = "",
    val serviceToken: String = "",
    val ssecurity: String = "",
    val passToken: String = "",
    val deviceId: String = "",
    val region: String = MiConstants.REGION_TAG_DEFAULT,
    val username: String = "",
) {
    val isAuthenticated: Boolean
        get() = serviceToken.isNotBlank() && ssecurity.isNotBlank()

    val canRefresh: Boolean
        get() = passToken.isNotBlank() && userId.isNotBlank()

    fun toJson(): String = JSONObject()
        .put("user_id", userId)
        .put("c_user_id", cUserId)
        .put("service_token", serviceToken)
        .put("ssecurity", ssecurity)
        .put("pass_token", passToken)
        .put("device_id", deviceId)
        .put("region", region)
        .put("username", username)
        .toString()

    companion object {
        fun fromJson(raw: String?): AuthToken? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                AuthToken(
                    userId = o.optString("user_id"),
                    cUserId = o.optString("c_user_id"),
                    serviceToken = o.optString("service_token"),
                    ssecurity = o.optString("ssecurity"),
                    passToken = o.optString("pass_token"),
                    deviceId = o.optString("device_id"),
                    region = o.optString("region", MiConstants.REGION_TAG_DEFAULT),
                    username = o.optString("username"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
