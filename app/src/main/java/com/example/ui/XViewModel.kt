package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.live.GeminiLiveSession
import com.example.service.XVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class XViewModel : ViewModel() {

    private var voiceService: XVoiceService? = null
    private var isBound = false

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? XVoiceService.LocalBinder
            val service = localBinder?.getService()
            voiceService = service
            isBound = true

            service?.let { svc ->
                viewModelScope.launch {
                    svc.sessionState.collect { _sessionState.value = it }
                }
                viewModelScope.launch {
                    svc.micAmplitude.collect { _micAmplitude.value = it }
                }
                viewModelScope.launch {
                    svc.speakerAmplitude.collect { _speakerAmplitude.value = it }
                }
                viewModelScope.launch {
                    svc.isMuted.collect { _isMuted.value = it }
                }
                viewModelScope.launch {
                    svc.activeToolSummary.collect { _activeToolSummary.value = it }
                }
                viewModelScope.launch {
                    svc.errorMessage.collect { _errorMessage.value = it }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            isBound = false
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, XVoiceService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            voiceService = null
        }
    }

    fun startVoiceSession(context: Context) {
        val intent = Intent(context, XVoiceService::class.java).apply {
            action = XVoiceService.ACTION_START
        }
        context.startForegroundService(intent)
        bindService(context)
        voiceService?.startVoiceSession()
    }

    fun stopVoiceSession(context: Context) {
        voiceService?.stopVoiceSession()
        val intent = Intent(context, XVoiceService::class.java).apply {
            action = XVoiceService.ACTION_STOP
        }
        context.stopService(intent)
        unbindService(context)
        _sessionState.value = GeminiLiveSession.SessionState.DISCONNECTED
    }

    fun toggleMute() {
        voiceService?.toggleMute()
    }

    fun interruptPlayback() {
        voiceService?.interruptPlayback()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
    }
}
