package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.Nullable;

import java.util.Objects;

public final class ContinuousHapticEngine {

    private static final long UPDATE_INTERVAL_MS = 70L;
    private static final int MAX_AMPLITUDE = 255;

    private final Vibrator vibrator;
    private final boolean hasVibrator;
    private final boolean hasAmplitudeControl;

    private float hapticMultiplier = 1.0f;
    private float hapticAudioGain = 1.0f;
    private float hapticGamma = 2.0f;

    private long lastVibrateTime = 0L;

    public ContinuousHapticEngine(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.vibrator = (vm != null) ? vm.getDefaultVibrator() : (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            this.vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        }

        this.hasVibrator = this.vibrator != null && this.vibrator.hasVibrator();
        this.hasAmplitudeControl = Build.VERSION.SDK_INT >= 26 && this.vibrator != null && this.vibrator.hasAmplitudeControl();
    }

    public synchronized void setHapticMultiplier(float multiplier) {
        this.hapticMultiplier = Math.max(0.3f, Math.min(1.5f, multiplier));
    }

    public synchronized void setHapticAudioGain(float gain) {
        this.hapticAudioGain = Math.max(0.1f, gain);
    }

    public synchronized void setHapticGamma(float gamma) {
        this.hapticGamma = Math.max(0.1f, gamma);
    }

    public synchronized float performHapticFeedback(float rawPeak, @Nullable AudioProcessor.VisualizerConfig config) {
        if (!hasVibrator) return 0f;

        long now = SystemClock.elapsedRealtime();
        if ((now - lastVibrateTime) < UPDATE_INTERVAL_MS) {
            return 0f;
        }

        float current = Math.max(0f, rawPeak) * hapticMultiplier * hapticAudioGain;
        float shaped = (float) Math.pow(current, hapticGamma);
        int amplitude = Math.round(Math.min(1.0f, shaped) * MAX_AMPLITUDE);

        if (amplitude < 20) {
            return 0f;
        }

        lastVibrateTime = now;
        executeVibration(amplitude);
        return (float) amplitude / MAX_AMPLITUDE;
    }

    public synchronized void stopHaptics() {
        if (vibrator != null) {
            vibrator.cancel();
        }
        lastVibrateTime = 0L;
    }

    private void executeVibration(int amplitude) {
        try {
            int effectiveAmplitude = hasAmplitudeControl ? amplitude : MAX_AMPLITUDE;

            // Duration is slightly longer than the interval to blend seamlessly without hard stops
            long duration = UPDATE_INTERVAL_MS + 15L;
            VibrationEffect effect = VibrationEffect.createOneShot(duration, effectiveAmplitude);

            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_MEDIA)
                        .build());
            } else if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(duration);
            }
        } catch (Exception ignored) {
            // Fail silently to prevent spamming logs during rapid audio streams
        }
    }
}