package com.better.nothing.music.vizualizer.logic;

import com.better.nothing.music.vizualizer.model.DeviceProfile;
import android.util.Log;
import java.util.Arrays;

/**
 * Handles glyph state computation using the centralized fftraw variable.
 */
public class GlyphRenderer {

    private static final int MAX_BRIGHTNESS_LIMIT = 6143;

    private float mGamma;
    private int mMaxBrightness = 4095;
    private final IdleBreathingRenderer mIdleBreathingRenderer;
    private int mDeviceType;
    private boolean mAlternateMode = false;
    private int[] mPreviousFftRaw = null;
    private float[] mSmoothedFft = null;
    private int[] mAlternateFft = null;

    private float[] mCurrentLightState = new float[0];
    private float[] mRangeDecayState = new float[0];
    private boolean[] mAlwaysOnZones = new boolean[0];
    private int mLastHash = Integer.MIN_VALUE;

    public GlyphRenderer(float gamma, boolean idleBreathingEnabled, int deviceType) {
        this.mGamma = gamma;
        this.mDeviceType = deviceType;
        this.mIdleBreathingRenderer = new IdleBreathingRenderer(deviceType, idleBreathingEnabled);
    }

    public void setIdleBreathingEnabled(boolean enabled) {
        mIdleBreathingRenderer.setEnabled(enabled);
    }

    public void startIdleBreathingAnimation(long nowMs) {
        mIdleBreathingRenderer.start(nowMs);
        mLastHash = Integer.MIN_VALUE;
    }

    public void setIdlePattern(String pattern) { mIdleBreathingRenderer.setPattern(pattern); }

    public void setIdleBrightness(float brightness) { mIdleBreathingRenderer.setBrightness(brightness); }
    public void setIdleBackgroundBrightness(float brightness) { mIdleBreathingRenderer.setBackgroundBrightness(brightness); }
    public void setIdleAnimationSpeed(float speed) { mIdleBreathingRenderer.setAnimationSpeed(speed); }

    public void setAlternateMode(boolean enabled) {
        this.mAlternateMode = enabled;
        if (!enabled) {
            mPreviousFftRaw = null;
            mSmoothedFft = null;
            mAlternateFft = null;
        }
    }

    public void setGamma(float gamma) {
        mGamma = gamma;
        mLastHash = Integer.MIN_VALUE;
    }

    public void setMaxBrightness(int brightness) {
        if (brightness > 0) {
            mMaxBrightness = Math.max(50, Math.min(MAX_BRIGHTNESS_LIMIT, brightness));
        } else {
            mMaxBrightness = 0;
        }
        mLastHash = Integer.MIN_VALUE;
    }

    public void setDeviceType(int deviceType) {
        this.mDeviceType = deviceType;
        mIdleBreathingRenderer.setDeviceType(deviceType);
        mIdleBreathingRenderer.prepareZoneCount(
                Math.max(mCurrentLightState.length, DeviceProfile.getLedCount(deviceType)));
        mLastHash = Integer.MIN_VALUE;
    }

    public void resetState(AudioProcessor.VisualizerConfig config) {
        if (config == null) {
            mCurrentLightState = new float[0];
            mRangeDecayState = new float[0];
            mAlwaysOnZones = new boolean[0];
        } else {
            mCurrentLightState = new float[config.zones.length];
            mRangeDecayState = new float[config.uniqueRanges.length];
            mAlwaysOnZones = new boolean[config.zones.length];
            for (int i = 0; i < config.zones.length; i++) {
                AudioProcessor.ZoneSpec zone = config.zones[i];
                mAlwaysOnZones[i] = zone.hasPercentSlice()
                        && zone.lowPercent == 0f && zone.highPercent == 0f;
            }
        }
        mIdleBreathingRenderer.prepareZoneCount(
                Math.max(mCurrentLightState.length, DeviceProfile.getLedCount(mDeviceType)));
        mLastHash = Integer.MIN_VALUE;
        mIdleBreathingRenderer.reset();
    }

    public float[] getCurrentLightState() { return mCurrentLightState; }

    public boolean isBreathing() {
        return mIdleBreathingRenderer.isBreathing();
    }

    public boolean isDeeplySilent() {
        return mIdleBreathingRenderer.isDeeplySilent();
    }

    public int[] processFrame(int[] fftraw, AudioProcessor.VisualizerConfig config, long nowMs, boolean isSilent) {
        if (config == null || fftraw == null) return new int[0];

        int[] actualFft = fftraw;
        if (mAlternateMode) {
            if (mSmoothedFft == null || mSmoothedFft.length != fftraw.length) {
                mPreviousFftRaw = new int[fftraw.length];
                mSmoothedFft = new float[fftraw.length];
                mAlternateFft = new int[fftraw.length];
                // Initialize to current state to avoid huge jump on first frame
                for (int i = 0; i < fftraw.length; i++) {
                    mSmoothedFft[i] = fftraw[i];
                    mPreviousFftRaw[i] = fftraw[i];
                }
            }
            
            for (int i = 0; i < fftraw.length; i++) {
                // Smooth the raw input to remove jitter
                mSmoothedFft[i] = mSmoothedFft[i] * 0.4f + fftraw[i] * 0.6f;
                
                int delta = (int) (mSmoothedFft[i] - mPreviousFftRaw[i]);
                // Noise gate: ignore tiny changes that are likely FFT jitter
                if (delta < 20) delta = 0; 
                
                mAlternateFft[i] = Math.max(0, Math.min(1023, delta)) * 4;
                mPreviousFftRaw[i] = (int) mSmoothedFft[i];
            }
            actualFft = mAlternateFft;
        }

        try {
            int hardwareCount = DeviceProfile.getLedCount(mDeviceType);
            int zoneCount = Math.max(config.zones.length, hardwareCount);

            if (mCurrentLightState.length != zoneCount) {
                mCurrentLightState = new float[zoneCount];
                mIdleBreathingRenderer.prepareZoneCount(zoneCount);
            }

            if (mRangeDecayState.length != config.uniqueRanges.length) {
                mRangeDecayState = new float[config.uniqueRanges.length];
            }

            // Exponential blending decay from original Python script:
            // prev = ad * prev + (1 - ad) * current
            float ad = config.decay; // The value is already pre-calculated in AudioCaptureService
            
            // 1. Calculate and decay unique frequency ranges first
            for (int r = 0; r < config.uniqueRanges.length; r++) {
                AudioProcessor.FrequencyRange range = config.uniqueRanges[r];
                float maxVal = 0f;
                int start = Math.max(0, Math.min(range.logBinLo, 511));
                int end = Math.max(start, Math.min(range.logBinHi, 511));
                for (int b = start; b <= end; b++) {
                    float val = actualFft[b];
                    if (val > maxVal) {
                        maxVal = val;
                    }
                }

                float normalized = Math.min(1.0f, maxVal / 4095f);
                
                // Exponential decay
                if (normalized > mRangeDecayState[r]) {
                    // Instant rise
                    mRangeDecayState[r] = normalized;
                } else {
                    // Exponential fall
                    mRangeDecayState[r] = ad * mRangeDecayState[r] + (1f - ad) * normalized;
                }
            }

            // 2. Map decayed ranges to zones and apply percent slicing
            for (int i = 0; i < zoneCount; i++) {
                if (i < config.zones.length) {
                    float maxDecayed = 0f;
                    for (int rangeIdx : config.zoneToRangeIndices[i]) {
                        if (mRangeDecayState[rangeIdx] > maxDecayed) {
                            maxDecayed = mRangeDecayState[rangeIdx];
                        }
                    }

                    float mapped = applyPercentSlice(maxDecayed, config.zones[i]);
                    mCurrentLightState[i] = applyGamma(mapped);
                } else {
                    // Decay padding LEDs
                    mCurrentLightState[i] *= ad;
                }
            }

            mIdleBreathingRenderer.apply(mCurrentLightState, mAlwaysOnZones, nowMs, isSilent);

            int[] frameColors = buildFrameColors(mCurrentLightState, zoneCount);
            int frameHash = Arrays.hashCode(frameColors);
            
            // If NOT in alternate mode, we still clone to keep mPreviousFftRaw updated for when mode toggles
            if (!mAlternateMode) {
                mPreviousFftRaw = fftraw.clone();
            }

            if (frameHash == mLastHash) return null;

            mLastHash = frameHash;
            return frameColors;
        } catch (Exception e) {
            Log.e("GlyphRenderer", "processFrame failed", e);
            return new int[0];
        }
    }

    private int[] buildFrameColors(float[] normalizedLightState, int expectedLength) {
        int[] frameColors = new int[expectedLength];
        float multiplier = (float) mMaxBrightness;
        for (int i = 0; i < Math.min(normalizedLightState.length, expectedLength); i++) {
            float n = normalizedLightState[i];
            // Hardware/SDK max is typically 4095. 
            // App multiplier goes up to 10000 (150% of old max) to act as gain.
            int val = Math.round(n * multiplier);
            frameColors[i] = Math.max(0, Math.min(4095, val));
        }
        return frameColors;
    }

    private float applyGamma(float normalizedValue) {
        return normalizedValue <= 0f ? 0f : (float) Math.pow(normalizedValue, mGamma);
    }

    private static float applyPercentSlice(float normalizedValue, AudioProcessor.ZoneSpec zone) {
        if (zone.lowHz == 0 && zone.highHz == 0) return 0f;
        if (!zone.hasPercentSlice()) return normalizedValue;
        float low = Math.min(zone.lowPercent, zone.highPercent);
        float high = Math.max(zone.lowPercent, zone.highPercent);
        float percent = normalizedValue * 100f;
        if (percent < low) return 0f;
        if (percent >= high || high == low) return 1f;
        return (percent - low) / (high - low);
    }
}
