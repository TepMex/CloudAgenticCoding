package com.tepmex.ankientertainer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmProvidersCodecTest {

    @Test
    fun encodeDecodeRoundTripPreservesOrderAndFields() {
        val providers = listOf(
            LlmProvider(
                baseUrl = "https://a.example",
                token = "token-a",
                modelNames = listOf("model-a1", "model-a2"),
            ),
            LlmProvider(
                baseUrl = "https://b.example",
                token = "",
                modelNames = listOf("model-b"),
            ),
        )
        val decoded = decodeProviders(encodeProviders(providers))
        assertEquals(providers, decoded)
    }

    @Test
    fun decodeEmptyOrBlankReturnsEmptyList() {
        assertEquals(emptyList<LlmProvider>(), decodeProviders(""))
        assertEquals(emptyList<LlmProvider>(), decodeProviders("   "))
    }

    @Test
    fun decodeMalformedJsonReturnsEmptyList() {
        assertEquals(emptyList<LlmProvider>(), decodeProviders("{not-json"))
    }

    @Test
    fun resolveProvidersMigratesLegacySingleProvider() {
        val providers = resolveProviders(
            providersJson = null,
            legacyBaseUrl = "https://legacy.example",
            legacyToken = "legacy-token",
            legacyModelsText = "gpt-4\ngpt-mini",
        )
        assertEquals(
            listOf(
                LlmProvider(
                    baseUrl = "https://legacy.example",
                    token = "legacy-token",
                    modelNames = listOf("gpt-4", "gpt-mini"),
                ),
            ),
            providers,
        )
    }

    @Test
    fun resolveProvidersPrefersJsonOverLegacyKeys() {
        val json = encodeProviders(
            listOf(LlmProvider(baseUrl = "https://new", modelNames = listOf("m"))),
        )
        val providers = resolveProviders(
            providersJson = json,
            legacyBaseUrl = "https://legacy",
            legacyToken = "t",
            legacyModelsText = "old",
        )
        assertEquals("https://new", providers.single().baseUrl)
        assertEquals(listOf("m"), providers.single().modelNames)
    }

    @Test
    fun resolveProvidersDefaultsToEmptySlot() {
        val providers = resolveProviders(null, null, null, null)
        assertEquals(listOf(LlmProvider()), providers)
    }

    @Test
    fun configuredProvidersSkipsIncompleteSlots() {
        val settings = AppSettings(
            providers = listOf(
                LlmProvider(baseUrl = "", token = "x", modelNames = listOf("m")),
                LlmProvider(baseUrl = "https://ok", token = "", modelNames = emptyList()),
                LlmProvider(baseUrl = "https://ok", token = "t", modelNames = listOf("m1")),
            ),
            chunkPrompt = "p",
            chunkCount = 5,
        )
        assertTrue(settings.isLlmConfigured())
        assertEquals(
            listOf(LlmProvider(baseUrl = "https://ok", token = "t", modelNames = listOf("m1"))),
            settings.configuredProviders(),
        )
    }

    @Test
    fun isLlmConfiguredFalseWhenNoUsableProvider() {
        val settings = AppSettings(
            providers = listOf(LlmProvider()),
            chunkPrompt = "p",
            chunkCount = 5,
        )
        assertFalse(settings.isLlmConfigured())
        assertFalse(LlmProvider(baseUrl = "https://x").isConfigured())
        assertFalse(LlmProvider(modelNames = listOf("m")).isConfigured())
    }
}
