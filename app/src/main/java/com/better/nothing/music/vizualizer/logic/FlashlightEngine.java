package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.better.nothing.music.vizualizer.model.TorchMode;

import java.util.Arrays;
import java.util.Objects;

/**
 * Flashlight controller with bass-amplitude and beat-detection modes.
 */
public final class FlashlightEngine {

    private static final String TAG = "FlashlightEngine";
    private static final float SPECTRUM_GAIN = 12.0f;
    private static final float EPSILON = 0.001f;
    private static final long MIN_RESUBMIT_INTERVAL_MS = 8L;
    private static final int BEAT_PATTERN_STEPS = 24;

    private final CameraManager cameraManager;
    private String cameraId;
    private boolean hasTorchStrength;
    private int maxTorchStrength = 1;
    private int detectedMaxTorchStrength = 1;
    private boolean forceMultiIntensity = false;

    private TorchMode torchMode = TorchMode.AMPLITUDE;

    // In amplitude mode this is a threshold for binary torches and a multiplier for
    // multi-intensity torches. Keeping one slider in the UI makes the behavior easy
    // to explain without branching the screen layout.
    private float amplitudeThresholdOrMultiplier = 0.15f;

    private float flashlightBeatSpeedMs = 90f;

    private final BeatDetector beatDetector = new BeatDetector();
    private final float[] beatPattern = buildBeatPattern();

    private long beatFlashStartMs = 0L;
    private long beatFlashDurationMs = 90L;

    private int lastLevel = -1;
    private long lastSubmitMs = 0L;
    private boolean torchActive = false;
    private float smoothedIntensity = 0f;
    private float prevTarget = 0f;

    public FlashlightEngine(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.cameraManager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        initCamera();
    }

    public static int detectTorchIntensityLevels(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        CameraManager manager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            return 1;
        }

        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                if (!supportsBackTorch(chars)) {
                    continue;
                }

                int max = readTorchStrengthLevel(chars);
                return Math.max(1, max);
            }
        } catch (CameraAccessException e) {
            Log.w(TAG, "Failed to detect torch intensity levels", e);
        }

        return 1;
    }

    public synchronized int getTorchIntensityLevels() {
        return (hasTorchStrength || forceMultiIntensity) ? Math.max(1, maxTorchStrength) : 1;
    }

    public synchronized int getCurrentLevel() {
        return lastLevel;
    }

    public synchronized boolean hasVariableTorchStrength() {
        return (hasTorchStrength || forceMultiIntensity) && maxTorchStrength > 1;
    }

    public synchronized void setForceMultiIntensity(boolean force) {
        this.forceMultiIntensity = force;
        if (force) {
            if (maxTorchStrength <= 1) {
                maxTorchStrength = 255;
            }
        } else {
            maxTorchStrength = detectedMaxTorchStrength;
            hasTorchStrength = detectedMaxTorchStrength > 1;
        }
    }

    private void initCamera() {
        if (cameraManager == null) {
            return;
        }

        try {
            String bestCameraId = null;
            int bestMaxStrength = 1;

            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                if (!supportsBackTorch(chars)) {
                    continue;
                }

                int max = readTorchStrengthLevel(chars);
                Log.d(TAG, "Camera " + id + " supports torch strength: " + max);

                if (bestCameraId == null || max > bestMaxStrength) {
                    bestCameraId = id;
                    bestMaxStrength = max;
                }
            }

            if (bestCameraId != null) {
                cameraId = bestCameraId;
                detectedMaxTorchStrength = bestMaxStrength;
                if (bestMaxStrength > 1) {
                    hasTorchStrength = true;
                    maxTorchStrength = bestMaxStrength;
                    Log.d(TAG, "Selected Camera " + cameraId + " with max torch strength: " + bestMaxStrength);
                } else {
                    Log.d(TAG, "Selected Camera " + cameraId + " (binary torch only)");
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to init camera for flashlight", e);
        }
    }

    private static boolean supportsBackTorch(@Nullable CameraCharacteristics chars) {
        if (chars == null) {
            return false;
        }
        Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
        return Boolean.TRUE.equals(hasFlash) && facing != null &&
                (facing == CameraCharacteristics.LENS_FACING_BACK || facing == CameraCharacteristics.LENS_FACING_EXTERNAL);
    }

    @SuppressWarnings("unchecked")
    private static int readTorchStrengthLevel(@Nullable CameraCharacteristics chars) {
        if (chars == null) {
            return 1;
        }

        // Use reflection to support FLASH_INFO_STRENGTH_MAX_LEVEL (API 33+)
        // even when compiling against older SDKs.
        try {
            Object field = CameraCharacteristics.class.getField("FLASH_INFO_STRENGTH_MAX_LEVEL").get(null);
            if (field instanceof CameraCharacteristics.Key) {
                CameraCharacteristics.Key<Integer> key = (CameraCharacteristics.Key<Integer>) field;
                Integer max = chars.get(key);
                if (max != null && max > 0) {
                    return max;
                }
            }
        } catch (Throwable ignored) {
        }

        return 1;
    }

    public synchronized void setFlashlightThreshold(float threshold) {
        this.amplitudeThresholdOrMultiplier = Math.max(0f, threshold);
    }

    public synchronized void setTorchMode(TorchMode mode) {
        this.torchMode = mode;
        if (mode == TorchMode.BEAT_DETECTION) {
            resetBeatDetection();
        } else {
            beatFlashStartMs = 0L;
        }
    }

    public synchronized void setFlashlightBeatSensitivity(float sensitivity) {
        beatDetector.setSensitivity(Math.max(0.3f, Math.min(6.0f, sensitivity)));
    }

    public synchronized void setFlashlightSpeedMs(float speedMs) {
        float min = hasVariableTorchStrength() ? 150f : 20f;
        float max = hasVariableTorchStrength() ? 700f : 150f;
        this.flashlightBeatSpeedMs = clamp(speedMs, min, max);
        this.beatFlashDurationMs = (long) flashlightBeatSpeedMs;
    }

    public synchronized void performFlashlightFeedback(
            float rawPeak,
            @Nullable AudioProcessor.VisualizerConfig config,
            float[] magnitude,
            int logBinLo,
            int logBinHi
    ) {
        if (cameraId == null) {
            return;
        }

        if (torchMode == TorchMode.BEAT_DETECTION) {
            performBeatDetection(magnitude, logBinLo, logBinHi);
            return;
        }

        performAmplitudeFeedback(rawPeak);
    }

    private void performAmplitudeFeedback(float rawPeak) {
        // Balanced gain for better dynamic range
        float rawTarget = clamp(Math.max(0f, rawPeak) * 16.0f, 0f, 1.2f);
        
        // Dynamic Gamma: steeper curve for lower intensities to make hits stand out
        float target = (float) Math.pow(rawTarget, 2.2);

        // Rhythmic boost: Add a derivative component to emphasize the "hits"
        float delta = Math.max(0f, target - prevTarget);
        float boostedTarget = target + delta * 2.5f; 
        prevTarget = target;

        // "Arc" Smoothing: Slow down the attack slightly to create a "build up" swell
        // rather than an instant jump.
        if (boostedTarget > smoothedIntensity) {
            smoothedIntensity = smoothedIntensity * 0.45f + boostedTarget * 0.55f;
        } else {
            // Decay remains smooth
            smoothedIntensity = smoothedIntensity * 0.75f + boostedTarget * 0.25f;
        }

        if (hasVariableTorchStrength()) {
            // Slider acts as a threshold. Above it, we scale to full intensity range.
            float threshold = amplitudeThresholdOrMultiplier * 0.5f; // Scale slider for better control
            if (smoothedIntensity < threshold) {
                stopFlashlightInternal();
                return;
            }
            
            float normalized = clamp((smoothedIntensity - threshold) / (1.0f - threshold), 0f, 1f);
            int level = Math.round(normalized * maxTorchStrength);
            if (level <= 0) {
                stopFlashlightInternal();
                return;
            }
            submitTorchLevel(Math.max(1, Math.min(maxTorchStrength, level)));
            return;
        }

        if (smoothedIntensity < amplitudeThresholdOrMultiplier) {
            stopFlashlightInternal();
            return;
        }

        submitTorchLevel(1);
    }

    private synchronized void performBeatDetection(float[] magnitude, int logBinLo, int logBinHi) {
        if (beatDetector.detect(magnitude, logBinLo, logBinHi)) {
            triggerBeat();
        }

        if (beatFlashStartMs != 0L) {
            updateBeatFlashState();
        } else {
            stopFlashlightInternal();
        }
    }

    public synchronized void triggerBeat() {
        beatFlashStartMs = SystemClock.elapsedRealtime();
        beatFlashDurationMs = (long) flashlightBeatSpeedMs;
        updateBeatFlashState();
    }

    private void updateBeatFlashState() {
        long now = SystemClock.elapsedRealtime();
        long elapsed = now - beatFlashStartMs;

        if (elapsed >= beatFlashDurationMs) {
            stopFlashlightInternal();
            return;
        }

        float progress = clamp(elapsed / (float) Math.max(1L, beatFlashDurationMs), 0f, 1f);
        float intensity = sampleBeatPattern(progress);

        if (hasVariableTorchStrength()) {
            int level = Math.max(1, Math.round(intensity * maxTorchStrength));
            submitTorchLevel(Math.min(maxTorchStrength, level));
        } else {
            submitTorchLevel(1);
        }
    }

    private float sampleBeatPattern(float progress) {
        int index = Math.min(
                BEAT_PATTERN_STEPS - 1,
                Math.max(0, Math.round(progress * (BEAT_PATTERN_STEPS - 1)))
        );
        return beatPattern[index];
    }

    private static float[] buildBeatPattern() {
        float[] pattern = new float[BEAT_PATTERN_STEPS];
        // Create an "Arc" envelope: Attack (20% of duration) -> Decay (80% of duration)
        int attackSteps = Math.max(1, BEAT_PATTERN_STEPS / 5); 
        
        for (int i = 0; i < BEAT_PATTERN_STEPS; i++) {
            if (i < attackSteps) {
                // Smooth quadratic attack for the "build up"
                float progress = i / (float) attackSteps;
                pattern[i] = (float) (1.0 - Math.pow(1.0 - progress, 2.0));
            } else {
                // Exponential decay for the "fade out"
                float progress = (i - attackSteps) / (float) (BEAT_PATTERN_STEPS - 1 - attackSteps);
                pattern[i] = (float) Math.pow(Math.E, -5.0 * progress);
            }
        }
        
        // Ensure seamless start/end
        pattern[0] = 0f;
        pattern[BEAT_PATTERN_STEPS - 1] = 0f;

        return pattern;
    }

    private void resetBeatDetection() {
        beatDetector.reset();
    }

    public synchronized void stopFlashlight() {
        stopFlashlightInternal();
        beatFlashStartMs = 0L;
    }

    private void submitTorchLevel(int level) {
        if (cameraId == null) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        boolean intervalPassed = (now - lastSubmitMs) >= MIN_RESUBMIT_INTERVAL_MS;
        
        // Zero deadzone for maximum smoothness in multi-intensity mode
        int deadzone = (hasVariableTorchStrength()) ? 1 : 0;
        boolean significantChange = Math.abs(level - lastLevel) >= deadzone;

        if (torchActive && !intervalPassed && !significantChange) {
            return;
        }

        if (significantChange) {
            Log.d(TAG, "Submitting torch level: " + level + " / " + maxTorchStrength + (forceMultiIntensity ? " (FORCED)" : ""));
        }

        try {
            if (hasVariableTorchStrength()) {
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                        cameraManager.turnOnTorchWithStrengthLevel(cameraId, Math.max(1, level));
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, "Requested intensity " + level + " failed: " + e.getMessage());
                        // If it fails because of range, try to automatically adjust maxTorchStrength down
                        if (forceMultiIntensity && level > 1) {
                            maxTorchStrength = Math.max(1, level - 1);
                            Log.d(TAG, "Auto-adjusting max forced intensity to: " + maxTorchStrength);
                        }
                        cameraManager.setTorchMode(cameraId, true);
                    }
                } else {
                    cameraManager.setTorchMode(cameraId, true);
                }
            } else {
                cameraManager.setTorchMode(cameraId, true);
            }

            torchActive = true;
            lastLevel = level;
            lastSubmitMs = now;
        } catch (CameraAccessException | SecurityException e) {
            Log.w(TAG, "Failed to set torch level", e);
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
        if (!torchActive) {
            lastLevel = 0;
            smoothedIntensity = 0f;
            prevTarget = 0f;
            return;
        }

        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, false);
            }
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Failed to turn off torch", e);
        }

        torchActive = false;
        lastLevel = 0;
        smoothedIntensity = 0f;
        prevTarget = 0f;
        lastSubmitMs = SystemClock.elapsedRealtime();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
