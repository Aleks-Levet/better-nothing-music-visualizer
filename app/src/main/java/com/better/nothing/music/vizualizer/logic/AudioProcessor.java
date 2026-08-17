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
    private int analysisWindow = 2048; // Fixed window for low latency
    private float hzPerBin;

    private boolean mHighQualityAnalysis = false;

    private float[] ring;
    private int ringPosition = 0;
    private int filled = 0;

    private double[] mFftBuffer;
    private float[] magnitude;
    private float[] hann;
    private DoubleFFT_1D fft;

    private final int[] mRawFFT = new int[512];

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

    public void setHighQualityAnalysis(boolean enabled) {
        if (this.mHighQualityAnalysis != enabled) {
            this.mHighQualityAnalysis = enabled;
            updateFFTSize(this.sampleRate);
        }
    }

    public void updateFFTSize(int sampleRate) {
        int newFftSize = mHighQualityAnalysis ? 8192 : 2048; 
        if (this.fftSize == newFftSize && this.fft != null && this.sampleRate == sampleRate) return;

        this.sampleRate = sampleRate;
        this.fftSize = newFftSize;
        // The analysis window stays at 2048 for temporal precision, 
        // while the fftSize expands for spectral precision (Zero Padding).
        this.hzPerBin = (float) sampleRate / (float) fftSize;

        this.fft = new DoubleFFT_1D(fftSize);
        this.mFftBuffer = new double[fftSize * 2];
        this.magnitude = new float[fftSize / 2 + 1];
        
        // Hann window must match the actual audio window, not the padded FFT size
        this.hann = buildHannWindow(analysisWindow);
        
        this.ring = new float[analysisWindow];
        this.ringPosition = 0;
        this.filled = 0;
    }

    public AudioFrameResult processAudioFrame(short[] hopBuffer, SourceType sourceType, float decayFactor) {
        if (hopBuffer == null || ring == null || hann == null || mFftBuffer == null) return null;

        for (short value : hopBuffer) {
            ring[ringPosition] = value / 32768f;
            ringPosition = (ringPosition + 1) % analysisWindow;
            filled = Math.min(filled + 1, analysisWindow);
        }
        if (filled < analysisWindow) return null;

        // Reset buffer (important for Zero Padding)
        for (int i = 0; i < mFftBuffer.length; i++) mFftBuffer[i] = 0.0;

        // Fill buffer with audio data * Hann window
        for (int i = 0; i < analysisWindow; i++) {
            mFftBuffer[i] = ring[(ringPosition + i) % analysisWindow] * hann[i];
        }

        try {
            fft.realForwardFull(mFftBuffer);
        } catch (Exception e) {
            return null;
        }
        
        int halfFftSize = fftSize / 2;
        float[] bandMax = {0f, 0f, 0f};

        for (int i = 0; i <= halfFftSize; i++) {
            if (2 * i + 1 >= mFftBuffer.length) break;
            double re = mFftBuffer[2 * i];
            double im = mFftBuffer[2 * i + 1];
            float mag = (float) (Math.hypot(re, im) / (analysisWindow / 2.0));
            float freq = i * hzPerBin;
            
            float boost = 1f + (freq / 10000f) * 4f;
            float rawMag = mag * boost;
            
            if (i < magnitude.length) {
                magnitude[i] = rawMag;
                if (freq < 250f) bandMax[0] = Math.max(bandMax[0], rawMag);
                else if (freq < 4000f) bandMax[1] = Math.max(bandMax[1], rawMag);
                else if (freq <= 16000f) bandMax[2] = Math.max(bandMax[2], rawMag);
            }
        }

        // AGC Logic...
        for (int i = 0; i < 3; i++) {
            float decay = bandMax[i] > mRunningMax[i] ? 0.7f : DECAY_SLOW;
            mRunningMax[i] = Math.max(mRunningMax[i] * decay, bandMax[i]);
            float effectiveMax = Math.max(mRunningMax[i], 0.001f);
            float target = TARGET_PEAK;
            float desiredGain;
            
            if (sourceType == SourceType.NETWORK) {
                desiredGain = 1.0f;
            } else {
                desiredGain = target / effectiveMax;
                if (sourceType == SourceType.INTERNAL) {
                    desiredGain = Math.max(0.7f, Math.min(1.4f, desiredGain));
                } else if (sourceType == SourceType.VIZUALIZER) {
                    desiredGain = Math.max(0.1f, Math.min(100.0f, desiredGain));
                } else {
                    desiredGain = Math.max(0.1f, Math.min(200.0f, desiredGain));
                }
            }
            
            float smoothing = desiredGain < mBandGain[i] ? GAIN_SMOOTHING_ATTACK : GAIN_SMOOTHING_DECAY;
            if (sourceType == SourceType.NETWORK) {
                mBandGain[i] = 1.0f;
            } else if (sourceType == SourceType.INTERNAL || sourceType == SourceType.VIZUALIZER) {
                float internalSmoothing = smoothing * 0.1f;
                mBandGain[i] = (mBandGain[i] * (1f - internalSmoothing)) + (desiredGain * internalSmoothing);
            } else {
                mBandGain[i] = (mBandGain[i] * (1f - smoothing)) + (desiredGain * smoothing);
            }
        }

        double p0 = Math.log10(86.6);
        double p1 = Math.log10(1000.0);
        double p2 = Math.log10(8000.0);

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

            float continuousBin = fCenter / hzPerBin;
            int b0 = (int) continuousBin;
            int b1 = Math.min(b0 + 1, magnitude.length - 1);
            float t = continuousBin - b0;
            float logMag = magnitude[b0] * (1f - t) + magnitude[b1] * t;
            
            int rawVal = (int) Math.min(4095, logMag * 4095f * gain);
            mRawFFT[i] = rawVal;
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
