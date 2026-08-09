package com.better.nothing.music.vizualizer.logic

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pro-Grade Beat Detector tailored for 512 Log-Spaced IntArray FFTs (0-4095 range).
 * Uses Smoothed Spectral Flux, O(1) EMA Tracking, and 3-Point Peak Detection.
 */
class BeatDetector(
    var sensitivity: Float = 1.0f,
    var cooldownMs: Long = 130L
) {
    // 1. Changed to IntArray to match your FFT source
    private var prevMagnitude: IntArray? = null

    // O(1) Statistical Tracking
    private var emaMean = 0f
    private var emaVariance = 0f
    private val alpha = 0.02f

    // 3-Point Peak Detection History
    private var flux0 = 0f
    private var flux1 = 0f
    private var flux2 = 0f

    private var lastTriggerMs = 0L
    private var thresholdMask = 0f

    // 2. Signature accepts IntArray
    fun detect(magnitude: IntArray, binLo: Int, binHi: Int): Boolean {
        if (magnitude.isEmpty()) return false

        if (prevMagnitude == null || prevMagnitude?.size != magnitude.size) {
            prevMagnitude = magnitude.copyOf()
            return false
        }
        val prev = prevMagnitude!!

        val start = max(0, minOf(binLo, magnitude.lastIndex))
        val end = max(start, minOf(binHi, magnitude.lastIndex))

        var totalFlux = 0f
        var weightSum = 0f

        for (i in start..end) {
            // 3. Normalize the 0-4095 Int range to a 0.0f - 1.0f Float range
            val currentNorm = magnitude[i] / 4095f
            val prevNorm = prev[i] / 4095f

            val diff = currentNorm - prevNorm
            if (diff > 0f) {
                // 4. Gentler weighting curve because log-spacing already emphasizes the low-end naturally
                val weight = 1.0f / (1.0f + (i - start) * 0.01f)
                totalFlux += diff * weight
                weightSum += weight
            }
        }

        // Native IntArray copy
        System.arraycopy(magnitude, 0, prev, 0, magnitude.size)

        val rawFlux = if (weightSum > 0f) totalFlux / weightSum else 0f

        // Flux Smoothing (Low-Pass Filter)
        val smoothedFlux = (rawFlux * 0.7f) + (flux0 * 0.3f)

        // Shift 3-point time window
        flux2 = flux1
        flux1 = flux0
        flux0 = smoothedFlux

        // O(1) Dynamic Thresholding
        val stdDev = sqrt(emaVariance)
        val multiplier = (1.5f / max(0.1f, sensitivity)).coerceIn(0.5f, 4.0f)
        val dynamicThreshold = max(emaMean + multiplier * stdDev, thresholdMask)

        // Noise gate works perfectly because we normalized to 0.0 - 1.0
        val noiseGate = 0.005f
        val now = SystemClock.elapsedRealtime()

        // True Local Peak Detection
        val isTruePeak = flux1 > flux2 && flux1 > flux0

        val isAboveThreshold = flux1 > dynamicThreshold && flux1 > noiseGate
        val cooldownPassed = (now - lastTriggerMs) >= cooldownMs

        val triggered = isTruePeak && isAboveThreshold && cooldownPassed

        if (triggered) {
            lastTriggerMs = now
            thresholdMask = flux1 * 0.7f
        } else {
            thresholdMask *= 0.85f
        }

        updateStatistics(flux0)

        return triggered
    }

    private fun updateStatistics(flux: Float) {
        val delta = flux - emaMean
        emaMean += alpha * delta
        emaVariance = (1f - alpha) * (emaVariance + alpha * delta * delta)
    }

    fun reset() {
        emaMean = 0f
        emaVariance = 0f
        flux0 = 0f
        flux1 = 0f
        flux2 = 0f
        lastTriggerMs = 0L
        thresholdMask = 0f
        prevMagnitude = null
    }
}