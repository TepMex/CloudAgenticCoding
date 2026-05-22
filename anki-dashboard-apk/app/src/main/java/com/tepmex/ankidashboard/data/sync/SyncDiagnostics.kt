package com.tepmex.ankidashboard.data.sync

import com.tepmex.ankidashboard.BuildConfig
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object SyncDiagnostics {
    const val LOG_TAG = "AnkiWebSync"

    data class SyncErrorReport(
        val summary: String,
        val details: String,
    )

    fun logFailure(phase: String?, throwable: Throwable) {
        Log.e(LOG_TAG, phase?.let { "Sync failed during $it" } ?: "Sync failed", throwable)
    }

    fun buildReport(
        throwable: Throwable,
        phase: String? = null,
        username: String? = null,
        endpoint: String? = null,
        syncHost: String? = null,
        baseUrl: String? = null,
        reusedSession: Boolean? = null,
    ): SyncErrorReport {
        val summary = userSummary(throwable)
        val details = buildString {
            appendLine("=== Anki Dashboard sync error ===")
            appendLine("Time: ${System.currentTimeMillis()}")
            appendLine("App: ${BuildConfig.APPLICATION_ID} (v${BuildConfig.VERSION_NAME})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            phase?.let { appendLine("Phase: $it") }
            username?.let { appendLine("Username: $it") }
            endpoint?.let { appendLine("Endpoint (form): $it") }
            baseUrl?.let { appendLine("Base URL (client): $it") }
            syncHost?.let { appendLine("Sync host: $it") }
            reusedSession?.let { appendLine("Reused saved session: $it") }
            appendLine()
            appendLine("Summary: $summary")
            appendLine("Exception: ${throwable.javaClass.name}")
            throwable.message?.takeIf { it.isNotBlank() }?.let { appendLine("Message: $it") }
            if (throwable is SyncException) {
                throwable.phase?.let { appendLine("Sync phase: $it") }
                throwable.method?.let { appendLine("HTTP method: $it") }
                throwable.url?.let { appendLine("Request URL: $it") }
                throwable.httpStatus?.let { appendLine("HTTP status: $it") }
                throwable.responseSnippet?.takeIf { it.isNotBlank() }?.let {
                    appendLine("Response snippet: $it")
                }
                throwable.syncHost?.let { appendLine("Client sync host: $it") }
                throwable.baseUrl?.let { appendLine("Client base URL: $it") }
            }
            appendLine()
            appendLine("--- Cause chain ---")
            appendCauseChain(this, throwable)
            appendLine()
            appendLine("--- Stack trace ---")
            append(stackTraceString(throwable))
        }
        return SyncErrorReport(summary = summary, details = details.trim())
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Anki sync error", text))
    }

    private fun userSummary(throwable: Throwable): String {
        val root = rootCause(throwable)
        val explicit = throwable.message?.takeIf { it.isNotBlank() }
            ?: root.message?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        return when (root) {
            is UnknownHostException -> "Cannot reach sync server (DNS/network)."
            is SocketTimeoutException -> "Sync timed out. Try again on a stable connection."
            is SSLException -> "Secure connection failed (TLS/SSL)."
            is SyncException -> "Sync failed."
            else -> "${root.javaClass.simpleName}"
        }
    }

    private fun appendCauseChain(builder: StringBuilder, throwable: Throwable) {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 8) {
            builder.appendLine("#$depth ${current.javaClass.name}: ${current.message.orEmpty()}")
            current = current.cause
            depth++
        }
    }

    private fun rootCause(throwable: Throwable): Throwable {
        var current = throwable
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun stackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString().trim()
    }
}
