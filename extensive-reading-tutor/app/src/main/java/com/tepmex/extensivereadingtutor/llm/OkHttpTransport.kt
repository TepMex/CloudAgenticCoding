package com.tepmex.extensivereadingtutor.llm

import com.tepmex.extensivereadingtutor.domain.LlmFailureKind
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpTransport : HttpTransport {
    override suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMillis: Long,
    ): HttpResult = withContext(Dispatchers.IO) {
        val wireRequest = mutableListOf<HttpHeader>()
        val client = OkHttpClient.Builder()
            .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor { chain ->
                chain.request().headers.forEach { (name, value) ->
                    wireRequest += HttpHeader(name, value)
                }
                chain.proceed(chain.request())
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
            .build()
        fun sentHeaders(): List<HttpHeader> =
            if (wireRequest.isEmpty()) headers.map { (name, value) -> HttpHeader(name, value) } else wireRequest.toList()
        val started = System.nanoTime()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val responseHeaders = response.headers.map { (name, value) -> HttpHeader(name, value) }
                when (response.code) {
                    in 200..299 -> HttpResult.Ok(response.code, text, sentHeaders(), responseHeaders)
                    401, 403 -> HttpResult.Fail(LlmFailureKind.UNAUTHORIZED, response.code, text, sentHeaders(), responseHeaders)
                    429 -> HttpResult.Fail(LlmFailureKind.RATE_LIMIT, response.code, text, sentHeaders(), responseHeaders)
                    in 500..599 -> HttpResult.Fail(LlmFailureKind.NETWORK, response.code, text, sentHeaders(), responseHeaders)
                    else -> HttpResult.Fail(LlmFailureKind.BAD_RESPONSE, response.code, text, sentHeaders(), responseHeaders)
                }
            }
        } catch (error: IOException) {
            val elapsed = (System.nanoTime() - started) / 1_000_000L
            val timedOut = error.isTimeout() || elapsed >= timeoutMillis - 50
            val kind = if (timedOut) LlmFailureKind.TIMEOUT else LlmFailureKind.NETWORK
            HttpResult.Fail(kind, null, null, sentHeaders(), emptyList())
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private fun IOException.isTimeout(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SocketTimeoutException) return true
        if (current.message?.contains("timeout", ignoreCase = true) == true) return true
        current = current.cause
    }
    return false
}
