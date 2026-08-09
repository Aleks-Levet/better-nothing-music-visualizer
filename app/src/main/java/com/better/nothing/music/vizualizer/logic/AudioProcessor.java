package com.better.nothing.music.vizualizer.logic;

import android.util.Log;
import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Handles audio capture, FFT processing, and frequency analysis.
 * Features independent 3-band auto-gain and centralized raw/decayed FFT variables.
 */
public class AudioProcessor {

    public enum ReadMethod {
        MAX,
        AVERAGE,
        RMS
    }

    private int sampleRate = 44100;
    private int fftSize;
    private int analysisWindow;
    private float hzPerBin;

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
    private static final float TARGET_PEAK = 0.45f;

    public AudioProcessor() {
        updateFFTSize();
    }

    public void updateFFTSize() {
        updateFFTSize(44100);
    }

    public void setManualGain(float gain) {
        this.mManualGain = gain;
    }

    public void updateFFTSize(int sampleRate) {
        int newFftSize = 2048; 
        if (this.fftSize == newFftSize && this.fft != null && this.sampleRate == sampleRate) return;

        this.sampleRate = sampleRate;
        this.fftSize = newFftSize;
        this.analysisWindow = fftSize;
        this.hzPerBin = (float) sampleRate / (float) fftSize;

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

    public AudioFrameResult processAudioFrame(short[] hopBuffer, boolean isInternalSource, float decayFactor) {
        if (hopBuffer == null || ring == null || hann == null || mFftBuffer == null) return null;

        for (short value : hopBuffer) {
            if (ring != null && ringPosition >= 0 && ringPosition < ring.length) {
                ring[ringPosition] = value / 32768f;
                ringPosition = (ringPosition + 1) % analysisWindow;
                filled = Math.min(filled + 1, analysisWindow);
            }
        }
        if (filled < analysisWindow) return null;

        for (int i = 0; i < fftSize; i++) {
            if (i < mFftBuffer.length && i < hann.length) {
                mFftBuffer[i] = ring[(ringPosition + i) % analysisWindow] * hann[i];
            }
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
            float mag = (float) (Math.hypot(re, im) / (fftSize / 2.0));
            float freq = i * hzPerBin;
            
            // High frequency tilt boost (pre-gain)
            float boost = 1f + (freq / 10000f) * 4f;
            float rawMag = mag * boost;
            
            if (i < magnitude.length) {
                magnitude[i] = rawMag;
                if (freq < 250f) bandMax[0] = Math.max(bandMax[0], rawMag);
                else if (freq < 4000f) bandMax[1] = Math.max(bandMax[1], rawMag);
                else if (freq <= 16000f) bandMax[2] = Math.max(bandMax[2], rawMag);
            }
        }

        // Compute band-specific gains
        for (int i = 0; i < 3; i++) {
            float decay = bandMax[i] > mRunningMax[i] ? 0.7f : DECAY_SLOW;
            mRunningMax[i] = Math.max(mRunningMax[i] * decay, bandMax[i]);
            float effectiveMax = Math.max(mRunningMax[i], 0.001f);
            float target = isInternalSource ? 0.55f : TARGET_PEAK;
            float desiredGain = target / effectiveMax;
            desiredGain = Math.max(0.1f, Math.min(200.0f, desiredGain));
            float smoothing = desiredGain < mBandGain[i] ? GAIN_SMOOTHING_ATTACK : GAIN_SMOOTHING_DECAY;
            mBandGain[i] = (mBandGain[i] * (1f - smoothing)) + (desiredGain * smoothing);
        }

        // Map to 512 log bins and apply band-specific AGC + Manual Gain
        for (int i = 0; i < 512; i++) {
            float fStart = FFT_FREQ_RANGES[i][0];
            int bandIdx = (fStart < 250f) ? 0 : (fStart < 4000f) ? 1 : 2;
            float gain = mBandGain[bandIdx] * mManualGain;

            int startBin = mLogBinToLinearRange[i][0];
            int endBin = mLogBinToLinearRange[i][1];
            float logMag = 0f;
            for (int b = startBin; b <= endBin && b < magnitude.length; b++) {
                if (magnitude[b] > logMag) logMag = magnitude[b];
            }
            
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
