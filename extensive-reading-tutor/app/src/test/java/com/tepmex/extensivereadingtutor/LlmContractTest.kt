package com.tepmex.extensivereadingtutor

import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.LlmCapabilities
import com.tepmex.extensivereadingtutor.domain.LlmFailureKind
import com.tepmex.extensivereadingtutor.llm.HttpResult
import com.tepmex.extensivereadingtutor.llm.HttpTransport
import com.tepmex.extensivereadingtutor.llm.LlmProvider
import com.tepmex.extensivereadingtutor.llm.LlmRequest
import com.tepmex.extensivereadingtutor.llm.LlmResponse
import com.tepmex.extensivereadingtutor.llm.OpenAiCompatibleProvider
import com.tepmex.extensivereadingtutor.llm.RetryingCalls
import com.tepmex.extensivereadingtutor.llm.chatCompletionsUrl
import com.tepmex.extensivereadingtutor.llm.extractJsonObject
import com.tepmex.extensivereadingtutor.llm.lessonJson
import com.tepmex.extensivereadingtutor.llm.parseEvaluation
import com.tepmex.extensivereadingtutor.llm.parsePrepared
import com.tepmex.extensivereadingtutor.llm.redact
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmContractTest {
    @Test
    fun retriesRateLimitAndStopsOnUnauthorized() = runBlocking {
        val http = ScriptHttp()
        http.queue += HttpResult.Fail(LlmFailureKind.RATE_LIMIT, 429, "slow sk-live-secret")
        http.queue += HttpResult.Ok(200, """{"choices":[{"message":{"content":"{\"introduction\":\"Вступление.\",\"hints\":[],\"questions\":[{\"type\":\"MAIN_IDEA\",\"prompt\":\"О чём фрагмент?\",\"targetSkill\":\"READING_COMPREHENSION\",\"sourceQuote\":\"雷\"}]}"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}""")
        val provider = OpenAiCompatibleProvider(LlmCapabilities(), "http://127.0.0.1:8000", "sk-live-secret", http)
        val result = RetryingCalls(sleeper = { }).generate(provider, request())
        assertTrue(result is AppResult.Ok)
        assertEquals(2, http.calls.size)
        assertTrue(http.headers.all { it["Authorization"] == "Bearer sk-live-secret" })
        assertFalse(http.calls.any { it.contains("sk-live-secret") })
        val ok = (result as AppResult.Ok).value
        assertEquals(3, ok.usage?.promptTokens)
        val unauthorized = ScriptHttp()
        unauthorized.queue += HttpResult.Fail(LlmFailureKind.UNAUTHORIZED, 401, "bad sk-live-secret")
        val denied = RetryingCalls(sleeper = { }).generate(
            OpenAiCompatibleProvider(LlmCapabilities(), "https://api.example/v1", "sk-live-secret", unauthorized),
            request(),
        )
        assertTrue(denied is AppResult.Err)
        assertEquals(1, unauthorized.calls.size)
        val detail = ((denied as AppResult.Err).error as DomainError.LlmFailed).detail
        assertFalse(detail.contains("sk-live-secret"))
        assertEquals("https://api.example/v1/chat/completions", chatCompletionsUrl("https://api.example/v1"))
        assertEquals("https://api.example/v1/chat/completions", chatCompletionsUrl("https://api.example"))
    }

    @Test
    fun parsersAcceptFencedJsonAndDropABrokenQuestion() {
        val prepared = parsePrepared(
            lessonJson,
            """```json
            {"introduction":"Смотрите сами.","hints":["одна"],"questions":[
              {"type":"NOPE","prompt":"x","targetSkill":"READING_COMPREHENSION","sourceQuote":"雷"},
              {"type":"IDIOM","prompt":"Что это?","targetSkill":"IDIOM_COMPREHENSION","targetExpression":"雷打不动","sourceQuote":"雷打不动"}
            ]}
            ```""",
        )
        val draft = (prepared as AppResult.Ok).value
        assertEquals(1, draft.questions.size)
        assertEquals("雷打不动", draft.questions.single().sourceQuote)
        val fenced = extractJsonObject("note ```json {\"a\":1} ```")
        assertTrue(fenced.contains("\"a\""))
        val review = parseEvaluation(
            lessonJson,
            """{"questionReviews":[{"questionId":"q1","verdict":"PARTIAL","explanation":"Почти.","evidenceSufficient":false}],"observations":[{"skillType":"NOT_A_SKILL","observationType":"UNKNOWN_VOCABULARY","outcome":"NEGATIVE","confidence":"LOW"}],"nextRecommendations":["ещё раз"]}""",
            setOf("q1"),
        )
        val parsed = (review as AppResult.Ok).value
        assertEquals(1, parsed.reviews.size)
        assertEquals(1, parsed.rejectedObservations.size)
        assertTrue(parseEvaluation(lessonJson, """{"questionReviews":[]}""", setOf("q1")) is AppResult.Err)
    }

    @Test
    fun missingKeyDoesNotCallTheNetwork() = runBlocking {
        val http = ScriptHttp()
        val result = OpenAiCompatibleProvider(LlmCapabilities(), "https://example", "", http).generate(request())
        assertTrue(result is AppResult.Err)
        assertEquals(LlmFailureKind.UNAUTHORIZED, ((result as AppResult.Err).error as DomainError.LlmFailed).kind)
        assertTrue(http.calls.isEmpty())
        assertEquals("Bearer [redacted]", redact("Bearer sk-live-secret", listOf("sk-live-secret")))
    }

    private fun request() = LlmRequest("model-a", emptyList(), 0.2, 200, true, 1000)
}

private class ScriptHttp : HttpTransport {
    val queue = ArrayDeque<HttpResult>()
    val calls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()
    override suspend fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMillis: Long): HttpResult {
        calls += body
        this.headers += headers
        return queue.removeFirst()
    }
}

private class StaticProvider(private val content: String) : LlmProvider {
    override val capabilities = LlmCapabilities()
    override suspend fun generate(request: LlmRequest): AppResult<LlmResponse> =
        AppResult.Ok(LlmResponse(content, null, "stop"))
}
