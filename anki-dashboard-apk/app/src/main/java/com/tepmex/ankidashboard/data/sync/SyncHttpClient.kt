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
 * Mirrors [anki-dashboard SyncHttpClient](https://github.com/TepMex/anki-dashboard).
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

    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun hasResolvedShard(): Boolean = isNumberedSyncShard(syncHost)

    @Throws(IOException::class)
    fun login(username: String, password: String): String {
        val decompressed = post(
            method = "hostKey",
            phase = "login",
            body = mapOf("u" to username, "p" to password),
            useSessionKey = false,
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
        val decompressed = post(
            method = "meta",
            phase = "meta",
            body = mapOf("v" to SYNC_VERSION, "cv" to CLIENT_VERSION),
            useSessionKey = false,
        )
        val jsonText = String(decompressed, Charsets.UTF_8)
        return try {
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
        useSessionKey: Boolean = true,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
        retriedShard: Boolean = false,
        retriedRedirect: Boolean = false,
    ): ByteArray {
        val url = "${baseUrl}sync/$method"
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

        val request = Request.Builder()
            .url(url)
            .post(compressedBody.toRequestBody(OCTET_STREAM))
            .headers(buildHeaders(useSessionKey).build())
            .build()

        try {
            http.newCall(request).execute().use { response ->
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
                    !retriedShard
                ) {
                    return post(
                        method = method,
                        phase = phase,
                        body = body,
                        useSessionKey = useSessionKey,
                        onProgress = onProgress,
                        retriedShard = true,
                        retriedRedirect = retriedRedirect,
                    )
                }

                if (response.code == 308 && !retriedRedirect) {
                    val location = response.header("location")
                    if (!location.isNullOrBlank()) {
                        applySyncRedirect(location)
                        return post(
                            method = method,
                            phase = phase,
                            body = body,
                            useSessionKey = useSessionKey,
                            onProgress = onProgress,
                            retriedShard = retriedShard,
                            retriedRedirect = true,
                        )
                    }
                }

                if (!response.isSuccessful) {
                    val errText = response.body?.string().orEmpty()
                    throw syncError(
                        phase = phase,
                        method = method,
                        message = "Sync $method failed (HTTP ${response.code})",
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

    private fun applyResolvedSyncHost(hostname: String) {
        syncHost = hostname
        baseUrl = "https://$hostname/"
    }

    private fun applySyncRedirect(location: String) {
        val url = URL(location)
        syncHost = url.host
        baseUrl = "${url.protocol}://${url.host}/"
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

    private fun buildHeaders(useSessionKey: Boolean): okhttp3.Headers.Builder {
        val headerJson = JSONObject().apply {
            put("v", SYNC_VERSION)
            put("k", hkey)
            put("s", if (useSessionKey) sessionKey else "")
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
