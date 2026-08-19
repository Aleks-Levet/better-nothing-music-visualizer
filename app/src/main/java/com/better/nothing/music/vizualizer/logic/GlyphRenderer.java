package com.better.nothing.music.vizualizer.logic;

import com.better.nothing.music.vizualizer.model.DeviceProfile;
import android.util.Log;
import java.util.Arrays;

/**
 * Handles glyph state computation using the centralized fftraw variable.
 */
public class GlyphRenderer {

    private static final int MAX_BRIGHTNESS_LIMIT = 5000;
    private static final float SILENCE_THRESHOLD = 20f; // in 0-4095 range
    private static final long BREATH_DELAY_MS = 2500L;

    private float mGamma;
    private int mMaxBrightness = 4095;
    private boolean mIdleBreathingEnabled;
    private int mDeviceType;
    private String mIdlePattern = "pulse";
    private float mIdleBrightness = 0.4f;
    private float mIdleBackgroundBrightness = 0.02f;
    private boolean mAlternateMode = false;
    private int[] mPreviousFftRaw = null;

    private float[] mCurrentLightState = new float[0];
    private float[] mRangeDecayState = new float[0];
    private int mLastHash = Integer.MIN_VALUE;
    private long mSilenceStartTimeMs = 0;
    private long mLastFrameMs = 0;
    private float mBreathingEnvelope = 0f;

    public GlyphRenderer(float gamma, boolean idleBreathingEnabled, int deviceType) {
        this.mGamma = gamma;
        this.mIdleBreathingEnabled = idleBreathingEnabled;
        this.mDeviceType = deviceType;
    }

    public void setIdleBreathingEnabled(boolean enabled) {
        mIdleBreathingEnabled = enabled;
        if (!enabled) mSilenceStartTimeMs = 0;
    }

    public void setIdlePattern(String pattern) { this.mIdlePattern = pattern; }

    public void setIdleBrightness(float brightness) { this.mIdleBrightness = brightness; }
    public void setIdleBackgroundBrightness(float brightness) { this.mIdleBackgroundBrightness = brightness; }

    public void setAlternateMode(boolean enabled) {
        this.mAlternateMode = enabled;
        if (!enabled) mPreviousFftRaw = null;
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
        mLastHash = Integer.MIN_VALUE;
    }

    public void resetState(AudioProcessor.VisualizerConfig config) {
        if (config == null) {
            mCurrentLightState = new float[0];
            mRangeDecayState = new float[0];
        } else {
            mCurrentLightState = new float[config.zones.length];
            mRangeDecayState = new float[config.uniqueRanges.length];
        }
        mLastHash = Integer.MIN_VALUE;
        mSilenceStartTimeMs = 0;
        mLastFrameMs = 0;
        mBreathingEnvelope = 0f;
    }

    public float[] getCurrentLightState() { return mCurrentLightState; }

    public boolean isBreathing() {
        return mBreathingEnvelope > 0.01f;
    }

    public boolean isDeeplySilent() {
        return mSilenceStartTimeMs > 0 && (System.currentTimeMillis() - mSilenceStartTimeMs) > BREATH_DELAY_MS;
    }

    public int[] processFrame(int[] fftraw, AudioProcessor.VisualizerConfig config, long nowMs) {
        if (config == null || fftraw == null) return new int[0];

        int[] actualFft = fftraw;
        if (mAlternateMode) {
            if (mPreviousFftRaw == null || mPreviousFftRaw.length != fftraw.length) {
                mPreviousFftRaw = new int[fftraw.length];
            }
            actualFft = new int[fftraw.length];
            for (int i = 0; i < fftraw.length; i++) {
                actualFft[i] = Math.max(0, Math.min(2047, fftraw[i] - mPreviousFftRaw[i])) * 2;
            }
        }

        try {
            int hardwareCount = DeviceProfile.getLedCount(mDeviceType);
            int zoneCount = Math.max(config.zones.length, hardwareCount);

            if (mCurrentLightState.length != zoneCount) {
                mCurrentLightState = new float[zoneCount];
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
                double sumSq = 0;
                int count = 0;
                int start = Math.max(0, Math.min(range.logBinLo, 511));
                int end = Math.max(start, Math.min(range.logBinHi, 511));
                for (int b = start; b <= end; b++) {
                    float val = actualFft[b];
                    sumSq += (double) val * val;
                    count++;
                }

                float rmsVal = (count > 0) ? (float) Math.sqrt(sumSq / count) : 0f;
                float normalized = Math.min(1.0f, rmsVal / 4095f);
                
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

            applyIdleBreathing(mCurrentLightState, fftraw, config, nowMs);

            int[] frameColors = buildFrameColors(mCurrentLightState, zoneCount);
            int frameHash = Arrays.hashCode(frameColors);
            
            // Clone AFTER everything else is done so the next frame can compare
            mPreviousFftRaw = fftraw.clone();

            if (frameHash == mLastHash) return null;

            mLastHash = frameHash;
            return frameColors;
        } catch (Exception e) {
            Log.e("GlyphRenderer", "processFrame failed", e);
            return new int[0];
        }
    }

    private void applyIdleBreathing(float[] state, int[] fftraw, AudioProcessor.VisualizerConfig config, long nowMs) {
        long deltaMs = (mLastFrameMs > 0) ? (nowMs - mLastFrameMs) : 16;
        mLastFrameMs = nowMs;

        boolean isSilent = true;
        for (int val : fftraw) {
            if (val > SILENCE_THRESHOLD) {
                isSilent = false;
                break;
            }
        }

        if (isSilent) {
            if (mSilenceStartTimeMs == 0) mSilenceStartTimeMs = nowMs;
        } else {
            mSilenceStartTimeMs = 0;
        }

        long silenceDuration = (mSilenceStartTimeMs > 0) ? (nowMs - mSilenceStartTimeMs) : 0;
        boolean shouldBreathe = mIdleBreathingEnabled && (silenceDuration > BREATH_DELAY_MS);
        float targetEnvelope = shouldBreathe ? 1.0f : 0.0f;

        if (mBreathingEnvelope < targetEnvelope) {
            mBreathingEnvelope += (float) deltaMs / 2500f;
            if (mBreathingEnvelope > targetEnvelope) mBreathingEnvelope = targetEnvelope;
        } else if (mBreathingEnvelope > targetEnvelope) {
            mBreathingEnvelope -= (float) deltaMs / 300f;
            if (mBreathingEnvelope < targetEnvelope) mBreathingEnvelope = targetEnvelope;
        }

        if (mBreathingEnvelope > 0.01f) {
            int zoneCount = state.length;
            for (int i = 0; i < zoneCount; i++) {
                int effectiveIndex = i;
                if (mDeviceType == DeviceProfile.DEVICE_NP3A) {
                    if (i >= 20 && i <= 30) {
                        effectiveIndex = i - 20; // A1-A11 -> 0-10
                    } else if (i >= 31 && i <= 35) {
                        effectiveIndex = 11 + (i - 31); // B1-B5 -> 11-15
                    } else if (i >= 0 && i <= 19) {
                        effectiveIndex = 16 + i; // C1-C20 -> 16-35
                    }
                }
                float intensity = getIdleIntensity(effectiveIndex, zoneCount, nowMs);
                
                // Breath value interpolates between background and peak brightness
                float breathVal = (mIdleBackgroundBrightness + intensity * (mIdleBrightness - mIdleBackgroundBrightness)) * mBreathingEnvelope;

                // Check if this is an "always on" zone (0,0 percentage range)
                boolean isAlwaysOn = false;
                if (config != null && i < config.zones.length) {
                    AudioProcessor.ZoneSpec spec = config.zones[i];
                    if (spec.hasPercentSlice() && spec.lowPercent == 0f && spec.highPercent == 0f) {
                        isAlwaysOn = true;
                    }
                }

                if (isAlwaysOn) {
                    // For always-on LEDs, smoothly transition from the signal state (which is 1.0)
                    // to the idle breath state as the envelope grows.
                    state[i] = (state[i] * (1.0f - mBreathingEnvelope)) + (breathVal * mBreathingEnvelope);
                } else {
                    if (state[i] < breathVal) state[i] = breathVal;
                }
            }
        }
    }

    private float getIdleIntensity(int i, int zoneCount, long nowMs) {
        return switch (mIdlePattern) {
            case "pulse" -> {
                double timeProg = (double) (nowMs % 3000L) / 3000L;
                yield (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * timeProg - Math.PI / 2.0));
            }
            case "wave" -> {
                double timeProg = (double) (nowMs % 2000L) / 2000L;
                float phaseShift = (float) i / zoneCount;
                yield (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (timeProg - phaseShift)));
            }
            case "scanner" -> {
                double timeProg = (double) (nowMs % 2500L) / 2500L;
                float scannerPos = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * timeProg));
                float ledPos = (float) i / zoneCount;
                float dist = Math.abs(ledPos - scannerPos);
                yield (float) Math.exp(-dist * dist * 40.0);
            }
            case "zebra" -> {
                double timeProg = (double) (nowMs % 1500L) / 1500L;
                boolean even = (i % 2 == 0);
                double sinVal = Math.sin(2.0 * Math.PI * timeProg);
                yield even ? (float) (0.5 + 0.5 * sinVal) : (float) (0.5 - 0.5 * sinVal);
            }
            case "heartbeat" -> {
                double t = (double) (nowMs % 2000L) / 2000L;
                if (t < 0.12) yield (float) Math.sin(Math.PI * t / 0.12);
                else if (t > 0.22 && t < 0.34) yield (float) Math.sin(Math.PI * (t - 0.22) / 0.12) * 0.7f;
                yield 0f;
            }
            case "orbit" -> {
                double t = (double) (nowMs % 4000L) / 4000L;
                float center = (float) (0.5 + 0.4 * Math.sin(2.0 * Math.PI * t));
                float ledPos = (float) i / zoneCount;
                float dist = Math.abs(ledPos - center);
                if (dist > 0.5f) dist = 1.0f - dist;
                yield (float) Math.exp(-dist * dist * 80.0);
            }
            case "rain" -> {
                int width = DeviceProfile.getMatrixWidth(mDeviceType);
                if (width > 0) {
                    // Matrix Rain Effect (Falling drops)
                    int height = DeviceProfile.getMatrixHeight(mDeviceType);
                    int x = i % width;
                    int y = i / width;

                    // Column-based pseudo-randomness for speed and timing
                    double colHash = Math.abs(Math.sin(x * 12.9898 + 7.23)) * 43758.5453;
                    colHash = colHash - (long) colHash;

                    double speed = 0.1 + colHash * 0.2;
                    double time = nowMs * speed * 0.05;
                    double dropY = time % (height + 20); // 20 units of padding between drops

                    double dist = y - dropY;
                    if (dist <= 0 && dist > -8) {
                        // Head is brightest, 8-pixel tail
                        yield (float) Math.pow((8.0 + dist) / 8.0, 2.0);
                    }
                    yield 0.0f;
                } else {
                    // Discrete Glyph Rain (Sparse slow sparkles/raindrops)
                    double zoneHash = Math.abs(Math.sin(i * 43.123 + 9.87)) * 1234.56;
                    zoneHash = zoneHash - (long) zoneHash;

                    // Each zone has a different period and offset for natural look
                    double period = 2500.0 + zoneHash * 4500.0;
                    double offset = zoneHash * 10000.0;
                    double t = ((nowMs + offset) % period) / period;

                    // Pulse active during the first 15% of its period
                    if (t < 0.15) {
                        yield (float) Math.pow(Math.sin(Math.PI * (t / 0.15)), 3.0);
                    }
                    yield 0.0f;
                }
            }
            default -> {
                double timeProg = (double) (nowMs % 3000L) / 3000L;
                float phaseShift = (float) i * 0.02f;
                yield (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (timeProg + phaseShift) - Math.PI / 2.0));
            }
        };
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
