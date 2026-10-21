package com.tepmex.extensivereadingtutor

import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.LlmCapabilities
import com.tepmex.extensivereadingtutor.domain.LlmFailureKind
import com.tepmex.extensivereadingtutor.domain.defaultPreferences
import com.tepmex.extensivereadingtutor.llm.FileLlmExchangeLog
import com.tepmex.extensivereadingtutor.llm.GatewayLessonAi
import com.tepmex.extensivereadingtutor.llm.HttpHeader
import com.tepmex.extensivereadingtutor.llm.HttpResult
import com.tepmex.extensivereadingtutor.llm.HttpTransport
import com.tepmex.extensivereadingtutor.llm.JsonTone
import com.tepmex.extensivereadingtutor.llm.LlmCallSettings
import com.tepmex.extensivereadingtutor.llm.LlmExchange
import com.tepmex.extensivereadingtutor.llm.LlmMessage
import com.tepmex.extensivereadingtutor.llm.LlmProvider
import com.tepmex.extensivereadingtutor.llm.LlmRequest
import com.tepmex.extensivereadingtutor.llm.LlmResponse
import com.tepmex.extensivereadingtutor.llm.MemoryLlmExchangeLog
import com.tepmex.extensivereadingtutor.llm.OkHttpTransport
import com.tepmex.extensivereadingtutor.llm.OpenAiCompatibleProvider
import com.tepmex.extensivereadingtutor.llm.PrepareLessonInput
import com.tepmex.extensivereadingtutor.llm.RetryingCalls
import com.tepmex.extensivereadingtutor.llm.highlightJson
import com.tepmex.extensivereadingtutor.llm.parseTimeoutSeconds
import com.tepmex.extensivereadingtutor.llm.prettyJson
import com.tepmex.extensivereadingtutor.llm.redactHeaders
import com.tepmex.extensivereadingtutor.llm.validateExtraBody
import com.tepmex.extensivereadingtutor.pedagogy.MemoryPacket
import com.tepmex.extensivereadingtutor.pedagogy.PedagogicalStrategy
import com.tepmex.extensivereadingtutor.ui.formatDuration
import com.tepmex.extensivereadingtutor.ui.formatTokens
import com.tepmex.extensivereadingtutor.ui.formatWhen
import com.tepmex.extensivereadingtutor.ui.previewText
import com.tepmex.extensivereadingtutor.ui.statusLabel
import com.tepmex.extensivereadingtutor.ui.toRow
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeLogTest {
    @Test
    fun extraFieldsMergeAndReservedKeysStayLocal() = runBlocking {
        val http = RecordingHttp()
        http.queue += HttpResult.Ok(
            200,
            """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":4,"total_tokens":15}}""",
            responseHeaders = listOf(HttpHeader("X-Request-Id", "abc")),
        )
        val log = MemoryLlmExchangeLog()
        val provider = OpenAiCompatibleProvider(
            LlmCapabilities(),
            "https://api.example/v1",
            "sk-live-secret",
            http,
            log,
        )
        val result = provider.generate(
            LlmRequest(
                model = "model-a",
                messages = listOf(LlmMessage("user", "Привет 雷")),
                temperature = 0.4,
                maxOutputTokens = 20,
                jsonObjectMode = true,
                timeoutMillis = 12_345,
                extraBodyJson = """{"reasoning":{"effort":"high"},"temperature":0}""",
                purpose = "probe",
            ),
        )
        assertTrue(result is AppResult.Ok)
        assertEquals(12_345L, http.timeouts.single())
        val sent = Json.parseToJsonElement(http.calls.single()) as JsonObject
        assertEquals("model-a", sent["model"]!!.jsonPrimitive.content)
        assertEquals("Привет 雷", sent.jsonArrayFirstContent())
        assertEquals("high", sent["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("0", sent["temperature"]!!.jsonPrimitive.content)
        assertEquals("json_object", sent["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val saved = log.list().single()
        assertEquals("probe", saved.purpose)
        assertEquals(11, saved.promptTokens)
        assertEquals(4, saved.completionTokens)
        assertEquals(15, saved.totalTokens)
        assertEquals(200, saved.httpStatus)
        assertTrue(saved.sent)
        assertTrue(saved.requestHeadersJson.contains("Bearer [redacted]"))
        assertTrue(saved.responseHeadersJson.contains("abc"))
        assertFalse(saved.requestHeadersJson.contains("sk-live-secret"))
        assertFalse(saved.requestBody.contains("sk-live-secret"))
        assertFalse(saved.responseBody.orEmpty().contains("sk-live-secret"))
        val blocked = provider.generate(
            LlmRequest("model-a", emptyList(), 0.2, 20, false, 1000, extraBodyJson = """{"messages":[]}"""),
        )
        assertTrue(blocked is AppResult.Err)
        assertEquals(1, http.calls.size)
        assertEquals("INVALID_EXTRA", log.list().first().failureKind)
        assertFalse(log.list().first().sent)
        assertEquals(
            "Дополнительные поля должны быть JSON-объектом.",
            validateExtraBody("[1]"),
        )
        assertNull(validateExtraBody("  "))
        assertNull(parseTimeoutSeconds("0"))
        assertNull(parseTimeoutSeconds("1801"))
        assertEquals(60L, parseTimeoutSeconds(" 60 "))
        assertEquals(1800L, parseTimeoutSeconds("1800"))
    }

    @Test
    fun retriesAreSeparateJournalRows() = runBlocking {
        val http = RecordingHttp()
        http.queue += HttpResult.Fail(LlmFailureKind.TIMEOUT, null, "slow sk-live-secret")
        http.queue += HttpResult.Ok(
            200,
            """{"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}""",
        )
        val log = MemoryLlmExchangeLog()
        val provider = OpenAiCompatibleProvider(LlmCapabilities(), "https://api.example/v1", "sk-live-secret", http, log)
        val result = RetryingCalls(sleeper = { }).generate(
            provider,
            LlmRequest("model-a", listOf(LlmMessage("user", "ping")), 0.0, 16, false, 5_000, purpose = "prepare"),
        )
        assertTrue(result is AppResult.Ok)
        assertEquals(2, log.list().size)
        assertEquals(200, log.list().first().httpStatus)
        assertEquals("prepare", log.list().first().purpose)
        assertEquals(LlmFailureKind.TIMEOUT.name, log.list()[1].failureKind)
        assertTrue(log.list().all { !it.requestHeadersJson.contains("sk-live-secret") })
        assertTrue(log.list().all { it.requestHeadersJson.contains("Bearer [redacted]") })
        assertTrue(log.list().all { !it.responseBody.orEmpty().contains("sk-live-secret") })
    }

    @Test
    fun gatewayCopiesTimeoutAndExtraIntoTheRequest() = runBlocking {
        val seen = mutableListOf<LlmRequest>()
        val provider = object : LlmProvider {
            override val capabilities = LlmCapabilities()
            override suspend fun generate(request: LlmRequest): AppResult<LlmResponse> {
                seen += request
                return AppResult.Ok(
                    LlmResponse(
                        """{"introduction":"Смотрите.","hints":[],"questions":[{"type":"MAIN_IDEA","prompt":"О чём?","targetSkill":"READING_COMPREHENSION","sourceQuote":"雷"}]}""",
                        null,
                        "stop",
                    ),
                )
            }
        }
        val ai = GatewayLessonAi(
            providers = { AppResult.Ok(provider) },
            retry = RetryingCalls(sleeper = { }),
            policy = { LlmCallSettings(timeoutMillis = 15_000, extraBodyJson = """{"reasoning_effort":"low"}""") },
        )
        val result = ai.prepareLesson(
            PrepareLessonInput(
                providerConfigId = "default",
                providerKind = "OPENAI_COMPATIBLE",
                model = "model-a",
                strategy = PedagogicalStrategy.V1,
                preferences = defaultPreferences(Instant.EPOCH),
                chapterTitle = null,
                precedingText = null,
                fragmentText = "雷",
                memory = MemoryPacket(emptyList(), emptyList(), emptyList(), 0),
            ),
        )
        assertTrue(result is AppResult.Ok)
        assertEquals(15_000L, seen.single().timeoutMillis)
        assertEquals("""{"reasoning_effort":"low"}""", seen.single().extraBodyJson)
        assertEquals("prepare", seen.single().purpose)
    }

    @Test
    fun fileJournalKeepsNewestAndDropsACorruptFile() {
        val file = File.createTempFile("llm-exchanges", ".json")
        val log = FileLlmExchangeLog(file, limit = 2)
        log.append(sample("1"))
        log.append(sample("2"))
        log.append(sample("3"))
        assertEquals(listOf("3", "2"), log.list().map { it.id })
        val reopened = FileLlmExchangeLog(file, limit = 2)
        assertEquals("雷打不动", reopened.list().first().requestBody)
        assertFalse(file.readText().contains("sk-live-secret"))
        log.clear()
        assertTrue(FileLlmExchangeLog(file).list().isEmpty())
        file.writeText("{not json")
        val recovered = FileLlmExchangeLog(file)
        assertTrue(recovered.list().isEmpty())
        recovered.append(sample("9"))
        assertEquals("9", recovered.list().single().id)
        file.delete()
    }

    @Test
    fun prettyJsonColorsKeysStringsNumbersAndKeywords() {
        val pretty = prettyJson("""{"reasoning_effort":"low","n":1,"ok":false,"z":null,"say":"a\"b"}""")
        assertTrue(pretty!!.contains("\n"))
        val tones = highlightJson(pretty).groupBy { it.tone }
        assertTrue(tones.getValue(JsonTone.Key).any { it.text.contains("reasoning_effort") })
        assertTrue(tones.getValue(JsonTone.StringValue).any { it.text.contains("low") })
        assertTrue(tones.getValue(JsonTone.StringValue).any { it.text.contains("a\\\"b") })
        assertTrue(tones.getValue(JsonTone.Number).any { it.text == "1" })
        assertTrue(tones.getValue(JsonTone.Keyword).any { it.text == "false" })
        assertTrue(tones.getValue(JsonTone.Keyword).any { it.text == "null" })
        val redacted = redactHeaders(
            listOf(HttpHeader("Authorization", "Bearer sk-live-secret"), HttpHeader("X-Api-Key", "sk-live-secret")),
            listOf("sk-live-secret"),
        )
        assertEquals("Bearer [redacted]", redacted[0].value)
        assertEquals("[redacted]", redacted[1].value)
        val row = sample("1").copy(purpose = "prepare", promptTokens = 11, completionTokens = 4, totalTokens = 15).toRow()
        assertEquals("Подготовка занятия", row.title)
        assertEquals("11 запрос · 4 ответ · 15 всего", formatTokens(11, 4, 15))
        assertEquals("Токены не сообщены", formatTokens(null, null, null))
        assertEquals("900 мс", formatDuration(900))
        assertEquals("1.5 с", formatDuration(1500))
        assertEquals("Неверные доп. поля", statusLabel(sample("x").copy(sent = false, httpStatus = null, failureKind = "INVALID_EXTRA")))
        assertTrue(formatWhen(0, ZoneOffset.UTC).contains("00:00:00"))
        assertTrue(previewText("""{"a":1}""").contains("\"a\""))
        assertEquals("Пусто", previewText(null))
    }

    @Test
    fun transportReportsWireHeadersAndTimeout() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("X-Test", "yes").setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        try {
            val url = server.url("/v1/chat/completions").toString()
            val ok = OkHttpTransport().postJson(url, mapOf("Authorization" to "Bearer secret"), """{"model":"m"}""", 5_000)
            val success = ok as HttpResult.Ok
            assertEquals("""{"ok":true}""", success.body)
            assertTrue(success.requestHeaders.any { it.name.equals("Authorization", true) && it.value == "Bearer secret" })
            assertTrue(success.requestHeaders.any { it.name.equals("Content-Type", true) && it.value.contains("json") })
            assertTrue(success.responseHeaders.any { it.name.equals("X-Test", true) && it.value == "yes" })
            val timedOut = OkHttpTransport().postJson(url, emptyMap(), "{}", 400)
            assertEquals(LlmFailureKind.TIMEOUT, (timedOut as HttpResult.Fail).kind)
        } finally {
            server.shutdown()
        }
    }

    private fun sample(id: String) = LlmExchange(
        id = id,
        purpose = "probe",
        model = "model-a",
        url = "https://api.example/v1/chat/completions",
        startedAtEpochMillis = 0,
        finishedAtEpochMillis = 20,
        durationMillis = 20,
        sent = true,
        httpStatus = 200,
        requestHeadersJson = """[{"name":"Authorization","value":"Bearer [redacted]"}]""",
        requestBody = "雷打不动",
        responseHeadersJson = "[]",
        responseBody = """{"ok":true}""",
        promptTokens = 1,
        completionTokens = 1,
        totalTokens = 2,
    )

    private fun JsonObject.jsonArrayFirstContent(): String {
        val message = (get("messages") as kotlinx.serialization.json.JsonArray).first() as JsonObject
        return message["content"]!!.jsonPrimitive.content
    }
}

private class RecordingHttp : HttpTransport {
    val queue = ArrayDeque<HttpResult>()
    val calls = mutableListOf<String>()
    val timeouts = mutableListOf<Long>()

    override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMillis: Long): HttpResult {
        calls += body
        timeouts += timeoutMillis
        val wire = headers.map { HttpHeader(it.key, it.value) }
        return when (val next = queue.removeFirst()) {
            is HttpResult.Ok -> next.copy(requestHeaders = wire)
            is HttpResult.Fail -> next.copy(requestHeaders = wire)
        }
    }
}
