package com.example.model

import java.util.UUID

enum class AiProvider(
    val displayName: String,
    val shortName: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val accentHex: String,
    val tagline: String,
    val keyPlaceholder: String
) {
    OPENAI(
        displayName = "OpenAI",
        shortName = "OpenAI",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        suggestedModels = listOf("gpt-4o-mini", "gpt-4o", "o3-mini"),
        accentHex = "#10B981",
        tagline = "GPT-4o & raisonnement rapide",
        keyPlaceholder = "sk-proj-..."
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        shortName = "Claude",
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-sonnet-4-20250514",
        suggestedModels = listOf("claude-sonnet-4-20250514", "claude-3-5-haiku-latest"),
        accentHex = "#FFB300",
        tagline = "Claude Code & architecture",
        keyPlaceholder = "sk-ant-..."
    ),
    GEMINI_REST(
        displayName = "Google Gemini (Clé API)",
        shortName = "Gemini API",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/models",
        defaultModel = "gemini-2.5-flash",
        suggestedModels = listOf("gemini-2.5-flash", "gemini-2.5-pro"),
        accentHex = "#00E5FF",
        tagline = "Clé Google AI Studio personnelle",
        keyPlaceholder = "AIzaSy..."
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        shortName = "DeepSeek",
        defaultEndpoint = "https://api.deepseek.com/chat/completions",
        defaultModel = "deepseek-chat",
        suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        accentHex = "#3B82F6",
        tagline = "Spécialiste code & algorithmes",
        keyPlaceholder = "sk-..."
    ),
    GROQ(
        displayName = "Groq",
        shortName = "Groq",
        defaultEndpoint = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "llama-3.3-70b-versatile",
        suggestedModels = listOf("llama-3.3-70b-versatile", "qwen-2.5-coder-32b"),
        accentHex = "#F97316",
        tagline = "Inférence ultra-rapide Llama/Qwen",
        keyPlaceholder = "gsk_..."
    ),
    MISTRAL(
        displayName = "Mistral / Codestral",
        shortName = "Mistral",
        defaultEndpoint = "https://api.mistral.ai/v1/chat/completions",
        defaultModel = "codestral-latest",
        suggestedModels = listOf("codestral-latest", "mistral-large-latest"),
        accentHex = "#EC4899",
        tagline = "Codestral dédié au développement",
        keyPlaceholder = "..."
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        shortName = "OpenRouter",
        defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "anthropic/claude-3.5-sonnet",
        suggestedModels = listOf(
            "anthropic/claude-3.5-sonnet",
            "deepseek/deepseek-chat",
            "qwen/qwen-2.5-coder-32b-instruct"
        ),
        accentHex = "#A855F7",
        tagline = "Routeur unifié multi-modèles",
        keyPlaceholder = "sk-or-v1-..."
    ),
    CUSTOM_OPENAI(
        displayName = "Endpoint Custom (OpenAI)",
        shortName = "Custom",
        defaultEndpoint = "",
        defaultModel = "custom-coder",
        suggestedModels = emptyList(),
        accentHex = "#94A3B8",
        tagline = "Together, Fireworks, Ollama, vLLM...",
        keyPlaceholder = "Bearer token / clé..."
    );

    companion object {
        fun fromName(raw: String): AiProvider {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OPENAI
        }
    }
}

enum class KeyHealthStatus(val label: String) {
    UNTESTED("Non testée"),
    CONNECTED("Connectée"),
    RATE_LIMITED("Quota / 429"),
    ERROR("Erreur")
}

enum class AiSpecialty(
    val title: String,
    val shortTitle: String,
    val roleDescription: String,
    val accentHex: String
) {
    ARCHITECTURE_UI(
        title = "Architecture & Interface UI",
        shortTitle = "UI / Archi",
        roleDescription = "Écrans Jetpack Compose, navigation, thème Material 3 & ergonomie tactile",
        accentHex = "#FFB300"
    ),
    CORE_LOGIC_ALGO(
        title = "Logique Métier & Algorithmes",
        shortTitle = "Logique / Moteur",
        roleDescription = "Moteur de jeu, physique, algorithmes, ViewModel & gestion d'état",
        accentHex = "#3B82F6"
    ),
    INTERACTIVE_PREVIEW(
        title = "Prototype Tactile & Canvas",
        shortTitle = "Aperçu Tactile",
        roleDescription = "Bundle HTML5/Canvas/JS interactif autonome pour l'aperçu mobile et l'APK",
        accentHex = "#F97316"
    ),
    INTEGRATION_BUILD(
        title = "Configuration, Build & Tests",
        shortTitle = "Build & Tests",
        roleDescription = "AndroidManifest, build.gradle.kts, ressources & tests unitaires",
        accentHex = "#10B981"
    );

    companion object {
        fun fromNameOrNull(raw: String?): AiSpecialty? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }
    }
}

data class AiApiKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val provider: AiProvider = AiProvider.OPENAI,
    val apiKey: String = "",
    val modelId: String = "",
    val customBaseUrl: String = "",
    val isEnabled: Boolean = true,
    val specialtyOverride: AiSpecialty? = null,
    val status: KeyHealthStatus = KeyHealthStatus.UNTESTED,
    val lastLatencyMs: Long? = null,
    val lastError: String? = null,
    val usageCount: Int = 0
) {
    fun displayTitle(): String {
        return label.trim().ifBlank { "${provider.shortName} (${effectiveModel()})" }
    }

    fun effectiveModel(): String {
        return modelId.trim().ifBlank { provider.defaultModel }
    }

    fun effectiveEndpoint(): String {
        return if (provider == AiProvider.CUSTOM_OPENAI) {
            customBaseUrl.trim()
        } else {
            customBaseUrl.trim().ifBlank { provider.defaultEndpoint }
        }
    }

    fun bestSpecialty(): AiSpecialty {
        specialtyOverride?.let { return it }
        val modelLower = effectiveModel().lowercase()
        return when {
            "claude" in modelLower -> AiSpecialty.ARCHITECTURE_UI
            "deepseek" in modelLower || "reasoner" in modelLower || "coder" in modelLower || "qwen" in modelLower || "o3" in modelLower || "o1" in modelLower ->
                AiSpecialty.CORE_LOGIC_ALGO
            provider == AiProvider.GROQ || "llama" in modelLower || "flash" in modelLower ->
                AiSpecialty.INTERACTIVE_PREVIEW
            provider == AiProvider.ANTHROPIC -> AiSpecialty.ARCHITECTURE_UI
            provider == AiProvider.DEEPSEEK || provider == AiProvider.MISTRAL -> AiSpecialty.CORE_LOGIC_ALGO
            provider == AiProvider.OPENAI || provider == AiProvider.GEMINI_REST -> AiSpecialty.INTEGRATION_BUILD
            else -> AiSpecialty.INTEGRATION_BUILD
        }
    }

    fun maskedKey(): String {
        val trimmed = apiKey.trim()
        return when {
            trimmed.length <= 6 -> "••••••"
            trimmed.length <= 12 -> "${trimmed.take(2)}••••${trimmed.takeLast(2)}"
            else -> "${trimmed.take(5)}••••${trimmed.takeLast(4)}"
        }
    }
}

enum class AiRoutingMode(val title: String, val subtitle: String) {
    AUTO_FAILOVER_POOL(
        title = "Pool Multi-Clés (Failover Auto)",
        subtitle = "Enchaîne vos clés actives automatiquement en cas de quota ou d'erreur"
    ),
    SINGLE_KEY(
        title = "Clé spécifique",
        subtitle = "Utilise en priorité la clé API sélectionnée"
    ),
    BUILTIN_FIREBASE(
        title = "Firebase IA Intégrée",
        subtitle = "Moteur Gemini intégré sans clé API externe"
    )
}

data class AiRoutingSettings(
    val mode: AiRoutingMode = AiRoutingMode.AUTO_FAILOVER_POOL,
    val selectedKeyId: String? = null,
    val fallbackToBuiltIn: Boolean = true
)

sealed interface KeyTestResult {
    data class Success(val latencyMs: Long, val message: String) : KeyTestResult
    data class Failure(val status: KeyHealthStatus, val errorMessage: String) : KeyTestResult
}
