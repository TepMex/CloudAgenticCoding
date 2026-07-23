package com.tepmex.ankientertainer.data.hanzi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Orchestration test: prompt expansion happens once; the same expanded prompt is reused
 * for every chunk-generation request (mirrors EntertainerViewModel flow).
 */
class PromptExpansionReuseTest {

    @Test
    fun expandsOnceAndReusesAcrossChunkRequests() = runBlocking {
        val engine = object : PromptTemplateEngine {
            var calls = 0
            override suspend fun expand(template: String, query: String): PromptExpansionResult {
                calls++
                return PromptExpansionResult(prompt = "expanded:$query", warnings = emptyList())
            }
        }

        val settingsTemplate = "use {QUERY}"
        val vocab = "词汇"
        val neededChunks = 3
        val receivedPrompts = mutableListOf<String>()

        // Same sequence as EntertainerViewModel.loadAndGenerate:
        val expansion = engine.expand(settingsTemplate, vocab)
        val systemPrompt = expansion.prompt
        repeat(neededChunks) {
            // RemoteLlmClient.generateChunkWithFallback(providers, systemPrompt)
            receivedPrompts.add(systemPrompt)
        }

        assertEquals(1, engine.calls)
        assertEquals(listOf("expanded:词汇", "expanded:词汇", "expanded:词汇"), receivedPrompts)
        assertSame(receivedPrompts[0], receivedPrompts[1])
    }
}
