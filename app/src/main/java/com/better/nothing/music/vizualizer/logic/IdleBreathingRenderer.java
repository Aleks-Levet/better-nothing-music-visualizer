package com.better.nothing.music.vizualizer.logic;

import android.os.SystemClock;

import com.better.nothing.music.vizualizer.model.DeviceProfile;

import java.util.Arrays;

public final class IdleBreathingRenderer {
    private static final long BREATH_DELAY_MS = 2500L;

    private int mDeviceType;
    private boolean mEnabled;
    private String mPattern = "pulse";
    private float mBrightness = 0.4f;
    private float mBackgroundBrightness = 0.02f;
    private float mAnimationSpeed = 1.0f;
    private long mSilenceStartTimeMs;
    private long mLastFrameMs;
    private float mBreathingEnvelope;
    private float[] mStandaloneState = new float[0];

    public IdleBreathingRenderer(int deviceType, boolean enabled) {
        mDeviceType = deviceType;
        mEnabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) mSilenceStartTimeMs = 0;
    }

    public void setDeviceType(int deviceType) {
        mDeviceType = deviceType;
    }

    public void start(long nowMs) {
        mEnabled = true;
        mSilenceStartTimeMs = nowMs - BREATH_DELAY_MS - 1;
        mLastFrameMs = nowMs;
        mBreathingEnvelope = 1f;
    }

    public void setPattern(String pattern) {
        mPattern = pattern;
    }

    public void setBrightness(float brightness) {
        mBrightness = brightness;
    }

    public void setBackgroundBrightness(float brightness) {
        mBackgroundBrightness = brightness;
    }

    public void setAnimationSpeed(float speed) {
        mAnimationSpeed = Math.max(0.5f, Math.min(2.0f, speed));
    }

    public boolean isBreathing() {
        return mEnabled && mBreathingEnvelope > 0.01f;
    }

    public boolean isDeeplySilent() {
        return mSilenceStartTimeMs > 0
                && SystemClock.elapsedRealtime() - mSilenceStartTimeMs > BREATH_DELAY_MS;
    }

    public void reset() {
        mSilenceStartTimeMs = 0;
        mLastFrameMs = 0;
        mBreathingEnvelope = 0f;
        mStandaloneState = new float[0];
    }

    public void apply(float[] state, boolean[] alwaysOnZones, long nowMs, boolean isSilent) {
        long deltaMs = mLastFrameMs > 0 ? nowMs - mLastFrameMs : 16;
        mLastFrameMs = nowMs;

        if (isSilent) {
            if (mSilenceStartTimeMs == 0) mSilenceStartTimeMs = nowMs;
        } else {
            mSilenceStartTimeMs = 0;
        }

        long silenceDuration = mSilenceStartTimeMs > 0 ? nowMs - mSilenceStartTimeMs : 0;
        float targetEnvelope = mEnabled && silenceDuration > BREATH_DELAY_MS ? 1f : 0f;

        if (mBreathingEnvelope < targetEnvelope) {
            mBreathingEnvelope += (float) deltaMs * mAnimationSpeed / 2500f;
            if (mBreathingEnvelope > targetEnvelope) mBreathingEnvelope = targetEnvelope;
        } else if (mBreathingEnvelope > targetEnvelope) {
            mBreathingEnvelope -= (float) deltaMs * mAnimationSpeed / 300f;
            if (mBreathingEnvelope < targetEnvelope) mBreathingEnvelope = targetEnvelope;
        }

        if (mBreathingEnvelope <= 0.01f) return;

        int zoneCount = state.length;
        for (int i = 0; i < zoneCount; i++) {
            int effectiveIndex = i;
            if (mDeviceType == DeviceProfile.DEVICE_NP3A) {
                if (i >= 20 && i <= 30) {
                    effectiveIndex = i - 20;
                } else if (i >= 31 && i <= 35) {
                    effectiveIndex = 11 + (i - 31);
                } else if (i >= 0 && i <= 19) {
                    effectiveIndex = 16 + i;
                }
            }

            float intensity = getIntensity(effectiveIndex, zoneCount, nowMs);
            float breathValue = (mBackgroundBrightness
                    + intensity * (mBrightness - mBackgroundBrightness)) * mBreathingEnvelope;

            boolean alwaysOn = alwaysOnZones != null
                    && i < alwaysOnZones.length && alwaysOnZones[i];

            if (alwaysOn) {
                state[i] = state[i] * (1f - mBreathingEnvelope)
                        + breathValue * mBreathingEnvelope;
            } else if (state[i] < breathValue) {
                state[i] = breathValue;
            }
        }
    }

    public int[] renderFrame(int zoneCount, int maxBrightness, long nowMs) {
        if (zoneCount <= 0) return new int[0];
        if (mStandaloneState.length != zoneCount) {
            mStandaloneState = new float[zoneCount];
        } else {
            Arrays.fill(mStandaloneState, 0f);
        }

        apply(mStandaloneState, null, nowMs, true);
        int[] colors = new int[zoneCount];
        for (int i = 0; i < zoneCount; i++) {
            colors[i] = Math.max(0, Math.min(4095,
                    Math.round(mStandaloneState[i] * maxBrightness)));
        }
        return colors;
    }

    private float getIntensity(int index, int zoneCount, long nowMs) {
        double animationTimeMs = nowMs * mAnimationSpeed;
        return switch (mPattern) {
            case "pulse" -> {
                double time = (animationTimeMs % 3000.0) / 3000.0;
                yield (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * time - Math.PI / 2.0));
            }
            case "wave" -> {
                double time = (animationTimeMs % 2000.0) / 2000.0;
                float phase = (float) index / zoneCount;
                yield (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (time - phase)));
            }
            case "scanner" -> {
                double time = (animationTimeMs % 2500.0) / 2500.0;
                float scannerPosition = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * time));
                float distance = Math.abs((float) index / zoneCount - scannerPosition);
                yield (float) Math.exp(-distance * distance * 40.0);
            }
            case "zebra" -> {
                double time = (animationTimeMs % 1500.0) / 1500.0;
                double value = Math.sin(2.0 * Math.PI * time);
                yield index % 2 == 0 ? (float) (0.5 + 0.5 * value) : (float) (0.5 - 0.5 * value);
            }
            case "heartbeat" -> {
                double time = (animationTimeMs % 2000.0) / 2000.0;
                if (time < 0.12) yield (float) Math.sin(Math.PI * time / 0.12);
                if (time > 0.22 && time < 0.34) {
                    yield (float) Math.sin(Math.PI * (time - 0.22) / 0.12) * 0.7f;
                }
                yield 0f;
            }
            case "orbit" -> {
                double time = (animationTimeMs % 4000.0) / 4000.0;
                float center = (float) (0.5 + 0.4 * Math.sin(2.0 * Math.PI * time));
                float distance = Math.abs((float) index / zoneCount - center);
                if (distance > 0.5f) distance = 1f - distance;
                yield (float) Math.exp(-distance * distance * 80.0);
            }
            case "rain" -> getRainIntensity(index, zoneCount, animationTimeMs);
            default -> {
                double time = (animationTimeMs % 3000.0) / 3000.0;
                float phase = index * 0.02f;
                yield (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (time + phase) - Math.PI / 2.0));
            }
        };
    }

    private float getRainIntensity(int index, int zoneCount, double animationTimeMs) {
        int width = DeviceProfile.getMatrixWidth(mDeviceType);
        if (width > 0) {
            int height = DeviceProfile.getMatrixHeight(mDeviceType);
            int x = index % width;
            int y = index / width;
            double columnHash = Math.abs(Math.sin(x * 12.9898 + 7.23)) * 43758.5453;
            columnHash -= (long) columnHash;
            double speed = 0.1 + columnHash * 0.2;
            double dropY = animationTimeMs * speed * 0.05 % (height + 20);
            double distance = y - dropY;
            return distance <= 0 && distance > -8
                    ? (float) Math.pow((8.0 + distance) / 8.0, 2.0)
                    : 0f;
        }

        double hash = Math.abs(Math.sin(index * 43.123 + 9.87)) * 1234.56;
        hash -= (long) hash;
        double period = 2500.0 + hash * 4500.0;
        double offset = hash * 10000.0;
        double time = (animationTimeMs + offset) % period / period;
        return time < 0.15 ? (float) Math.pow(Math.sin(Math.PI * time / 0.15), 3.0) : 0f;
    }
}
