package com.tepmex.ankientertainer.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RemoteLlmClientFallbackTest {

    private lateinit var firstServer: MockWebServer
    private lateinit var secondServer: MockWebServer
    private lateinit var client: RemoteLlmClient

    @Before
    fun setUp() {
        firstServer = MockWebServer()
        secondServer = MockWebServer()
        firstServer.start()
        secondServer.start()
        client = RemoteLlmClient(
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
        )
    }

    @After
    fun tearDown() {
        firstServer.shutdown()
        secondServer.shutdown()
    }

    @Test
    fun fallsBackToSecondProviderWhenFirstFails() = runBlocking {
        firstServer.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        secondServer.enqueue(successBody("from-second"))

        val result = client.generateChunkWithFallback(
            providers = listOf(
                LlmProvider(
                    baseUrl = firstServer.url("/").toString().trimEnd('/'),
                    token = "a",
                    modelNames = listOf("model-a"),
                ),
                LlmProvider(
                    baseUrl = secondServer.url("/").toString().trimEnd('/'),
                    token = "b",
                    modelNames = listOf("model-b"),
                ),
            ),
            systemPrompt = "sys",
            pickModel = { it.first() },
        )

        assertEquals("from-second", result.text)
        assertEquals("model-b", result.modelName)
        assertEquals(1, firstServer.requestCount)
        assertEquals(1, secondServer.requestCount)
        val secondRequest = secondServer.takeRequest()
        assertEquals("Bearer b", secondRequest.getHeader("Authorization"))
        assertTrue(secondRequest.path!!.endsWith("/v1/chat/completions"))
    }

    @Test
    fun usesFirstProviderWhenItResponds() = runBlocking {
        firstServer.enqueue(successBody("from-first"))
        secondServer.enqueue(successBody("should-not-use"))

        val result = client.generateChunkWithFallback(
            providers = listOf(
                LlmProvider(
                    baseUrl = firstServer.url("/").toString().trimEnd('/'),
                    token = "",
                    modelNames = listOf("model-a"),
                ),
                LlmProvider(
                    baseUrl = secondServer.url("/").toString().trimEnd('/'),
                    token = "",
                    modelNames = listOf("model-b"),
                ),
            ),
            systemPrompt = "sys",
            pickModel = { it.first() },
        )

        assertEquals("from-first", result.text)
        assertEquals("model-a", result.modelName)
        assertEquals(1, firstServer.requestCount)
        assertEquals(0, secondServer.requestCount)
    }

    @Test
    fun skipsUnconfiguredProviders() = runBlocking {
        secondServer.enqueue(successBody("ok"))

        val result = client.generateChunkWithFallback(
            providers = listOf(
                LlmProvider(baseUrl = "", token = "x", modelNames = listOf("m")),
                LlmProvider(
                    baseUrl = secondServer.url("/").toString().trimEnd('/'),
                    token = "",
                    modelNames = listOf("model-b"),
                ),
            ),
            systemPrompt = "sys",
            pickModel = { it.first() },
        )

        assertEquals("ok", result.text)
        assertEquals(1, secondServer.requestCount)
    }

    @Test
    fun throwsWhenAllProvidersFail() = runBlocking {
        firstServer.enqueue(MockResponse().setResponseCode(500))
        secondServer.enqueue(MockResponse().setResponseCode(502))

        var threw = false
        try {
            client.generateChunkWithFallback(
                providers = listOf(
                    LlmProvider(
                        baseUrl = firstServer.url("/").toString().trimEnd('/'),
                        modelNames = listOf("a"),
                    ),
                    LlmProvider(
                        baseUrl = secondServer.url("/").toString().trimEnd('/'),
                        modelNames = listOf("b"),
                    ),
                ),
                systemPrompt = "sys",
                pickModel = { it.first() },
            )
        } catch (e: Exception) {
            threw = true
            assertTrue(e.message!!.contains("502") || e.message!!.contains("HTTP"))
        }
        assertTrue(threw)
        assertEquals(1, firstServer.requestCount)
        assertEquals(1, secondServer.requestCount)
    }

    private fun successBody(content: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                  "choices": [
                    { "message": { "content": "$content" } }
                  ]
                }
                """.trimIndent(),
            )
}
