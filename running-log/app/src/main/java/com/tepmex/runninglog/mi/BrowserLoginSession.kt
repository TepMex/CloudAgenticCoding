package com.tepmex.runninglog.mi

/**
 * Xiaomi long-poll / QR login session.
 *
 * Open [loginUrl] in Custom Tabs or a WebView; when the user finishes sign-in,
 * [longPollUrl] returns credentials (same protocol as miband-bot QR login).
 */
data class BrowserLoginSession(
    val loginUrl: String,
    val longPollUrl: String,
    val qrImageUrl: String = "",
    val timeoutSec: Long = 300,
)
