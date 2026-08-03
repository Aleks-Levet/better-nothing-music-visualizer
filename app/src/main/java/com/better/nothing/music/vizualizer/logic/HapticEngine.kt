package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.os.*
import android.util.Log
import kotlin.math.pow

class BeatDetectionHapticEngine(context: Context) {

    private val TAG = "BeatDetectionHaptic"

    private val vibrator: Vibrator?
    private val vibratorManager: VibratorManager?

    private var waveform: VibrationEffect? = null
    private var hapticMultiplier = 1.0f
    private var hapticGamma = 8.0f // Default "speed"
    
    private val beatDetector = BeatDetector()

    init {
        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager =
                appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            vibratorManager = null
            vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        waveform = buildWaveform()
    }

    fun performHapticFeedback(
        fftraw: IntArray,
        range: AudioProcessor.FrequencyRange?
    ) {
        if (
            vibrator == null ||
            !vibrator.hasVibrator() ||
            waveform == null ||
            range == null ||
            fftraw.isEmpty()
        ) {
            return
        }

        // Convert to 0-1 range for the beat detector
        val magnitude = FloatArray(fftraw.size)
        for (i in fftraw.indices) magnitude[i] = fftraw[i] / 4095f

        if (beatDetector.detect(magnitude, range.logBinLo, range.logBinHi)) {
            triggerWaveform()
        }
    }

    private fun triggerWaveform() {
        try {
            vibrate(waveform!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed vibration", e)
        }
    }

    private fun buildWaveform(): VibrationEffect {
        val sustainMs = 40
        val decayMs = 1500
        val stepMs = 10

        val count = (sustainMs + decayMs) / stepMs
        val timings = LongArray(count) { stepMs.toLong() }
        val amplitudes = IntArray(count)

        if (vibrator != null && !vibrator.hasAmplitudeControl()) {
            Log.w(TAG, "Device does not support amplitude control. Waveform will be binary.")
        }

        for (i in 0 until count) {
            val t = i * stepMs
            val amp = if (t < sustainMs) {
                255f
            } else {
                val x = 1f - ((t - sustainMs).toFloat() / decayMs.toFloat())
                255f * x.coerceIn(0f, 1f).pow(hapticGamma)
            }
            amplitudes[i] = (amp * hapticMultiplier).toInt().coerceIn(0, 255)
        }

        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun cancelVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibratorManager?.cancel()
            } else {
                vibrator?.cancel()
            }
        } catch (_: Exception) {
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        if (vibrator != null) {
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager?.vibrate(
                CombinedVibration.createParallel(effect)
            )
        }
    }

    fun stopHaptics() {
        cancelVibration()
    }

    fun resetDetectionState() {
        beatDetector.reset()
    }

    fun setHapticMultiplier(multiplier: Float) {
        if (hapticMultiplier != multiplier) {
            hapticMultiplier = multiplier
            waveform = buildWaveform()
        }
    }

    fun setHapticGamma(gamma: Float) {
        if (hapticGamma != gamma) {
            hapticGamma = gamma.coerceIn(4f, 15f)
            waveform = buildWaveform()
        }
    }

    fun setHapticSensitivity(sensitivity: Float) {
        beatDetector.sensitivity = sensitivity
    }
}
