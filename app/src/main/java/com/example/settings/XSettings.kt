package com.example.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceOption(
    val id: String,
    val displayName: String,
    val gender: String,
    val isFemale: Boolean,
    val description: String
)

data class XSettingsState(
    val aiProvider: String = "Gemini Live (Multimodal)",
    val aiModel: String = "gemini-2.5-flash-native-audio-preview-12-2025",
    val codingModel: String = "gemini-3.1-pro-preview", // Dedicated AI coding model
    val visionModel: String = "gemini-2.5-flash-image", // Screen/Vision model
    val selectedVoice: String = "Aoede", // Natural conversational female voice as default
    val selectedLanguage: String = "Auto Detect (Urdu / Roman Urdu / English / Pashto / Hindi)",
    val wakeWordEnabled: Boolean = true,
    val backgroundListeningEnabled: Boolean = true,
    val screenUnderstandingEnabled: Boolean = true,
    val codingAgentAutoApply: Boolean = true,
    val ollamaUrl: String = "http://localhost:11434"
)

class XSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("x_agent_settings", Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<XSettingsState> = _settingsState.asStateFlow()

    companion object {
        // Natural female voice "Aoede" is the primary default, followed by "Kore"
        val AVAILABLE_VOICES = listOf(
            VoiceOption(
                id = "Aoede",
                displayName = "Aoede (Female)",
                gender = "Female",
                isFemale = true,
                description = "Warm, natural, clear and conversational female voice"
            ),
            VoiceOption(
                id = "Kore",
                displayName = "Kore (Female)",
                gender = "Female",
                isFemale = true,
                description = "Gentle, calm and articulate female voice"
            ),
            VoiceOption(
                id = "Puck",
                displayName = "Puck (Male)",
                gender = "Male",
                isFemale = false,
                description = "Energetic, upbeat and friendly tone"
            ),
            VoiceOption(
                id = "Charon",
                displayName = "Charon (Male)",
                gender = "Male",
                isFemale = false,
                description = "Deep, resonant and authoritative tone"
            ),
            VoiceOption(
                id = "Fenrir",
                displayName = "Fenrir (Male)",
                gender = "Male",
                isFemale = false,
                description = "Crisp, dynamic and direct tone"
            )
        )

        val DEFAULT_FEMALE_VOICE = "Aoede"

        val AVAILABLE_LANGUAGES = listOf(
            "Auto Detect (Urdu / Roman Urdu / English / Pashto / Hindi)",
            "Urdu",
            "Roman Urdu",
            "English",
            "Pashto",
            "Hindi"
        )

        val AVAILABLE_PROVIDERS = listOf(
            "Gemini Live (Multimodal)",
            "Gemini 2.5 Flash (REST)",
            "Local Ollama"
        )

        val AVAILABLE_MODELS = listOf(
            "gemini-2.5-flash-native-audio-preview-12-2025",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview"
        )

        val CODING_MODELS = listOf(
            "gemini-3.1-pro-preview",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite-preview"
        )
    }

    private fun loadSettings(): XSettingsState {
        return XSettingsState(
            aiProvider = prefs.getString("ai_provider", "Gemini Live (Multimodal)") ?: "Gemini Live (Multimodal)",
            aiModel = prefs.getString("ai_model", "gemini-2.5-flash-native-audio-preview-12-2025") ?: "gemini-2.5-flash-native-audio-preview-12-2025",
            codingModel = prefs.getString("coding_model", "gemini-3.1-pro-preview") ?: "gemini-3.1-pro-preview",
            visionModel = prefs.getString("vision_model", "gemini-2.5-flash-image") ?: "gemini-2.5-flash-image",
            // Natural female voice default:
            selectedVoice = prefs.getString("selected_voice", DEFAULT_FEMALE_VOICE) ?: DEFAULT_FEMALE_VOICE,
            // Language defaults to Auto Detect:
            selectedLanguage = prefs.getString("selected_language", "Auto Detect (Urdu / Roman Urdu / English / Pashto / Hindi)")
                ?: "Auto Detect (Urdu / Roman Urdu / English / Pashto / Hindi)",
            wakeWordEnabled = prefs.getBoolean("wake_word_enabled", true),
            backgroundListeningEnabled = prefs.getBoolean("background_listening", true),
            screenUnderstandingEnabled = prefs.getBoolean("screen_understanding", true),
            codingAgentAutoApply = prefs.getBoolean("coding_auto_apply", true),
            ollamaUrl = prefs.getString("ollama_url", "http://localhost:11434") ?: "http://localhost:11434"
        )
    }

    fun updateSettings(update: (XSettingsState) -> XSettingsState) {
        val current = _settingsState.value
        val newSettings = update(current)
        _settingsState.value = newSettings

        prefs.edit()
            .putString("ai_provider", newSettings.aiProvider)
            .putString("ai_model", newSettings.aiModel)
            .putString("coding_model", newSettings.codingModel)
            .putString("vision_model", newSettings.visionModel)
            .putString("selected_voice", newSettings.selectedVoice)
            .putString("selected_language", newSettings.selectedLanguage)
            .putBoolean("wake_word_enabled", newSettings.wakeWordEnabled)
            .putBoolean("background_listening", newSettings.backgroundListeningEnabled)
            .putBoolean("screen_understanding", newSettings.screenUnderstandingEnabled)
            .putBoolean("coding_auto_apply", newSettings.codingAgentAutoApply)
            .putString("ollama_url", newSettings.ollamaUrl)
            .apply()
    }
}

