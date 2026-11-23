package com.example.data

import android.util.Log
import com.example.model.AiApiKeyEntry
import com.example.model.AiProvider
import com.example.model.AiRoutingMode
import com.example.model.AiRoutingSettings
import com.example.model.KeyHealthStatus
import com.example.model.KeyTestResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

sealed interface AiCandidate {
    data class ExternalKey(val entry: AiApiKeyEntry) : AiCandidate
    data object BuiltInFirebase : AiCandidate

    fun badgeName(): String = when (this) {
        is ExternalKey -> "${entry.provider.shortName} · ${entry.effectiveModel()}"
        BuiltInFirebase -> "Firebase IA · gemini-3.8-flash"
    }
}

sealed interface MultiStreamEvent {
    data class EngineSelected(val badge: String, val failoverUsed: Boolean) : MultiStreamEvent
    data class Chunk(val accumulatedText: String) : MultiStreamEvent
}

data class AiJsonOutcome(
    val jsonText: String,
    val engineBadge: String,
    val failoverUsed: Boolean
)

class MultiAiClient(
    private val vaultRepository: AiKeyVaultRepository? = null
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun testKeyConnection(entry: AiApiKeyEntry): KeyTestResult {
        if (entry.apiKey.isBlank()) {
            val res = KeyTestResult.Failure(KeyHealthStatus.ERROR, "Clé API vide")
            vaultRepository?.updateKeyHealth(entry.id, KeyHealthStatus.ERROR, error = "Clé API vide")
            return res
        }
        if (entry.effectiveEndpoint().isBlank()) {
            val res = KeyTestResult.Failure(KeyHealthStatus.ERROR, "URL d'endpoint manquante")
            vaultRepository?.updateKeyHealth(entry.id, KeyHealthStatus.ERROR, error = "URL manquante")
            return res
        }

        val startMs = System.currentTimeMillis()
        return try {
            withTimeout(25_000L) {
                withContext(Dispatchers.IO) {
                    val reply = executeCompletionCall(
                        entry = entry,
                        systemPrompt = "You are a connectivity checker.",
                        historyTurns = emptyList(),
                        userPrompt = "Reply with only OK.",
                        jsonMode = false,
                        maxTokens = 16
                    )
                    if (reply.isBlank()) {
                        throw IllegalStateException("Réponse vide du fournisseur")
                    }
                }
            }
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            vaultRepository?.updateKeyHealth(
                id = entry.id,
                status = KeyHealthStatus.CONNECTED,
                latencyMs = latency,
                error = null
            )
            KeyTestResult.Success(latencyMs = latency, message = "Connectée (${latency} ms)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpProviderException) {
            val status = if (e.code == 429) KeyHealthStatus.RATE_LIMITED else KeyHealthStatus.ERROR
            vaultRepository?.updateKeyHealth(entry.id, status, error = e.message)
            KeyTestResult.Failure(status, e.message ?: "HTTP ${e.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Key test failed for ${entry.provider}", e)
            vaultRepository?.updateKeyHealth(entry.id, KeyHealthStatus.ERROR, error = e.localizedMessage)
            KeyTestResult.Failure(KeyHealthStatus.ERROR, e.localizedMessage ?: "Échec réseau")
        }
    }

    suspend fun generateStructuredJson(
        systemAndPrompt: String,
        builtinCall: suspend () -> String
    ): AiJsonOutcome {
        val candidates = resolveCandidates(
            keys = vaultRepository?.keys?.value.orEmpty(),
            settings = vaultRepository?.routingSettings?.value ?: AiRoutingSettings()
        )
        var lastError: Exception? = null
        var attemptIndex = 0

        for (candidate in candidates) {
            val isFailover = attemptIndex > 0
            attemptIndex++
            try {
                when (candidate) {
                    is AiCandidate.BuiltInFirebase -> {
                        val result = builtinCall()
                        return AiJsonOutcome(
                            jsonText = result,
                            engineBadge = candidate.badgeName(),
                            failoverUsed = isFailover
                        )
                    }
                    is AiCandidate.ExternalKey -> {
                        val t0 = System.currentTimeMillis()
                        val raw = withTimeout(90_000L) {
                            withContext(Dispatchers.IO) {
                                executeCompletionCall(
                                    entry = candidate.entry,
                                    systemPrompt = "You are a strict JSON code generator. Respond ONLY with a valid JSON object matching the requested schema, with no markdown wrappers.",
                                    historyTurns = emptyList(),
                                    userPrompt = systemAndPrompt,
                                    jsonMode = true,
                                    maxTokens = 8192
                                )
                            }
                        }
                        val latency = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                        val cleanJson = extractJsonObject(raw)
                        vaultRepository?.updateKeyHealth(
                            id = candidate.entry.id,
                            status = KeyHealthStatus.CONNECTED,
                            latencyMs = latency,
                            error = null
                        )
                        vaultRepository?.incrementUsage(candidate.entry.id)
                        return AiJsonOutcome(
                            jsonText = cleanJson,
                            engineBadge = candidate.badgeName(),
                            failoverUsed = isFailover
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpProviderException) {
                val health = if (e.code == 429) KeyHealthStatus.RATE_LIMITED else KeyHealthStatus.ERROR
                if (candidate is AiCandidate.ExternalKey) {
                    vaultRepository?.updateKeyHealth(candidate.entry.id, health, error = e.message)
                }
                Log.w(TAG, "Candidate ${candidate.badgeName()} failed (${e.code}), trying next if available", e)
                lastError = e
            } catch (e: TimeoutCancellationException) {
                if (candidate is AiCandidate.ExternalKey) {
                    vaultRepository?.updateKeyHealth(candidate.entry.id, KeyHealthStatus.ERROR, error = "Timeout 90s")
                }
                lastError = e
            } catch (e: Exception) {
                if (candidate is AiCandidate.ExternalKey) {
                    vaultRepository?.updateKeyHealth(candidate.entry.id, KeyHealthStatus.ERROR, error = e.localizedMessage)
                }
                Log.w(TAG, "Candidate ${candidate.badgeName()} failed", e)
                lastError = e
            }
        }

        throw lastError ?: IllegalStateException("Aucun moteur IA disponible.")
    }

    suspend fun generateWithSpecificCandidate(
        candidate: AiCandidate,
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean = true,
        maxTokens: Int = 6144,
        builtinCall: suspend () -> String
    ): String {
        return when (candidate) {
            is AiCandidate.BuiltInFirebase -> {
                val raw = builtinCall()
                if (jsonMode) extractJsonObject(raw) else raw
            }
            is AiCandidate.ExternalKey -> {
                val t0 = System.currentTimeMillis()
                try {
                    val raw = withTimeout(90_000L) {
                        withContext(Dispatchers.IO) {
                            executeCompletionCall(
                                entry = candidate.entry,
                                systemPrompt = systemPrompt,
                                historyTurns = emptyList(),
                                userPrompt = userPrompt,
                                jsonMode = jsonMode,
                                maxTokens = maxTokens
                            )
                        }
                    }
                    val latency = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                    vaultRepository?.updateKeyHealth(
                        id = candidate.entry.id,
                        status = KeyHealthStatus.CONNECTED,
                        latencyMs = latency,
                        error = null
                    )
                    vaultRepository?.incrementUsage(candidate.entry.id)
                    if (jsonMode) extractJsonObject(raw) else raw
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpProviderException) {
                    val health = if (e.code == 429) KeyHealthStatus.RATE_LIMITED else KeyHealthStatus.ERROR
                    vaultRepository?.updateKeyHealth(candidate.entry.id, health, error = e.message)
                    throw e
                } catch (e: Exception) {
                    vaultRepository?.updateKeyHealth(
                        candidate.entry.id,
                        KeyHealthStatus.ERROR,
                        error = e.localizedMessage
                    )
                    throw e
                }
            }
        }
    }

    fun streamChat(
        systemPrompt: String,
        historyTurns: List<Pair<String, String>>,
        userInput: String,
        builtinStream: () -> Flow<String>
    ): Flow<MultiStreamEvent> = flow {
        val candidates = resolveCandidates(
            keys = vaultRepository?.keys?.value.orEmpty(),
            settings = vaultRepository?.routingSettings?.value ?: AiRoutingSettings()
        )
        var lastError: Exception? = null
        var attemptIndex = 0

        for (candidate in candidates) {
            val isFailover = attemptIndex > 0
            attemptIndex++
            var emittedAnyChunk = false
            try {
                emit(MultiStreamEvent.EngineSelected(candidate.badgeName(), isFailover))
                when (candidate) {
                    is AiCandidate.BuiltInFirebase -> {
                        builtinStream().collect { accumulated ->
                            emittedAnyChunk = true
                            emit(MultiStreamEvent.Chunk(accumulated))
                        }
                        if (!emittedAnyChunk) {
                            throw IllegalStateException("Réponse vide de Firebase IA")
                        }
                        return@flow
                    }
                    is AiCandidate.ExternalKey -> {
                        val t0 = System.currentTimeMillis()
                        withTimeout(60_000L) {
                            executeStreamingCall(
                                entry = candidate.entry,
                                systemPrompt = systemPrompt,
                                historyTurns = historyTurns,
                                userPrompt = userInput
                            ) { accumulated ->
                                emittedAnyChunk = true
                                emit(MultiStreamEvent.Chunk(accumulated))
                            }
                        }
                        val latency = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                        if (!emittedAnyChunk) {
                            throw IllegalStateException("Flux vide de ${candidate.entry.displayTitle()}")
                        }
                        vaultRepository?.updateKeyHealth(
                            id = candidate.entry.id,
                            status = KeyHealthStatus.CONNECTED,
                            latencyMs = latency,
                            error = null
                        )
                        vaultRepository?.incrementUsage(candidate.entry.id)
                        return@flow
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpProviderException) {
                val health = if (e.code == 429) KeyHealthStatus.RATE_LIMITED else KeyHealthStatus.ERROR
                if (candidate is AiCandidate.ExternalKey) {
                    vaultRepository?.updateKeyHealth(candidate.entry.id, health, error = e.message)
                }
                lastError = e
                if (emittedAnyChunk) throw e
            } catch (e: Exception) {
                if (candidate is AiCandidate.ExternalKey) {
                    vaultRepository?.updateKeyHealth(candidate.entry.id, KeyHealthStatus.ERROR, error = e.localizedMessage)
                }
                lastError = e
                if (emittedAnyChunk) throw e
            }
        }

        throw lastError ?: IllegalStateException("Aucun moteur IA disponible pour répondre.")
    }.flowOn(Dispatchers.IO)

    private suspend fun executeStreamingCall(
        entry: AiApiKeyEntry,
        systemPrompt: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        onAccumulated: suspend (String) -> Unit
    ) {
        val request = buildProviderRequest(
            entry = entry,
            systemPrompt = systemPrompt,
            historyTurns = historyTurns,
            userPrompt = userPrompt,
            stream = true,
            jsonMode = false,
            maxTokens = 4096
        )

        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw HttpProviderException(resp.code, extractProviderErrorMessage(resp.code, errBody))
            }

            val body = resp.body ?: throw IOException("Corps de réponse vide")
            val source = body.source()
            val accumulated = StringBuilder()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed == "data: [DONE]") continue

                val delta = when (entry.provider) {
                    AiProvider.ANTHROPIC -> parseAnthropicSseLine(trimmed)
                    AiProvider.GEMINI_REST -> parseGeminiSseLine(trimmed)
                    else -> parseOpenAiSseLine(trimmed)
                }
                if (!delta.isNullOrEmpty()) {
                    accumulated.append(delta)
                    onAccumulated(accumulated.toString())
                }
            }
        }
    }

    private fun executeCompletionCall(
        entry: AiApiKeyEntry,
        systemPrompt: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        jsonMode: Boolean,
        maxTokens: Int
    ): String {
        val request = buildProviderRequest(
            entry = entry,
            systemPrompt = systemPrompt,
            historyTurns = historyTurns,
            userPrompt = userPrompt,
            stream = false,
            jsonMode = jsonMode,
            maxTokens = maxTokens
        )

        httpClient.newCall(request).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw HttpProviderException(resp.code, extractProviderErrorMessage(resp.code, rawBody))
            }
            return when (entry.provider) {
                AiProvider.ANTHROPIC -> {
                    val obj = JSONObject(rawBody)
                    val contentArr = obj.optJSONArray("content")
                    buildString {
                        if (contentArr != null) {
                            for (i in 0 until contentArr.length()) {
                                val block = contentArr.optJSONObject(i) ?: continue
                                if (block.optString("type") == "text") {
                                    append(block.optString("text"))
                                }
                            }
                        }
                    }
                }
                AiProvider.GEMINI_REST -> {
                    val obj = JSONObject(rawBody)
                    val candidates = obj.optJSONArray("candidates")
                    val first = candidates?.optJSONObject(0)
                    val parts = first?.optJSONObject("content")?.optJSONArray("parts")
                    buildString {
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                append(parts.optJSONObject(i)?.optString("text").orEmpty())
                            }
                        }
                    }
                }
                else -> {
                    val obj = JSONObject(rawBody)
                    val choices = obj.optJSONArray("choices")
                    choices?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                }
            }
        }
    }

    private fun buildProviderRequest(
        entry: AiApiKeyEntry,
        systemPrompt: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        stream: Boolean,
        jsonMode: Boolean,
        maxTokens: Int
    ): Request {
        val model = entry.effectiveModel()
        val key = entry.apiKey.trim()

        return when (entry.provider) {
            AiProvider.ANTHROPIC -> {
                val messagesArr = JSONArray()
                historyTurns.forEach { (role, text) ->
                    val normalizedRole = if (role.equals("model", true) || role.equals("assistant", true)) "assistant" else "user"
                    messagesArr.put(JSONObject().apply {
                        put("role", normalizedRole)
                        put("content", text)
                    })
                }
                messagesArr.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
                val bodyJson = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", maxTokens)
                    put("system", systemPrompt)
                    put("messages", messagesArr)
                    if (stream) put("stream", true)
                }
                Request.Builder()
                    .url(entry.effectiveEndpoint())
                    .addHeader("x-api-key", key)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            }
            AiProvider.GEMINI_REST -> {
                val base = entry.effectiveEndpoint().trimEnd('/')
                val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
                val url = "$base/$model:$action"

                val contentsArr = JSONArray()
                historyTurns.forEach { (role, text) ->
                    val geminiRole = if (role.equals("model", true) || role.equals("assistant", true)) "model" else "user"
                    contentsArr.put(JSONObject().apply {
                        put("role", geminiRole)
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    })
                }
                contentsArr.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                })

                val genConfig = JSONObject().apply {
                    put("maxOutputTokens", maxTokens)
                    if (jsonMode) put("responseMimeType", "application/json")
                }

                val bodyJson = JSONObject().apply {
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                    put("contents", contentsArr)
                    put("generationConfig", genConfig)
                }
                Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", key)
                    .addHeader("content-type", "application/json")
                    .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            }
            else -> {
                val messagesArr = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messagesArr.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                historyTurns.forEach { (role, text) ->
                    val openAiRole = if (role.equals("model", true) || role.equals("assistant", true)) "assistant" else "user"
                    messagesArr.put(JSONObject().apply {
                        put("role", openAiRole)
                        put("content", text)
                    })
                }
                messagesArr.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
                val bodyJson = JSONObject().apply {
                    put("model", model)
                    put("messages", messagesArr)
                    put("max_tokens", maxTokens)
                    if (stream) put("stream", true)
                    if (jsonMode && entry.provider in JSON_OBJECT_CAPABLE_PROVIDERS) {
                        put("response_format", JSONObject().put("type", "json_object"))
                    }
                }
                val builder = Request.Builder()
                    .url(entry.effectiveEndpoint())
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("content-type", "application/json")
                if (entry.provider == AiProvider.OPENROUTER) {
                    builder.addHeader("HTTP-Referer", "https://forgeapk.aistudio.local")
                    builder.addHeader("X-Title", "ForgeAPK Studio")
                }
                builder.post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
            }
        }
    }

    private fun extractProviderErrorMessage(code: Int, rawBody: String): String {
        val parsedMsg = try {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")
                ?: obj.optString("message").ifBlank { null }
        } catch (_: Exception) {
            null
        }
        return when (code) {
            401, 403 -> "HTTP $code · Clé API refusée ou expirée"
            429 -> "HTTP 429 · Limite de requêtes/quota atteinte"
            404 -> "HTTP 404 · Modèle ou endpoint introuvable"
            else -> parsedMsg?.take(120) ?: "Erreur fournisseur HTTP $code"
        }
    }

    companion object {
        private const val TAG = "MultiAiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JSON_OBJECT_CAPABLE_PROVIDERS = setOf(
            AiProvider.OPENAI,
            AiProvider.DEEPSEEK,
            AiProvider.GROQ,
            AiProvider.MISTRAL,
            AiProvider.OPENROUTER
        )

        fun resolveCandidates(
            keys: List<AiApiKeyEntry>,
            settings: AiRoutingSettings
        ): List<AiCandidate> {
            val activeKeys = keys.filter {
                it.isEnabled && it.apiKey.isNotBlank() && it.effectiveEndpoint().isNotBlank()
            }
            val list = mutableListOf<AiCandidate>()

            when (settings.mode) {
                AiRoutingMode.BUILTIN_FIREBASE -> {
                    list.add(AiCandidate.BuiltInFirebase)
                    return list
                }
                AiRoutingMode.SINGLE_KEY -> {
                    val primary = activeKeys.firstOrNull { it.id == settings.selectedKeyId }
                        ?: activeKeys.firstOrNull()
                    if (primary != null) {
                        list.add(AiCandidate.ExternalKey(primary))
                    }
                    if (settings.fallbackToBuiltIn || list.isEmpty()) {
                        list.add(AiCandidate.BuiltInFirebase)
                    }
                }
                AiRoutingMode.AUTO_FAILOVER_POOL -> {
                    val prioritized = if (settings.selectedKeyId != null) {
                        val selected = activeKeys.firstOrNull { it.id == settings.selectedKeyId }
                        if (selected != null) {
                            listOf(selected) + activeKeys.filterNot { it.id == selected.id }
                        } else {
                            activeKeys
                        }
                    } else {
                        activeKeys
                    }
                    prioritized.forEach { list.add(AiCandidate.ExternalKey(it)) }
                    if (settings.fallbackToBuiltIn || list.isEmpty()) {
                        list.add(AiCandidate.BuiltInFirebase)
                    }
                }
            }
            return list
        }

        fun extractJsonObject(raw: String): String {
            val trimmed = raw.trim()
            val stripped = if (trimmed.startsWith("```")) {
                trimmed
                    .removePrefix("```json")
                    .removePrefix("```JSON")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
            } else {
                trimmed
            }
            val firstBrace = stripped.indexOf('{')
            val lastBrace = stripped.lastIndexOf('}')
            return if (firstBrace != -1 && lastBrace > firstBrace) {
                stripped.substring(firstBrace, lastBrace + 1)
            } else {
                stripped
            }
        }

        fun parseOpenAiSseLine(line: String): String? {
            if (!line.startsWith("data:")) return null
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") return null
            return try {
                val obj = JSONObject(payload)
                val choices = obj.optJSONArray("choices") ?: return null
                val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
                if (delta.isNull("content")) null else delta.optString("content", "").ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }

        fun parseAnthropicSseLine(line: String): String? {
            if (!line.startsWith("data:")) return null
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) return null
            return try {
                val obj = JSONObject(payload)
                if (obj.optString("type") == "content_block_delta") {
                    val delta = obj.optJSONObject("delta") ?: return null
                    if (delta.isNull("text")) null else delta.optString("text", "").ifEmpty { null }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun parseGeminiSseLine(line: String): String? {
            if (!line.startsWith("data:")) return null
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) return null
            return try {
                val obj = JSONObject(payload)
                val candidates = obj.optJSONArray("candidates") ?: return null
                val parts = candidates.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: return null
                buildString {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (!part.isNull("text")) {
                            append(part.optString("text", ""))
                        }
                    }
                }.ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }
    }
}

class HttpProviderException(val code: Int, message: String) : IOException(message)
