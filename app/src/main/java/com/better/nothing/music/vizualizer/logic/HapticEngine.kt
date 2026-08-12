package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.os.*
import android.util.Log
import com.better.nothing.music.vizualizer.model.BeatEngineMode
import kotlin.math.pow

class BeatDetectionHapticEngine(context: Context) {

    private val TAG = "BeatDetectionHaptic"

    private val vibrator: Vibrator?
    private val vibratorManager: VibratorManager?

    private var waveform: VibrationEffect? = null
    private var pulseEffect: VibrationEffect? = null
    
    private var hapticMultiplier = 1.0f
    private var hapticGamma = 8.0f // Default "speed"
    private var engineMode = BeatEngineMode.SMOOTH
    private var pulseDurationMs = 40
    
    private val beatDetector = BeatDetector()
    private var lastTriggerTime = 0L
    private var isBeatTriggeredThisFrame = false

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

        // Default mode if no amplitude control is Short Pulse
        if (vibrator != null && !vibrator.hasAmplitudeControl()) {
            engineMode = BeatEngineMode.SHORT_PULSE
        }

        waveform = buildWaveform()
        pulseEffect = buildPulseEffect()
    }

    fun hasAmplitudeControl(): Boolean = vibrator?.hasAmplitudeControl() == true

    fun performHapticFeedback(
        fftraw: IntArray,
        range: AudioProcessor.FrequencyRange?
    ) {
        isBeatTriggeredThisFrame = false
        if (
            vibrator == null ||
            !vibrator.hasVibrator() ||
            range == null ||
            fftraw.isEmpty()
        ) {
            return
        }

        if (beatDetector.detect(fftraw, range.logBinLo, range.logBinHi)) {
            triggerHaptic()
            isBeatTriggeredThisFrame = true
        }
    }

    fun isBeatTriggeredThisFrame(): Boolean = isBeatTriggeredThisFrame

    private fun triggerHaptic() {
        lastTriggerTime = SystemClock.elapsedRealtime()
        try {
            val effect = if (engineMode == BeatEngineMode.SHORT_PULSE || !hasAmplitudeControl()) {
                pulseEffect ?: buildPulseEffect()
            } else {
                waveform ?: buildWaveform()
            }
            vibrate(effect)
        } catch (e: Exception) {
            Log.w(TAG, "Failed vibration", e)
        }
    }

    private fun buildPulseEffect(): VibrationEffect {
        return VibrationEffect.createOneShot(pulseDurationMs.toLong(), (255 * hapticMultiplier).toInt().coerceIn(1, 255))
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
            pulseEffect = buildPulseEffect()
        }
    }

    fun setHapticGamma(gamma: Float) {
        if (hapticGamma != gamma) {
            hapticGamma = gamma.coerceIn(4f, 15f)
            waveform = buildWaveform()
        }
    }

    fun setBeatEngineMode(mode: BeatEngineMode) {
        this.engineMode = mode
    }

    fun setPulseDurationMs(duration: Int) {
        if (pulseDurationMs != duration) {
            pulseDurationMs = duration.coerceIn(5, 200)
            pulseEffect = buildPulseEffect()
        }
    }

    fun setHapticSensitivity(sensitivity: Float) {
        beatDetector.sensitivity = sensitivity
    }

    fun getCurrentIntensity(): Float {
        if (lastTriggerTime == 0L) return 0f
        val elapsed = SystemClock.elapsedRealtime() - lastTriggerTime
        
        return if (engineMode == BeatEngineMode.SHORT_PULSE || !hasAmplitudeControl()) {
            if (elapsed < pulseDurationMs) hapticMultiplier else 0f
        } else {
            val sustainMs = 40
            val decayMs = 1500
            val t = elapsed.toInt()
            
            val amp = if (t < sustainMs) {
                1.0f
            } else if (t < sustainMs + decayMs) {
                val x = 1f - ((t - sustainMs).toFloat() / decayMs.toFloat())
                x.coerceIn(0f, 1f).pow(hapticGamma)
            } else {
                0f
            }
            amp * hapticMultiplier
        }
    }
}
