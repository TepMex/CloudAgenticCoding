package com.tepmex.idealtiming.mi

import com.tepmex.idealtiming.domain.SleepRecord
import com.tepmex.idealtiming.domain.SleepRecordParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MiHealthClient(
    private var token: AuthToken,
    private val auth: MiAuth? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val refreshMutex = Mutex()

    fun updateToken(token: AuthToken) {
        this.token = token
    }

    /**
     * Fetch recent sleep aggregates for [relativeUid] (usually the signed-in user id).
     */
    suspend fun fetchSleepRecords(
        relativeUid: Long,
        days: Int = 3,
        nowEpochSec: Long = System.currentTimeMillis() / 1000L,
    ): List<SleepRecord> = withContext(Dispatchers.IO) {
        val windowDays = days.coerceAtLeast(1)
        val end = nowEpochSec
        val start = end - 86_400L * windowDays + 1
        val result = request(
            method = "GET",
            path = MiConstants.AGGREGATED_FITNESS_BY_TIME,
            params = mapOf(
                "relative_uid" to relativeUid,
                "key" to MiConstants.DATA_KEY_SLEEP,
                "tag" to MiConstants.DATA_TAG_DAILY_REPORT,
                "start_time" to start,
                "end_time" to end,
                "limit" to windowDays,
            ),
        )
        val payload = result.optJSONObject("result") ?: JSONObject()
        val list = payload.optJSONArray("data_list") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                SleepRecordParser.parseAggregatedItem(item)?.let { add(it) }
            }
        }
    }

    suspend fun request(
        method: String,
        path: String,
        params: Map<String, Any?>? = null,
        allowRefresh: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            encryptedRequest(method, path, params)
        } catch (e: TokenExpiredException) {
            if (!allowRefresh || auth == null || !token.canRefresh) throw e
            refreshMutex.withLock {
                val refreshed = auth.refresh()
                token = refreshed
            }
            encryptedRequest(method, path, params)
        }
    }

    private fun encryptedRequest(
        method: String,
        path: String,
        params: Map<String, Any?>?,
    ): JSONObject {
        if (!token.isAuthenticated) {
            throw AuthException("Not logged in")
        }
        val enc = MiCrypto.buildEncryptedParams(method, path, token.ssecurity, params)
        val nonce = enc.getValue("_nonce")
        val base = MiConstants.healthApiBase(token.region).trimEnd('/')
        val urlBuilder = (base + path).toHttpUrl().newBuilder()
        val bodyBuilder = FormBody.Builder()
        for ((k, v) in enc) {
            if (method.equals("GET", ignoreCase = true)) {
                urlBuilder.addQueryParameter(k, v)
            } else {
                bodyBuilder.add(k, v)
            }
        }
        val reqBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", MiConstants.DEFAULT_USER_AGENT)
            .header("region_tag", token.region.ifBlank { MiConstants.REGION_TAG_DEFAULT })
            .header("handleparams", "true")
            .header("Cookie", "cUserId=${token.cUserId}; serviceToken=${token.serviceToken}")
        val req = if (method.equals("GET", ignoreCase = true)) {
            reqBuilder.get().build()
        } else {
            reqBuilder.post(bodyBuilder.build()).build()
        }
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401) throw TokenExpiredException()
            if (resp.code != 200) {
                throw ApiException("HTTP ${resp.code} for $method $path", code = resp.code)
            }
            val cipher = resp.body?.string().orEmpty()
            val decrypted = MiCrypto.decryptResponse(token.ssecurity, nonce, cipher)
            val result = decrypted as? JSONObject
                ?: throw ApiException("Non-JSON decrypt result")
            val code = result.optInt("code", -1)
            if (code != 0) {
                val msg = sequenceOf("message", "msg", "desc", "description")
                    .map { result.optString(it) }
                    .firstOrNull { it.isNotBlank() }
                    ?: "unknown"
                throw ApiException("API error code=$code: $msg", code = code)
            }
            return result
        }
    }
}
