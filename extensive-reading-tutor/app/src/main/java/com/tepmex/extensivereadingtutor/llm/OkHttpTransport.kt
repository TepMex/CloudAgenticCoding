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
        val client = OkHttpClient.Builder()
            .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> HttpResult.Ok(response.code, text)
                    401, 403 -> HttpResult.Fail(LlmFailureKind.UNAUTHORIZED, response.code, text)
                    429 -> HttpResult.Fail(LlmFailureKind.RATE_LIMIT, response.code, text)
                    in 500..599 -> HttpResult.Fail(LlmFailureKind.NETWORK, response.code, text)
                    else -> HttpResult.Fail(LlmFailureKind.BAD_RESPONSE, response.code, text)
                }
            }
        } catch (_: SocketTimeoutException) {
            HttpResult.Fail(LlmFailureKind.TIMEOUT, null, null)
        } catch (_: IOException) {
            HttpResult.Fail(LlmFailureKind.NETWORK, null, null)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
