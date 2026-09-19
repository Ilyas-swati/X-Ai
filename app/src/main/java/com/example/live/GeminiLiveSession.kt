package com.example.live

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.tools.AgentToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onStateChanged: (SessionState) -> Unit,
    private val onToolExecuted: (toolName: String, summary: String) -> Unit,
    private val onError: (String) -> Unit
) {
    enum class SessionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED_LISTENING,
        THINKING,
        AI_SPEAKING,
        INTERRUPTED
    }

    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val DEFAULT_LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val WS_BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private var isSetupComplete = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for persistent streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        if (isConnected.get()) return

        val secureStorage = com.example.settings.SecureStorageManager(context)
        val apiKey = secureStorage.getApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "No API key configured in secure storage.")
            onError("AI Connection cannot be established: Missing API key. Please open Settings or Complete Setup.")
            onStateChanged(SessionState.DISCONNECTED)
            return
        }

        onStateChanged(SessionState.CONNECTING)

        val wsUrl = "$WS_BASE_URL?key=$apiKey"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to Gemini Live")
                isConnected.set(true)
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected.set(false)
                isSetupComplete = false
                onStateChanged(SessionState.DISCONNECTED)
                val responseBody = try { response?.body?.string() } catch (e: Exception) { null }
                val errorDetails = if (response?.code == 400 || response?.code == 403) {
                    "Invalid API key or unauthorized. Please verify your API key in Settings."
                } else if (responseBody != null && responseBody.contains("API_KEY_INVALID")) {
                    "Invalid API key. Please update your key in Settings."
                } else {
                    "Connection error: ${t.localizedMessage ?: "Network or API key error"}"
                }
                onError(errorDetails)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed ($code): $reason")
                isConnected.set(false)
                isSetupComplete = false
                onStateChanged(SessionState.DISCONNECTED)
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val settings = com.example.settings.XSettingsManager(context).settingsState.value
            val voiceName = settings.selectedVoice
            val languagePref = settings.selectedLanguage
            val selectedModelName = if (settings.aiModel.startsWith("models/")) settings.aiModel else "models/${settings.aiModel}"

            val systemInstructionPrompt = buildString {
                append("You are X, an intelligent, natural, and lightning-fast voice-to-voice AI agent on Android. ")
                append("Your name is X. Keep your spoken responses warm, natural, human-like, and conversational without markdown or robotic formatting. ")
                append("CRITICAL AUTOMATIC LANGUAGE DETECTION RULE: ")
                append("You must automatically detect the language the user is speaking or writing and respond in the EXACT SAME language. ")
                append("Supported languages include: Urdu, Roman Urdu, English, Pashto, Hindi, and any other language spoken by the user. ")
                append("Examples: ")
                append("- If the user speaks Urdu, reply in natural Urdu. ")
                append("- If the user speaks Roman Urdu (e.g. 'kya haal hai', 'WhatsApp kholo'), reply naturally in Roman Urdu. ")
                append("- If the user speaks English (e.g. 'switch to English', 'what's the weather'), reply in English. ")
                append("- If the user speaks Pashto (e.g. 'tsanga ye', 'jawab raaka'), reply in Pashto. ")
                append("- If the user speaks Hindi, reply in Hindi. ")
                append("If the user mixes languages, detect the dominant conversational language and respond in that language. ")
                append("Keep the language consistent throughout your current response unless the user changes language. ")
                if (!languagePref.startsWith("Auto Detect")) {
                    append("User's explicit preference is: $languagePref. ")
                }
                append("You are equipped with autonomous Android capabilities including device actions, screen perception, and a built-in AI Coding Agent that can inspect files, search code, edit files, and build the project when instructed.")
            }

            val setupObj = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", selectedModelName)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstructionPrompt)
                            })
                        })
                    })
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", AgentToolRegistry.getFunctionDeclarationsJson())
                        })
                    })
                })
            }

            val setupString = setupObj.toString()
            Log.d(TAG, "Sending setup configuration for voice: $voiceName, model: $selectedModelName")
            ws.send(setupString)
            isSetupComplete = true
            onStateChanged(SessionState.CONNECTED_LISTENING)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup frame", e)
            onError("Failed to initialize Live voice session: ${e.message}")
        }
    }

    fun sendAudioChunk(pcmBytes: ByteArray) {
        val ws = webSocket ?: return
        if (!isConnected.get() || !isSetupComplete) return

        try {
            val base64Pcm = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val chunkObj = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    })
                })
            }
            ws.send(chunkObj.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Error sending realtime audio chunk: ${e.message}")
        }
    }

    /**
     * Sends an interruption signal to the Gemini Live session so it immediately halts current generation.
     */
    fun sendInterruptionSignal() {
        val ws = webSocket ?: return
        if (!isConnected.get()) return

        try {
            val interruptObj = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray())
                    put("turnComplete", true)
                })
            }
            ws.send(interruptObj.toString())
            onStateChanged(SessionState.INTERRUPTED)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending interruption frame: ${e.message}")
        }
    }

    private fun handleServerMessage(ws: WebSocket, message: String) {
        try {
            val json = JSONObject(message)

            // 1. Audio stream chunks from modelTurn
            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val audioBase64 = inlineData.optString("data", "")
                                if (audioBase64.isNotEmpty()) {
                                    val pcm = Base64.decode(audioBase64, Base64.DEFAULT)
                                    onStateChanged(SessionState.AI_SPEAKING)
                                    onAudioReceived(pcm)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    onStateChanged(SessionState.CONNECTED_LISTENING)
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    onStateChanged(SessionState.INTERRUPTED)
                }
            }

            // 2. Structured Tool/Function Calls
            val toolCall = json.optJSONObject("toolCall")
            if (toolCall != null) {
                onStateChanged(SessionState.THINKING)
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    scope.launch(Dispatchers.IO) {
                        val responsesArray = JSONArray()
                        for (i in 0 until functionCalls.length()) {
                            val call = functionCalls.getJSONObject(i)
                            val callId = call.optString("id", "")
                            val name = call.optString("name", "")
                            val args = call.optJSONObject("args") ?: JSONObject()

                            Log.d(TAG, "Executing tool call $name ($callId)")
                            val result = AgentToolRegistry.executeTool(context, name, args)
                            onToolExecuted(name, result.summary)

                            val fnResponse = JSONObject().apply {
                                put("id", callId)
                                put("name", name)
                                put("response", JSONObject().apply {
                                    put("output", JSONObject().apply {
                                        put("success", result.success)
                                        put("result", result.summary)
                                        put("details", result.data)
                                    })
                                })
                            }
                            responsesArray.put(fnResponse)
                        }

                        // Send tool response back to Gemini session
                        val toolResponseObj = JSONObject().apply {
                            put("toolResponse", JSONObject().apply {
                                put("functionResponses", responsesArray)
                            })
                        }
                        ws.send(toolResponseObj.toString())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server message", e)
        }
    }

    fun disconnect() {
        isConnected.set(false)
        isSetupComplete = false
        try {
            webSocket?.close(1000, "Session ended by user")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing websocket: ${e.message}")
        }
        webSocket = null
        onStateChanged(SessionState.DISCONNECTED)
    }
}
