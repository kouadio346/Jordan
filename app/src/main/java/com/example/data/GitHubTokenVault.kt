package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.model.GitHubUserProfile
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encrypted local GitHub Personal Access Token vault backed by Android Keystore (AES-256-GCM).
 * Tokens stay strictly on-device and are only sent over HTTPS to api.github.com when exporting code.
 */
class GitHubTokenVault(
    context: Context,
    userId: String
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val safeUserKey = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default" }
    private val tokenStorageKey = "encrypted_gh_pat_$safeUserKey"
    private val loginStorageKey = "gh_login_$safeUserKey"
    private val nameStorageKey = "gh_name_$safeUserKey"
    private val lock = Any()

    private val _hasStoredToken = MutableStateFlow(!getToken().isNullOrBlank())
    val hasStoredToken: StateFlow<Boolean> = _hasStoredToken.asStateFlow()

    fun saveToken(token: String) = synchronized(lock) {
        val clean = token.trim()
        if (clean.isEmpty()) return@synchronized
        val encrypted = encryptString(clean)
        prefs.edit().putString(tokenStorageKey, encrypted).commit()
        _hasStoredToken.value = true
    }

    fun getToken(): String? = synchronized(lock) {
        val encrypted = prefs.getString(tokenStorageKey, null) ?: return@synchronized null
        decryptString(encrypted)?.takeIf { it.isNotBlank() }
    }

    fun saveCachedProfile(profile: GitHubUserProfile) = synchronized(lock) {
        prefs.edit()
            .putString(loginStorageKey, profile.login)
            .putString(nameStorageKey, profile.name ?: "")
            .commit()
    }

    fun getCachedProfile(): GitHubUserProfile? = synchronized(lock) {
        val login = prefs.getString(loginStorageKey, null)?.takeIf { it.isNotBlank() } ?: return@synchronized null
        val name = prefs.getString(nameStorageKey, null)?.takeIf { it.isNotBlank() }
        GitHubUserProfile(login = login, name = name)
    }

    fun clear() = synchronized(lock) {
        prefs.edit()
            .remove(tokenStorageKey)
            .remove(loginStorageKey)
            .remove(nameStorageKey)
            .commit()
        _hasStoredToken.value = false
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
            Log.w(TAG, "Could not decrypt stored GitHub token", e)
            null
        }
    }

    companion object {
        private const val TAG = "GitHubTokenVault"
        private const val PREFS_NAME = "forgeapk_encrypted_github_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "forgeapk_github_token_master_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
