package com.tepmex.runninglog.mi

import com.tepmex.runninglog.domain.ParsedSportRecord
import com.tepmex.runninglog.domain.SportRecordParser
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

    suspend fun fetchRunningWorkouts(
        relativeUid: Long,
        startWatermark: Long = 0L,
        pageLimit: Int = 50,
    ): List<ParsedSportRecord> = withContext(Dispatchers.IO) {
        val out = ArrayList<ParsedSportRecord>()
        var wm = startWatermark
        var hasMore = true
        while (hasMore) {
            val result = request(
                method = "GET",
                path = MiConstants.SPORT_RECORDS_BY_WATERMARK,
                params = mapOf(
                    "relative_uid" to relativeUid,
                    "watermark" to wm,
                    "limit" to pageLimit,
                ),
            )
            val payload = result.optJSONObject("result") ?: JSONObject()
            val records = payload.optJSONArray("sport_records")
            hasMore = payload.optBoolean("has_more", false)
            if (records == null || records.length() == 0) break
            for (i in 0 until records.length()) {
                val rec = records.getJSONObject(i)
                wm = maxOf(wm, rec.optLong("watermark", 0L))
                val parsed = SportRecordParser.parseCloudRecord(rec) ?: continue
                if (SportRecordParser.isRunning(parsed.sportType)) {
                    out += parsed
                }
            }
        }
        out
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
