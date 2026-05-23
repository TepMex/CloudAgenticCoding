package com.tepmex.ankidashboard.data.sync

import com.github.luben.zstd.Zstd
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * AnkiWeb sync v11 HTTP client (download-only).
 * Redirect handling follows [anki meta_with_redirect](https://github.com/ankitects/anki/blob/main/rslib/src/sync/collection/meta.rs)
 * and the web dashboard proxy in [anki-dashboard vite.config.js](https://github.com/TepMex/anki-dashboard).
 */
class SyncHttpClient(
    endpoint: String,
    var hkey: String = "",
    private val sessionKey: String = generateSessionKey(),
) {
    var baseUrl: String = resolveSyncBaseUrl(endpoint)
        private set
    var syncHost: String? = parseSyncHostFromEndpoint(endpoint)
        private set

    /** True when the last request changed [syncHost] / [baseUrl] (e.g. HTTP 308). */
    var hostChangedOnLastRequest: Boolean = false
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun hasResolvedShard(): Boolean = isNumberedSyncShard(syncHost)

    /** Point the client at the public AnkiWeb entry host before a fresh login. */
    fun resetToDefaultEntryHost() {
        baseUrl = resolveSyncBaseUrl(null)
        syncHost = null
        hostChangedOnLastRequest = false
    }

    @Throws(IOException::class)
    fun login(username: String, password: String): String {
        val decompressed = post(
            method = "hostKey",
            phase = "login",
            body = mapOf("u" to username, "p" to password),
        )
        val jsonText = String(decompressed, Charsets.UTF_8)
        val json = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            throw syncError(
                phase = "login",
                method = "hostKey",
                message = "Login response was not valid JSON",
                cause = e,
                responseSnippet = jsonText.take(500),
            )
        }
        val key = json.optString("key")
        if (key.isBlank()) {
            throw syncError(
                phase = "login",
                method = "hostKey",
                message = "Login failed: no host key returned",
                responseSnippet = jsonText.take(500),
            )
        }
        hkey = key
        return key
    }

    @Throws(IOException::class)
    fun meta(): JSONObject {
        if (hkey.isBlank()) {
            throw syncError(
                phase = "meta",
                method = "meta",
                message = "Not logged in (missing host key). Enter your AnkiWeb password and try again.",
            )
        }
        val decompressed = post(
            method = "meta",
            phase = "meta",
            body = mapOf("v" to SYNC_VERSION, "cv" to CLIENT_VERSION),
        )
        val jsonText = String(decompressed, Charsets.UTF_8)
        val json = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            throw syncError(
                phase = "meta",
                method = "meta",
                message = "Server meta response was not valid JSON",
                cause = e,
                responseSnippet = jsonText.take(500),
            )
        }
        applyHostNumberFromMeta(json)
        return json
    }

    @Throws(IOException::class)
    fun download(onProgress: ((received: Long, total: Long?) -> Unit)? = null): ByteArray {
        return post(method = "download", phase = "download", body = emptyMap(), onProgress = onProgress)
    }

    @Throws(IOException::class)
    private fun post(
        method: String,
        phase: String,
        body: Map<String, Any>,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
    ): ByteArray {
        val compressedBody = try {
            compressJson(body)
        } catch (e: Exception) {
            throw syncError(
                phase = phase,
                method = method,
                message = "Failed to compress sync request",
                cause = e,
            )
        }

        var redirectHops = 0
        var retriedResolvedHost = false
        while (true) {
            hostChangedOnLastRequest = false
            val requestUrl = "${baseUrl}sync/$method"
            val request = Request.Builder()
                .url(requestUrl)
                .post(compressedBody.toRequestBody(OCTET_STREAM))
                .headers(buildHeaders().build())
                .build()

            try {
                http.newCall(request).execute().use { response ->
                    if (response.code in REDIRECT_STATUS_CODES && redirectHops < MAX_REDIRECT_HOPS) {
                        val location = response.header("Location")
                        if (!location.isNullOrBlank()) {
                            applySyncRedirect(location, requestUrl)
                            redirectHops++
                            continue
                        }
                    }

                    val previousSyncHost = syncHost
                    val resolvedSyncHost = response.header("x-resolved-sync-host")
                    if (!resolvedSyncHost.isNullOrBlank() && isNumberedSyncShard(resolvedSyncHost)) {
                        applyResolvedSyncHost(resolvedSyncHost)
                    }

                    if (
                        !response.isSuccessful &&
                        !resolvedSyncHost.isNullOrBlank() &&
                        isNumberedSyncShard(resolvedSyncHost) &&
                        resolvedSyncHost != previousSyncHost &&
                        !retriedResolvedHost
                    ) {
                        retriedResolvedHost = true
                        continue
                    }

                    if (!response.isSuccessful) {
                        val errText = response.body?.string().orEmpty()
                        val message = when {
                            response.code == 400 && hkey.isBlank() ->
                                "Sync $method failed (HTTP 400): not logged in. Enter your AnkiWeb password."
                            response.code == 400 ->
                                "Sync $method failed (HTTP 400)"
                            else ->
                                "Sync $method failed (HTTP ${response.code})"
                        }
                        throw syncError(
                            phase = phase,
                            method = method,
                            message = message,
                            httpStatus = response.code,
                            responseSnippet = errText.take(1000),
                        )
                    }

                    val originalSize = response.header("anki-original-size")?.toLongOrNull() ?: 0L
                    val bodyBytes = response.body?.bytes()
                        ?: throw syncError(
                            phase = phase,
                            method = method,
                            message = "Sync response has no body",
                        )
                    onProgress?.invoke(bodyBytes.size.toLong(), originalSize.takeIf { it > 0 })
                    return try {
                        decompressBody(bodyBytes, originalSize)
                    } catch (e: Exception) {
                        throw syncError(
                            phase = phase,
                            method = method,
                            message = "Failed to decompress sync response",
                            cause = e,
                        )
                    }
                }
            } catch (e: SyncException) {
                throw e
            } catch (e: IOException) {
                throw syncError(
                    phase = phase,
                    method = method,
                    message = e.message ?: "Network error during sync $method",
                    cause = e,
                )
            } catch (e: Exception) {
                throw syncError(
                    phase = phase,
                    method = method,
                    message = e.message ?: "Unexpected error during sync $method",
                    cause = e,
                )
            }
        }
    }

    private fun applyHostNumberFromMeta(meta: JSONObject) {
        if (!meta.has("hostNum") || meta.isNull("hostNum")) return
        val hostNum = meta.optInt("hostNum", 0)
        if (hostNum <= 0) return
        val hostname = "sync$hostNum.ankiweb.net"
        if (syncHost != hostname) {
            applyResolvedSyncHost(hostname)
        }
    }

    private fun applyResolvedSyncHost(hostname: String) {
        val previous = syncHost
        syncHost = hostname
        baseUrl = "https://$hostname/"
        if (previous != hostname) {
            hostChangedOnLastRequest = true
        }
    }

    private fun applySyncRedirect(location: String, requestUrl: String) {
        val resolved = resolveRedirectTarget(requestUrl, location)
        val previous = syncHost
        syncHost = resolved.host
        baseUrl = "https://${resolved.host}/"
        if (previous != resolved.host) {
            hostChangedOnLastRequest = true
        }
    }

    private fun syncError(
        phase: String,
        method: String,
        message: String,
        httpStatus: Int? = null,
        responseSnippet: String? = null,
        cause: Throwable? = null,
    ): SyncException = SyncException(
        message = message,
        phase = phase,
        method = method,
        url = "${baseUrl}sync/$method",
        httpStatus = httpStatus,
        responseSnippet = responseSnippet,
        syncHost = syncHost,
        baseUrl = baseUrl,
        cause = cause,
    )

    private fun buildHeaders(): okhttp3.Headers.Builder {
        val headerJson = JSONObject().apply {
            put("v", SYNC_VERSION)
            put("k", hkey)
            put("s", sessionKey)
            put("c", CLIENT_VERSION)
        }
        return okhttp3.Headers.Builder()
            .add("Content-Type", "application/octet-stream")
            .add("anki-sync", headerJson.toString())
    }

    private fun compressJson(data: Map<String, Any>): ByteArray {
        val json = JSONObject(data).toString()
        val bytes = json.toByteArray(Charsets.UTF_8)
        return Zstd.compress(bytes)
    }

    private fun decompressBody(compressed: ByteArray, originalSize: Long): ByteArray {
        val size = when {
            originalSize > 0 -> originalSize.toInt()
            else -> {
                val frameSize = Zstd.getFrameContentSize(compressed)
                if (frameSize <= 0 || frameSize > Int.MAX_VALUE.toLong()) {
                    throw IOException("Failed to determine decompressed sync response size")
                }
                frameSize.toInt()
            }
        }
        return Zstd.decompress(compressed, size)
    }

    companion object {
        private const val SYNC_VERSION = 11
        private const val CLIENT_VERSION = "anki-dashboard-android/1.0"
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private const val DEFAULT_ENDPOINT = "https://sync.ankiweb.net/"
        private const val MAX_REDIRECT_HOPS = 5
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private const val SESSION_TABLE =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun resolveSyncBaseUrl(endpoint: String?): String {
            val normalized = endpoint?.takeIf { it.isNotBlank() } ?: DEFAULT_ENDPOINT
            val withSlash = if (normalized.endsWith("/")) normalized else "$normalized/"
            return withSlash
        }

        fun parseSyncHostFromEndpoint(endpoint: String?): String? {
            if (endpoint.isNullOrBlank()) return null
            return try {
                val hostname = URL(endpoint).host
                if (isNumberedSyncShard(hostname)) hostname else null
            } catch (_: Exception) {
                null
            }
        }

        fun isNumberedSyncShard(hostname: String?): Boolean =
            hostname != null && Regex("^sync\\d+\\.").containsMatchIn(hostname)

        /**
         * Treat the public entry host and a numbered shard as the same saved session.
         * AnkiWeb redirects accounts to syncN.ankiweb.net after login/meta.
         */
        fun endpointsEquivalent(savedEndpoint: String?, formEndpoint: String?): Boolean {
            val saved = resolveSyncBaseUrl(savedEndpoint)
            val form = resolveSyncBaseUrl(formEndpoint)
            if (saved == form) return true
            val entry = resolveSyncBaseUrl(null)
            return (parseSyncHostFromEndpoint(savedEndpoint) != null && form == entry) ||
                (parseSyncHostFromEndpoint(formEndpoint) != null && saved == entry)
        }

        /**
         * Resolve AnkiWeb 308 Location against the request URL, preserving the sync path when
         * the server redirects to a bare origin (same as the web dashboard dev proxy).
         */
        fun resolveRedirectTarget(requestUrl: String, location: String): URL {
            val current = URL(requestUrl)
            val redirect = URL(current, location)
            if (redirect.path == "/" || redirect.path.isNullOrEmpty()) {
                val path = current.path + (current.query?.let { "?$it" } ?: "")
                return URL(redirect.protocol, redirect.host, redirect.port, path)
            }
            return redirect
        }

        fun generateSessionKey(): String {
            var n = Random.nextLong().absoluteValue + 1L
            val base = SESSION_TABLE.length.toLong()
            val chars = StringBuilder()
            while (n > 0L) {
                chars.insert(0, SESSION_TABLE[(n % base).toInt()])
                n /= base
            }
            return if (chars.isEmpty()) "a" else chars.toString()
        }
    }
}
