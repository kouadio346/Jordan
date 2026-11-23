package com.example

import com.example.data.AiCandidate
import com.example.data.MultiAiClient
import com.example.model.AiApiKeyEntry
import com.example.model.AiProvider
import com.example.model.AiRoutingMode
import com.example.model.AiRoutingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAiRoutingTest {

    @Test
    fun maskedKey_hidesSensitiveMiddleCharacters() {
        val longKey = AiApiKeyEntry(apiKey = "sk-proj-1234567890abcdef")
        assertEquals("sk-pr••••cdef", longKey.maskedKey())

        val shortKey = AiApiKeyEntry(apiKey = "12345")
        assertEquals("••••••", shortKey.maskedKey())
    }

    @Test
    fun resolveCandidates_autoFailoverOrdersPrioritizedSelectedFirstAndAppendsBuiltin() {
        val k1 = AiApiKeyEntry(id = "k1", provider = AiProvider.OPENAI, apiKey = "sk-1", isEnabled = true)
        val k2 = AiApiKeyEntry(id = "k2", provider = AiProvider.GROQ, apiKey = "gsk_2", isEnabled = false)
        val k3 = AiApiKeyEntry(id = "k3", provider = AiProvider.DEEPSEEK, apiKey = "sk-3", isEnabled = true)

        val candidates = MultiAiClient.resolveCandidates(
            keys = listOf(k1, k2, k3),
            settings = AiRoutingSettings(
                mode = AiRoutingMode.AUTO_FAILOVER_POOL,
                selectedKeyId = "k3",
                fallbackToBuiltIn = true
            )
        )

        assertEquals(3, candidates.size)
        assertEquals("k3", (candidates[0] as AiCandidate.ExternalKey).entry.id)
        assertEquals("k1", (candidates[1] as AiCandidate.ExternalKey).entry.id)
        assertTrue(candidates[2] is AiCandidate.BuiltInFirebase)
    }

    @Test
    fun resolveCandidates_singleKeyWithoutBuiltinFallbackReturnsOnlySelected() {
        val k1 = AiApiKeyEntry(id = "k1", provider = AiProvider.ANTHROPIC, apiKey = "sk-ant-1", isEnabled = true)
        val k2 = AiApiKeyEntry(id = "k2", provider = AiProvider.OPENAI, apiKey = "sk-2", isEnabled = true)

        val candidates = MultiAiClient.resolveCandidates(
            keys = listOf(k1, k2),
            settings = AiRoutingSettings(
                mode = AiRoutingMode.SINGLE_KEY,
                selectedKeyId = "k2",
                fallbackToBuiltIn = false
            )
        )

        assertEquals(1, candidates.size)
        assertEquals("k2", (candidates[0] as AiCandidate.ExternalKey).entry.id)
    }

    @Test
    fun extractJsonObject_stripsMarkdownFences() {
        val raw = """
            ```json
            {"appName":"ForgeTest","files":[]}
            ```
        """.trimIndent()
        assertEquals("""{"appName":"ForgeTest","files":[]}""", MultiAiClient.extractJsonObject(raw))
    }

    @Test
    fun sseParsers_ignoreNonDataAndDoneMarkers() {
        assertNull(MultiAiClient.parseOpenAiSseLine("event: ping"))
        assertNull(MultiAiClient.parseOpenAiSseLine("data: [DONE]"))
        assertNull(MultiAiClient.parseAnthropicSseLine("event: content_block_delta"))
        assertNull(MultiAiClient.parseGeminiSseLine(""))
    }

    @Test
    fun keyEntry_resolvesDefaultsAndCustomEndpoints() {
        val custom = AiApiKeyEntry(
            provider = AiProvider.CUSTOM_OPENAI,
            apiKey = "tok-1234567890",
            modelId = "",
            customBaseUrl = "https://api.together.xyz/v1/chat/completions"
        )
        assertEquals("https://api.together.xyz/v1/chat/completions", custom.effectiveEndpoint())
        assertEquals("custom-coder", custom.effectiveModel())

        val groq = AiApiKeyEntry(provider = AiProvider.GROQ, apiKey = "gsk_abcdefghijkl")
        assertEquals("https://api.groq.com/openai/v1/chat/completions", groq.effectiveEndpoint())
        assertEquals("llama-3.3-70b-versatile", groq.effectiveModel())
    }
}
