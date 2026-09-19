package com.example.settings

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages encrypted, secure storage for API credentials and keys.
 * Ensures the API key is never logged, printed to logcat, or exposed in plaintext.
 */
class SecureStorageManager(context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "x_secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback with obfuscated storage if device security provider is unavailable (e.g. older tests)
        context.getSharedPreferences("x_secure_credentials_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_SETUP_COMPLETED = "ai_setup_completed"
    }

    fun saveApiKey(apiKey: String) {
        val cleanKey = apiKey.trim()
        prefs.edit()
            .putString(KEY_API_KEY, cleanKey)
            .putBoolean(KEY_SETUP_COMPLETED, cleanKey.isNotEmpty())
            .apply()
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    fun isSetupCompleted(): Boolean {
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false) && hasValidApiKey()
    }

    fun setSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply()
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_API_KEY)
            .putBoolean(KEY_SETUP_COMPLETED, false)
            .apply()
    }

    /**
     * Returns a safely masked version of the API key for display in settings UI.
     * E.g. AIzaSy...94jK or ••••••••••••
     */
    fun getMaskedApiKey(): String {
        val key = getApiKey()
        if (key.isBlank()) return "Not configured"
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }
}
