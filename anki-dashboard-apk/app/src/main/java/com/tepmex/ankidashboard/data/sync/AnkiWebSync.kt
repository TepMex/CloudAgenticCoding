package com.tepmex.ankidashboard.data.sync

import android.content.Context
import com.tepmex.ankidashboard.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class SyncProgress(
    val phase: String,
    val received: Long = 0,
    val total: Long? = null,
)

data class SyncResult(
    val serverMeta: JSONObject?,
    val byteLength: Int,
)

/**
 * Download-only AnkiWeb sync — mirrors web [AnkiWebSync].
 */
class AnkiWebSync(
    private val context: Context,
    endpoint: String,
    hkey: String = "",
) {
    private val client = SyncHttpClient(endpoint, hkey)

    /** Exposes HTTP client state for error reports (host/base URL after shard resolution). */
    fun clientForDiagnostics(): SyncHttpClient = client

    @Throws(IOException::class)
    fun login(username: String, password: String): String =
        client.login(username, password)

    @Throws(IOException::class)
    private fun fetchMeta(): JSONObject? {
        return try {
            client.meta()
        } catch (e: IOException) {
            if (!client.hasResolvedShard()) throw e
            SyncDiagnostics.logFailure("meta (first attempt)", e)
            null
        }
    }

    suspend fun sync(
        username: String,
        password: String?,
        endpoint: String,
        preferences: AppPreferences,
        onProgress: ((SyncProgress) -> Unit)? = null,
    ): SyncResult = withContext(Dispatchers.IO) {
        val existingAuth = preferences.getAnkiWebAuth()
        val normalizedEndpoint = SyncHttpClient.resolveSyncBaseUrl(endpoint)
        val canReuseSession = password.isNullOrBlank() &&
            !existingAuth.hkey.isNullOrBlank() &&
            existingAuth.username == username &&
            SyncHttpClient.resolveSyncBaseUrl(existingAuth.endpoint) == normalizedEndpoint

        if (!canReuseSession) {
            if (password.isNullOrBlank()) {
                throw SyncException(
                    message = "Password required for login (no saved session for this username/endpoint)",
                    phase = "login",
                )
            }
            login(username, password)
        } else {
            client.hkey = existingAuth.hkey.orEmpty()
        }

        if (!password.isNullOrBlank() && client.hasResolvedShard()) {
            login(username, password)
        }

        onProgress?.invoke(SyncProgress("meta"))
        var serverMeta = fetchMeta()
        if (serverMeta == null && !password.isNullOrBlank()) {
            login(username, password)
            serverMeta = fetchMeta()
        }
        if (serverMeta == null && client.hasResolvedShard()) {
            serverMeta = fetchMeta()
        }

        onProgress?.invoke(SyncProgress("download"))
        val collectionData = client.download { received, total ->
            onProgress?.invoke(SyncProgress("download", received, total))
        }

        validateCollectionBytes(collectionData)

        onProgress?.invoke(SyncProgress("saving"))
        try {
            CollectionStore.saveCollection(
                context,
                collectionData,
                mapOf(
                    "serverMod" to serverMeta?.opt("mod"),
                    "serverUsn" to serverMeta?.opt("usn"),
                ),
            )
        } catch (e: Exception) {
            throw SyncException(
                message = "Failed to save collection locally: ${e.message ?: e.javaClass.simpleName}",
                phase = "saving",
                cause = e,
            )
        }

        val savedEndpoint = if (client.syncHost != null) {
            "https://${client.syncHost}/"
        } else {
            client.baseUrl
        }
        preferences.saveAnkiWebAuth(
            hkey = client.hkey,
            endpoint = savedEndpoint,
            username = username,
            syncedAt = System.currentTimeMillis(),
            serverMod = serverMeta?.optLong("mod")?.takeIf { it != 0L || serverMeta.has("mod") },
        )
        preferences.saveAnkiWebSettings(username, savedEndpoint)

        SyncResult(serverMeta, collectionData.size)
    }

    private fun validateCollectionBytes(data: ByteArray) {
        if (data.isEmpty()) {
            throw SyncException(
                message = "Downloaded collection is empty",
                phase = "download",
            )
        }
        val header = String(data, 0, minOf(16, data.size), Charsets.US_ASCII)
        if (!header.startsWith("SQLite format 3")) {
            throw SyncException(
                message = "Downloaded file does not look like collection.anki2 (missing SQLite header)",
                phase = "download",
                responseSnippet = header,
            )
        }
    }
}
