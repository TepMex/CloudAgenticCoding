package com.tepmex.ankientertainer.data

import kotlinx.coroutines.CancellationException
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
import kotlin.random.Random

data class GeneratedChunk(
    val text: String,
    val modelName: String,
    val providerBaseUrl: String,
)

/**
 * OpenAI-compatible chat client.
 * Receives an already-expanded system prompt and never queries Hanzi metadata.
 * When multiple providers are configured, they are tried in order until one responds.
 */
class RemoteLlmClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    /**
     * Tries each configured provider in order. On network/HTTP/parse failure,
     * moves to the next provider. Cancellation is never swallowed.
     */
    suspend fun generateChunkWithFallback(
        providers: List<LlmProvider>,
        systemPrompt: String,
        pickModel: (List<String>) -> String = { pickRandomModel(it) },
    ): GeneratedChunk {
        val configured = providers.filter { it.isConfigured() }
        require(configured.isNotEmpty()) { "No LLM providers configured" }
        var lastError: Exception? = null
        for (provider in configured) {
            try {
                val model = pickModel(provider.modelNames)
                val text = generateChunk(provider, systemPrompt, model)
                return GeneratedChunk(
                    text = text,
                    modelName = model,
                    providerBaseUrl = provider.baseUrl,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All LLM providers failed")
    }

    suspend fun generateChunk(
        provider: LlmProvider,
        systemPrompt: String,
        modelName: String,
    ): String = withContext(Dispatchers.IO) {
        val root = provider.baseUrl.trimEnd('/')
        val url = "$root/v1/chat/completions"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(
                JSONObject().put("role", "user").put(
                    "content",
                    "Write exactly one short text chunk. Plain text only, no title or markdown.",
                ),
            )
        val bodyJson = JSONObject()
            .put("model", modelName)
            .put("messages", messages)
            .put("temperature", 0.8)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = bodyJson.toString().toRequestBody(mediaType)
        val builder = Request.Builder().url(url).post(requestBody)
        if (provider.token.isNotBlank()) {
            builder.header("Authorization", "Bearer ${provider.token.trim()}")
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
                val content = message.optString("content").trim()
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

    fun pickRandomModel(models: List<String>): String {
        require(models.isNotEmpty())
        return models[Random.nextInt(models.size)]
    }
}
