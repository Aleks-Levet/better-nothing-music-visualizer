package com.better.nothing.music.vizualizer.logic

import android.os.SystemClock
import kotlin.math.max
import kotlin.math.sqrt

class BeatDetector(
    var sensitivity: Float = 1.0f,
    var cooldownMs: Long = 130L,
    var noiseGate: Float = 0.015f
) {
    private var prevMagnitude: IntArray? = null

    private var emaMean = 0f
    private var emaVariance = 0f
    private val alpha = 0.02f

    private var flux0 = 0f
    private var flux1 = 0f
    private var flux2 = 0f

    private var lastTriggerMs = 0L
    private var thresholdMask = 0f

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
            val currentNorm = magnitude[i] / 4095f
            val prevNorm = prev[i] / 4095f

            val diff = currentNorm - prevNorm
            if (diff > 0f) {
                // SQUARING the difference transforms amplitude into energy.
                // This exaggerates sharp transients and suppresses minor noise.
                val energy = diff * diff

                val weight = 1.0f / (1.0f + (i - start) * 0.01f)
                totalFlux += energy * weight
                weightSum += weight
            }
        }

        System.arraycopy(magnitude, 0, prev, 0, magnitude.size)

        val rawFlux = if (weightSum > 0f) totalFlux / weightSum else 0f

        // Flux Smoothing
        val smoothedFlux = (rawFlux * 0.7f) + (flux0 * 0.3f)

        flux2 = flux1
        flux1 = flux0
        flux0 = smoothedFlux

        // Variance Floor: Prevents the threshold from dropping to zero during quiet sections
        val stdDev = max(0.005f, sqrt(emaVariance))

        val multiplier = (1.5f / max(0.1f, sensitivity)).coerceIn(0.5f, 4.0f)
        val dynamicThreshold = max(emaMean + multiplier * stdDev, thresholdMask)

        val now = SystemClock.elapsedRealtime()

        val isTruePeak = flux1 > flux2 && flux1 > flux0
        val isAboveThreshold = flux1 > dynamicThreshold && flux1 > noiseGate
        val cooldownPassed = (now - lastTriggerMs) >= cooldownMs

        val triggered = isTruePeak && isAboveThreshold && cooldownPassed

        if (triggered) {
            lastTriggerMs = now
            thresholdMask = flux1 * 0.7f
        } else {
            // Slower decay. 0.95f ensures the mask acts as a true envelope follower
            // rather than dropping instantly after a beat.
            thresholdMask *= 0.95f
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