package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.model.AiApiKeyEntry
import com.example.model.AiProvider
import com.example.model.AiRoutingMode
import com.example.model.AiRoutingSettings
import com.example.model.AiSpecialty
import com.example.model.KeyHealthStatus
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted on-device AI API key vault backed by Android Keystore (AES-256-GCM).
 * Keys never leave the device except when making direct requests to the user's chosen AI provider.
 */
class AiKeyVaultRepository(
    context: Context,
    userId: String
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val safeUserKey = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default" }
    private val keysStorageKey = "encrypted_keys_$safeUserKey"
    private val settingsStorageKey = "routing_settings_$safeUserKey"
    private val vaultLock = Any()

    private val _keys = MutableStateFlow<List<AiApiKeyEntry>>(emptyList())
    val keys: StateFlow<List<AiApiKeyEntry>> = _keys.asStateFlow()

    private val _routingSettings = MutableStateFlow(AiRoutingSettings())
    val routingSettings: StateFlow<AiRoutingSettings> = _routingSettings.asStateFlow()

    init {
        loadFromPrefs()
    }

    fun addOrUpdateKey(
        existingId: String? = null,
        label: String,
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        customBaseUrl: String,
        specialtyOverride: AiSpecialty? = null,
        isEnabled: Boolean = true
    ): AiApiKeyEntry = synchronized(vaultLock) {
        val current = _keys.value.toMutableList()
        val cleanKey = apiKey.trim()
        val cleanModel = modelId.trim().ifBlank { provider.defaultModel }
        val cleanUrl = customBaseUrl.trim()
        val entry = if (!existingId.isNullOrBlank()) {
            val idx = current.indexOfFirst { it.id == existingId }
            val updated = AiApiKeyEntry(
                id = existingId,
                label = label.trim(),
                provider = provider,
                apiKey = cleanKey,
                modelId = cleanModel,
                customBaseUrl = cleanUrl,
                isEnabled = isEnabled,
                specialtyOverride = specialtyOverride,
                status = KeyHealthStatus.UNTESTED,
                lastLatencyMs = null,
                lastError = null,
                usageCount = if (idx >= 0) current[idx].usageCount else 0
            )
            if (idx >= 0) current[idx] = updated else current.add(updated)
            updated
        } else {
            val created = AiApiKeyEntry(
                id = UUID.randomUUID().toString(),
                label = label.trim(),
                provider = provider,
                apiKey = cleanKey,
                modelId = cleanModel,
                customBaseUrl = cleanUrl,
                isEnabled = isEnabled,
                specialtyOverride = specialtyOverride
            )
            current.add(created)
            created
        }

        persistKeys(current)

        val currentSettings = _routingSettings.value
        if (currentSettings.selectedKeyId == null) {
            persistSettings(currentSettings.copy(selectedKeyId = entry.id))
        }
        entry
    }

    fun toggleKeyEnabled(id: String) = synchronized(vaultLock) {
        val updated = _keys.value.map { entry ->
            if (entry.id == id) entry.copy(isEnabled = !entry.isEnabled) else entry
        }
        persistKeys(updated)
    }

    fun moveKey(id: String, delta: Int) = synchronized(vaultLock) {
        val list = _keys.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index == -1) return@synchronized
        val targetIndex = (index + delta).coerceIn(0, list.lastIndex)
        if (targetIndex == index) return@synchronized
        val item = list.removeAt(index)
        list.add(targetIndex, item)
        persistKeys(list)
    }

    fun deleteKey(id: String) = synchronized(vaultLock) {
        val updated = _keys.value.filterNot { it.id == id }
        persistKeys(updated)
        val settings = _routingSettings.value
        if (settings.selectedKeyId == id) {
            val nextSelected = updated.firstOrNull { it.isEnabled }?.id ?: updated.firstOrNull()?.id
            val nextMode = if (updated.isEmpty() && settings.mode == AiRoutingMode.SINGLE_KEY) {
                AiRoutingMode.AUTO_FAILOVER_POOL
            } else {
                settings.mode
            }
            persistSettings(settings.copy(mode = nextMode, selectedKeyId = nextSelected))
        }
    }

    fun updateKeyHealth(
        id: String,
        status: KeyHealthStatus,
        latencyMs: Long? = null,
        error: String? = null
    ) = synchronized(vaultLock) {
        val updated = _keys.value.map { entry ->
            if (entry.id == id) {
                entry.copy(
                    status = status,
                    lastLatencyMs = latencyMs ?: entry.lastLatencyMs,
                    lastError = error
                )
            } else {
                entry
            }
        }
        persistKeys(updated)
    }

    fun incrementUsage(id: String) = synchronized(vaultLock) {
        val updated = _keys.value.map { entry ->
            if (entry.id == id) entry.copy(usageCount = entry.usageCount + 1) else entry
        }
        persistKeys(updated)
    }

    fun setRoutingMode(mode: AiRoutingMode, selectedKeyId: String? = _routingSettings.value.selectedKeyId) {
        val validSelected = selectedKeyId ?: _keys.value.firstOrNull { it.isEnabled }?.id
        persistSettings(
            _routingSettings.value.copy(
                mode = mode,
                selectedKeyId = validSelected
            )
        )
    }

    fun setFallbackToBuiltIn(enabled: Boolean) {
        persistSettings(_routingSettings.value.copy(fallbackToBuiltIn = enabled))
    }

    private fun loadFromPrefs() {
        try {
            val encryptedBlob = prefs.getString(keysStorageKey, null)
            if (!encryptedBlob.isNullOrBlank()) {
                val jsonString = decryptString(encryptedBlob)
                if (jsonString != null) {
                    _keys.value = deserializeKeys(jsonString)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load encrypted AI keys", e)
            _keys.value = emptyList()
        }

        try {
            val settingsJson = prefs.getString(settingsStorageKey, null)
            if (!settingsJson.isNullOrBlank()) {
                val obj = JSONObject(settingsJson)
                val modeName = obj.optString("mode", AiRoutingMode.AUTO_FAILOVER_POOL.name)
                val mode = AiRoutingMode.entries.firstOrNull { it.name == modeName }
                    ?: AiRoutingMode.AUTO_FAILOVER_POOL
                val selectedId = obj.optString("selectedKeyId", "").ifBlank { null }
                val fallback = obj.optBoolean("fallbackToBuiltIn", true)
                _routingSettings.value = AiRoutingSettings(
                    mode = mode,
                    selectedKeyId = selectedId,
                    fallbackToBuiltIn = fallback
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load AI routing settings", e)
        }
    }

    private fun persistKeys(entries: List<AiApiKeyEntry>) {
        _keys.value = entries
        val jsonString = serializeKeys(entries)
        val encrypted = encryptString(jsonString)
        prefs.edit().putString(keysStorageKey, encrypted).commit()
    }

    private fun persistSettings(settings: AiRoutingSettings) {
        _routingSettings.value = settings
        val obj = JSONObject().apply {
            put("mode", settings.mode.name)
            put("selectedKeyId", settings.selectedKeyId ?: "")
            put("fallbackToBuiltIn", settings.fallbackToBuiltIn)
        }
        prefs.edit().putString(settingsStorageKey, obj.toString()).commit()
    }

    private fun serializeKeys(entries: List<AiApiKeyEntry>): String {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("label", entry.label)
                    put("provider", entry.provider.name)
                    put("apiKey", entry.apiKey)
                    put("modelId", entry.modelId)
                    put("customBaseUrl", entry.customBaseUrl)
                    put("isEnabled", entry.isEnabled)
                    put("specialtyOverride", entry.specialtyOverride?.name ?: "")
                    put("status", entry.status.name)
                    put("lastLatencyMs", entry.lastLatencyMs ?: -1L)
                    put("lastError", entry.lastError ?: "")
                    put("usageCount", entry.usageCount)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeKeys(raw: String): List<AiApiKeyEntry> {
        val arr = JSONArray(raw)
        val list = mutableListOf<AiApiKeyEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val provider = AiProvider.fromName(obj.optString("provider", AiProvider.OPENAI.name))
            val statusName = obj.optString("status", KeyHealthStatus.UNTESTED.name)
            val status = KeyHealthStatus.entries.firstOrNull { it.name == statusName }
                ?: KeyHealthStatus.UNTESTED
            val latencyRaw = obj.optLong("lastLatencyMs", -1L)
            list.add(
                AiApiKeyEntry(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    label = obj.optString("label", ""),
                    provider = provider,
                    apiKey = obj.optString("apiKey", ""),
                    modelId = obj.optString("modelId", provider.defaultModel),
                    customBaseUrl = obj.optString("customBaseUrl", ""),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    specialtyOverride = AiSpecialty.fromNameOrNull(obj.optString("specialtyOverride", "")),
                    status = status,
                    lastLatencyMs = if (latencyRaw >= 0L) latencyRaw else null,
                    lastError = obj.optString("lastError", "").ifBlank { null },
                    usageCount = obj.optInt("usageCount", 0)
                )
            )
        }
        return list
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptString(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val buffer = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(ciphertext)
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    private fun decryptString(encoded: String): String? {
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes)
            val ivSize = buffer.int
            if (ivSize !in 12..32) return null
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decrypt stored key vault", e)
            null
        }
    }

    companion object {
        private const val TAG = "AiKeyVaultRepo"
        private const val PREFS_NAME = "forgeapk_encrypted_ai_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "forgeapk_ai_keys_master_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
