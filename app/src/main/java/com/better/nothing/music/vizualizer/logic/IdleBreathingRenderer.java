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
    private int[] mStandaloneColors = new int[0];
    private int mCachedDeviceType = DeviceProfile.DEVICE_UNKNOWN;
    private int mCachedZoneCount = -1;
    private int mMatrixWidth;
    private int mMatrixHeight;
    private int[] mEffectiveIndices = new int[0];
    private float[] mZonePositions = new float[0];
    private double[] mRainParameters = new double[0];
    private double[] mRainPeriods = new double[0];
    private double[] mRainOffsets = new double[0];

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
        mCachedZoneCount = -1;
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
        int zoneCount = state.length;
        ensureZoneCache(zoneCount);

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

        double animationTimeMs = nowMs * mAnimationSpeed;
        String pattern = mPattern;
        double frameTime = 0.0;
        float sharedValue = 0f;
        float frameCenter = 0f;
        switch (pattern) {
            case "static" -> { }
            case "pulse" -> {
                frameTime = (animationTimeMs % 3000.0) / 3000.0;
                sharedValue = (float) (0.5 + 0.5
                        * Math.sin(2.0 * Math.PI * frameTime - Math.PI / 2.0));
            }
            case "wave" -> frameTime = (animationTimeMs % 2000.0) / 2000.0;
            case "scanner" -> {
                frameTime = (animationTimeMs % 2500.0) / 2500.0;
                frameCenter = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * frameTime));
            }
            case "zebra" -> {
                frameTime = (animationTimeMs % 1500.0) / 1500.0;
                sharedValue = (float) Math.sin(2.0 * Math.PI * frameTime);
            }
            case "heartbeat" -> {
                frameTime = (animationTimeMs % 2000.0) / 2000.0;
                if (frameTime < 0.12) {
                    sharedValue = (float) Math.sin(Math.PI * frameTime / 0.12);
                } else if (frameTime > 0.22 && frameTime < 0.34) {
                    sharedValue = (float) Math.sin(
                            Math.PI * (frameTime - 0.22) / 0.12) * 0.7f;
                }
            }
            case "orbit" -> {
                frameTime = (animationTimeMs % 4000.0) / 4000.0;
                frameCenter = (float) (0.5 + 0.4 * Math.sin(2.0 * Math.PI * frameTime));
            }
            case "rain" -> { }
            default -> frameTime = (animationTimeMs % 3000.0) / 3000.0;
        }
        for (int i = 0; i < zoneCount; i++) {
            float intensity = getIntensity(
                    pattern, mEffectiveIndices[i], i, frameTime, frameCenter, sharedValue,
                    mZonePositions[i], animationTimeMs);
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
        return renderFrame(zoneCount, maxBrightness, nowMs, 1f);
    }

    public int[] renderFrame(int zoneCount, int maxBrightness, long nowMs, float fadeMultiplier) {
        if (zoneCount <= 0) return new int[0];
        if (mStandaloneState.length != zoneCount) {
            mStandaloneState = new float[zoneCount];
        }
        Arrays.fill(mStandaloneState, 0f);
        if (mStandaloneColors.length != zoneCount) mStandaloneColors = new int[zoneCount];

        apply(mStandaloneState, null, nowMs, true);
        for (int i = 0; i < zoneCount; i++) {
            mStandaloneColors[i] = Math.max(0, Math.min(4095,
                    Math.round(mStandaloneState[i] * maxBrightness * fadeMultiplier)));
        }
        return mStandaloneColors;
    }

    private void ensureZoneCache(int zoneCount) {
        if (mCachedDeviceType == mDeviceType && mCachedZoneCount == zoneCount) return;

        mCachedDeviceType = mDeviceType;
        mCachedZoneCount = zoneCount;
        mMatrixWidth = DeviceProfile.getMatrixWidth(mDeviceType);
        mMatrixHeight = DeviceProfile.getMatrixHeight(mDeviceType);
        mEffectiveIndices = new int[zoneCount];
        mZonePositions = new float[zoneCount];
        mRainParameters = new double[zoneCount];
        mRainPeriods = new double[zoneCount];
        mRainOffsets = new double[zoneCount];

        for (int i = 0; i < zoneCount; i++) {
            int effectiveIndex = i;
            if (mDeviceType == DeviceProfile.DEVICE_NP3A) {
                if (i >= 20 && i <= 30) {
                    effectiveIndex = i - 20;
                } else if (i >= 31 && i <= 35) {
                    effectiveIndex = 11 + (i - 31);
                } else if (i <= 19) {
                    effectiveIndex = 16 + i;
                }
            }
            mEffectiveIndices[i] = effectiveIndex;
            mZonePositions[i] = (float) effectiveIndex / zoneCount;

            if (mMatrixWidth > 0) {
                int x = effectiveIndex % mMatrixWidth;
                double hash = Math.abs(Math.sin(x * 12.9898 + 7.23)) * 43758.5453;
                hash -= (long) hash;
                mRainParameters[i] = 0.1 + hash * 0.2;
            } else {
                double hash = Math.abs(Math.sin(effectiveIndex * 43.123 + 9.87)) * 1234.56;
                hash -= (long) hash;
                mRainParameters[i] = hash;
                mRainPeriods[i] = 2500.0 + hash * 4500.0;
                mRainOffsets[i] = hash * 10000.0;
            }
        }
    }

    private float getIntensity(
            String pattern,
            int index,
            int zoneSlot,
            double frameTime,
            float frameCenter,
            float sharedValue,
            float position,
            double animationTimeMs) {
        return switch (pattern) {
            case "static" -> 1f;
            case "pulse" -> sharedValue;
            case "wave" ->
                    (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (frameTime - position)));
            case "scanner" -> {
                float distance = Math.abs(position - frameCenter);
                yield (float) Math.exp(-distance * distance * 40.0);
            }
            case "zebra" -> index % 2 == 0
                    ? (float) (0.5 + 0.5 * sharedValue)
                    : (float) (0.5 - 0.5 * sharedValue);
            case "heartbeat" -> sharedValue;
            case "orbit" -> {
                float distance = Math.abs(position - frameCenter);
                if (distance > 0.5f) distance = 1f - distance;
                yield (float) Math.exp(-distance * distance * 80.0);
            }
            case "rain" -> getRainIntensity(zoneSlot, index, animationTimeMs);
            default -> (float) (0.5 + 0.5 * Math.sin(
                    2.0 * Math.PI * (frameTime + index * 0.02f) - Math.PI / 2.0));
        };
    }

    private float getRainIntensity(int zoneSlot, int effectiveIndex, double animationTimeMs) {
        if (mMatrixWidth > 0) {
            int y = effectiveIndex / mMatrixWidth;
            double dropY = animationTimeMs * mRainParameters[zoneSlot] * 0.05
                    % (mMatrixHeight + 20);
            double distance = y - dropY;
            return distance <= 0 && distance > -8
                    ? (float) Math.pow((8.0 + distance) / 8.0, 2.0)
                    : 0f;
        }

        double time = (animationTimeMs + mRainOffsets[zoneSlot]) % mRainPeriods[zoneSlot]
                / mRainPeriods[zoneSlot];
        return time < 0.15 ? (float) Math.pow(Math.sin(Math.PI * time / 0.15), 3.0) : 0f;
    }
}
