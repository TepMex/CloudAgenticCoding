package com.tepmex.tinglistories.data

import com.tepmex.tinglistories.domain.chatCompletionsUrl
import com.tepmex.tinglistories.domain.readMessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LlmClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun complete(
        baseUrl: String,
        token: String,
        model: String,
        system: String,
        user: String,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("stream", false)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
        val requestBuilder = Request.Builder()
            .url(chatCompletionsUrl(baseUrl))
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = text.take(180).ifBlank { response.message }
                        continuation.resumeWithException(IllegalStateException("HTTP ${response.code}: $detail"))
                        return@suspendCancellableCoroutine
                    }
                    val json = JSONObject(text)
                    val message = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                    val content = if (message == null) "" else readMessageContent(message)
                    if (content.isBlank()) {
                        continuation.resumeWithException(IllegalStateException("Пустой ответ модели"))
                    } else {
                        continuation.resume(content)
                    }
                }
            } catch (_: IOException) {
                if (continuation.isCancelled) return@suspendCancellableCoroutine
                continuation.resumeWithException(IllegalStateException("Нет соединения с API"))
            } catch (e: Exception) {
                if (continuation.isCancelled) return@suspendCancellableCoroutine
                continuation.resumeWithException(e)
            }
        }
    }
}
