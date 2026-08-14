package com.better.nothing.music.vizualizer.logic;

import android.util.Log;
import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Handles audio capture, FFT processing, and frequency analysis.
 * Features independent 3-band auto-gain and centralized raw/decayed FFT variables.
 */
public class AudioProcessor {

    public enum SourceType {
        MIC,
        INTERNAL,
        VIZUALIZER,
        NETWORK
    }

    public enum ReadMethod {
        MAX,
        MEAN,
        RMS
    }

    private int sampleRate = 44100;
    private int fftSize;
    private int analysisWindow;
    private float hzPerBin;

    private boolean mDualFftEnabled = false;
    private int lowFftSize, highFftSize;
    private DoubleFFT_1D fftLow, fftHigh;
    private double[] mFftBufferLow, mFftBufferHigh;
    private float[] magnitudeLow, magnitudeHigh;
    private float[] hannLow, hannHigh;
    private float hzPerBinLow, hzPerBinHigh;

    private float[] ring;
    private int ringPosition = 0;
    private int filled = 0;

    private double[] mFftBuffer;
    private float[] magnitude;
    private float[] hann;
    private DoubleFFT_1D fft;

    private final int[] mRawFFT = new int[512];
    int[][] mLogBinToLinearRange = new int[512][2];

    // 512 logarithmic bins from 30Hz to 16kHz
    public static final float[][] FFT_FREQ_RANGES = new float[512][2];
    static {
        float fMin = 30f;
        float fMax = 16000f;
        for (int i = 0; i < 512; i++) {
            FFT_FREQ_RANGES[i][0] = (float) (fMin * Math.pow(fMax / fMin, (double) i / 512.0));
            FFT_FREQ_RANGES[i][1] = (float) (fMin * Math.pow(fMax / fMin, (double) (i + 1) / 512.0));
        }
    }

    // 3-Band Auto-Gain State (Bass, Mids, Highs)
    // 0: Bass (30-250Hz), 1: Mids (250-4000Hz), 2: Highs (4000-16000Hz)
    private final float[] mRunningMax = {0.01f, 0.01f, 0.01f};
    private final float[] mBandGain = {1.0f, 1.0f, 1.0f};
    private float mManualGain = 4.0f;

    private static final float DECAY_SLOW = 0.998f;
    private static final float GAIN_SMOOTHING_ATTACK = 0.15f;
    private static final float GAIN_SMOOTHING_DECAY = 0.02f;
    private static final float TARGET_PEAK = 0.6f;

    public AudioProcessor() {
        updateFFTSize();
    }

    public void updateFFTSize() {
        updateFFTSize(44100);
    }

    public void setManualGain(float gain) {
        this.mManualGain = gain;
    }

    public void setDualFftEnabled(boolean enabled) {
        if (this.mDualFftEnabled != enabled) {
            this.mDualFftEnabled = enabled;
            updateFFTSize(this.sampleRate);
        }
    }

    public void updateFFTSize(int sampleRate) {
        int newFftSize = 2048; 
        if (!mDualFftEnabled && this.fftSize == newFftSize && this.fft != null && this.sampleRate == sampleRate) return;
        if (mDualFftEnabled && this.sampleRate == sampleRate && fftLow != null) return;

        this.sampleRate = sampleRate;
        this.fftSize = newFftSize;
        this.analysisWindow = fftSize;
        this.hzPerBin = (float) sampleRate / (float) fftSize;

        if (mDualFftEnabled) {
            this.lowFftSize = (int) (sampleRate * 0.021);
            this.highFftSize = (int) (sampleRate * 0.017);
            
            this.fftLow = new DoubleFFT_1D(lowFftSize);
            this.mFftBufferLow = new double[lowFftSize * 2];
            this.magnitudeLow = new float[lowFftSize / 2 + 1];
            this.hannLow = buildHannWindow(lowFftSize);
            this.hzPerBinLow = (float) sampleRate / (float) lowFftSize;

            this.fftHigh = new DoubleFFT_1D(highFftSize);
            this.mFftBufferHigh = new double[highFftSize * 2];
            this.magnitudeHigh = new float[highFftSize / 2 + 1];
            this.hannHigh = buildHannWindow(highFftSize);
            this.hzPerBinHigh = (float) sampleRate / (float) highFftSize;
        }

        this.fft = new DoubleFFT_1D(fftSize);
        this.mFftBuffer = new double[fftSize * 2];
        this.magnitude = new float[fftSize / 2 + 1];
        this.hann = buildHannWindow(fftSize);
        this.ring = new float[analysisWindow];
        this.ringPosition = 0;
        this.filled = 0;
        
        for (int i = 0; i < 512; i++) {
            float fStart = FFT_FREQ_RANGES[i][0];
            float fEnd = FFT_FREQ_RANGES[i][1];
            mLogBinToLinearRange[i][0] = Math.max(0, (int) Math.floor(fStart / hzPerBin));
            mLogBinToLinearRange[i][1] = Math.max(mLogBinToLinearRange[i][0], (int) Math.floor(fEnd / hzPerBin));
        }
    }

    public AudioFrameResult processAudioFrame(short[] hopBuffer, SourceType sourceType, float decayFactor) {
        if (hopBuffer == null || ring == null) return null;

        for (short value : hopBuffer) {
            ring[ringPosition] = value / 32768f;
            ringPosition = (ringPosition + 1) % analysisWindow;
            filled = Math.min(filled + 1, analysisWindow);
        }
        if (filled < (mDualFftEnabled ? Math.max(lowFftSize, highFftSize) : fftSize)) return null;

        if (mDualFftEnabled) {
            // Process Low FFT (21ms)
            for (int i = 0; i < lowFftSize; i++) {
                mFftBufferLow[i] = ring[(ringPosition - lowFftSize + i + analysisWindow) % analysisWindow] * hannLow[i];
            }
            try {
                fftLow.realForwardFull(mFftBufferLow);
                int halfLow = lowFftSize / 2;
                for (int i = 0; i <= halfLow; i++) {
                    double re = mFftBufferLow[2 * i];
                    double im = mFftBufferLow[2 * i + 1];
                    float mag = (float) (Math.hypot(re, im) / (lowFftSize / 2.0));
                    float freq = i * hzPerBinLow;
                    float boost = 1f + (freq / 10000f) * 4f;
                    magnitudeLow[i] = mag * boost;
                }
            } catch (Exception ignored) {}

            // Process High FFT (17ms)
            for (int i = 0; i < highFftSize; i++) {
                mFftBufferHigh[i] = ring[(ringPosition - highFftSize + i + analysisWindow) % analysisWindow] * hannHigh[i];
            }
            try {
                fftHigh.realForwardFull(mFftBufferHigh);
                int halfHigh = highFftSize / 2;
                for (int i = 0; i <= halfHigh; i++) {
                    double re = mFftBufferHigh[2 * i];
                    double im = mFftBufferHigh[2 * i + 1];
                    float mag = (float) (Math.hypot(re, im) / (highFftSize / 2.0));
                    float freq = i * hzPerBinHigh;
                    float boost = 1f + (freq / 10000f) * 4f;
                    magnitudeHigh[i] = mag * boost;
                }
            } catch (Exception ignored) {}
        } else {
            for (int i = 0; i < fftSize; i++) {
                mFftBuffer[i] = ring[(ringPosition + i) % analysisWindow] * hann[i];
            }
            try {
                fft.realForwardFull(mFftBuffer);
                int halfFftSize = fftSize / 2;
                for (int i = 0; i <= halfFftSize; i++) {
                    double re = mFftBuffer[2 * i];
                    double im = mFftBuffer[2 * i + 1];
                    float mag = (float) (Math.hypot(re, im) / (fftSize / 2.0));
                    float freq = i * hzPerBin;
                    float boost = 1f + (freq / 10000f) * 4f;
                    magnitude[i] = mag * boost;
                }
            } catch (Exception ignored) {}
        }
        
        float[] bandMax = {0f, 0f, 0f};
        float crossoverFreq = 400f; // Crossover point between low and high FFTs

        // Log pivots for easy 3-band transition
        double p0 = Math.log10(86.6);
        double p1 = Math.log10(1000.0);
        double p2 = Math.log10(8000.0);

        // Pre-calculate bandMax for AGC
        if (mDualFftEnabled) {
            for (int i = 0; i < magnitudeLow.length; i++) {
                float freq = i * hzPerBinLow;
                if (freq < 250f) bandMax[0] = Math.max(bandMax[0], magnitudeLow[i]);
            }
            for (int i = 0; i < magnitudeHigh.length; i++) {
                float freq = i * hzPerBinHigh;
                if (freq >= 250f && freq < 4000f) bandMax[1] = Math.max(bandMax[1], magnitudeHigh[i]);
                else if (freq >= 4000f && freq <= 16000f) bandMax[2] = Math.max(bandMax[2], magnitudeHigh[i]);
            }
        } else {
            for (int i = 0; i < magnitude.length; i++) {
                float freq = i * hzPerBin;
                if (freq < 250f) bandMax[0] = Math.max(bandMax[0], magnitude[i]);
                else if (freq < 4000f) bandMax[1] = Math.max(bandMax[1], magnitude[i]);
                else if (freq <= 16000f) bandMax[2] = Math.max(bandMax[2], magnitude[i]);
            }
        }

        // Compute band-specific gains (same as before)
        for (int i = 0; i < 3; i++) {
            float decay = bandMax[i] > mRunningMax[i] ? 0.7f : DECAY_SLOW;
            mRunningMax[i] = Math.max(mRunningMax[i] * decay, bandMax[i]);
            float effectiveMax = Math.max(mRunningMax[i], 0.001f);
            float target = TARGET_PEAK;
            float desiredGain = target / effectiveMax;
            
            if (sourceType == SourceType.NETWORK) desiredGain = 1.0f;
            else if (sourceType == SourceType.INTERNAL) desiredGain = Math.max(0.7f, Math.min(1.4f, desiredGain));
            else if (sourceType == SourceType.VIZUALIZER) desiredGain = Math.max(0.1f, Math.min(20.0f, desiredGain));
            else desiredGain = Math.max(0.1f, Math.min(200.0f, desiredGain));
            
            float smoothing = desiredGain < mBandGain[i] ? GAIN_SMOOTHING_ATTACK : GAIN_SMOOTHING_DECAY;
            if (sourceType == SourceType.NETWORK) mBandGain[i] = 1.0f;
            else if (sourceType == SourceType.INTERNAL || sourceType == SourceType.VIZUALIZER) {
                float internalSmoothing = smoothing * 0.1f;
                mBandGain[i] = (mBandGain[i] * (1f - internalSmoothing)) + (desiredGain * internalSmoothing);
            } else {
                mBandGain[i] = (mBandGain[i] * (1f - smoothing)) + (desiredGain * smoothing);
            }
        }

        for (int i = 0; i < 512; i++) {
            float fCenter = (FFT_FREQ_RANGES[i][0] + FFT_FREQ_RANGES[i][1]) / 2f;
            double logF = Math.log10(Math.max(1.0, fCenter));
            
            float interpolatedGain;
            if (logF <= p0) interpolatedGain = mBandGain[0];
            else if (logF < p1) {
                float t = (float) ((logF - p0) / (p1 - p0));
                interpolatedGain = mBandGain[0] * (1f - t) + mBandGain[1] * t;
            } else if (logF < p2) {
                float t = (float) ((logF - p1) / (p2 - p1));
                interpolatedGain = mBandGain[1] * (1f - t) + mBandGain[2] * t;
            } else interpolatedGain = mBandGain[2];
            
            float gain = interpolatedGain * mManualGain;
            float logMag;

            if (mDualFftEnabled) {
                if (fCenter < crossoverFreq) {
                    float continuousBin = fCenter / hzPerBinLow;
                    int b0 = (int) continuousBin;
                    int b1 = Math.min(b0 + 1, magnitudeLow.length - 1);
                    float t = continuousBin - b0;
                    logMag = magnitudeLow[b0] * (1f - t) + magnitudeLow[b1] * t;
                } else {
                    float continuousBin = fCenter / hzPerBinHigh;
                    int b0 = (int) continuousBin;
                    int b1 = Math.min(b0 + 1, magnitudeHigh.length - 1);
                    float t = continuousBin - b0;
                    logMag = magnitudeHigh[b0] * (1f - t) + magnitudeHigh[b1] * t;
                }
            } else {
                float continuousBin = fCenter / hzPerBin;
                int b0 = (int) continuousBin;
                int b1 = Math.min(b0 + 1, magnitude.length - 1);
                float t = continuousBin - b0;
                logMag = magnitude[b0] * (1f - t) + magnitude[b1] * t;
            }
            
            mRawFFT[i] = (int) Math.min(4095, logMag * 4095f * gain);
        }

        return new AudioFrameResult(mRawFFT.clone());
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

    public static int findLogBinIndex(float freq) {
        for (int i = 0; i < 512; i++) {
            if (freq >= FFT_FREQ_RANGES[i][0] && freq <= FFT_FREQ_RANGES[i][1]) return i;
        }
        if (freq < 30f) return 0;
        return 511;
    }

    // Boilerplate inner classes...
    public static final class VisualizerConfig {
        public final String presetKey;
        public final String description;
        public final float decay;
        public final ZoneSpec[] zones;
        public final FrequencyRange[] uniqueRanges;
        public final int[][] zoneToRangeIndices;

        public VisualizerConfig(String presetKey, String description, float decay, ZoneSpec[] zones, FrequencyRange[] uniqueRanges, int[][] zoneToRangeIndices) {
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
            this.lowHz = lowHz; this.highHz = highHz; this.lowPercent = lowPercent; this.highPercent = highPercent;
        }
        boolean hasPercentSlice() { return !Float.isNaN(lowPercent) && !Float.isNaN(highPercent); }
    }

    public static final class FrequencyRange {
        public final float lowHz;
        public final float highHz;
        public final int logBinLo;
        public final int logBinHi;
        public FrequencyRange(float lowHz, float highHz) {
            this.lowHz = lowHz; this.highHz = highHz; this.logBinLo = findLogBinIndex(lowHz); this.logBinHi = findLogBinIndex(highHz);
        }
    }

    public static final class AudioFrameResult {
        public final int[] fftraw;
        public AudioFrameResult(int[] fftraw) {
            this.fftraw = fftraw;
        }
    }
}
