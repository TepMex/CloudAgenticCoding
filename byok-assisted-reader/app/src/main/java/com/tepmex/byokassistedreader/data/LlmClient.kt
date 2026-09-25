package com.tepmex.byokassistedreader.data

import com.tepmex.byokassistedreader.domain.chatCompletionsUrl
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
            .put("temperature", 0.1)
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
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = text.take(180).ifBlank { response.message }
                        cont.resumeWithException(IllegalStateException("HTTP ${response.code}: $detail"))
                        return@suspendCancellableCoroutine
                    }
                    val json = JSONObject(text)
                    val content = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                    if (content.isBlank()) {
                        cont.resumeWithException(IllegalStateException("Пустой ответ модели"))
                    } else {
                        cont.resume(content)
                    }
                }
            } catch (e: IOException) {
                if (cont.isCancelled) return@suspendCancellableCoroutine
                cont.resumeWithException(e)
            } catch (e: Exception) {
                if (cont.isCancelled) return@suspendCancellableCoroutine
                cont.resumeWithException(e)
            }
        }
    }
}
