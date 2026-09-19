package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.live.GeminiLiveSession
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun XVoiceScreen(
    sessionState: GeminiLiveSession.SessionState,
    micAmplitude: Float,
    speakerAmplitude: Float,
    isMuted: Boolean,
    activeToolSummary: String?,
    errorMessage: String?,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onOpenCodingAgent: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSessionActive = sessionState != GeminiLiveSession.SessionState.DISCONNECTED
    val isAiSpeaking = sessionState == GeminiLiveSession.SessionState.AI_SPEAKING

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF090D16),
                        Color(0xFF0E1424),
                        Color(0xFF070A10)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: Branding "X" & Navigation Buttons
            TopBarSection(
                sessionState = sessionState,
                onOpenCodingAgent = onOpenCodingAgent,
                onOpenSettings = onOpenSettings
            )

            // Center: Sonic Orb Visualizer & Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                if (!hasMicPermission) {
                    PermissionNoticeCard(onRequestPermission = onRequestMicPermission)
                } else {
                    VoiceVisualizerOrb(
                        sessionState = sessionState,
                        micAmplitude = micAmplitude,
                        speakerAmplitude = speakerAmplitude,
                        isAiSpeaking = isAiSpeaking,
                        onOrbTap = {
                            if (isAiSpeaking) {
                                onInterrupt()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                StatusDescriptionBadge(
                    sessionState = sessionState,
                    isMuted = isMuted,
                    errorMessage = errorMessage
                )

                // Tool Execution Banner
                AnimatedVisibility(
                    visible = activeToolSummary != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    activeToolSummary?.let { summary ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x3300E5FF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .testTag("active_tool_card")
                        ) {
                            Text(
                                text = "⚡ Action: $summary",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Control Dock
            BottomControlsSection(
                sessionState = sessionState,
                isSessionActive = isSessionActive,
                isMuted = isMuted,
                hasMicPermission = hasMicPermission,
                onStartSession = onStartSession,
                onStopSession = onStopSession,
                onToggleMute = onToggleMute,
                onInterrupt = onInterrupt
            )
        }
    }
}

@Composable
fun TopBarSection(
    sessionState: GeminiLiveSession.SessionState,
    onOpenCodingAgent: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onOpenCodingAgent,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E293B), CircleShape)
                    .testTag("nav_coding_agent")
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "AI Coding Agent",
                    tint = Color(0xFF38BDF8)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "X",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (sessionState != GeminiLiveSession.SessionState.DISCONNECTED) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (sessionState != GeminiLiveSession.SessionState.DISCONNECTED) Color(0xFF00E5FF).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = "LIVE VOICE",
                        color = if (sessionState != GeminiLiveSession.SessionState.DISCONNECTED) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        letterSpacing = 1.sp
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E293B), CircleShape)
                    .testTag("nav_settings")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Urdu • Roman Urdu • English • Pashto • Hindi",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun VoiceVisualizerOrb(
    sessionState: GeminiLiveSession.SessionState,
    micAmplitude: Float,
    speakerAmplitude: Float,
    isAiSpeaking: Boolean,
    onOrbTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val activeAmp = if (isAiSpeaking) speakerAmplitude else micAmplitude
    val dynamicOrbScale = (1.0f + activeAmp * 0.45f) * (if (sessionState != GeminiLiveSession.SessionState.DISCONNECTED) pulseScale else 1f)

    val orbColor by animateColorAsState(
        targetValue = when (sessionState) {
            GeminiLiveSession.SessionState.AI_SPEAKING -> Color(0xFF9D4EDD)
            GeminiLiveSession.SessionState.CONNECTED_LISTENING -> Color(0xFF00E5FF)
            GeminiLiveSession.SessionState.THINKING -> Color(0xFFFFB703)
            GeminiLiveSession.SessionState.INTERRUPTED -> Color(0xFFFF5400)
            GeminiLiveSession.SessionState.CONNECTING -> Color(0xFF48CAE4)
            GeminiLiveSession.SessionState.DISCONNECTED -> Color(0xFF4A5568)
        },
        label = "orb_color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOrbTap
            )
            .testTag("visualizer_orb")
    ) {
        // Dynamic Canvas Ripple Waves
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.45f

            // Outer acoustic energy wave
            if (sessionState != GeminiLiveSession.SessionState.DISCONNECTED) {
                for (i in 1..3) {
                    val waveRadius = baseRadius + (i * 26f) + (activeAmp * 45f * i)
                    drawCircle(
                        color = orbColor.copy(alpha = (0.28f / i) + (activeAmp * 0.15f)),
                        radius = waveRadius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.5f + (activeAmp * 4f),
                            cap = StrokeCap.Round
                        )
                    )
                }

                // Radiating particles/spokes when speaking
                if (isAiSpeaking) {
                    val spokeCount = 16
                    for (j in 0 until spokeCount) {
                        val angle = (j * (360.0 / spokeCount)) * (Math.PI / 180.0)
                        val length = baseRadius + 15f + (activeAmp * 50f * ((j % 3) + 1))
                        val startX = center.x + (baseRadius * cos(angle)).toFloat()
                        val startY = center.y + (baseRadius * sin(angle)).toFloat()
                        val endX = center.x + (length * cos(angle)).toFloat()
                        val endY = center.y + (length * sin(angle)).toFloat()
                        drawLine(
                            color = orbColor.copy(alpha = 0.5f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Central Glowing Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbColor.copy(alpha = 0.85f),
                        orbColor.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * dynamicOrbScale
                ),
                radius = baseRadius * dynamicOrbScale,
                center = center
            )
        }

        // Inner Iconic "X"
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .scale(dynamicOrbScale.coerceIn(0.9f, 1.3f))
                .clip(CircleShape)
                .background(Color(0xFF0F1524))
                .border(2.dp, orbColor, CircleShape)
        ) {
            Text(
                text = "X",
                color = orbColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
fun StatusDescriptionBadge(
    sessionState: GeminiLiveSession.SessionState,
    isMuted: Boolean,
    errorMessage: String?
) {
    val statusText = when {
        errorMessage != null -> errorMessage
        isMuted -> "Microphone Muted"
        sessionState == GeminiLiveSession.SessionState.AI_SPEAKING -> stringResource(R.string.status_speaking)
        sessionState == GeminiLiveSession.SessionState.CONNECTED_LISTENING -> stringResource(R.string.status_listening)
        sessionState == GeminiLiveSession.SessionState.THINKING -> stringResource(R.string.status_thinking)
        sessionState == GeminiLiveSession.SessionState.INTERRUPTED -> stringResource(R.string.status_interrupted)
        sessionState == GeminiLiveSession.SessionState.CONNECTING -> stringResource(R.string.status_connecting)
        else -> stringResource(R.string.status_ready)
    }

    val statusColor = when {
        errorMessage != null -> Color(0xFFFF5252)
        isMuted -> Color(0xFFFFB703)
        sessionState == GeminiLiveSession.SessionState.AI_SPEAKING -> Color(0xFFC77DFF)
        sessionState == GeminiLiveSession.SessionState.CONNECTED_LISTENING -> Color(0xFF00E5FF)
        sessionState == GeminiLiveSession.SessionState.THINKING -> Color(0xFFFFD166)
        sessionState == GeminiLiveSession.SessionState.INTERRUPTED -> Color(0xFFFF5400)
        else -> Color.White.copy(alpha = 0.7f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        if (sessionState == GeminiLiveSession.SessionState.AI_SPEAKING) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Natural barge-in active • Speak anytime to interrupt",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun PermissionNoticeCard(onRequestPermission: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161E30)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.permission_mic_rationale),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF0A0E17)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_grant),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BottomControlsSection(
    sessionState: GeminiLiveSession.SessionState,
    isSessionActive: Boolean,
    isMuted: Boolean,
    hasMicPermission: Boolean,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute / Unmute Button
        IconButton(
            onClick = onToggleMute,
            enabled = isSessionActive,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (isSessionActive) {
                        if (isMuted) Color(0xFFFFB703).copy(alpha = 0.25f) else Color(0xFF161F33)
                    } else Color.White.copy(alpha = 0.05f)
                )
                .testTag("mute_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isSessionActive) {
                    if (isMuted) Color(0xFFFFB703) else Color.White
                } else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Central Main Action Button (Start / Stop)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (isSessionActive) {
                        Brush.radialGradient(listOf(Color(0xFFFF5252), Color(0xFFD32F2F)))
                    } else {
                        Brush.radialGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B4D8)))
                    }
                )
                .clickable {
                    if (!hasMicPermission) {
                        // Request handled by parent
                    } else if (isSessionActive) {
                        onStopSession()
                    } else {
                        onStartSession()
                    }
                }
                .testTag("primary_session_button")
        ) {
            Icon(
                imageVector = if (isSessionActive) Icons.Default.CallEnd else Icons.Default.Mic,
                contentDescription = if (isSessionActive) "End Session" else "Start Session",
                tint = if (isSessionActive) Color.White else Color(0xFF0A0E17),
                modifier = Modifier.size(34.dp)
            )
        }

        // Barge-in / Stop Speech Button
        IconButton(
            onClick = onInterrupt,
            enabled = sessionState == GeminiLiveSession.SessionState.AI_SPEAKING,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (sessionState == GeminiLiveSession.SessionState.AI_SPEAKING) {
                        Color(0xFF9D4EDD).copy(alpha = 0.25f)
                    } else Color.White.copy(alpha = 0.05f)
                )
                .testTag("interrupt_button")
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Interrupt Speech",
                tint = if (sessionState == GeminiLiveSession.SessionState.AI_SPEAKING) {
                    Color(0xFFC77DFF)
                } else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ================= PREVIEWS =================

@Preview(showBackground = true, name = "X Voice Screen - Ready")
@Composable
fun XVoiceScreenReadyPreview() {
    MyApplicationTheme {
        XVoiceScreen(
            sessionState = GeminiLiveSession.SessionState.DISCONNECTED,
            micAmplitude = 0f,
            speakerAmplitude = 0f,
            isMuted = false,
            activeToolSummary = null,
            errorMessage = null,
            hasMicPermission = true,
            onRequestMicPermission = {},
            onStartSession = {},
            onStopSession = {},
            onToggleMute = {},
            onInterrupt = {}
        )
    }
}

@Preview(showBackground = true, name = "X Voice Screen - Speaking")
@Composable
fun XVoiceScreenSpeakingPreview() {
    MyApplicationTheme {
        XVoiceScreen(
            sessionState = GeminiLiveSession.SessionState.AI_SPEAKING,
            micAmplitude = 0.1f,
            speakerAmplitude = 0.75f,
            isMuted = false,
            activeToolSummary = "Opened Camera",
            errorMessage = null,
            hasMicPermission = true,
            onRequestMicPermission = {},
            onStartSession = {},
            onStopSession = {},
            onToggleMute = {},
            onInterrupt = {}
        )
    }
}
