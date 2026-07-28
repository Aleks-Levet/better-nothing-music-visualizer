package com.better.nothing.music.vizualizer.logic;

import android.util.Log;
import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Handles audio capture, FFT processing, and frequency analysis.
 */
public class AudioProcessor {

    private int sampleRate = 44100;
    private static final float SPECTRUM_LEAKAGE_FLOOR_RATIO = 0.12f;
    private static final float EPSILON = 0.000001f;

    private int fftSize;
    private int analysisWindow;
    private float hzPerBin;

    private float[] ring;
    private int ringPosition = 0;
    private int filled = 0;

    private double[] fftData;
    private float[] magnitude;
    private float[] hann;
    private DoubleFFT_1D fft;
    private FrequencyRange mUiRange;

    // Improved Autogain state
    private float mRunningMax = 0.01f;
    private float mTargetPeak = 0.45f;
    private float mAutoGain = 1.0f;

    private static final float DECAY_SLOW = 0.998f;
    private static final float GAIN_SMOOTHING_ATTACK = 0.15f;
    private static final float GAIN_SMOOTHING_DECAY = 0.02f;

    public AudioProcessor() {
        updateFFTSize(); // Default
    }

    public void updateFFTSize() {
        updateFFTSize(44100);
    }

    public void updateFFTSize(int sampleRate) {
        // Reduced from 4096 to 2048 to improve temporal responsiveness and reduce window latency.
        int newFftSize = 2048; 

        if (this.fftSize == newFftSize && this.fft != null && this.sampleRate == sampleRate) {
            return;
        }

        this.sampleRate = sampleRate;
        this.fftSize = newFftSize;
        this.analysisWindow = fftSize;
        this.hzPerBin = (float) sampleRate / (float) fftSize;

        this.fft = new DoubleFFT_1D(fftSize);
        this.fftData = new double[fftSize * 2];
        this.magnitude = new float[fftSize / 2 + 1];
        this.hann = buildHannWindow(fftSize);

        this.ring = new float[analysisWindow];
        this.ringPosition = 0;
        this.filled = 0;
        
        this.mUiRange = new FrequencyRange(70f, 130f, hzPerBin, fftSize);
    }

    public float getHzPerBin() {
        return hzPerBin;
    }

    public int getFFTSize() {
        return fftSize;
    }


    public AudioFrameResult processAudioFrame(short[] hopBuffer, VisualizerConfig config, FrequencyRange hapticRange, FrequencyRange flashlightRange, boolean isInternalSource) {
        if (hopBuffer == null || ring == null || hann == null || fftData == null) {
            Log.e("AudioProcessor", "processAudioFrame: One or more buffers are null");
            return null;
        }

        // Fill ring buffer
        for (short value : hopBuffer) {
            if (ringPosition >= 0 && ringPosition < ring.length) {
                ring[ringPosition] = value / 32768f;
                ringPosition = (ringPosition + 1) % analysisWindow;
            }
        }
        filled = Math.min(filled + hopBuffer.length, analysisWindow);

        if (filled < analysisWindow) {
            return null; // Not enough data yet
        }

        if (fftSize <= 0) {
            Log.e("AudioProcessor", "fftSize is 0 or negative");
            return null;
        }

        // Process FFT
        for (int i = 0; i < fftSize; i++) {
            if (i < fftData.length && i < hann.length) {
                fftData[i] = ring[(ringPosition + i) % analysisWindow] * hann[i];
            }
        }

        try {
            fft.realForwardFull(fftData);
        } catch (Exception e) {
            Log.e("AudioProcessor", "FFT processing failed", e);
            return null;
        }
        
        int halfFftSize = fftSize / 2;
        float frameMax = 0f;

        // First pass: compute raw magnitudes and find frame peak
        for (int i = 0; i <= halfFftSize; i++) {
            if (2 * i + 1 >= fftData.length) break;
            
            double re = fftData[2 * i];
            double im = fftData[2 * i + 1];
            float mag = (float) (Math.hypot(re, im) / (fftSize / 2.0));

            // Amplify high frequencies
            float freq = i * hzPerBin;
            float boost = 1f + (freq / 10000f) * 4f;
            float rawMag = mag * boost;
            
            if (i < magnitude.length) {
                magnitude[i] = rawMag;
                if (rawMag > frameMax) frameMax = rawMag;
            }
        }

        // Global Auto-Gain Logic
        // Update running max with asymmetric decay
        float decay = frameMax > mRunningMax ? 0.7f : DECAY_SLOW;
        mRunningMax = Math.max(mRunningMax * decay, frameMax);
        
        // Ensure running max doesn't drop too low to avoid extreme gain on noise
        float floor = 0.001f;
        float effectiveMax = Math.max(mRunningMax, floor);
        
        float targetPeak = isInternalSource ? 0.55f : mTargetPeak;
        float desiredGain = targetPeak / effectiveMax;
        
        // Clamp gain
        desiredGain = Math.max(0.1f, Math.min(200.0f, desiredGain));
        
        // Smooth gain changes asymmetrically (faster decrease, slower increase)
        float smoothing = desiredGain < mAutoGain ? GAIN_SMOOTHING_ATTACK : GAIN_SMOOTHING_DECAY;
        mAutoGain = (mAutoGain * (1f - smoothing)) + (desiredGain * smoothing);

        // Second pass: apply gain to magnitudes
        for (int i = 0; i < magnitude.length; i++) {
            magnitude[i] *= mAutoGain;
        }

        // Compute pre-calculated peaks for UI/Logic
        float hapticPeak = hapticRange != null ? computeRangeMagnitude(hapticRange, magnitude) : 0f;
        float flashlightPeak = flashlightRange != null ? computeRangeMagnitude(flashlightRange, magnitude) : 0f;
        
        // UI range peak (70-130Hz)
        float uiPeak = mUiRange != null ? computeRangeMagnitude(mUiRange, magnitude) : 0f;

        // Compute zone magnitudes
        float[] uniqueMagnitudes = computeUniqueMagnitudes(config, magnitude);

        return new AudioFrameResult(uniqueMagnitudes, hapticPeak, uiPeak, flashlightPeak, magnitude);
    }

    private float[] computeUniqueMagnitudes(VisualizerConfig config, float[] magnitude) {
        if (config == null) return new float[0];
        float[] uniqueMagnitudes = new float[config.uniqueRanges.length];
        float dominantMagnitude = 0f;
        for (int i = 0; i < config.uniqueRanges.length; i++) {
            float magnitudeSum = computeRangeMagnitude(config.uniqueRanges[i], magnitude);
            uniqueMagnitudes[i] = magnitudeSum;
            if (magnitudeSum > dominantMagnitude) {
                dominantMagnitude = magnitudeSum;
            }
        }

        if (dominantMagnitude <= EPSILON) {
            return uniqueMagnitudes;
        }

        float leakageFloor = dominantMagnitude * SPECTRUM_LEAKAGE_FLOOR_RATIO;
        boolean hasFilteredEnergy = false;
        for (int i = 0; i < uniqueMagnitudes.length; i++) {
            uniqueMagnitudes[i] = Math.max(0f, uniqueMagnitudes[i] - leakageFloor);
            if (uniqueMagnitudes[i] > EPSILON) {
                hasFilteredEnergy = true;
            }
        }

        // If the leakage floor wipes every band, fall back to the raw per-range values
        // so the visualizer still receives energy and can normalize it downstream.
        if (!hasFilteredEnergy) {
            for (int i = 0; i < config.uniqueRanges.length; i++) {
                uniqueMagnitudes[i] = computeRangeMagnitude(config.uniqueRanges[i], magnitude);
            }
        }
        return uniqueMagnitudes;
    }

    public float computeRangeMagnitude(FrequencyRange range, float[] magnitude) {
        if (range == null || magnitude == null || magnitude.length == 0) {
            return 0f;
        }

        int start = Math.max(0, Math.min(range.binLo, magnitude.length - 1));
        int end = Math.max(start, Math.min(range.binHi, magnitude.length - 1));
        float maxMag = 0f;
        for (int bin = start; bin <= end; bin++) {
            if (magnitude[bin] > maxMag) {
                maxMag = magnitude[bin];
            }
        }
        return maxMag;
    }

    private static float[] buildHannWindow(int size) {
        float[] hann = new float[size];
        double denom = Math.max(1d, size - 1d);
        for (int i = 0; i < size; i++) {
            double phase = (2d * Math.PI * i) / denom;
            hann[i] = (float) (0.5d * (1d - Math.cos(phase)));
        }
        return hann;
    }

    // Inner classes for config
    public static final class VisualizerConfig {
        public final String presetKey;
        public final String description;
        public final float decay;
        public final ZoneSpec[] zones;
        public final FrequencyRange[] uniqueRanges;
        public final int[][] zoneToRangeIndices;

        public VisualizerConfig(
                String presetKey,
                String description,
                float decay,
                ZoneSpec[] zones,
                FrequencyRange[] uniqueRanges,
                int[][] zoneToRangeIndices
        ) {
            this.presetKey = presetKey;
            this.description = description;
            this.decay = decay;
            this.zones = zones;
            this.uniqueRanges = uniqueRanges;
            this.zoneToRangeIndices = zoneToRangeIndices;
        }
    }

    public static final class ZoneSpec {
        public final float lowHz;
        public final float highHz;
        public final float lowPercent;
        public final float highPercent;

        public ZoneSpec(float lowHz, float highHz, float lowPercent, float highPercent) {
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.lowPercent = lowPercent;
            this.highPercent = highPercent;
        }

        boolean hasPercentSlice() {
            return !Float.isNaN(lowPercent) && !Float.isNaN(highPercent);
        }
    }

    public static final class FrequencyRange {
        public final float lowHz;
        public final float highHz;
        public final int binLo;
        public final int binHi;

        public FrequencyRange(float lowHz, float highHz, float hzPerBin, int fftSize) {
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.binLo = Math.max(0, (int) Math.ceil(lowHz / hzPerBin));
            this.binHi = Math.max(binLo, Math.min(fftSize / 2, (int) Math.floor(highHz / hzPerBin)));
        }
    }

    public static final class AudioFrameResult {
        public final float[] uniqueMagnitudes;
        public final float hapticPeak;
        public final float uiPeak;
        public final float flashlightPeak;
        public final float[] magnitude;

        public AudioFrameResult(float[] uniqueMagnitudes, float hapticPeak, float uiPeak, float flashlightPeak, float[] magnitude) {
            this.uniqueMagnitudes = uniqueMagnitudes;
            this.hapticPeak = hapticPeak;
            this.uiPeak = uiPeak;
            this.flashlightPeak = flashlightPeak;
            this.magnitude = magnitude;
        }
    }
}
