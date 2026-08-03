package com.better.nothing.music.vizualizer.logic;

import android.util.Log;
import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Handles audio capture, FFT processing, and frequency analysis.
 * Consolidated to a single 512-bin logarithmic FFT variable (fftraw).
 */
public class AudioProcessor {

    public enum ReadMethod {
        MAX, MEAN, RMS
    }

    private ReadMethod mReadMethod = ReadMethod.MAX;

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

    // The central source of truth for the entire pipeline
    private final int[] mRawFFT = new int[512];
    private final int[][] mLogBinToLinearRange = new int[512][2];

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

    // AGC and Manual Gain
    private float mRunningMax = 0.01f;
    private float mTargetPeak = 0.45f;
    private float mAutoGain = 1.0f;
    private float mManualGain = 4.0f;

    private static final float DECAY_SLOW = 0.998f;
    private static final float GAIN_SMOOTHING_ATTACK = 0.15f;
    private static final float GAIN_SMOOTHING_DECAY = 0.02f;

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
        this.fftData = new double[fftSize * 2];
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

    public void setReadMethod(ReadMethod method) {
        this.mReadMethod = method;
    }

    public AudioFrameResult processAudioFrame(short[] hopBuffer, boolean isInternalSource) {
        if (hopBuffer == null || ring == null || hann == null || fftData == null) return null;

        for (short value : hopBuffer) {
            if (ringPosition >= 0 && ringPosition < ring.length) {
                ring[ringPosition] = value / 32768f;
                ringPosition = (ringPosition + 1) % analysisWindow;
            }
        }
        filled = Math.min(filled + hopBuffer.length, analysisWindow);
        if (filled < analysisWindow) return null;

        for (int i = 0; i < fftSize; i++) {
            if (i < fftData.length && i < hann.length) {
                fftData[i] = ring[(ringPosition + i) % analysisWindow] * hann[i];
            }
        }

        try {
            fft.realForwardFull(fftData);
        } catch (Exception e) {
            return null;
        }
        
        int halfFftSize = fftSize / 2;
        float frameMax = 0f;
        for (int i = 0; i <= halfFftSize; i++) {
            if (2 * i + 1 >= fftData.length) break;
            double re = fftData[2 * i];
            double im = fftData[2 * i + 1];
            float mag = (float) (Math.hypot(re, im) / (fftSize / 2.0));
            float freq = i * hzPerBin;
            float boost = 1f + (freq / 10000f) * 4f;
            float rawMag = mag * boost;
            if (i < magnitude.length) {
                magnitude[i] = rawMag;
                if (rawMag > frameMax) frameMax = rawMag;
            }
        }

        // Apply AGC
        float decay = frameMax > mRunningMax ? 0.7f : DECAY_SLOW;
        mRunningMax = Math.max(mRunningMax * decay, frameMax);
        float effectiveMax = Math.max(mRunningMax, 0.001f);
        float targetPeak = isInternalSource ? 0.55f : mTargetPeak;
        float desiredGain = targetPeak / effectiveMax;
        desiredGain = Math.max(0.1f, Math.min(200.0f, desiredGain));
        float smoothing = desiredGain < mAutoGain ? GAIN_SMOOTHING_ATTACK : GAIN_SMOOTHING_DECAY;
        mAutoGain = (mAutoGain * (1f - smoothing)) + (desiredGain * smoothing);

        // Map to 512 log bins and apply BOTH dynamic (AutoGain) and static (ManualGain) gain
        for (int i = 0; i < 512; i++) {
            int startBin = mLogBinToLinearRange[i][0];
            int endBin = mLogBinToLinearRange[i][1];
            float logMag = 0f;
            for (int b = startBin; b <= endBin && b < magnitude.length; b++) {
                if (magnitude[b] > logMag) logMag = magnitude[b];
            }
            
            // Only place where dynamic and manual gain are applied to the pipeline
            int rawVal = (int) Math.min(4095, logMag * 4095f * mAutoGain * mManualGain);
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
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.lowPercent = lowPercent;
            this.highPercent = highPercent;
        }
        boolean hasPercentSlice() { return !Float.isNaN(lowPercent) && !Float.isNaN(highPercent); }
    }

    public static final class FrequencyRange {
        public final float lowHz;
        public final float highHz;
        public final int logBinLo;
        public final int logBinHi;
        public FrequencyRange(float lowHz, float highHz) {
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.logBinLo = findLogBinIndex(lowHz);
            this.logBinHi = findLogBinIndex(highHz);
        }
    }

    public static final class AudioFrameResult {
        public final int[] fftraw;
        public AudioFrameResult(int[] fftraw) {
            this.fftraw = fftraw;
        }
    }
}
