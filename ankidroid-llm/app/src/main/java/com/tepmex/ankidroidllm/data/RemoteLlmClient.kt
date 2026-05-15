package com.tepmex.ankidroidllm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
        val response = client.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Remote LLM error: HTTP ${response.code} ${response.message}")
        }
        val text = response.body?.string().orEmpty()
        val json = JSONObject(text)
        val choices = json.optJSONArray("choices")
            ?: throw IllegalStateException("Invalid API response (no choices)")
        val first = choices.optJSONObject(0) ?: throw IllegalStateException("Invalid API response")
        val message = first.optJSONObject("message")
            ?: throw IllegalStateException("Invalid API response (no message)")
        val content = message.optString("content")
        if (content.isEmpty()) {
            throw IllegalStateException("Empty model content")
        }
        content
    }
}
