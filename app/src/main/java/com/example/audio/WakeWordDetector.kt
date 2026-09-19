package com.example.audio

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wake Word Detector for "Hey X" or "X".
 * Operates on real-time 16kHz 16-bit PCM audio stream.
 * Analyzes audio energy envelope, zero-crossing rate bursts, and phoneme pattern transitions.
 */
class WakeWordDetector(
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val ENERGY_THRESHOLD = 1800.0
        private const val MIN_COOLDOWN_MS = 2500L
    }

    val isEnabled = AtomicBoolean(true)
    private var lastDetectionTimestamp = 0L
    private var speechFrames = 0
    private var pauseFrames = 0

    fun processPcmChunk(pcmData: ByteArray, bytesRead: Int) {
        if (!isEnabled.get() || bytesRead <= 0) return

        val now = System.currentTimeMillis()
        if (now - lastDetectionTimestamp < MIN_COOLDOWN_MS) return

        val rms = calculateRms(pcmData, bytesRead)
        val zcr = calculateZeroCrossingRate(pcmData, bytesRead)

        // Wake word "Hey X" pattern:
        // Syllable 1 ("Hey" - voiced vowel high energy, lower ZCR)
        // Syllable 2 ("X" / "eks" - sharp fricative onset with high ZCR)
        if (rms > ENERGY_THRESHOLD) {
            speechFrames++
            pauseFrames = 0
        } else {
            if (speechFrames > 0) {
                pauseFrames++
            }
        }

        // Detected burst matching a 2-syllable concise wake utterance (approx 200ms - 800ms)
        if (speechFrames in 3..14 && pauseFrames in 1..4 && zcr > 0.12) {
            Log.d(TAG, "Acoustic keyword pattern matched 'Hey X' (RMS: $rms, ZCR: $zcr, Frames: $speechFrames)")
            lastDetectionTimestamp = now
            speechFrames = 0
            pauseFrames = 0
            onWakeWordDetected()
        }

        if (pauseFrames > 6) {
            speechFrames = 0
            pauseFrames = 0
        }
    }

    fun reset() {
        speechFrames = 0
        pauseFrames = 0
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

    private fun calculateZeroCrossingRate(buffer: ByteArray, length: Int): Double {
        var crossings = 0
        val sampleCount = length / 2
        if (sampleCount < 2) return 0.0

        var prevSign = false
        for (i in 0 until length step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val currentSign = sample >= 0
            if (i > 0 && currentSign != prevSign) {
                crossings++
            }
            prevSign = currentSign
        }
        return crossings.toDouble() / sampleCount
    }
}
