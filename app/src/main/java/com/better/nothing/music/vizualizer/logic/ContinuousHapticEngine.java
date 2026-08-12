package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Drop-in haptic controller for a visualizer-style amplitude stream.
 */
public final class ContinuousHapticEngine {

    private static final String TAG = "ContinuousHapticEngine";
    private static final int HAPTIC_DURATION_MS = 100;
    private static final long MIN_RESUBMIT_INTERVAL_MS = 20L;
    private static final int MAX_AMPLITUDE = 255;

    private final Vibrator vibrator;

    private float hapticMultiplier = 1.0f;
    private float hapticAudioGain = 1.0f;
    private float hapticGamma = 2.0f;

    private int lastAmplitude = -1;
    private long lastSubmitMs = 0L;

    public ContinuousHapticEngine(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.vibrator = (vm != null) ? vm.getDefaultVibrator() : (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            this.vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    public synchronized void setHapticMultiplier(float multiplier) { this.hapticMultiplier = Math.max(0.3f, Math.min(1.5f, multiplier)); }
    public synchronized void setHapticAudioGain(float gain) { this.hapticAudioGain = Math.max(0.1f, gain); }
    public synchronized void setHapticGamma(float gamma) { this.hapticGamma = Math.max(0.1f, gamma); }

    public synchronized float performHapticFeedback(float rawPeak, @Nullable AudioProcessor.VisualizerConfig config) {
        if (vibrator == null || !vibrator.hasVibrator()) return 0f;

        // Use the peak directly from fftraw (0-1 range). 
        // No extra SPECTRUM_GAIN here as it's already in fftraw.
        float current = Math.max(0f, rawPeak) * hapticMultiplier * hapticAudioGain;
        float shaped = (float) Math.pow(current, hapticGamma);
        int nextAmplitude = Math.round(shaped * MAX_AMPLITUDE);
        if (shaped >= 0.95f) nextAmplitude = MAX_AMPLITUDE;

        if (nextAmplitude <= 0) {
            if (lastAmplitude != 0) submitOneShot(0);
            return 0f;
        }

        final long now = SystemClock.elapsedRealtime();
        if ((now - lastSubmitMs) < MIN_RESUBMIT_INTERVAL_MS) return (float)lastAmplitude / MAX_AMPLITUDE;
        submitOneShot(Math.min(MAX_AMPLITUDE, nextAmplitude));
        return (float)nextAmplitude / MAX_AMPLITUDE;
    }

    public synchronized void stopHaptics() {
        if (vibrator != null) vibrator.cancel();
        lastAmplitude = -1;
        lastSubmitMs = 0L;
    }

    private void submitOneShot(int amplitude) {
        try {
            VibrationEffect effect = VibrationEffect.createOneShot(HAPTIC_DURATION_MS, amplitude);
            vibrator.vibrate(effect);
            lastAmplitude = amplitude;
            lastSubmitMs = SystemClock.elapsedRealtime();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed vibration", e);
        }
    }
}
