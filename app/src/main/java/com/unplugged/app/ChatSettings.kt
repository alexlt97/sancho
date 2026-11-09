package com.unplugged.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class WebSearchMode {
    VANADIUM,
    SEARXNG,
    BRAVE_API,
}

class ChatSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun metricsEnabled(): Boolean = preferences.getBoolean(METRICS_ENABLED_KEY, true)

    fun setMetricsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(METRICS_ENABLED_KEY, enabled).apply()
    }

    fun showSearchQuery(): Boolean = preferences.getBoolean(SHOW_SEARCH_QUERY_KEY, false)

    fun setShowSearchQuery(enabled: Boolean) {
        preferences.edit().putBoolean(SHOW_SEARCH_QUERY_KEY, enabled).apply()
    }

    fun allowMultipleSearches(): Boolean =
        preferences.getBoolean(ALLOW_MULTIPLE_SEARCHES_KEY, false)

    fun setAllowMultipleSearches(enabled: Boolean) {
        preferences.edit().putBoolean(ALLOW_MULTIPLE_SEARCHES_KEY, enabled).apply()
    }

    fun webSearchMode(): WebSearchMode = runCatching {
        WebSearchMode.valueOf(preferences.getString(WEB_SEARCH_MODE_KEY, null).orEmpty())
    }.getOrDefault(WebSearchMode.VANADIUM)

    fun setWebSearchMode(mode: WebSearchMode) {
        preferences.edit().putString(WEB_SEARCH_MODE_KEY, mode.name).apply()
    }

    fun searxngEndpoint(): String = preferences.getString(SEARXNG_ENDPOINT_KEY, "").orEmpty()

    fun setSearxngEndpoint(endpoint: String) {
        preferences.edit().putString(SEARXNG_ENDPOINT_KEY, endpoint.trim()).apply()
    }

    fun braveApiKey(): String = encryptedBraveApiKey.get()

    fun setBraveApiKey(apiKey: String) {
        encryptedBraveApiKey.set(apiKey.trim())
    }

    private val encryptedBraveApiKey = EncryptedPreference(
        preferences = preferences,
        preferenceKey = BRAVE_API_KEY_KEY,
        keyAlias = BRAVE_API_KEY_ALIAS,
    )

    private companion object {
        const val PREFERENCES_NAME = "sancho_chat_settings"
        const val METRICS_ENABLED_KEY = "metrics_enabled"
        const val SHOW_SEARCH_QUERY_KEY = "show_search_query"
        const val ALLOW_MULTIPLE_SEARCHES_KEY = "allow_multiple_searches"
        const val WEB_SEARCH_MODE_KEY = "web_search_mode"
        const val SEARXNG_ENDPOINT_KEY = "searxng_endpoint"
        const val BRAVE_API_KEY_KEY = "brave_api_key_encrypted"
        const val BRAVE_API_KEY_ALIAS = "sancho_brave_api_key"
    }
}

private class EncryptedPreference(
    private val preferences: SharedPreferences,
    private val preferenceKey: String,
    private val keyAlias: String,
) {
    fun get(): String = runCatching {
        val encoded = preferences.getString(preferenceKey, null) ?: return ""
        val combined = Base64.decode(encoded, Base64.DEFAULT)
        require(combined.size > IV_LENGTH) { "Encrypted preference is invalid." }
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }.getOrDefault("")

    fun set(value: String) {
        if (value.isBlank()) {
            preferences.edit().remove(preferenceKey).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + ciphertext
        preferences.edit()
            .putString(preferenceKey, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
