package com.tepmex.extensivereadingtutor.llm

import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.LlmFailureKind
import com.tepmex.extensivereadingtutor.domain.TokenUsage
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val LLM_EXCHANGE_LIMIT = 40
const val MIN_TIMEOUT_SECONDS = 1L
const val MAX_TIMEOUT_SECONDS = 1800L
const val DEFAULT_TIMEOUT_SECONDS = 60L

private const val MAX_LOGGED_BODY = 256_000
private val secretHeaderNames = setOf(
    "authorization",
    "proxy-authorization",
    "x-api-key",
    "api-key",
    "cookie",
    "set-cookie",
)
private val reservedExtraKeys = setOf("model", "messages")

data class HttpHeader(val name: String, val value: String)

@Serializable
data class LlmExchange(
    val id: String,
    val purpose: String,
    val model: String,
    val url: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val durationMillis: Long,
    val sent: Boolean,
    val httpStatus: Int? = null,
    val failureKind: String? = null,
    val requestHeadersJson: String,
    val requestBody: String,
    val responseHeadersJson: String,
    val responseBody: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

interface LlmExchangeLog {
    fun append(exchange: LlmExchange)
    fun list(): List<LlmExchange>
    fun clear()
}

object NoopLlmExchangeLog : LlmExchangeLog {
    override fun append(exchange: LlmExchange) = Unit
    override fun list(): List<LlmExchange> = emptyList()
    override fun clear() = Unit
}

class MemoryLlmExchangeLog(private val limit: Int = LLM_EXCHANGE_LIMIT) : LlmExchangeLog {
    private val items = ArrayDeque<LlmExchange>()

    override fun append(exchange: LlmExchange) = synchronized(this) {
        items.addFirst(exchange)
        while (items.size > limit) items.removeLast()
    }

    override fun list(): List<LlmExchange> = synchronized(this) { items.toList() }

    override fun clear() = synchronized(this) { items.clear() }
}

class FileLlmExchangeLog(
    private val file: File,
    private val limit: Int = LLM_EXCHANGE_LIMIT,
) : LlmExchangeLog {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    override fun append(exchange: LlmExchange) = synchronized(lock) {
        write(listOf(exchange) + readUnlocked())
    }

    override fun list(): List<LlmExchange> = synchronized(lock) { readUnlocked() }

    override fun clear() {
        synchronized(lock) {
            file.delete()
            File(file.parentFile, "${file.name}.tmp").delete()
        }
    }

    private fun readUnlocked(): List<LlmExchange> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return try {
            json.decodeFromString<List<LlmExchange>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(items: List<LlmExchange>) {
        val parent = file.parentFile ?: return
        parent.mkdirs()
        val tmp = File(parent, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(items.take(limit)))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

fun parseTimeoutSeconds(raw: String): Long? {
    val value = raw.trim().toLongOrNull() ?: return null
    if (value !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) return null
    return value
}

fun validateExtraBody(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val element = try {
        Json.parseToJsonElement(trimmed)
    } catch (_: Exception) {
        return "Дополнительные поля должны быть JSON-объектом."
    }
    if (element !is JsonObject) return "Дополнительные поля должны быть JSON-объектом."
    val reserved = element.keys.intersect(reservedExtraKeys)
    if (reserved.isNotEmpty()) {
        return "Поля ${reserved.sorted().joinToString()} задаёт приложение."
    }
    return null
}

fun chatPayload(request: LlmRequest, jsonObjectMode: Boolean): AppResult<String> {
    val extraError = validateExtraBody(request.extraBodyJson)
    if (extraError != null) {
        return AppResult.Err(DomainError.LlmFailed(LlmFailureKind.BAD_RESPONSE, extraError))
    }
    val base = buildJsonObject {
        put("model", request.model)
        put("temperature", request.temperature)
        put("messages", buildJsonArray {
            request.messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })
        if (request.jsonObjectMode && jsonObjectMode) {
            put("response_format", buildJsonObject { put("type", "json_object") })
        }
    }
    val extra = request.extraBodyJson.trim()
    val merged = if (extra.isEmpty()) {
        base
    } else {
        val overlay = Json.parseToJsonElement(extra) as JsonObject
        buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            overlay.forEach { (key, value) -> put(key, value) }
        }
    }
    return AppResult.Ok(merged.toString())
}

fun headersJson(headers: List<HttpHeader>): String =
    buildJsonArray {
        headers.forEach { header ->
            add(buildJsonObject {
                put("name", header.name)
                put("value", header.value)
            })
        }
    }.toString()

fun redactSecrets(text: String, secrets: List<String>): String {
    var out = text
    for (secret in secrets) {
        if (secret.length >= 4) out = out.replace(secret, "[redacted]")
    }
    return out.replace(Regex("""Bearer\s+\S+"""), "Bearer [redacted]")
}

fun redactHeaders(headers: List<HttpHeader>, secrets: List<String>): List<HttpHeader> =
    headers.map { header ->
        val sensitive = header.name.lowercase() in secretHeaderNames
        val value = if (sensitive) {
            if (header.value.trimStart().startsWith("Bearer ", ignoreCase = true)) {
                "Bearer [redacted]"
            } else {
                "[redacted]"
            }
        } else {
            redactSecrets(header.value, secrets)
        }
        HttpHeader(header.name, value)
    }

fun loggedBody(text: String, secrets: List<String>): String =
    redactSecrets(text, secrets).take(MAX_LOGGED_BODY)

fun tokenUsageOrNull(body: String?): TokenUsage? {
    if (body.isNullOrBlank()) return null
    return try {
        val root = Json.parseToJsonElement(body) as? JsonObject ?: return null
        val usage = root["usage"] as? JsonObject ?: return null
        TokenUsage(
            promptTokens = jsonInt(usage, "prompt_tokens"),
            completionTokens = jsonInt(usage, "completion_tokens"),
            totalTokens = jsonInt(usage, "total_tokens"),
        )
    } catch (_: Exception) {
        null
    }
}

private fun jsonInt(obj: JsonObject, name: String): Int? =
    (obj[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

private val prettyJsonEncoder = Json { prettyPrint = true }

fun prettyJson(raw: String): String? = try {
    val element = Json.parseToJsonElement(raw)
    prettyJsonEncoder.encodeToString(JsonElement.serializer(), element)
} catch (_: Exception) {
    null
}

enum class JsonTone { Plain, Key, StringValue, Number, Keyword, Punctuation }

data class JsonSpan(val text: String, val tone: JsonTone)

fun highlightJson(source: String): List<JsonSpan> {
    val spans = mutableListOf<JsonSpan>()
    var index = 0
    fun push(text: String, tone: JsonTone) {
        if (text.isEmpty()) return
        val last = spans.lastOrNull()
        if (last != null && last.tone == tone) {
            spans[spans.lastIndex] = last.copy(text = last.text + text)
        } else {
            spans += JsonSpan(text, tone)
        }
    }
    while (index < source.length) {
        val ch = source[index]
        if (ch.isWhitespace()) {
            val start = index
            while (index < source.length && source[index].isWhitespace()) index++
            push(source.substring(start, index), JsonTone.Plain)
            continue
        }
        if (ch == '"') {
            val start = index
            index++
            while (index < source.length) {
                if (source[index] == '\\') {
                    index++
                    if (index < source.length) index++
                    continue
                }
                if (source[index] == '"') {
                    index++
                    break
                }
                index++
            }
            var look = index
            while (look < source.length && source[look].isWhitespace()) look++
            val tone = if (look < source.length && source[look] == ':') JsonTone.Key else JsonTone.StringValue
            push(source.substring(start, index), tone)
            continue
        }
        if (ch == '-' || ch.isDigit()) {
            val start = index
            index++
            while (index < source.length && source[index] in "0123456789.eE+-") index++
            push(source.substring(start, index), JsonTone.Number)
            continue
        }
        val keyword = listOf("true", "false", "null").firstOrNull { word ->
            source.startsWith(word, index) &&
                (index + word.length == source.length || !source[index + word.length].isLetterOrDigit())
        }
        if (keyword != null) {
            push(keyword, JsonTone.Keyword)
            index += keyword.length
            continue
        }
        if (ch in "{}[]:,") {
            push(ch.toString(), JsonTone.Punctuation)
            index++
            continue
        }
        push(ch.toString(), JsonTone.Plain)
        index++
    }
    return spans
}

data class JsonDocument(val text: String, val highlighted: Boolean)

fun jsonDocument(raw: String?): JsonDocument {
    if (raw.isNullOrBlank()) return JsonDocument("—", false)
    val pretty = prettyJson(raw)
    return if (pretty != null) JsonDocument(pretty, true) else JsonDocument(raw, false)
}
