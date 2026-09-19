package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.settings.AiConnectionTester
import com.example.settings.ConnectionTestResult
import com.example.settings.SecureStorageManager
import com.example.settings.XSettingsManager
import com.example.tools.AgentToolRegistry
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val settingsManager = remember { XSettingsManager(context) }
    val secureStorage = remember { SecureStorageManager(context) }
    val settingsState by settingsManager.settingsState.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isEditingApiKey by remember { mutableStateOf(!secureStorage.hasValidApiKey()) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<ConnectionTestResult?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1E293B), CircleShape)
                    .testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "X Settings & Persona",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Voice, API credentials, models & multilingual",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Credentials & Security Card
            item {
                SettingsSectionCard(
                    title = "AI Credentials & Secure Storage",
                    icon = Icons.Default.Key
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Gemini API Key",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (secureStorage.hasValidApiKey()) "Masked: ${secureStorage.getMaskedApiKey()}" else "No key configured",
                                color = if (secureStorage.hasValidApiKey()) Color(0xFF10B981) else Color(0xFFFF5252),
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                isEditingApiKey = !isEditingApiKey
                                if (isEditingApiKey && apiKeyInput.isEmpty()) {
                                    apiKeyInput = secureStorage.getApiKey()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("toggle_edit_api_key")
                        ) {
                            Text(if (isEditingApiKey) "Cancel" else "Change Key", fontSize = 12.sp)
                        }
                    }

                    if (isEditingApiKey) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                connectionTestResult = null
                            },
                            placeholder = { Text("Enter new Gemini API key...", color = Color(0xFF64748B), fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_api_key_field"),
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (apiKeyInput.isNotBlank()) {
                                        isTestingConnection = true
                                        scope.launch {
                                            connectionTestResult = AiConnectionTester.testConnection(
                                                context = context,
                                                provider = settingsState.aiProvider,
                                                apiKey = apiKeyInput,
                                                model = settingsState.aiModel
                                            )
                                            isTestingConnection = false
                                        }
                                    }
                                },
                                enabled = !isTestingConnection && apiKeyInput.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                                } else {
                                    Text("Test Key", fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    val cleanKey = apiKeyInput.trim()
                                    if (cleanKey.isNotBlank()) {
                                        secureStorage.saveApiKey(cleanKey)
                                        isEditingApiKey = false
                                        apiKeyInput = ""
                                    }
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF080D1A)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    connectionTestResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val isSuccess = result is ConnectionTestResult.Success
                        val msg = when (result) {
                            is ConnectionTestResult.Success -> result.message
                            is ConnectionTestResult.Failure -> result.error
                        }
                        Text(
                            text = msg,
                            color = if (isSuccess) Color(0xFF34D399) else Color(0xFFFB7185),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Voice Persona & Model Section
            item {
                SettingsSectionCard(
                    title = "AI Voice Persona & Natural Tone",
                    icon = Icons.Default.RecordVoiceOver
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Voice Selection",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Default: Natural Female Voice",
                                color = Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        XSettingsManager.AVAILABLE_VOICES.forEach { voice ->
                            val isSelected = settingsState.selectedVoice == voice.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    settingsManager.updateSettings { it.copy(selectedVoice = voice.id) }
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(voice.displayName, fontSize = 12.sp, fontWeight = if (voice.isFemale) FontWeight.Bold else FontWeight.Normal)
                                        if (voice.isFemale) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("✦", color = Color(0xFFF43F5E), fontSize = 11.sp)
                                        }
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (voice.isFemale) Color(0xFFE11D48) else Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("settings_voice_${voice.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Voice & Live Audio Model",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        XSettingsManager.AVAILABLE_MODELS.forEach { model ->
                            val isSelected = settingsState.aiModel == model
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    settingsManager.updateSettings { it.copy(aiModel = model) }
                                },
                                label = { Text(model, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Autonomous Coding Model",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        XSettingsManager.CODING_MODELS.forEach { model ->
                            val isSelected = settingsState.codingModel == model
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    settingsManager.updateSettings { it.copy(codingModel = model) }
                                },
                                label = { Text(model, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "AI Service Provider",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        XSettingsManager.AVAILABLE_PROVIDERS.forEach { provider ->
                            val isSelected = settingsState.aiProvider == provider
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    settingsManager.updateSettings { it.copy(aiProvider = provider) }
                                },
                                label = { Text(provider, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    if (settingsState.aiProvider.contains("Ollama")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settingsState.ollamaUrl,
                            onValueChange = { url ->
                                settingsManager.updateSettings { it.copy(ollamaUrl = url) }
                            },
                            label = { Text("Ollama Endpoint URL") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }

            // Language & Multilingual
            item {
                SettingsSectionCard(
                    title = "Automatic Language Detection & Multilingual",
                    icon = Icons.Default.Translate
                ) {
                    Text(
                        text = "X automatically detects what language you are speaking or typing and responds back in the same language without manual switching.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        XSettingsManager.AVAILABLE_LANGUAGES.forEach { lang ->
                            val isSelected = settingsState.selectedLanguage == lang
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    settingsManager.updateSettings { it.copy(selectedLanguage = lang) }
                                },
                                label = { Text(lang, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("settings_lang_${lang.take(4)}")
                            )
                        }
                    }
                }
            }

            // Listening Triggers & Background
            item {
                SettingsSectionCard(
                    title = "Wake Triggers & Background Operation",
                    icon = Icons.Default.Settings
                ) {
                    SettingToggleRow(
                        title = "Wake Word Detection (\"Hey X\")",
                        subtitle = "Continuously listens for wake acoustic pattern to trigger assistant automatically",
                        checked = settingsState.wakeWordEnabled,
                        onCheckedChange = { isChecked ->
                            settingsManager.updateSettings { it.copy(wakeWordEnabled = isChecked) }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Continuous Background Listening",
                        subtitle = "Keep voice-to-voice active when app is minimized or screen is locked",
                        checked = settingsState.backgroundListeningEnabled,
                        onCheckedChange = { isChecked ->
                            settingsManager.updateSettings { it.copy(backgroundListeningEnabled = isChecked) }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Screen Understanding & Perception",
                        subtitle = "Allow X to perceive screen contents, UI buttons, and text fields",
                        checked = settingsState.screenUnderstandingEnabled,
                        onCheckedChange = { isChecked ->
                            settingsManager.updateSettings { it.copy(screenUnderstandingEnabled = isChecked) }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Auto-Apply Coding Edits",
                        subtitle = "Immediately save and test code modifications suggested by AI Coding Agent",
                        checked = settingsState.codingAgentAutoApply,
                        onCheckedChange = { isChecked ->
                            settingsManager.updateSettings { it.copy(codingAgentAutoApply = isChecked) }
                        }
                    )
                }
            }

            // Agent Tools Registry Status
            item {
                SettingsSectionCard(
                    title = "Active AI Agent Tools",
                    icon = Icons.Default.SmartToy
                ) {
                    val allTools = remember { AgentToolRegistry.getAllTools() }
                    Text(
                        text = "${allTools.size} modular tools ready for autonomous execution:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allTools.forEach { tool ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = tool.description,
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Permissions Status
            item {
                SettingsSectionCard(
                    title = "System Security & Permissions",
                    icon = Icons.Default.Security
                ) {
                    Text(
                        text = "X strictly requests only runtime permissions required for voice streaming, foreground background service, and device tools. All API credentials are encrypted with AES-256 GCM on-device.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Text(
                text = subtitle,
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    Surface {
        SettingsScreen()
    }
}
