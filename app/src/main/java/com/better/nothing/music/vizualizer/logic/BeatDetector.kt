package com.better.nothing.music.vizualizer.logic

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pro-Grade Beat Detector using Smoothed Spectral Flux,
 * Exponential Moving Averages (O(1) CPU), and 3-Point Peak Detection.
 */
class BeatDetector(
    var sensitivity: Float = 1.0f,
    var cooldownMs: Long = 130L
) {
    private var prevMagnitude: FloatArray? = null

    // O(1) Statistical Tracking (Exponential Moving Averages)
    private var emaMean = 0f
    private var emaVariance = 0f
    // Alpha dictates memory length. 0.02f roughly equals a 50-frame history.
    private val alpha = 0.02f

    // 3-Point Peak Detection History
    private var flux0 = 0f // Current frame (t)
    private var flux1 = 0f // Previous frame (t-1)
    private var flux2 = 0f // Frame before previous (t-2)

    private var lastTriggerMs = 0L
    private var thresholdMask = 0f

    fun detect(magnitude: FloatArray, binLo: Int, binHi: Int): Boolean {
        if (magnitude.isEmpty()) return false

        if (prevMagnitude == null || prevMagnitude?.size != magnitude.size) {
            prevMagnitude = magnitude.copyOf()
            return false
        }
        val prev = prevMagnitude!!

        val start = max(0, minOf(binLo, magnitude.lastIndex))
        val end = max(start, minOf(binHi, magnitude.lastIndex))

        // 1. Calculate Weighted Spectral Flux
        var totalFlux = 0f
        var weightSum = 0f

        for (i in start..end) {
            val diff = magnitude[i] - prev[i]
            if (diff > 0f) {
                // Emphasize bass transients (1.0 weight for sub-bass, decaying for treble)
                val weight = 1.0f / (1.0f + (i - start) * 0.05f)
                totalFlux += diff * weight
                weightSum += weight
            }
        }

        System.arraycopy(magnitude, 0, prev, 0, magnitude.size)

        val rawFlux = if (weightSum > 0f) totalFlux / weightSum else 0f

        // 2. Flux Smoothing (Low-Pass Filter)
        // Blends 70% of new data with 30% of old data to remove micro-jitter
        val smoothedFlux = (rawFlux * 0.7f) + (flux0 * 0.3f)

        // Shift 3-point time window
        flux2 = flux1
        flux1 = flux0
        flux0 = smoothedFlux

        // 3. O(1) Dynamic Thresholding via EMA (T = μ + k*σ)
        val stdDev = sqrt(emaVariance)
        val multiplier = (1.5f / max(0.1f, sensitivity)).coerceIn(0.5f, 4.0f)
        val dynamicThreshold = max(emaMean + multiplier * stdDev, thresholdMask)

        val noiseGate = 0.005f
        val now = SystemClock.elapsedRealtime()

        // 4. True Local Peak Detection
        // A peak occurred at flux1 (t-1) IF it is higher than both flux2 (t-2) and flux0 (t)
        val isTruePeak = flux1 > flux2 && flux1 > flux0

        // We evaluate the threshold against flux1 (the peak), not flux0
        val isAboveThreshold = flux1 > dynamicThreshold && flux1 > noiseGate
        val cooldownPassed = (now - lastTriggerMs) >= cooldownMs

        val triggered = isTruePeak && isAboveThreshold && cooldownPassed

        if (triggered) {
            lastTriggerMs = now
            thresholdMask = flux1 * 0.7f
        } else {
            thresholdMask *= 0.85f
        }

        // 5. Update Statistics AFTER evaluation
        // This prevents the current massive transient from sabotaging its own trigger probability
        updateStatistics(flux0)

        return triggered
    }

    private fun updateStatistics(flux: Float) {
        // Exponential Moving Average for Mean
        val delta = flux - emaMean
        emaMean += alpha * delta

        // Exponential Moving Average for Variance
        // Calculated using the difference between the flux and the updated mean
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