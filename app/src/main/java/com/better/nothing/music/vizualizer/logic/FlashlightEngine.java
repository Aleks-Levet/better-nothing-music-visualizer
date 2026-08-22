package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.better.nothing.music.vizualizer.model.BeatEngineMode;
import com.better.nothing.music.vizualizer.model.TorchMode;

import java.util.Arrays;
import java.util.Objects;

/**
 * Flashlight controller using the centralized fftraw variable.
 */
public final class FlashlightEngine {

    private static final String TAG = "FlashlightEngine";
    private static final long MIN_RESUBMIT_INTERVAL_MS = 8L;
    private static final int BEAT_PATTERN_STEPS = 24;

    private final CameraManager cameraManager;
    private String cameraId;
    private boolean hasTorchStrength;
    private int maxTorchStrength = 1;
    private Integer spoofIntensityLevels = null;

    private TorchMode torchMode = TorchMode.AMPLITUDE;
    private BeatEngineMode beatEngineMode = BeatEngineMode.SMOOTH;
    private int pulseDurationMs = 40;

    private float amplitudeThresholdOrMultiplier = 0.15f;
    private float flashlightBeatSpeedMs = 90f;
    private float flashlightBeatGamma = 8.0f;
    private int userMaxIntensity = -1;

    private final BeatDetector beatDetector = new BeatDetector();

    private long beatFlashStartMs = 0L;
    private long beatFlashDurationMs = 90L;

    private int lastLevel = -1;
    private long lastSubmitMs = 0L;
    private boolean torchActive = false;
    private float smoothedIntensity = 0f;
    private float prevTarget = 0f;

    private boolean isBeatTriggeredThisFrame = false;

    public FlashlightEngine(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.cameraManager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        initCamera();
    }

    public static int detectTorchIntensityLevels(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        CameraManager manager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return 1;
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    int max = readTorchStrengthLevel(chars);
                    return Math.max(1, max);
                }
            }
        } catch (CameraAccessException e) {
            Log.w(TAG, "Failed to detect torch intensity levels", e);
        }
        return 1;
    }

    public synchronized int getTorchIntensityLevels() {
        if (spoofIntensityLevels != null) return spoofIntensityLevels;
        return hasTorchStrength ? Math.max(1, maxTorchStrength) : 1;
    }

    public synchronized void setSpoofIntensityLevels(Integer levels) {
        this.spoofIntensityLevels = levels;
    }

    public synchronized int getCurrentLevel() {
        if (spoofIntensityLevels != null) return lastLevel;
        if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                return cameraManager.getTorchStrengthLevel(cameraId);
            } catch (Exception ignored) {}
        }
        return lastLevel;
    }

    public synchronized boolean hasVariableTorchStrength() {
        return getTorchIntensityLevels() > 1;
    }

    private void initCamera() {
        if (cameraManager == null) return;
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                if (Boolean.TRUE.equals(hasFlash) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    int max = readTorchStrengthLevel(chars);
                    if (max > 1) {
                        hasTorchStrength = true;
                        maxTorchStrength = max;
                    }
                    return;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to init camera", e);
        }
    }

    private static int readTorchStrengthLevel(@Nullable CameraCharacteristics chars) {
        if (chars == null) return 1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                Integer max = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                if (max != null && max > 0) return max;
            } catch (Exception ignored) {}
        }

        // Reflection fallback for older SDKs or non-standard vendor implementations
        try {
            String[] keys = {"FLASH_TORCH_STRENGTH_MAX_LEVEL", "FLASH_INFO_STRENGTH_MAX_LEVEL", "FLASH_INFO_STRENGTH_MAXIMUM_LEVEL"};
            for (String keyName : keys) {
                try {
                    Object field = CameraCharacteristics.class.getField(keyName).get(null);
                    if (field instanceof CameraCharacteristics.Key) {
                        @SuppressWarnings("unchecked")
                        CameraCharacteristics.Key<Integer> key = (CameraCharacteristics.Key<Integer>) field;
                        Integer max = chars.get(key);
                        if (max != null && max > 0) return max;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    public synchronized void setFlashlightThreshold(float threshold) {
        this.amplitudeThresholdOrMultiplier = Math.max(0f, threshold);
    }

    public synchronized void setTorchMode(TorchMode mode) {
        this.torchMode = mode;
        if (mode == TorchMode.BEAT_DETECTION) resetBeatDetection();
        else beatFlashStartMs = 0L;
    }

    public synchronized void setFlashlightBeatSensitivity(float sensitivity) {
        beatDetector.setSensitivity(Math.max(0.3f, Math.min(6.0f, sensitivity)));
    }

    public synchronized void setBeatEngineMode(BeatEngineMode mode) {
        this.beatEngineMode = mode;
    }

    public synchronized void setPulseDurationMs(int duration) {
        this.pulseDurationMs = duration;
    }

    public synchronized void setFlashlightSpeedMs(float speedMs) {
        float min = hasVariableTorchStrength() ? 150f : 20f;
        float max = hasVariableTorchStrength() ? 700f : 150f;
        this.flashlightBeatSpeedMs = clamp(speedMs, min, max);
        this.beatFlashDurationMs = (long) flashlightBeatSpeedMs;
    }

    public synchronized void setFlashlightBeatGamma(float gamma) {
        this.flashlightBeatGamma = Math.max(4.0f, Math.min(15.0f, gamma));
    }

    public synchronized void setUserMaxIntensity(int intensity) {
        this.userMaxIntensity = intensity;
    }

    public synchronized int getUserMaxIntensity() {
        return userMaxIntensity > 0 ? userMaxIntensity : getTorchIntensityLevels();
    }

    public synchronized void performFlashlightFeedback(
            float rawPeak,
            @Nullable AudioProcessor.VisualizerConfig config,
            int[] fftraw,
            int logBinLo,
            int logBinHi
    ) {
        if (cameraId == null && spoofIntensityLevels == null) return;
        if (torchMode == TorchMode.BEAT_DETECTION) {
            performBeatDetection(fftraw, logBinLo, logBinHi);
            return;
        }
        performAmplitudeFeedback(rawPeak);
    }

    private void performAmplitudeFeedback(float rawPeak) {
        float rawTarget = clamp(Math.max(0f, rawPeak), 0f, 1.2f);
        float target = (float) Math.pow(rawTarget, 2.2);
        float delta = Math.max(0f, target - prevTarget);
        float boostedTarget = target + delta * 2.5f; 
        prevTarget = target;

        if (boostedTarget > smoothedIntensity) smoothedIntensity = smoothedIntensity * 0.45f + boostedTarget * 0.55f;
        else smoothedIntensity = smoothedIntensity * 0.75f + boostedTarget * 0.25f;

        if (hasVariableTorchStrength()) {
            float threshold = amplitudeThresholdOrMultiplier * 0.5f;
            if (smoothedIntensity < threshold) { stopFlashlightInternal(); return; }
            float normalized = clamp((smoothedIntensity - threshold) / (1.0f - threshold), 0f, 1f);
            int max = getUserMaxIntensity();
            int level = Math.round(normalized * max);
            if (level <= 0) { stopFlashlightInternal(); return; }
            submitTorchLevel(Math.max(1, Math.min(max, level)));
            return;
        }

        if (smoothedIntensity < amplitudeThresholdOrMultiplier) { stopFlashlightInternal(); return; }
        submitTorchLevel(1);
    }

    private synchronized void performBeatDetection(int[] fftraw, int logBinLo, int logBinHi) {
        isBeatTriggeredThisFrame = false;
        if (beatDetector.detect(fftraw, logBinLo, logBinHi)) {
            triggerBeat();
            isBeatTriggeredThisFrame = true;
        }
        if (beatFlashStartMs != 0L) updateBeatFlashState();
        else stopFlashlightInternal();
    }

    public synchronized boolean isBeatTriggeredThisFrame() {
        return isBeatTriggeredThisFrame;
    }

    public synchronized void triggerBeat() {
        beatFlashStartMs = SystemClock.elapsedRealtime();
        beatFlashDurationMs = (beatEngineMode == BeatEngineMode.SHORT_PULSE) ? pulseDurationMs : 1540L;
        updateBeatFlashState();
    }

    private void updateBeatFlashState() {
        long now = SystemClock.elapsedRealtime();
        long elapsed = now - beatFlashStartMs;
        if (elapsed >= beatFlashDurationMs) { stopFlashlightInternal(); return; }

        if (beatEngineMode == BeatEngineMode.SHORT_PULSE || !hasVariableTorchStrength()) {
            if (hasVariableTorchStrength()) {
                submitTorchLevel(maxTorchStrength);
            } else {
                submitTorchLevel(1);
            }
            return;
        }

        float sustainMs = 40f;
        float decayMs = 1500f;
        float intensity;
        if (elapsed < sustainMs) {
            intensity = 1.0f;
        } else {
            float x = 1f - ((elapsed - sustainMs) / decayMs);
            intensity = (float) Math.pow(Math.max(0f, Math.min(1f, x)), flashlightBeatGamma);
        }

        if (hasVariableTorchStrength()) {
            int max = getUserMaxIntensity();
            int level = Math.max(1, Math.round(intensity * max));
            submitTorchLevel(Math.min(max, level));
        } else submitTorchLevel(1);
    }

    private void resetBeatDetection() { beatDetector.reset(); }

    public synchronized void stopFlashlight() {
        stopFlashlightInternal();
        beatFlashStartMs = 0L;
    }

    private void submitTorchLevel(int level) {
        final long now = SystemClock.elapsedRealtime();
        boolean intervalPassed = (now - lastSubmitMs) >= MIN_RESUBMIT_INTERVAL_MS;
        int deadzone = (getTorchIntensityLevels() > 1) ? 1 : 0;
        boolean significantChange = Math.abs(level - lastLevel) >= deadzone;
        if (torchActive && !intervalPassed && !significantChange) return;

        if (spoofIntensityLevels != null) {
            torchActive = true;
            lastLevel = level;
            lastSubmitMs = now;
            return;
        }

        if (cameraId == null) return;
        try {
            if (hasVariableTorchStrength()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        cameraManager.turnOnTorchWithStrengthLevel(cameraId, Math.max(1, level));
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Failed to set torch strength, falling back to binary", e);
                        hasTorchStrength = false;
                        maxTorchStrength = 1;
                        cameraManager.setTorchMode(cameraId, true);
                    }
                } else cameraManager.setTorchMode(cameraId, true);
            } else cameraManager.setTorchMode(cameraId, true);
            torchActive = true;
            lastLevel = level;
            lastSubmitMs = now;
        } catch (CameraAccessException | SecurityException e) {
            torchActive = false;
        } catch (Throwable t) {
            try {
                cameraManager.setTorchMode(cameraId, true);
                torchActive = true;
            } catch (Exception ignored) {
            }
        }
    }

    private void stopFlashlightInternal() {
        if (!torchActive) { lastLevel = 0; smoothedIntensity = 0f; prevTarget = 0f; return; }
        if (spoofIntensityLevels == null) {
            try { if (cameraId != null) cameraManager.setTorchMode(cameraId, false); }
            catch (CameraAccessException | IllegalArgumentException | SecurityException e) {}
        }
        torchActive = false;
        lastLevel = 0;
        smoothedIntensity = 0f;
        prevTarget = 0f;
        lastSubmitMs = SystemClock.elapsedRealtime();
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
