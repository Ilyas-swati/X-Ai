package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioRecordManager(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onBargeInDetected: () -> Unit
) {
    companion object {
        private const val TAG = "AudioRecordManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BARGE_IN_THRESHOLD = 1400.0 // RMS threshold for user speaking while AI is talking
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    val isMuted = AtomicBoolean(false)
    val isAiSpeaking = AtomicBoolean(false)

    private var consecutiveSpeechFrames = 0

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isRecording.get()) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(3200)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            val sessionId = record.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not attach AcousticEchoCanceler: ${e.message}")
                }
            }

            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not attach NoiseSuppressor: ${e.message}")
                }
            }

            record.startRecording()
            audioRecord = record
            isRecording.set(true)

            recordingJob = scope.launch(Dispatchers.IO) {
                val readBuffer = ByteArray(3200) // 100ms at 16kHz 16-bit mono
                while (isActive && isRecording.get()) {
                    val bytesRead = record.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        if (isMuted.get()) {
                            onAmplitudeChanged(0f)
                            continue
                        }

                        val rms = calculateRms(readBuffer, bytesRead)
                        val normalizedAmp = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
                        onAmplitudeChanged(normalizedAmp)

                        // Check Barge-in / natural interruption:
                        // If AI is currently speaking and user speaks firmly
                        if (isAiSpeaking.get()) {
                            if (rms > BARGE_IN_THRESHOLD) {
                                consecutiveSpeechFrames++
                                if (consecutiveSpeechFrames >= 2) {
                                    Log.d(TAG, "Barge-in triggered: user spoke with RMS $rms")
                                    onBargeInDetected()
                                    consecutiveSpeechFrames = 0
                                }
                            } else {
                                consecutiveSpeechFrames = 0
                            }
                        } else {
                            consecutiveSpeechFrames = 0
                        }

                        // Copy exact buffer slice to emit
                        val chunk = readBuffer.copyOf(bytesRead)
                        onAudioChunk(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            stop()
        }
    }

    fun stop() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        onAmplitudeChanged(0f)
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0.0

        for (i in 0 until length step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        return sqrt(sum / sampleCount)
    }
}
