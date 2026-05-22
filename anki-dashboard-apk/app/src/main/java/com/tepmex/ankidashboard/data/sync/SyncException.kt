package com.tepmex.ankidashboard.data.sync

import java.io.IOException

/**
 * Sync failure with structured context for user-facing diagnostics.
 */
class SyncException(
    message: String,
    val phase: String? = null,
    val method: String? = null,
    val url: String? = null,
    val httpStatus: Int? = null,
    val responseSnippet: String? = null,
    val syncHost: String? = null,
    val baseUrl: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
