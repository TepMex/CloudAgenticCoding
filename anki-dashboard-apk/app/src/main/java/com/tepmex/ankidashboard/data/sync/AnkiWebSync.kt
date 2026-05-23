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
 * Download-only AnkiWeb sync — mirrors web [AnkiWebSync.js] at
 * [97e7425](https://github.com/TepMex/anki-dashboard/commit/97e742545188380d02192818437aada9d8b9ccdb).
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
    private fun loginOnResolvedHost(username: String, password: String) {
        login(username, password)
        var attempts = 0
        while (client.hostChangedOnLastRequest && attempts < MAX_HOST_LOGIN_ATTEMPTS) {
            login(username, password)
            attempts++
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
        val wantsExplicitLogin = !password.isNullOrBlank()
        val canReuseSession = !wantsExplicitLogin &&
            !existingAuth.hkey.isNullOrBlank() &&
            existingAuth.username == username &&
            SyncHttpClient.endpointsEquivalent(existingAuth.endpoint, endpoint)

        when {
            wantsExplicitLogin -> {
                client.resetToDefaultEntryHost()
                loginOnResolvedHost(username, password!!)
            }
            canReuseSession -> {
                client.hkey = existingAuth.hkey.orEmpty()
                if (client.hkey.isBlank()) {
                    throw SyncException(
                        message = "Saved AnkiWeb session is incomplete. Enter your password to sync.",
                        phase = "login",
                    )
                }
            }
            else -> {
                throw SyncException(
                    message = "Password required for login (no saved session for this username/endpoint)",
                    phase = "login",
                )
            }
        }

        onProgress?.invoke(SyncProgress("meta"))
        var serverMeta: JSONObject? = null
        try {
            serverMeta = client.meta()
        } catch (e: IOException) {
            if (!client.hasResolvedShard()) {
                if (password.isNullOrBlank()) {
                    val httpStatus = (e as? SyncException)?.httpStatus
                    if (httpStatus == 400 || httpStatus == 403 || client.hostChangedOnLastRequest) {
                        throw SyncException(
                            message = "AnkiWeb session expired or moved to ${client.syncHost ?: "another server"}. " +
                                "Enter your password to sync again.",
                            phase = "meta",
                            cause = e,
                        )
                    }
                }
                throw e
            }
            SyncDiagnostics.logFailure("meta (ignored on numbered shard)", e)
        }

        if (!password.isNullOrBlank() && client.hasResolvedShard()) {
            login(username, password)
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
            serverMod = serverMeta?.optLong("mod")?.takeIf { serverMeta.has("mod") },
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

    companion object {
        private const val MAX_HOST_LOGIN_ATTEMPTS = 3
    }
}
