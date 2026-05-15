package com.tepmex.ankidroidllm.data

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
import kotlinx.coroutines.CancellationException

class RemoteLlmClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun chatCompletion(
        baseUrl: String,
        bearerToken: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
    ): String = withContext(Dispatchers.IO) {
        val root = baseUrl.trimEnd('/')
        val url = "$root/v1/chat/completions"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userMessage))
        val bodyJson = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.8)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)
        val builder = Request.Builder().url(url).post(requestBody)
        if (bearerToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${bearerToken.trim()}")
        }
        val request = builder.build()
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    response.close()
                    cont.resumeWithException(
                        IllegalStateException("Remote LLM error: HTTP ${response.code} ${response.message}"),
                    )
                    return@suspendCancellableCoroutine
                }
                val text = response.body?.string().orEmpty()
                response.close()
                val json = JSONObject(text)
                val choices = json.optJSONArray("choices")
                    ?: throw IllegalStateException("Invalid API response (no choices)")
                val first = choices.optJSONObject(0) ?: throw IllegalStateException("Invalid API response")
                val message = first.optJSONObject("message")
                    ?: throw IllegalStateException("Invalid API response (no message)")
                val content = message.optString("content")
                if (content.isEmpty()) {
                    cont.resumeWithException(IllegalStateException("Empty model content"))
                    return@suspendCancellableCoroutine
                }
                if (!cont.isActive) return@suspendCancellableCoroutine
                cont.resume(content)
            } catch (e: IOException) {
                if (call.isCanceled()) {
                    cont.cancel(CancellationException("Remote LLM request canceled"))
                } else {
                    cont.resumeWithException(e)
                }
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }
}
