package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioTrackPlayer(
    private val onPlaybackStateChanged: (Boolean) -> Unit,
    private val onSpeakerAmplitudeChanged: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioTrackPlayer"
        const val SAMPLE_RATE_24K = 24000
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioChannel = Channel<ByteArray>(capacity = 100)
    private val isRunning = AtomicBoolean(false)
    val isPlaying = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (isRunning.get()) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_24K,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4800)

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE_24K)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
            isRunning.set(true)

            playbackJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRunning.get()) {
                    val pcmData = audioChannel.receiveCatching().getOrNull()
                    if (pcmData != null && pcmData.isNotEmpty()) {
                        if (!isPlaying.get()) {
                            isPlaying.set(true)
                            onPlaybackStateChanged(true)
                        }

                        val rms = calculateRms(pcmData)
                        val normAmp = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
                        onSpeakerAmplitudeChanged(normAmp)

                        audioTrack?.write(pcmData, 0, pcmData.size)
                    } else {
                        if (isPlaying.get()) {
                            isPlaying.set(false)
                            onPlaybackStateChanged(false)
                            onSpeakerAmplitudeChanged(0f)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    fun playChunk(pcmData: ByteArray) {
        if (!isRunning.get()) return
        audioChannel.trySend(pcmData)
    }

    /**
     * Instantly stops playback and flushes queued audio for barge-in / natural interruption.
     */
    fun interrupt() {
        // Drain pending audio chunks from the channel
        while (audioChannel.tryReceive().isSuccess) {
            // Drop queued chunks
        }

        try {
            audioTrack?.apply {
                pause()
                flush()
                play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
        }

        isPlaying.set(false)
        onPlaybackStateChanged(false)
        onSpeakerAmplitudeChanged(0f)
    }

    fun release() {
        isRunning.set(false)
        playbackJob?.cancel()
        playbackJob = null

        interrupt()

        try {
            audioTrack?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    private fun calculateRms(buffer: ByteArray): Double {
        var sum = 0.0
        val sampleCount = buffer.size / 2
        if (sampleCount == 0) return 0.0

        for (i in buffer.indices step 2) {
            if (i + 1 < buffer.size) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                val sampleShort = sample.toShort()
                sum += sampleShort * sampleShort
            }
        }
        return sqrt(sum / sampleCount)
    }
}
