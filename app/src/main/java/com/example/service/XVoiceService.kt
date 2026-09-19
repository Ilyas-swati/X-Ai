package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioRecordManager
import com.example.audio.AudioTrackPlayer
import com.example.live.GeminiLiveSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class XVoiceService : Service() {

    companion object {
        private const val TAG = "XVoiceService"
        const val CHANNEL_ID = "x_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_TOGGLE_MUTE = "com.example.service.ACTION_TOGGLE_MUTE"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    private var audioRecordManager: AudioRecordManager? = null
    private var audioTrackPlayer: AudioTrackPlayer? = null
    private var liveSession: GeminiLiveSession? = null
    private var wakeWordDetector: com.example.audio.WakeWordDetector? = null

    private val _sessionState = MutableStateFlow(GeminiLiveSession.SessionState.DISCONNECTED)
    val sessionState: StateFlow<GeminiLiveSession.SessionState> = _sessionState.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _speakerAmplitude = MutableStateFlow(0f)
    val speakerAmplitude: StateFlow<Float> = _speakerAmplitude.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _activeToolSummary = MutableStateFlow<String?>(null)
    val activeToolSummary: StateFlow<String?> = _activeToolSummary.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): XVoiceService = this@XVoiceService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()

        val settings = com.example.settings.XSettingsManager(applicationContext).settingsState.value
        wakeWordDetector = com.example.audio.WakeWordDetector {
            Log.d(TAG, "Wake word 'Hey X' triggered!")
            if (_sessionState.value == GeminiLiveSession.SessionState.DISCONNECTED) {
                startVoiceSession()
            } else if (_sessionState.value == GeminiLiveSession.SessionState.AI_SPEAKING) {
                interruptPlayback()
            }
        }.apply {
            isEnabled.set(settings.wakeWordEnabled)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVoiceSession()
                stopSelf()
            }
            ACTION_TOGGLE_MUTE -> {
                toggleMute()
            }
            ACTION_START -> {
                startForegroundServiceCompat()
                startVoiceSession()
            }
            else -> {
                startForegroundServiceCompat()
                startVoiceSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceCompat() {
        val notification = buildNotification("X is ready and listening...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, fgType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun startVoiceSession() {
        if (_sessionState.value != GeminiLiveSession.SessionState.DISCONNECTED) return

        _errorMessage.value = null

        // 1. Initialize Player
        audioTrackPlayer = AudioTrackPlayer(
            onPlaybackStateChanged = { isPlaying ->
                audioRecordManager?.isAiSpeaking?.set(isPlaying)
                if (isPlaying) {
                    _sessionState.value = GeminiLiveSession.SessionState.AI_SPEAKING
                    updateNotification("X is speaking…")
                } else if (_sessionState.value == GeminiLiveSession.SessionState.AI_SPEAKING) {
                    _sessionState.value = GeminiLiveSession.SessionState.CONNECTED_LISTENING
                    updateNotification("X is listening…")
                }
            },
            onSpeakerAmplitudeChanged = { amp ->
                _speakerAmplitude.value = amp
            }
        ).apply {
            start(serviceScope)
        }

        // 2. Initialize Live Session
        liveSession = GeminiLiveSession(
            context = applicationContext,
            scope = serviceScope,
            onAudioReceived = { pcmBytes ->
                audioTrackPlayer?.playChunk(pcmBytes)
            },
            onStateChanged = { state ->
                _sessionState.value = state
                val notifText = when (state) {
                    GeminiLiveSession.SessionState.CONNECTING -> "Connecting to live voice session…"
                    GeminiLiveSession.SessionState.CONNECTED_LISTENING -> "Listening to your voice…"
                    GeminiLiveSession.SessionState.THINKING -> "Processing request…"
                    GeminiLiveSession.SessionState.AI_SPEAKING -> "X is speaking…"
                    GeminiLiveSession.SessionState.INTERRUPTED -> "Interrupted — listening to you…"
                    GeminiLiveSession.SessionState.DISCONNECTED -> "Voice session disconnected."
                }
                updateNotification(notifText)
            },
            onToolExecuted = { toolName, summary ->
                _activeToolSummary.value = "$toolName: $summary"
            },
            onError = { err ->
                _errorMessage.value = err
                updateNotification("Status: $err")
            }
        ).apply {
            connect()
        }

        // 3. Initialize Audio Recorder
        audioRecordManager = AudioRecordManager(
            onAudioChunk = { pcmChunk ->
                wakeWordDetector?.processPcmChunk(pcmChunk, pcmChunk.size)
                liveSession?.sendAudioChunk(pcmChunk)
            },
            onAmplitudeChanged = { amp ->
                _micAmplitude.value = amp
            },
            onBargeInDetected = {
                // Natural Barge-In / Interruption triggered!
                Log.d(TAG, "Natural Barge-In: user speaking detected. Halting X audio immediately.")
                audioTrackPlayer?.interrupt()
                liveSession?.sendInterruptionSignal()
                _sessionState.value = GeminiLiveSession.SessionState.INTERRUPTED
                updateNotification("Interrupted — listening…")
            }
        ).apply {
            start(serviceScope)
        }
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        audioRecordManager?.isMuted?.set(newMute)
        updateNotification(if (newMute) "Microphone muted" else "X is listening…")
    }

    fun interruptPlayback() {
        audioTrackPlayer?.interrupt()
        liveSession?.sendInterruptionSignal()
    }

    fun stopVoiceSession() {
        audioRecordManager?.stop()
        audioRecordManager = null

        audioTrackPlayer?.release()
        audioTrackPlayer = null

        liveSession?.disconnect()
        liveSession = null

        _sessionState.value = GeminiLiveSession.SessionState.DISCONNECTED
        _micAmplitude.value = 0f
        _speakerAmplitude.value = 0f
        _activeToolSummary.value = null
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XVoiceAgent:WakeLock").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // Safe limit
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock: ${e.message}")
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bg_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.bg_service_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, XVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, XVoiceService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this,
            2,
            muteIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val muteActionTitle = if (_isMuted.value) getString(R.string.action_unmute) else getString(R.string.action_mute)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bg_notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(0, muteActionTitle, mutePendingIntent)
            .addAction(0, getString(R.string.action_stop), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        stopVoiceSession()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
