package com.tepmex.ankidashboard.data.sync

import android.content.Context
import com.tepmex.ankidashboard.data.AppPreferences
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

    @Throws(IOException::class)
    fun login(username: String, password: String): String =
        client.login(username, password)

    @Throws(IOException::class)
    private fun fetchMeta(): JSONObject? {
        return try {
            client.meta()
        } catch (e: IOException) {
            if (!client.hasResolvedShard()) throw e
            null
        }
    }

    @Throws(IOException::class)
    suspend fun sync(
        username: String,
        password: String?,
        endpoint: String,
        preferences: AppPreferences,
        onProgress: ((SyncProgress) -> Unit)? = null,
    ): SyncResult {
        val existingAuth = preferences.getAnkiWebAuth()
        val normalizedEndpoint = SyncHttpClient.resolveSyncBaseUrl(endpoint)
        val canReuseSession = password.isNullOrBlank() &&
            !existingAuth.hkey.isNullOrBlank() &&
            existingAuth.username == username &&
            SyncHttpClient.resolveSyncBaseUrl(existingAuth.endpoint) == normalizedEndpoint

        if (!canReuseSession) {
            if (password.isNullOrBlank()) {
                throw IOException("Password required for login")
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
        if (serverMeta == null && client.hasResolvedShard()) {
            serverMeta = fetchMeta()
        }

        onProgress?.invoke(SyncProgress("download"))
        val collectionData = client.download { received, total ->
            onProgress?.invoke(SyncProgress("download", received, total))
        }

        onProgress?.invoke(SyncProgress("saving"))
        CollectionStore.saveCollection(
            context,
            collectionData,
            mapOf(
                "serverMod" to serverMeta?.opt("mod"),
                "serverUsn" to serverMeta?.opt("usn"),
            ),
        )

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

        return SyncResult(serverMeta, collectionData.size)
    }
}
