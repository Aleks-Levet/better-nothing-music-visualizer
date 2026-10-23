package com.better.nothing.music.vizualizer.logic;

import android.os.SystemClock;

import com.better.nothing.music.vizualizer.model.DeviceProfile;

import java.util.Arrays;

public final class IdleBreathingRenderer {
    private static final long BREATH_DELAY_MS = 2500L;
    private static final int PATTERN_STATIC = 0;
    private static final int PATTERN_PULSE = 1;
    private static final int PATTERN_WAVE = 2;
    private static final int PATTERN_SCANNER = 3;
    private static final int PATTERN_ZEBRA = 4;
    private static final int PATTERN_HEARTBEAT = 5;
    private static final int PATTERN_ORBIT = 6;
    private static final int PATTERN_RAIN = 7;
    private static final int PATTERN_DEFAULT = 8;

    private static final float PI = 3.1415927f;
    private static final float TWO_PI = 6.2831855f;
    private static final float RECIPROCAL_3000 = 1f / 3000f;
    private static final float RECIPROCAL_2500 = 1f / 2500f;
    private static final float RECIPROCAL_2000 = 1f / 2000f;
    private static final float RECIPROCAL_1500 = 1f / 1500f;
    private static final float RECIPROCAL_300 = 1f / 300f;
    private static final float RECIPROCAL_0_12 = 1f / 0.12f;
    private static final float RECIPROCAL_0_15 = 1f / 0.15f;
    private static final float RECIPROCAL_4500 = 1f / 4500f;
    private static final float RECIPROCAL_WRAP = 1f / 60000f;
    private static final float ANIMATION_TIME_WRAP_MS = 60000f;
    private static final float[] EMPTY_FLOATS = new float[0];
    private static final int[] EMPTY_COLORS = new int[0];

    private int mDeviceType;
    private boolean mEnabled;
    private int mPattern = PATTERN_PULSE;
    private float mBrightness = 0.4f;
    private float mBackgroundBrightness = 0.02f;
    private float mAnimationSpeed = 1f;
    private long mSilenceStartTimeMs;
    private long mLastFrameMs;
    private float mBreathingEnvelope;
    private float mAnimationTimeMs;
    private float[] mStandaloneState = EMPTY_FLOATS;
    private int[] mStandaloneColors = EMPTY_COLORS;
    private int mPreparedDeviceType = DeviceProfile.DEVICE_UNKNOWN;
    private int mPreparedZoneCount = -1;
    private int mMatrixWidth;
    private int mMatrixHeight;
    private float mMatrixPeriod;
    private float mMatrixPeriodReciprocal;
    private float mZoneReciprocal;
    private int[] mEffectiveIndices = new int[0];
    private int[] mMatrixRows = new int[0];
    private float[] mZonePositions = EMPTY_FLOATS;
    private float[] mRainSpeeds = EMPTY_FLOATS;
    private float[] mRainPeriods = EMPTY_FLOATS;
    private float[] mRainPeriodReciprocals = EMPTY_FLOATS;
    private float[] mRainPhases = EMPTY_FLOATS;

    public IdleBreathingRenderer(int deviceType, boolean enabled) {
        mDeviceType = deviceType;
        mEnabled = enabled;
        prepareZoneCount(DeviceProfile.getLedCount(deviceType));
    }

    private void applyStatic(float[] state, boolean[] alwaysOnZones, int zoneCount) {
        float breathValue = mBrightness * mBreathingEnvelope;
        if (alwaysOnZones == null) {
            for (int i = 0; i < zoneCount; i++) {
                if (state[i] < breathValue) state[i] = breathValue;
            }
            return;
        }

        int alwaysOnCount = Math.min(zoneCount, alwaysOnZones.length);
        float inverseBreathing = 1f - mBreathingEnvelope;
        for (int i = 0; i < alwaysOnCount; i++) {
            if (alwaysOnZones[i]) {
                state[i] = state[i] * inverseBreathing
                        + breathValue * mBreathingEnvelope;
            } else if (state[i] < breathValue) {
                state[i] = breathValue;
            }
        }
        for (int i = alwaysOnCount; i < zoneCount; i++) {
            if (state[i] < breathValue) state[i] = breathValue;
        }
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) mSilenceStartTimeMs = 0;
    }

    public void setDeviceType(int deviceType) {
        if (mDeviceType == deviceType) return;
        mDeviceType = deviceType;
        prepareZoneCount(DeviceProfile.getLedCount(deviceType));
    }

    public void prepareZoneCount(int zoneCount) {
        int count = Math.max(0, zoneCount);
        if (mPreparedDeviceType == mDeviceType && mPreparedZoneCount == count) return;

        mPreparedDeviceType = mDeviceType;
        mPreparedZoneCount = count;
        mMatrixWidth = DeviceProfile.getMatrixWidth(mDeviceType);
        mMatrixHeight = DeviceProfile.getMatrixHeight(mDeviceType);
        mMatrixPeriod = mMatrixHeight + 20f;
        mMatrixPeriodReciprocal = mMatrixPeriod > 0f ? 1f / mMatrixPeriod : 0f;
        mZoneReciprocal = count > 0 ? 1f / count : 0f;
        mEffectiveIndices = new int[count];
        mMatrixRows = new int[count];
        mZonePositions = new float[count];
        mRainSpeeds = new float[count];
        mRainPeriods = new float[count];
        mRainPeriodReciprocals = new float[count];
        mRainPhases = new float[count];
        mStandaloneState = new float[count];
        mStandaloneColors = new int[count];

        for (int i = 0; i < count; i++) {
            int effectiveIndex = i;
            if (mDeviceType == DeviceProfile.DEVICE_NP3A) {
                if (i >= 20 && i <= 30) {
                    effectiveIndex = i - 20;
                } else if (i >= 31 && i <= 35) {
                    effectiveIndex = 11 + i - 31;
                } else if (i <= 19) {
                    effectiveIndex = 16 + i;
                }
            }

            mEffectiveIndices[i] = effectiveIndex;
            mZonePositions[i] = effectiveIndex * mZoneReciprocal;
            if (mMatrixWidth > 0) {
                int x = effectiveIndex % mMatrixWidth;
                mMatrixRows[i] = effectiveIndex / mMatrixWidth;
                float hash = abs(sin(x * 12.9898f + 7.23f) * 43758.5453f);
                hash -= (int) hash;
                mRainSpeeds[i] = 0.1f + hash * 0.2f;
                mRainPhases[i] = hash * (mMatrixHeight + 20);
            } else {
                float hash = abs(sin(effectiveIndex * 43.123f + 9.87f) * 1234.56f);
                hash -= (int) hash;
                float period = 2500f + hash * 4500f;
                mRainPeriods[i] = period;
                mRainPeriodReciprocals[i] = 1f / period;
                mRainPhases[i] = wrapPositive(hash * 10000f, period, mRainPeriodReciprocals[i]);
            }
        }
    }

    public void start(long nowMs) {
        mEnabled = true;
        mSilenceStartTimeMs = nowMs - BREATH_DELAY_MS - 1L;
        mLastFrameMs = nowMs;
        mBreathingEnvelope = 1f;
        mAnimationTimeMs = 0f;
    }

    public void setPattern(String pattern) {
        if ("static".equals(pattern)) {
            mPattern = PATTERN_STATIC;
        } else if ("pulse".equals(pattern)) {
            mPattern = PATTERN_PULSE;
        } else if ("wave".equals(pattern)) {
            mPattern = PATTERN_WAVE;
        } else if ("scanner".equals(pattern)) {
            mPattern = PATTERN_SCANNER;
        } else if ("zebra".equals(pattern)) {
            mPattern = PATTERN_ZEBRA;
        } else if ("heartbeat".equals(pattern)) {
            mPattern = PATTERN_HEARTBEAT;
        } else if ("orbit".equals(pattern)) {
            mPattern = PATTERN_ORBIT;
        } else if ("rain".equals(pattern)) {
            mPattern = PATTERN_RAIN;
        } else {
            mPattern = PATTERN_DEFAULT;
        }
    }

    public void setBrightness(float brightness) {
        mBrightness = brightness;
    }

    public void setBackgroundBrightness(float brightness) {
        mBackgroundBrightness = brightness;
    }

    public void setAnimationSpeed(float speed) {
        mAnimationSpeed = Math.max(0.5f, Math.min(2f, speed));
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
        mAnimationTimeMs = 0f;
        resetRainPhases();
        Arrays.fill(mStandaloneState, 0f);
        Arrays.fill(mStandaloneColors, 0);
    }

    public void apply(float[] state, boolean[] alwaysOnZones, long nowMs, boolean isSilent) {
        int zoneCount = state.length;
        if (zoneCount == 0) return;
        if (mPreparedDeviceType != mDeviceType || mPreparedZoneCount != zoneCount) {
            throw new IllegalStateException("Call prepareZoneCount before applying this zone count");
        }

        long deltaMs = mLastFrameMs > 0 ? nowMs - mLastFrameMs : 16L;
        mLastFrameMs = nowMs;
        float deltaTimeMs = (float) Math.max(0L, deltaMs) * mAnimationSpeed;
        if (mPattern != PATTERN_STATIC) {
            mAnimationTimeMs = wrapPositive(
                    mAnimationTimeMs + deltaTimeMs, ANIMATION_TIME_WRAP_MS, RECIPROCAL_WRAP);
        }
        if (isSilent) {
            if (mSilenceStartTimeMs == 0) mSilenceStartTimeMs = nowMs;
        } else {
            mSilenceStartTimeMs = 0;
        }

        long silenceDurationMs = mSilenceStartTimeMs > 0 ? nowMs - mSilenceStartTimeMs : 0L;
        float targetEnvelope = mEnabled && silenceDurationMs > BREATH_DELAY_MS ? 1f : 0f;
        if (mBreathingEnvelope < targetEnvelope) {
            mBreathingEnvelope += deltaTimeMs * RECIPROCAL_2500;
            if (mBreathingEnvelope > targetEnvelope) mBreathingEnvelope = targetEnvelope;
        } else if (mBreathingEnvelope > targetEnvelope) {
            mBreathingEnvelope -= deltaTimeMs * RECIPROCAL_300;
            if (mBreathingEnvelope < targetEnvelope) mBreathingEnvelope = targetEnvelope;
        }

        if (mBreathingEnvelope <= 0.01f) {
            if (mPattern == PATTERN_RAIN) advanceRainPhases(deltaTimeMs);
            return;
        }

        if (mPattern == PATTERN_STATIC) {
            applyStatic(state, alwaysOnZones, zoneCount);
            return;
        }

        float animationTimeMs = mAnimationTimeMs;
        float frameTime = 0f;
        float sharedValue = 0f;
        float frameCenter = 0f;
        switch (mPattern) {
            case PATTERN_PULSE -> {
                frameTime = cycleProgress(animationTimeMs, RECIPROCAL_3000);
                sharedValue = 0.5f + 0.5f * sin(TWO_PI * frameTime - PI * 0.5f);
            }
            case PATTERN_WAVE -> frameTime = cycleProgress(animationTimeMs, RECIPROCAL_2000);
            case PATTERN_SCANNER -> {
                frameTime = cycleProgress(animationTimeMs, RECIPROCAL_2500);
                frameCenter = 0.5f + 0.5f * sin(TWO_PI * frameTime);
            }
            case PATTERN_ZEBRA -> {
                frameTime = cycleProgress(animationTimeMs, RECIPROCAL_1500);
                sharedValue = sin(TWO_PI * frameTime);
            }
            case PATTERN_HEARTBEAT -> {
                frameTime = cycleProgress(animationTimeMs, RECIPROCAL_2000);
                if (frameTime < 0.12f) {
                    sharedValue = sin(PI * frameTime * RECIPROCAL_0_12);
                } else if (frameTime > 0.22f && frameTime < 0.34f) {
                    sharedValue = sin(PI * (frameTime - 0.22f) * RECIPROCAL_0_12) * 0.7f;
                }
            }
            case PATTERN_ORBIT -> {
                frameTime = cycleProgress(animationTimeMs, 0.00025f);
                frameCenter = 0.5f + 0.4f * sin(TWO_PI * frameTime);
            }
            case PATTERN_DEFAULT -> frameTime = cycleProgress(animationTimeMs, RECIPROCAL_3000);
            default -> { }
        }

        float breathingEnvelope = mBreathingEnvelope;
        float backgroundBrightness = mBackgroundBrightness;
        float brightnessRange = mBrightness - backgroundBrightness;
        boolean hasFullAlwaysOnArray = alwaysOnZones != null && alwaysOnZones.length >= zoneCount;

        if (alwaysOnZones == null) {
            applyZonesWithoutAlwaysOn(
                    state, 0, zoneCount, mPattern, frameTime, frameCenter, sharedValue,
                    breathingEnvelope, backgroundBrightness, brightnessRange, deltaTimeMs);
        } else if (hasFullAlwaysOnArray) {
            applyZonesWithAlwaysOn(
                    state, 0, zoneCount, alwaysOnZones, mPattern, frameTime,
                    frameCenter, sharedValue, breathingEnvelope, backgroundBrightness,
                    brightnessRange, deltaTimeMs);
        } else {
            int alwaysOnCount = Math.min(zoneCount, alwaysOnZones.length);
            applyZonesWithAlwaysOn(
                    state, 0, alwaysOnCount, alwaysOnZones, mPattern,
                    frameTime, frameCenter, sharedValue, breathingEnvelope,
                    backgroundBrightness, brightnessRange, deltaTimeMs);
            applyZonesWithoutAlwaysOn(
                    state, alwaysOnCount, zoneCount, mPattern, frameTime,
                    frameCenter, sharedValue, breathingEnvelope, backgroundBrightness,
                    brightnessRange, deltaTimeMs);
        }
    }

    private void applyZonesWithoutAlwaysOn(
            float[] state,
            int start,
            int end,
            int pattern,
            float frameTime,
            float frameCenter,
            float sharedValue,
            float breathingEnvelope,
            float backgroundBrightness,
            float brightnessRange,
            float deltaTimeMs) {
        for (int i = start; i < end; i++) {
            int effectiveIndex = mEffectiveIndices[i];
            float position = mZonePositions[i];
            float intensity = getIntensity(
                    pattern, effectiveIndex, i, position,
                    frameTime, frameCenter, sharedValue, deltaTimeMs);
            float breathValue = (backgroundBrightness + intensity * brightnessRange)
                    * breathingEnvelope;

            if (state[i] < breathValue) state[i] = breathValue;
        }
    }

    private void applyZonesWithAlwaysOn(
            float[] state,
            int start,
            int end,
            boolean[] alwaysOnZones,
            int pattern,
            float frameTime,
            float frameCenter,
            float sharedValue,
            float breathingEnvelope,
            float backgroundBrightness,
            float brightnessRange,
            float deltaTimeMs) {
        float inverseBreathing = 1f - breathingEnvelope;
        for (int i = start; i < end; i++) {
            int effectiveIndex = mEffectiveIndices[i];
            float position = mZonePositions[i];
            float intensity = getIntensity(
                    pattern, effectiveIndex, i, position,
                    frameTime, frameCenter, sharedValue, deltaTimeMs);
            float breathValue = (backgroundBrightness + intensity * brightnessRange)
                    * breathingEnvelope;

            if (alwaysOnZones[i]) {
                state[i] = state[i] * inverseBreathing + breathValue * breathingEnvelope;
            } else if (state[i] < breathValue) {
                state[i] = breathValue;
            }
        }
    }

    public int[] renderFrame(int zoneCount, int maxBrightness, long nowMs) {
        return renderFrame(zoneCount, maxBrightness, nowMs, 1f);
    }

    public int[] renderFrame(
            int zoneCount, int maxBrightness, long nowMs, float fadeMultiplier) {
        if (zoneCount <= 0) return EMPTY_COLORS;
        if (zoneCount != mPreparedZoneCount || mStandaloneState.length != zoneCount) {
            throw new IllegalArgumentException("Call prepareZoneCount before rendering this zone count");
        }

        Arrays.fill(mStandaloneState, 0f);
        apply(mStandaloneState, null, nowMs, true);
        float brightnessScale = maxBrightness * fadeMultiplier;
        for (int i = 0; i < zoneCount; i++) {
            int brightness = Math.round(mStandaloneState[i] * brightnessScale);
            mStandaloneColors[i] = brightness < 0 ? 0 : Math.min(4095, brightness);
        }
        return mStandaloneColors;
    }

    private float getIntensity(
            int pattern,
            int effectiveIndex,
            int zoneSlot,
            float position,
            float frameTime,
            float frameCenter,
            float sharedValue,
            float deltaTimeMs) {
        switch (pattern) {
            case PATTERN_STATIC:
                return 1f;
            case PATTERN_PULSE:
                return sharedValue;
            case PATTERN_WAVE:
                return 0.5f + 0.5f * sin(TWO_PI * (frameTime - position));
            case PATTERN_SCANNER: {
                float distance = abs(position - frameCenter);
                return expNegative(distance * distance * 40f);
            }
            case PATTERN_ZEBRA:
                return (effectiveIndex & 1) == 0
                        ? 0.5f + 0.5f * sharedValue
                        : 0.5f - 0.5f * sharedValue;
            case PATTERN_HEARTBEAT:
                return sharedValue;
            case PATTERN_ORBIT: {
                float distance = abs(position - frameCenter);
                if (distance > 0.5f) distance = 1f - distance;
                return expNegative(distance * distance * 80f);
            }
            case PATTERN_RAIN:
                return getRainIntensity(zoneSlot, deltaTimeMs);
            default:
                return 0.5f + 0.5f * sin(
                        TWO_PI * (frameTime + effectiveIndex * 0.02f) - PI * 0.5f);
        }
    }

    private float getRainIntensity(int zoneSlot, float deltaTimeMs) {
        advanceRainPhase(zoneSlot, deltaTimeMs);
        if (mMatrixWidth > 0) {
            int y = mMatrixRows[zoneSlot];
            float dropY = mRainPhases[zoneSlot];
            float distance = y - dropY;
            if (distance <= 0f && distance > -8f) {
                float tail = (8f + distance) * 0.125f;
                return tail * tail;
            }
            return 0f;
        }

        float rainTime = mRainPhases[zoneSlot] * mRainPeriodReciprocals[zoneSlot];
        if (rainTime >= 0.15f) return 0f;
        float wave = sin(PI * rainTime * RECIPROCAL_0_15);
        return wave * wave * wave;
    }

    private void advanceRainPhases(float deltaTimeMs) {
        for (int i = 0; i < mPreparedZoneCount; i++) {
            advanceRainPhase(i, deltaTimeMs);
        }
    }

    private void advanceRainPhase(int zoneSlot, float deltaTimeMs) {
        if (mMatrixWidth > 0) {
            float phase = mRainPhases[zoneSlot]
                    + deltaTimeMs * mRainSpeeds[zoneSlot] * 0.05f;
            mRainPhases[zoneSlot] = wrapPositive(
                    phase, mMatrixPeriod, mMatrixPeriodReciprocal);
        } else {
            float period = mRainPeriods[zoneSlot];
            float phase = mRainPhases[zoneSlot] + deltaTimeMs;
            mRainPhases[zoneSlot] = wrapPositive(
                    phase, period, mRainPeriodReciprocals[zoneSlot]);
        }
    }

    private void resetRainPhases() {
        if (mMatrixWidth > 0) {
            float matrixHeight = mMatrixHeight + 20;
            for (int i = 0; i < mPreparedZoneCount; i++) {
                float hash = (mRainSpeeds[i] - 0.1f) * 5f;
                mRainPhases[i] = hash * matrixHeight;
            }
        } else {
            for (int i = 0; i < mPreparedZoneCount; i++) {
                float hash = (mRainPeriods[i] - 2500f) * RECIPROCAL_4500;
                float offset = hash * 10000f;
                float period = mRainPeriods[i];
                mRainPhases[i] = wrapPositive(
                        offset, period, mRainPeriodReciprocals[i]);
            }
        }
    }

    private static float sin(float value) {
        return (float) Math.sin(value);
    }

    private static float cycleProgress(float timeMs, float reciprocalPeriod) {
        float cycles = timeMs * reciprocalPeriod;
        return cycles - (int) cycles;
    }

    private static float wrapPositive(float value, float period, float reciprocalPeriod) {
        float wrapped = value - (int) (value * reciprocalPeriod) * period;
        if (wrapped < 0f) wrapped += period;
        if (wrapped >= period) wrapped -= period;
        return wrapped;
    }

    private static float expNegative(float value) {
        if (value <= 0f) return 1f;
        if (value >= 80f) return 0f;

        float x = value * (1f / 64f);
        float x2 = x * x;
        float result = 1f - x + x2 * 0.5f - x2 * x * (1f / 6f)
                + x2 * x2 * (1f / 24f);
        result *= result;
        result *= result;
        result *= result;
        result *= result;
        result *= result;
        result *= result;
        return result;
    }

    private static float abs(float value) {
        return value < 0f ? -value : value;
    }
}
