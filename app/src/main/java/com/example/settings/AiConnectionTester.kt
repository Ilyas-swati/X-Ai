package com.example.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ConnectionTestResult {
    data class Success(val message: String) : ConnectionTestResult()
    data class Failure(val error: String) : ConnectionTestResult()
}

/**
 * Validates connection with the chosen AI Provider and API key.
 * Does not invent keys or store plaintext keys in logs.
 */
object AiConnectionTester {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(
        context: Context,
        provider: String,
        apiKey: String,
        model: String = "gemini-2.5-flash"
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext ConnectionTestResult.Failure("API Key cannot be empty.")
        }

        if (provider.contains("Ollama")) {
            return@withContext ConnectionTestResult.Success("Configured for Local Ollama.")
        }

        try {
            // Test with a lightweight generateContent call to verify authentication
            val sanitizedModel = if (model.contains("native-audio")) "gemini-2.5-flash" else model
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$sanitizedModel:generateContent?key=$cleanKey"

            val testPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "ping") })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(testPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                return@withContext ConnectionTestResult.Success("Connection successful! API key is verified.")
            } else {
                val errorMsg = try {
                    val json = JSONObject(body)
                    json.optJSONObject("error")?.optString("message") ?: "HTTP error $code"
                } catch (e: Exception) {
                    "Authentication failed (HTTP $code)"
                }
                return@withContext ConnectionTestResult.Failure(errorMsg)
            }
        } catch (e: Exception) {
            return@withContext ConnectionTestResult.Failure("Network error: ${e.localizedMessage ?: "Failed to reach AI server"}")
        }
    }
}
