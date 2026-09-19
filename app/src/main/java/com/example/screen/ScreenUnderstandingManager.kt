package com.example.screen

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import android.view.View
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object ScreenUnderstandingManager {
    private const val TAG = "ScreenUnderstanding"
    private var currentActivityRef: WeakReference<Activity>? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun setCurrentActivity(activity: Activity?) {
        currentActivityRef = if (activity != null) WeakReference(activity) else null
    }

    suspend fun captureCurrentScreen(): Bitmap? = withContext(Dispatchers.Main) {
        val activity = currentActivityRef?.get() ?: return@withContext null
        val window = activity.window ?: return@withContext null
        val view = window.decorView.rootView ?: return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            suspendCancellableCoroutine<Bitmap?> { cont ->
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val location = IntArray(2)
                view.getLocationInWindow(location)

                try {
                    PixelCopy.request(
                        window,
                        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                        bitmap,
                        { copyResult ->
                            if (copyResult == PixelCopy.SUCCESS) {
                                cont.resume(bitmap)
                            } else {
                                Log.w(TAG, "PixelCopy failed with code $copyResult")
                                cont.resume(null)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error in PixelCopy: ${e.message}")
                    cont.resume(null)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            view.isDrawingCacheEnabled = true
            @Suppress("DEPRECATION")
            val bitmap = Bitmap.createBitmap(view.drawingCache)
            @Suppress("DEPRECATION")
            view.isDrawingCacheEnabled = false
            bitmap
        }
    }

    suspend fun analyzeScreen(query: String = "What is visible on the screen? Identify main elements and action items."): ScreenAnalysisResult = withContext(Dispatchers.IO) {
        val bitmap = captureCurrentScreen() ?: return@withContext ScreenAnalysisResult(
            success = false,
            summary = "Screen capture is not available or app is not currently attached to an active window."
        )

        // Scale bitmap down to save bandwidth while preserving legibility
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * 0.5f).toInt().coerceAtLeast(320),
            (bitmap.height * 0.5f).toInt().coerceAtLeast(480),
            true
        )

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val activity = currentActivityRef?.get()
        val secureStorage = if (activity != null) {
            com.example.settings.SecureStorageManager(activity)
        } else {
            null
        }
        val apiKey = secureStorage?.getApiKey() ?: ""
        if (apiKey.isBlank()) {
            return@withContext ScreenAnalysisResult(
                success = false,
                summary = "AI API key is missing. Please configure your key in X Setup or Settings."
            )
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are X's screen perception module on Android. Analyze the provided screenshot and user request: '$query'. Provide concise, natural spoken breakdown of visible UI elements, active buttons, text fields, and suggested actions.")
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext ScreenAnalysisResult(
                    success = false,
                    summary = "Screen perception failed: HTTP ${response.code}"
                )
            }

            val respJson = JSONObject(body)
            val candidates = respJson.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text", "") ?: "No description available."

            ScreenAnalysisResult(
                success = true,
                summary = text.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screen analysis error: ${e.message}", e)
            ScreenAnalysisResult(
                success = false,
                summary = "Error analyzing screen: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}

data class ScreenAnalysisResult(
    val success: Boolean,
    val summary: String
)
