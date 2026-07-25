package com.better.nothing.music.vizualizer.service;

import com.better.nothing.music.vizualizer.model.DeviceProfile;
import com.better.nothing.music.vizualizer.model.HapticMode;
import com.better.nothing.music.vizualizer.model.TorchMode;
import com.better.nothing.music.vizualizer.model.AudioRouteInfo;
import com.better.nothing.music.vizualizer.logic.AudioProcessor;
import com.better.nothing.music.vizualizer.logic.GlyphRenderer;
import com.better.nothing.music.vizualizer.logic.AudioDeviceManager;
import com.better.nothing.music.vizualizer.logic.ContinuousHapticEngine;
import com.better.nothing.music.vizualizer.logic.BeatDetectionHapticEngine;
import com.better.nothing.music.vizualizer.logic.FlashlightEngine;
import com.better.nothing.music.vizualizer.ui.MainActivity;
import com.better.nothing.music.vizualizer.util.PermissionTrampolineActivity;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.audiofx.Visualizer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.graphics.PixelFormat;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.better.nothing.music.vizualizer.ui.VisualizerOverlayView;

import com.nothing.ketchum.Common;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphManager;
import com.nothing.ketchum.GlyphMatrixManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public class AudioCaptureService extends Service {

    private static final String TAG = "GlyphViz:Service";
    private static final String CHANNEL_ID = "glyph_viz_channel";
    private static final int NOTIF_ID = 1;
    public enum CaptureSource { INTERNAL, MIC, VIZUALIZER, SHIZUKU }
    private volatile CaptureSource mCaptureSource = CaptureSource.INTERNAL;

    public static final String ACTION_STOP = "com.better.nothing.music.vizualizer.action.STOP";
    public static final String ACTION_START = "com.better.nothing.music.vizualizer.action.START";
    public static final String ACTION_TOGGLE_HAPTICS = "com.better.nothing.music.vizualizer.action.TOGGLE_HAPTICS";
    public static final String ACTION_TOGGLE_TORCH = "com.better.nothing.music.vizualizer.action.TOGGLE_TORCH";
    public static final String ACTION_TOGGLE_GLYPHS = "com.better.nothing.music.vizualizer.action.TOGGLE_GLYPHS";
    public static final String ACTION_SET_SOURCE = "com.better.nothing.music.vizualizer.action.SET_SOURCE";
    public static final String ACTION_PREV_PRESET = "com.better.nothing.music.vizualizer.action.PREV_PRESET";
    public static final String ACTION_NEXT_PRESET = "com.better.nothing.music.vizualizer.action.NEXT_PRESET";

    public static final String EXTRA_SOURCE = "extra_source";
    public static final String EXTRA_PRESET_KEY = "preset_key";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "data";
    public static final float DEFAULT_GAMMA = 2f;

    private static final String PREFS_NAME = "glyph_visualizer_prefs";
    private static final String APP_PREFS_NAME = "viz_prefs";
    private static final String PREF_GAMMA = "gamma";
    private static final String PREF_LATENCY_PREFIX = "latency_device_";
    private static final String PREF_LATENCY_ROUTE_PREFIX = "latency_route_";
    private static final String PREF_LATENCY_PRESETS = "latency_presets";
    private static final int MAX_GLYPH_BRIGHTNESS = 4500;

    private static final String DEFAULT_PRESET_KEY = "np1s";
    private static final String PHONE_MODEL_UNKNOWN = "UNKNOWN";
    private static final String PHONE_MODEL_PHONE1 = "PHONE1";
    private static final String PHONE_MODEL_PHONE2 = "PHONE2";
    private static final String PHONE_MODEL_PHONE2A = "PHONE2A";
    private static final String PHONE_MODEL_PHONE3A = "PHONE3A";
    private static final String PHONE_MODEL_PHONE3 = "PHONE3";
    private static final String PHONE_MODEL_PHONE4A = "PHONE4A";
    private static final String PHONE_MODEL_PHONE4A_PRO = "PHONE4A_PRO";
    private static final String PHONE_MODEL_PHONE4B = "PHONE4B";

    private static final int SAMPLE_RATE = 44100;
    private static final int FPS = 60;
    private static final int HOP = Math.round(SAMPLE_RATE / (float) FPS);

    private static final long MIN_SEND_INTERVAL_MS = 16L;
    private static final long PROJECTION_SETTLE_DELAY_MS = 500L;

    private static volatile boolean sIsRunning = false;
    private static final MutableStateFlow<Boolean> sIsRunningFlow = StateFlowKt.MutableStateFlow(false);
    
    public StateFlow<Boolean> isRunningFlow() {
        return sIsRunningFlow;
    }

    private void setRunning(boolean running) {
        boolean wasRunning = sIsRunning;
        sIsRunning = running;
        sIsRunningFlow.setValue(running);
        requestWidgetRefresh(this);
        if (running && !wasRunning) {
            // Restart idle pulse if it was stopped
            mMainHandler.removeCallbacks(mIdlePulseRunnable);
            mMainHandler.post(mIdlePulseRunnable);
        }
    }
    public static AudioCaptureService sInstance = null;

    private com.better.nothing.music.vizualizer.util.AnalyticsHelper mAnalyticsHelper;
    private final IBinder mBinder = new LocalBinder();
    private final Object mCaptureLock = new Object();
    private final MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.d(TAG, "MediaProjection stopped externally");
            stopCapture();
            stopSelf();
        }
    };
    private final GlyphManager.Callback mGlyphCallback = new GlyphManager.Callback() {
        @Override
        public void onServiceConnected(ComponentName componentName) {
            if (mGM == null) {
                return;
            }

            Log.d(TAG, "Glyph service connected");
            registerGlyphManager();

            try {
                if (!mSessionOpen) {
                    mGM.openSession();
                    mSessionOpen = true;
                }
            } catch (GlyphException e) {
                Log.e(TAG, "Failed to open Glyph session", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSessionOpen = false;
        }
    };

    private final GlyphMatrixManager.Callback mGlyphMatrixCallback = new GlyphMatrixManager.Callback() {
        @Override
        public void onServiceConnected(ComponentName componentName) {
            if (mGMM == null) {
                return;
            }
            Log.d(TAG, "Glyph Matrix service connected");
            registerGlyphMatrixManager();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    private void registerGlyphManager() {
        if (mGM == null || mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) {
            Log.w(TAG, "registerGlyphManager: GM is null or device unknown: " + mSelectedDevice);
            return;
        }
        String deviceStr = switch (mSelectedDevice) {
            case DeviceProfile.DEVICE_NP1 -> Glyph.DEVICE_20111;
            case DeviceProfile.DEVICE_NP2 -> Glyph.DEVICE_22111;
            case DeviceProfile.DEVICE_NP2A -> Glyph.DEVICE_23111;
            case DeviceProfile.DEVICE_NP3A -> Glyph.DEVICE_24111;
            case DeviceProfile.DEVICE_NP4A -> Glyph.DEVICE_25111;
            case DeviceProfile.DEVICE_NP4APRO -> Glyph.DEVICE_25111p;
            case DeviceProfile.DEVICE_NP3 -> Glyph.DEVICE_23112;
            case DeviceProfile.DEVICE_NP4B -> "26111";
            default -> Glyph.DEVICE_25111;
        };
        Log.d(TAG, "Registering GlyphManager with device: " + deviceStr);
        mGM.register(deviceStr);
    }

    private void registerGlyphMatrixManager() {
        if (mGMM == null || mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return;
        if (mSelectedDevice == DeviceProfile.DEVICE_NP3) {
            mGMM.register(Glyph.DEVICE_23112);
        } else if (mSelectedDevice == DeviceProfile.DEVICE_NP4APRO) {
            mGMM.register(Glyph.DEVICE_25111p);
        }
    }

    private HandlerThread mWorkerThread;
    private Handler mWorkerHandler;
    private AudioManager mAudioManager;

    private GlyphManager mGM;
    private GlyphMatrixManager mGMM;
    private volatile boolean mSessionOpen = false;

    private MediaProjection mProjection;
    private AudioRecord mAudioRecord;
    private Visualizer mVisualizer;
    private final ArrayDeque<PendingFrame> mVisualizerPendingFrames = new ArrayDeque<>();
    private ExecutorService mCaptureExecutor;
    private volatile boolean mCapturing = false;

    private volatile AudioProcessor.VisualizerConfig mVisualizerConfig;
    private String mPresetKey = DEFAULT_PRESET_KEY;
    private String mDetectedPhoneModel = PHONE_MODEL_UNKNOWN;
    private List<String> mAvailablePresetKeys = Collections.emptyList();
    private int mSelectedDevice = DeviceProfile.DEVICE_UNKNOWN;
    private volatile int mLatencyCompensationMs = 0;
    private final AtomicInteger mLatencySettingsVersion = new AtomicInteger(0);
    private final AtomicInteger mPresetConfigVersion = new AtomicInteger(0);
    private final AtomicInteger mHapticSettingsVersion = new AtomicInteger(0);
    private volatile float mGamma = DEFAULT_GAMMA;
    private volatile int mMaxBrightness = 4095;

    private boolean mIdleBreathingEnabled = false;
    private boolean mDisableGlyphsWhenSilent = false;

    private boolean mOverlayEnabled = false;
    private boolean mLensVisualizerEnabled = false;
    public volatile float mLensVisualizerRadius = 40f;
    public volatile float mLensVisualizerX = 0.5f;
    public volatile float mLensVisualizerY = 0.05f;
    public volatile float mLensVisualizerBarWidth = 3f;
    public volatile float mLensVisualizerMaxHeight = 20f;
    public volatile int mLensVisualizerBarCount = 24;
    public volatile float mLensVisualizerSensitivity = 1.0f;

    public void setLensVisualizerEnabled(boolean enabled) {
        mLensVisualizerEnabled = enabled;
        updateOverlayVisibility();
    }

    public void setLensVisualizerRadius(float radius) {
        mLensVisualizerRadius = radius;
        // Broadcast to VisualizerService if running
    }

    public void setLensVisualizerX(float x) { mLensVisualizerX = x; }
    public void setLensVisualizerY(float y) { mLensVisualizerY = y; }
    public void setLensVisualizerBarWidth(float width) { mLensVisualizerBarWidth = width; }
    public void setLensVisualizerMaxHeight(float height) { mLensVisualizerMaxHeight = height; }
    public void setLensVisualizerBarCount(int count) { mLensVisualizerBarCount = count; }
    public void setLensVisualizerSensitivity(float sensitivity) { mLensVisualizerSensitivity = sensitivity; }
    private int mOverlayWidth = 120;
    private int mOverlayHeight = 12;
    private int mOverlayHeightBottom = 12;
    private int mOverlayYOffset = 2;
    private float mOverlaySensitivity = 1.0f;
    private float mOverlaySensitivityBottom = 1.0f;
    private boolean mOverlayTopEnabled = true;
    private boolean mOverlayBottomEnabled = false;
    private int mOverlayColor = android.graphics.Color.WHITE;
    private WindowManager mWindowManager;
    private VisualizerOverlayView mOverlayView;

    private volatile boolean mHapticEnabled = false;
    private volatile HapticMode mHapticMode = HapticMode.BASS_TO_AMPLITUDE;
    private volatile float mHapticMinHz = 60;
    private volatile float mHapticMaxHz = 250;
    private volatile AudioProcessor.FrequencyRange mHapticRange;
    
    private volatile float mHapticAudioGain = 1.0f;
    private volatile float mHapticBeatSensitivity = 1.0f;
    private volatile float mHapticBeatGamma = 8.0f;

    private volatile boolean mFlashlightEnabled = false;
    private volatile TorchMode mFlashlightMode = TorchMode.AMPLITUDE;
    private volatile float mFlashlightMinHz = 60;
    private volatile float mFlashlightMaxHz = 250;
    private volatile AudioProcessor.FrequencyRange mFlashlightRange;
    private volatile float mFlashlightThreshold = 0.15f;
    private volatile float mFlashlightBeatSensitivity = 1.0f;
    private volatile float mFlashlightSpeedMs = 90f;
    private volatile int mFlashlightIntensityLevels = 1;

    private ContinuousHapticEngine mContinuousHapticEngine;
    private BeatDetectionHapticEngine mBeatDetectionEngine;
    private FlashlightEngine mFlashlightEngine;

    private AudioProcessor mAudioProcessor;
    private GlyphRenderer mGlyphRenderer;
    private long mLastSendMs = 0L;
    private long mCaptureStartTimeMs = 0L;
    private float[] mLatestMagnitudes = new float[0];
    private final Object mFftLock = new Object();

    public float[] getLatestMagnitudes() {
        synchronized (mFftLock) {
            return mLatestMagnitudes;
        }
    }

    public long getCaptureDurationMs() {
        if (!sIsRunning || mCaptureStartTimeMs == 0) return 0;
        return SystemClock.elapsedRealtime() - mCaptureStartTimeMs;
    }
    private long mLastAudioActivityMs = 0L;
    private long mLastNotifUpdateMs = 0L;
    private final Handler mMainHandler = new Handler(android.os.Looper.getMainLooper());
    private final Runnable mIdlePulseRunnable = new Runnable() {
        @Override
        public void run() {
            if (sIsRunning) {
                long now = SystemClock.elapsedRealtime();

                if (now - mLastNotifUpdateMs >= 1000) {
                    refreshNotification();
                    mLastNotifUpdateMs = now;
                }

                if (mCaptureSource == CaptureSource.VIZUALIZER) {
                    synchronized (mVisualizerPendingFrames) {
                        dispatchDueFrames(mVisualizerPendingFrames);
                    }
                    // For built-in visualizer, force 60 FPS updates to ensure smooth decay even if data rate is low (e.g. 20 FPS)
                    if (now - mLastSendMs >= 16 && mVisualizerConfig != null) {
                        processFrame(new float[0], 0f, mVisualizerConfig, mPresetConfigVersion.get());
                    }
                } else if (mIdleBreathingEnabled && mSessionOpen && mVisualizerConfig != null) {
                    // If it's been more than 100ms since the last audio frame, manually trigger a frame for breathing
                    if (now - mLastAudioActivityMs > 100) {
                        processFrame(new float[0], 0f, mVisualizerConfig, mPresetConfigVersion.get());
                    }
                }

                mMainHandler.postDelayed(this, 16); // 60fps for engine drive and breathing
            }
        }
    };

    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            refreshLatencyForCurrentAudioRoute();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            refreshLatencyForCurrentAudioRoute();
        }
    };

    private void applyEffectiveMaxBrightness() {
        if (mGlyphRenderer == null) return;
        mGlyphRenderer.setMaxBrightness(mMaxBrightness);
    }

    private static final class PendingFrame {
        final float[] uniqueMagnitudes;
        final float[] magnitude;
        final float hapticPeak;
        final float flashlightPeak;
        final AudioProcessor.VisualizerConfig config;
        final int configVersion;
        final long dueAtMs;

        PendingFrame(float[] uniqueMagnitudes, float[] magnitude, float hapticPeak, float flashlightPeak, AudioProcessor.VisualizerConfig config, int configVersion, long dueAtMs) {
            this.uniqueMagnitudes = uniqueMagnitudes;
            this.magnitude = magnitude;
            this.hapticPeak = hapticPeak;
            this.flashlightPeak = flashlightPeak;
            this.config = config;
            this.configVersion = configVersion;
            this.dueAtMs = dueAtMs;
        }
    }

    public static final class PresetInfo {
        public final String key;
        public final String description;

        public PresetInfo(String key, String description) {
            this.key = key;
            this.description = description;
        }
    }

    public class LocalBinder extends Binder {
        public AudioCaptureService getService() {
            return AudioCaptureService.this;
        }
    }

    private AudioDeviceManager mAudioDeviceManager;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mAnalyticsHelper = new com.better.nothing.music.vizualizer.util.AnalyticsHelper(this);
        mAnalyticsHelper.logEvent("service_created", null);

        mWorkerThread = new HandlerThread("GlyphVizWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mWorkerThread.start();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mWorkerHandler = Handler.createAsync(mWorkerThread.getLooper());
        } else {
            mWorkerHandler = new Handler(mWorkerThread.getLooper());
        }
        mAudioManager = getSystemService(AudioManager.class);
        if (mAudioManager != null) {
            mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, mWorkerHandler);
        }

        if (hasHapticMotor(this)) {
            mContinuousHapticEngine = new ContinuousHapticEngine(this);
            mBeatDetectionEngine = new BeatDetectionHapticEngine(this);
        }
        if (hasFlashlight(this)) {
            mFlashlightEngine = new FlashlightEngine(this);
        }

        mAudioProcessor = new AudioProcessor();
        mAudioDeviceManager = new AudioDeviceManager(this, this::refreshLatencyForCurrentAudioRoute);

        mSelectedDevice = DeviceProfile.detectDevice();
        if (mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) {
            // Default to NP2 as a safe bet for modern Nothing devices if detection fails
            // This ensures we at least try to initialize the Glyph Manager
            mSelectedDevice = DeviceProfile.DEVICE_NP2;
        }
        mLatencyCompensationMs = loadLatencyCompensationMs(this, mSelectedDevice);
        mGamma = loadGamma(this);

        SharedPreferences appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        mMaxBrightness = clampGlyphBrightness(appPrefs.getInt("max_brightness", MAX_GLYPH_BRIGHTNESS));
        mIdleBreathingEnabled = appPrefs.getBoolean("idle_breathing_enabled", false);
        mDisableGlyphsWhenSilent = appPrefs.getBoolean("disable_glyphs_when_silent", false);
        mOverlayEnabled = appPrefs.getBoolean("overlay_enabled", false);
        mOverlayWidth = appPrefs.getInt("overlay_width", 120);
        mOverlayHeight = appPrefs.getInt("overlay_height", 12);
        mOverlayHeightBottom = appPrefs.getInt("overlay_height_bottom", 12);
        mOverlayYOffset = appPrefs.getInt("overlay_y_offset", 2);
        mOverlaySensitivity = appPrefs.getFloat("overlay_sensitivity", 1.0f);
        mOverlaySensitivityBottom = appPrefs.getFloat("overlay_sensitivity_bottom", 1.0f);
        mOverlayTopEnabled = appPrefs.getBoolean("overlay_top_enabled", true);
        mOverlayBottomEnabled = appPrefs.getBoolean("overlay_bottom_enabled", false);

        boolean dynamicGainEnabled = appPrefs.getBoolean("dynamic_gain_enabled", true);
        if (mAudioProcessor != null) {
            mAudioProcessor.setAutoGainEnabled(dynamicGainEnabled);
        }

        mGlyphRenderer = new GlyphRenderer(mGamma, mIdleBreathingEnabled, mSelectedDevice);
        mGlyphRenderer.setStrobeEnabled(appPrefs.getBoolean("strobe_enabled", false));
        mGlyphRenderer.setMaxBrightness(mMaxBrightness);
        float spectrumGain = appPrefs.getFloat("spectrum_gain", 4.0f);
        mGlyphRenderer.setSpectrumGain(spectrumGain);

        mHapticEnabled = hasHapticMotor(this) && appPrefs.getBoolean("haptic_motor_enabled", false);
        String hapticModeName = appPrefs.getString("haptic_mode", HapticMode.BASS_TO_AMPLITUDE.name());
        try {
            mHapticMode = HapticMode.valueOf(hapticModeName);
        } catch (Exception e) {
            mHapticMode = HapticMode.BASS_TO_AMPLITUDE;
        }
        mHapticMinHz = appPrefs.getInt("haptic_freq_min", 60);
        mHapticMaxHz = appPrefs.getInt("haptic_freq_max", 250);

        mFlashlightEnabled = hasFlashlight(this) && appPrefs.getBoolean("flashlight_enabled", false);
        mFlashlightMinHz = appPrefs.getInt("flashlight_freq_min", 60);
        mFlashlightMaxHz = appPrefs.getInt("flashlight_freq_max", 250);

        boolean forcedMulti = appPrefs.getBoolean("flashlight_multi_intensity_forced", false);
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setForceMultiIntensity(forcedMulti);
            mFlashlightIntensityLevels = mFlashlightEngine.getTorchIntensityLevels();
            mFlashlightThreshold = appPrefs.getFloat(
                    "flashlight_threshold",
                    mFlashlightIntensityLevels > 1 ? 1.0f : 0.15f
            );
            mFlashlightSpeedMs = loadFlashlightSpeedMs(appPrefs);
            mFlashlightEngine.setFlashlightThreshold(mFlashlightThreshold);
            mFlashlightEngine.setFlashlightSpeedMs(mFlashlightSpeedMs);
        } else {
            mFlashlightIntensityLevels = 1;
            mFlashlightThreshold = 0.15f;
            mFlashlightSpeedMs = loadFlashlightSpeedMs(appPrefs);
        }

        float hapticMultiplier = appPrefs.getFloat("haptic_multiplier", 1.0f);
        float hapticGamma = appPrefs.getFloat("haptic_gamma", 2.0f);
        mHapticAudioGain = appPrefs.getFloat("haptic_audio_gain", 1.0f);
        mHapticBeatSensitivity = appPrefs.getFloat("haptic_beat_sensitivity", 1.0f);
        mHapticBeatGamma = appPrefs.getFloat("haptic_beat_gamma", 8.0f);

        if (mContinuousHapticEngine != null) {
            mContinuousHapticEngine.setHapticMultiplier(hapticMultiplier);
            mContinuousHapticEngine.setHapticAudioGain(mHapticAudioGain);
            mContinuousHapticEngine.setHapticGamma(hapticGamma);
        }

        if (mBeatDetectionEngine != null) {
            mBeatDetectionEngine.setHapticMultiplier(hapticMultiplier);
            mBeatDetectionEngine.setHapticGamma(mHapticBeatGamma);
            mBeatDetectionEngine.setHapticSensitivity(mHapticBeatSensitivity);
        }
        
        String idlePattern = appPrefs.getString("idle_pattern", "pulse");
        mGlyphRenderer.setIdlePattern(idlePattern);

        refreshLatencyForCurrentAudioRoute();

        try {
            refreshPresetCatalog();
            if (!mAvailablePresetKeys.isEmpty()) {
                mPresetKey = chooseDefaultPresetKey(phoneModelForDevice(mSelectedDevice), mAvailablePresetKeys);
                mVisualizerConfig = loadVisualizerConfig(mPresetKey, SAMPLE_RATE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load zones.config", e);
            mVisualizerConfig = null;
        }
        resetVisualizerState();

        if (mSelectedDevice != DeviceProfile.DEVICE_UNKNOWN && Build.VERSION.SDK_INT >= 31) {
            ensureGlyphManagerInitialized();
        }

        mMainHandler.post(mIdlePulseRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP.equals(intent.getAction())) {
                stopCapture();
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_START.equals(intent.getAction())) {
                startVisualizer();
            } else if (ACTION_TOGGLE_HAPTICS.equals(intent.getAction())) {
                boolean newState = !mHapticEnabled;
                setHapticEnabled(newState);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean("haptic_motor_enabled", newState).apply();
                requestTileRefresh();
                requestWidgetRefresh(this);
            } else if (ACTION_TOGGLE_TORCH.equals(intent.getAction())) {
                boolean newState = !mFlashlightEnabled;
                setFlashlightEnabled(newState);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean("flashlight_enabled", newState).apply();
                requestTileRefresh();
                requestWidgetRefresh(this);
            } else if (ACTION_TOGGLE_GLYPHS.equals(intent.getAction())) {
                boolean newState = mMaxBrightness <= 0;
                setMaxBrightness(newState ? 4095 : 0);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
                        .edit().putInt("max_brightness", mMaxBrightness).apply();
                requestTileRefresh();
                requestWidgetRefresh(this);
            } else if (ACTION_PREV_PRESET.equals(intent.getAction())) {
                prevPreset();
            } else if (ACTION_NEXT_PRESET.equals(intent.getAction())) {
                nextPreset();
            } else if (ACTION_SET_SOURCE.equals(intent.getAction())) {
                String sourceName = intent.getStringExtra(EXTRA_SOURCE);
                if (sourceName != null) {
                    try {
                        CaptureSource source = CaptureSource.valueOf(sourceName);
                        getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
                                .edit().putString("capture_source", source.name()).apply();
                        if (sIsRunning) {
                            // Restart with new source if already running
                            stopCapture();
                            if (source == CaptureSource.INTERNAL) {
                                // We can't easily start INTERNAL from service without new permission data
                                // so we might need to trigger the trampoline if it's INTERNAL
                                Intent trampoline = new Intent(this, PermissionTrampolineActivity.class);
                                trampoline.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(trampoline);
                            } else if (source == CaptureSource.MIC) {
                                startMicCapture();
                            } else if (source == CaptureSource.VIZUALIZER) {
                                startVizualizerCapture();
                            }
                        }
                        requestWidgetRefresh(this);
                    } catch (Exception ignored) {}
                }
                return START_NOT_STICKY;
            }
        }

        String requestedPreset = intent != null ? intent.getStringExtra(EXTRA_PRESET_KEY) : null;
        if (requestedPreset != null && !requestedPreset.isBlank()) {
            setPreset(requestedPreset.trim());
        }

        if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE) && intent.hasExtra(EXTRA_DATA)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
            } else {
                data = intent.getParcelableExtra(EXTRA_DATA);
            }
            if (data != null) {
                startCapture(resultCode, data);
                return START_STICKY;
            }
        }

        if (sIsRunning) {
            // Service is running, just refresh notification if needed
            refreshNotification();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        stopCapture();
        clearGlyphSession();
        if (mGM != null) {
            mGM.unInit();
            mGM = null;
        }
        if (mGMM != null) {
            mGMM.unInit();
            mGMM = null;
        }
        if (mAudioManager != null) {
            mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
            mAudioManager = null;
        }
        if (mWorkerThread != null) {
            mWorkerThread.quitSafely();
            mWorkerThread = null;
            mWorkerHandler = null;
        }
        super.onDestroy();
    }

    public static boolean isRunning() {
        return sIsRunning;
    }

    public boolean isVisualizerRunning() {
        return sIsRunning;
    }

    public void startVisualizer() {
        if (mCaptureSource == CaptureSource.MIC) {
            startMicCapture();
        } else if (mCaptureSource == CaptureSource.VIZUALIZER) {
            startVizualizerCapture();
        } else if (mCaptureSource == CaptureSource.SHIZUKU) {
            startShizukuCapture();
        }
    }

    public void stopVisualizer() {
        stopCapture();
    }

    public static boolean isHapticEnabledGlobal(Context context) {
        if (sIsRunning && sInstance != null) {
            return sInstance.mHapticEnabled;
        }
        return context.getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
                .getBoolean("haptic_motor_enabled", false);
    }

    public static Intent createStopIntent(Context context) {
        return new Intent(context, AudioCaptureService.class).setAction(ACTION_STOP);
    }

    public static int loadLatencyCompensationMs(Context context, int device) {
        return getPreferences(context).getInt(latencyPreferenceKey(device), 0);
    }

    public static int loadLatencyCompensationMs(Context context, int device, String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            return loadLatencyCompensationMs(context, device);
        }

        SharedPreferences preferences = getPreferences(context);
        String preferenceKey = routeLatencyPreferenceKey(device, routeKey);
        if (preferences.contains(preferenceKey)) {
            return preferences.getInt(preferenceKey, 0);
        }
        return loadLatencyCompensationMs(context, device);
    }

    public static void saveLatencyCompensationMs(Context context, int device, int latencyMs) {
        getPreferences(context)
                .edit()
                .putInt(latencyPreferenceKey(device), latencyMs)
                .apply();
    }

    public static void saveLatencyCompensationMs(Context context, int device, String routeKey, int latencyMs) {
        if (routeKey == null || routeKey.isBlank()) {
            saveLatencyCompensationMs(context, device, latencyMs);
            return;
        }

        getPreferences(context)
                .edit()
                .putInt(routeLatencyPreferenceKey(device, routeKey), latencyMs)
                .apply();
    }

    public static float loadGamma(Context context) {
        return getPreferences(context).getFloat(PREF_GAMMA, DEFAULT_GAMMA);
    }

    public static void saveGamma(Context context, float gamma) {
        getPreferences(context)
                .edit()
                .putFloat(PREF_GAMMA, gamma)
                .apply();
    }

    private static float loadFlashlightSpeedMs(SharedPreferences preferences) {
        if (preferences.contains("flashlight_speed_ms")) {
            return clampFlashlightSpeedMs(preferences.getFloat("flashlight_speed_ms", 90f));
        }

        float legacyGamma = preferences.getFloat("flashlight_gamma", 2.2f);
        return legacyGammaToSpeedMs(legacyGamma);
    }

    private static float legacyGammaToSpeedMs(float gamma) {
        if (gamma <= 0f) {
            return 90f;
        }
        if (gamma < 10f) {
            float clampedGamma = Math.max(1f, Math.min(4f, gamma));
            float normalized = (clampedGamma - 1f) / 3f;
            return 150f - (normalized * 110f);
        }
        return clampFlashlightSpeedMs(gamma);
    }

    private static float clampFlashlightSpeedMs(float speedMs) {
        return Math.max(40f, Math.min(150f, speedMs));
    }

    public static List<Integer> loadLatencyPresets(Context context) {
        String saved = getPreferences(context).getString(PREF_LATENCY_PRESETS, null);
        if (saved == null || saved.isEmpty()) {
            return new ArrayList<>(Arrays.asList(10, 154, 300));
        }

        ArrayList<Integer> presets = new ArrayList<>();
        try {
            for (String part : saved.split(",")) {
                presets.add(Integer.parseInt(part.trim()));
            }
        } catch (Exception e) {
            return new ArrayList<>(Arrays.asList(10, 154, 300));
        }
        return presets;
    }

    public static void saveLatencyPresets(Context context, List<Integer> presets) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Integer preset : presets) {
            if (!first) {
                builder.append(",");
            }
            builder.append(preset);
            first = false;
        }
        getPreferences(context)
                .edit()
                .putString(PREF_LATENCY_PRESETS, builder.toString())
                .apply();
    }

    public static List<PresetInfo> loadPresetInfos(Context context, int device) {
        String detectedPhoneModel = detectPhoneModel();
        String selectedPhoneModel = phoneModelForDevice(device);
        String phoneModelForCatalog = PHONE_MODEL_UNKNOWN.equals(selectedPhoneModel)
                ? detectedPhoneModel
                : selectedPhoneModel;

        try {
            JSONObject root = loadZonesConfigRoot(context);
            List<String> matching = getPresetKeysForPhoneModel(root, phoneModelForCatalog);
            if (matching.isEmpty() && !PHONE_MODEL_UNKNOWN.equals(detectedPhoneModel) && PHONE_MODEL_UNKNOWN.equals(selectedPhoneModel)) {
                matching = getPresetKeysForPhoneModel(root, detectedPhoneModel);
            }
            // Strict filtering: no broad fallback if a specific model is intended
            if (matching.isEmpty() && device == DeviceProfile.DEVICE_UNKNOWN) {
                matching = getAllPresetKeys(root);
            }
            return buildPresetInfos(root, matching);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "zones.config missing while loading preset list");
            return Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load preset list", e);
            return Collections.emptyList();
        }
    }

    public void setPreset(String presetSelection) {
        if (presetSelection == null || presetSelection.isBlank()) {
            return;
        }
        applyPresetSelection(presetSelection.trim());
    }

    public void setCaptureSource(CaptureSource source) {
        if (mCaptureSource != source) {
            mCaptureSource = source;
            if (sIsRunning) {
                if (mWorkerHandler != null) {
                    mWorkerHandler.post(this::restartCapture);
                }
            }
            requestWidgetRefresh();
        }
    }

    private void restartCapture() {
        synchronized (mCaptureLock) {
            if (!mCapturing) return;
            if (mCaptureSource == CaptureSource.MIC) {
                startMicCapture();
            } else if (mCaptureSource == CaptureSource.VIZUALIZER) {
                startVizualizerCapture();
            } else if (mCaptureSource == CaptureSource.SHIZUKU) {
                startShizukuCapture();
            } else {
                stopCaptureLocked();
                setRunning(false);
                requestTileRefresh();
            }
        }
    }

    public void startShizukuCapture() {
        startCaptureInternal(CaptureSource.SHIZUKU, 0, null);
    }

    private MediaProjection getShizukuProjection() {
        try {
            String pkg = getPackageName();
            int uid = getApplicationInfo().uid;
            String[] cmds = {
                "appops set " + pkg + " PROJECT_MEDIA allow",
                "appops set " + pkg + " android:project_media allow",
                "appops set --uid " + uid + " PROJECT_MEDIA allow",
                "appops set --uid " + uid + " android:project_media allow",
                "appops set " + pkg + " RECORD_AUDIO allow",
                "appops set --uid " + uid + " RECORD_AUDIO allow",
                "appops set " + pkg + " CAPTURE_AUDIO_OUTPUT allow",
                "appops set --uid " + uid + " CAPTURE_AUDIO_OUTPUT allow"
            };
            
            for (String cmd : cmds) {
                try {
                    java.lang.reflect.Method newProcessMethod = null;
                    for (java.lang.reflect.Method m : Shizuku.class.getDeclaredMethods()) {
                        if (m.getName().equals("newProcess") && m.getParameterCount() == 3) {
                            newProcessMethod = m;
                            break;
                        }
                    }
                    if (newProcessMethod != null) {
                        newProcessMethod.setAccessible(true);
                        java.lang.Process remoteProcess = (java.lang.Process) newProcessMethod.invoke(
                                null, new String[]{"sh", "-c", cmd}, null, null);
                        if (remoteProcess != null) remoteProcess.waitFor();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to execute Shizuku appops: " + cmd, e);
                }
            }
            
            SystemClock.sleep(1000);

            IBinder binder = SystemServiceHelper.getSystemService("media_projection");
            if (binder == null) {
                Log.e(TAG, "Could not get media_projection service binder");
                return null;
            }

            IBinder wrapped = new ShizukuBinderWrapper(binder);

            Class<?> stubClass = Class.forName("android.media.projection.IMediaProjectionManager$Stub");

            Object service = null;
            for (java.lang.reflect.Method m : stubClass.getDeclaredMethods()) {
                if (m.getName().equals("asInterface") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    service = m.invoke(null, wrapped);
                    break;
                }
            }

            if (service == null) {
                Log.e(TAG, "Failed to create IMediaProjectionManager proxy");
                return null;
            }

            java.lang.reflect.Method createProjectionMethod = null;
            for (java.lang.reflect.Method m : service.getClass().getMethods()) {
                if (m.getName().equals("createProjection") && m.getParameterCount() == 4) {
                    createProjectionMethod = m;
                    break;
                }
            }

            if (createProjectionMethod == null) {
                Log.e(TAG, "createProjection method not found");
                return null;
            }

            // Use MediaProjectionManager.TYPE_MIRRORING (1) for system-wide capture if available (SCRCPY method)
            IBinder projectionBinder = (IBinder) createProjectionMethod.invoke(service, uid, pkg, 1, true);
            if (projectionBinder == null) {
                Log.e(TAG, "createProjection returned null binder");
                return null;
            }

            Class<?> iProjectionClass = Class.forName("android.media.projection.IMediaProjection");
            Class<?> iProjectionStubClass = Class.forName("android.media.projection.IMediaProjection$Stub");

            Object iProjection = null;
            for (java.lang.reflect.Method m : iProjectionStubClass.getDeclaredMethods()) {
                if (m.getName().equals("asInterface") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    iProjection = m.invoke(null, new ShizukuBinderWrapper(projectionBinder));
                    break;
                }
            }

            if (iProjection == null) {
                Log.e(TAG, "Failed to create IMediaProjection proxy");
                return null;
            }

            java.lang.reflect.Constructor<MediaProjection> constructor = MediaProjection.class.getConstructor(
                    Context.class, iProjectionClass);
            
            return constructor.newInstance(this, iProjection);

        } catch (Exception e) {
            Log.e(TAG, "Critical failure in Shizuku MediaProjection creation", e);
            return null;
        }
    }

    public void reloadConfig() {
        mWorkerHandler.post(() -> {
            try {
                refreshPresetCatalog();
                if (!mAvailablePresetKeys.contains(mPresetKey)) {
                    String fallback = resolvePresetKey(null, mAvailablePresetKeys);
                    applyPresetSelection(fallback);
                } else {
                    mVisualizerConfig = loadVisualizerConfig(mPresetKey, mAudioRecord != null ? mAudioRecord.getSampleRate() : SAMPLE_RATE);
                    mPresetConfigVersion.incrementAndGet();
                    resetVisualizerState();
                    refreshNotification();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to reload config", e);
            }
        });
    }

    public void setDevice(int device) {
        mSelectedDevice = device;
        if (mGlyphRenderer != null) {
            mGlyphRenderer.setDeviceType(device);
        }
        
        if (device != DeviceProfile.DEVICE_UNKNOWN && Build.VERSION.SDK_INT >= 31) {
            ensureGlyphManagerInitialized();
        }
        
        registerGlyphManager();
        registerGlyphMatrixManager();
        setLatencyCompensationMs(loadLatencyCompensationMs(this, device));
        try {
            refreshPresetCatalog();
            if (!mAvailablePresetKeys.isEmpty() && !mAvailablePresetKeys.contains(mPresetKey)) {
                applyPresetSelection(chooseDefaultPresetKey(phoneModelForDevice(device), mAvailablePresetKeys));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to refresh presets after device change", e);
        }
    }

    private void ensureGlyphManagerInitialized() {
        if (mGM == null && Build.VERSION.SDK_INT >= 31) {
            try {
                mGM = GlyphManager.getInstance(getApplicationContext());
                if (mGM != null) {
                    mGM.init(mGlyphCallback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize GlyphManager", e);
            }
        }
        if (mGMM == null && Build.VERSION.SDK_INT >= 31) {
            try {
                mGMM = GlyphMatrixManager.getInstance(getApplicationContext());
                if (mGMM != null) {
                    mGMM.init(mGlyphMatrixCallback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize GlyphMatrixManager", e);
            }
        }
    }

    public void setLatencyMs(int latencyMs) {
        setLatencyCompensationMs(latencyMs);
    }

    public void setLatencyCompensationMs(int latencyMs) {
        if (mLatencyCompensationMs != latencyMs) {
            mLatencyCompensationMs = latencyMs;
            mLatencySettingsVersion.incrementAndGet();
            mPresetConfigVersion.incrementAndGet();
        }
    }

    public void setGamma(float gamma) {
        mGamma = gamma;
        if (mGlyphRenderer != null) {
            mGlyphRenderer.setGamma(gamma);
        }
    }

    @SuppressWarnings("unused")
    public void setSpectrumGain(float gain) {
        if (mGlyphRenderer != null) {
            mGlyphRenderer.setSpectrumGain(gain);
        }
    }

    public void setSelectedPreset(String presetKey) {
        applyPresetSelection(presetKey);
    }

    public void setHapticMotorEnabled(boolean enabled) {
        mHapticEnabled = hasHapticMotor(this) && enabled;
    }

    public void setHapticMode(HapticMode mode) {
        mHapticMode = mode;
    }

    public void setMaxBrightness(int brightness) {
        int clamped = clampGlyphBrightness(brightness);
        final int targetBrightness = clamped;
        final boolean reopeningAfterEnable = mMaxBrightness <= 0 && targetBrightness > 0;
        mMaxBrightness = clamped;
        
        if (mWorkerHandler == null) return;
        mWorkerHandler.post(() -> {
            applyEffectiveMaxBrightness();
            if (targetBrightness <= 0) {
                clearGlyphSession();
                return;
            }
            if (reopeningAfterEnable) {
                clearGlyphSession();
                ensureGlyphSession();
                mLastSendMs = 0;
            } else {
                ensureGlyphSession();
            }
        });
        requestWidgetRefresh();
    }

    public void setIdleBreathingEnabled(boolean enabled) {
        mIdleBreathingEnabled = enabled;
        mGlyphRenderer.setIdleBreathingEnabled(enabled);
    }

    public void setIdlePattern(String pattern) {
        if (mGlyphRenderer != null) {
            mGlyphRenderer.setIdlePattern(pattern);
        }
    }

    public void setStrobeEnabled(boolean enabled) {
        if (mGlyphRenderer != null) {
            mGlyphRenderer.setStrobeEnabled(enabled);
        }
    }

    public void setDisableGlyphsWhenSilent(boolean enabled) {
        mDisableGlyphsWhenSilent = enabled;
        if (!enabled && !mSessionOpen && mGM != null) {
            mWorkerHandler.post(this::ensureGlyphSession);
        }
    }

    public void setDynamicGainEnabled(boolean enabled) {
        if (mAudioProcessor != null) {
            mAudioProcessor.setAutoGainEnabled(enabled);
        }
    }

    public void setOverlayEnabled(boolean enabled) {
        mOverlayEnabled = enabled;
        if (mWorkerHandler != null) {
            mWorkerHandler.post(this::updateOverlayVisibility);
        }
    }

    public void setOverlayWidth(int width) {
        mOverlayWidth = width;
        if (mOverlayView != null && mWorkerHandler != null) {
            mWorkerHandler.post(this::updateOverlayLayout);
        }
    }

    public void setOverlayHeight(int height) {
        mOverlayHeight = height;
        updateOverlayViewSettings();
        if (mOverlayView != null && mWorkerHandler != null) {
            mWorkerHandler.post(this::updateOverlayLayout);
        }
    }

    public void setOverlayHeightBottom(int height) {
        mOverlayHeightBottom = height;
        updateOverlayViewSettings();
        if (mOverlayView != null && mWorkerHandler != null) {
            mWorkerHandler.post(this::updateOverlayLayout);
        }
    }

    public void setOverlayYOffset(int offset) {
        mOverlayYOffset = offset;
        if (mOverlayView != null && mWorkerHandler != null) {
            mWorkerHandler.post(this::updateOverlayLayout);
        }
    }

    public void setOverlaySensitivity(float sensitivity) {
        mOverlaySensitivity = sensitivity;
        updateOverlayViewSettings();
    }

    public void setOverlaySensitivityBottom(float sensitivity) {
        mOverlaySensitivityBottom = sensitivity;
        updateOverlayViewSettings();
    }

    public void setOverlayTopEnabled(boolean enabled) {
        mOverlayTopEnabled = enabled;
        updateOverlayViewSettings();
        if (mOverlayView != null && mWorkerHandler != null) {
            mWorkerHandler.post(this::updateOverlayLayout);
        }
    }

    public void setOverlayBottomEnabled(boolean enabled) {
        mOverlayBottomEnabled = enabled;
        updateOverlayViewSettings();
        if (mOverlayView != null && mWorkerHandler != null) {
            mWorkerHandler.post(this::updateOverlayLayout);
        }
    }

    private void updateOverlayViewSettings() {
        if (mOverlayView != null) {
            mMainHandler.post(() -> {
                if (mOverlayView != null) {
                    mOverlayView.setTopEnabled(mOverlayTopEnabled);
                    mOverlayView.setBottomEnabled(mOverlayBottomEnabled);
                    mOverlayView.setTopSensitivity(mOverlaySensitivity);
                    mOverlayView.setBottomSensitivity(mOverlaySensitivityBottom);
                    
                    float density = getResources().getDisplayMetrics().density;
                    mOverlayView.setHeights(
                        (int) (mOverlayHeight * density),
                        (int) (mOverlayHeightBottom * density)
                    );
                }
            });
        }
    }

    private void updateOverlayLayout() {
        if (mOverlayView == null || mWindowManager == null) return;
        mMainHandler.post(() -> {
            if (mOverlayView == null || mWindowManager == null) return;
            try {
                float density = getResources().getDisplayMetrics().density;
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) mOverlayView.getLayoutParams();
                params.width = (int) (mOverlayWidth * density);
                
                int topHeightPx = mOverlayTopEnabled ? (int) (mOverlayHeight * density) : 0;
                int bottomHeightPx = mOverlayBottomEnabled ? (int) (mOverlayHeightBottom * density) : 0;
                int totalHeightPx = topHeightPx + bottomHeightPx;
                if (totalHeightPx <= 0) totalHeightPx = 1;
                
                params.height = totalHeightPx;
                // Adjust Y so the baseline (between top and bottom) stays at mOverlayYOffset
                params.y = (int) (mOverlayYOffset * density) - bottomHeightPx;
                mWindowManager.updateViewLayout(mOverlayView, params);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update overlay layout", e);
            }
        });
    }

    public void setOverlayColor(int color) {
        mOverlayColor = color;
        if (mOverlayView != null) {
            mMainHandler.post(() -> mOverlayView.setColor(color));
        }
    }

    private void updateOverlayVisibility() {
        boolean anyOverlay = mOverlayEnabled || mLensVisualizerEnabled;
        if (anyOverlay && sIsRunning) {
            if (Settings.canDrawOverlays(this)) {
                if (mOverlayEnabled && mOverlayView == null) {
                    mMainHandler.post(this::showOverlay);
                }
                updateVisualizerService();
            }
        } else {
            mMainHandler.post(this::hideOverlay);
            stopVisualizerService();
        }
    }

    private void updateVisualizerService() {
        Intent intent = new Intent(this, VisualizerService.class);
        if (mLensVisualizerEnabled && sIsRunning) {
            startService(intent);
        } else {
            stopService(intent);
        }
    }

    private void stopVisualizerService() {
        stopService(new Intent(this, VisualizerService.class));
    }

    private void showOverlay() {
        if (mOverlayView != null) return;
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mOverlayView = new VisualizerOverlayView(this);
        mOverlayView.setColor(mOverlayColor);
        updateOverlayViewSettings();

        float density = getResources().getDisplayMetrics().density;
        int width = (int) (mOverlayWidth * density);
        
        int topHeightPx = mOverlayTopEnabled ? (int) (mOverlayHeight * density) : 0;
        int bottomHeightPx = mOverlayBottomEnabled ? (int) (mOverlayHeightBottom * density) : 0;
        int totalHeightPx = topHeightPx + bottomHeightPx;
        if (totalHeightPx <= 0) totalHeightPx = 1;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                totalHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        // Adjust Y so the baseline (between top and bottom) stays at mOverlayYOffset
        params.y = (int) (mOverlayYOffset * density) - bottomHeightPx;
        try {
            mWindowManager.addView(mOverlayView, params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay view", e);
        }
    }

    private void hideOverlay() {
        if (mWindowManager != null && mOverlayView != null) {
            try {
                mWindowManager.removeView(mOverlayView);
            } catch (Exception ignored) {}
            mOverlayView = null;
        }
    }

    public void setHapticEnabled(boolean enabled) {
        mHapticEnabled = hasHapticMotor(this) && enabled;
        if (!mHapticEnabled) {
            if (mContinuousHapticEngine != null) mContinuousHapticEngine.stopHaptics();
            if (mBeatDetectionEngine != null) mBeatDetectionEngine.stopHaptics();
        }
        requestTileRefresh();
        requestWidgetRefresh();
    }

    public void setMusicArtwork(android.graphics.Bitmap bitmap) {
        // Optional: use for theming
    }

    public void setAudioRoute(com.better.nothing.music.vizualizer.ui.AudioRoute route) {
        if (route != null) {
            setLatencyCompensationMs(loadLatencyCompensationMs(this, mSelectedDevice, route.getStorageKey()));
        }
    }

    public String getActiveAudioRouteKey() {
        AudioRouteInfo info = resolveCurrentAudioRoute();
        return info != null ? info.storageKey : null;
    }

    public String getActiveAudioRouteName() {
        AudioRouteInfo info = resolveCurrentAudioRoute();
        return info != null ? info.displayName : null;
    }

    public void setHapticFreqRange(float minHz, float maxHz) {
        mHapticMinHz = minHz;
        mHapticMaxHz = maxHz;
        if (mBeatDetectionEngine != null) {
            mBeatDetectionEngine.resetDetectionState();
        }
        mHapticSettingsVersion.incrementAndGet();
    }

    public void setHapticMultiplier(float multiplier) {
        if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticMultiplier(multiplier);
        if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticMultiplier(multiplier);
    }

    public void setHapticAudioGain(float gain) {
        mHapticAudioGain = gain;
        if (mContinuousHapticEngine != null) {
            mContinuousHapticEngine.setHapticAudioGain(gain);
        }
    }

    public void setHapticGamma(float gamma) {
        if (mContinuousHapticEngine != null) {
            mContinuousHapticEngine.setHapticGamma(gamma);
        }
    }

    public void setHapticBeatSensitivity(float sensitivity) {
        mHapticBeatSensitivity = sensitivity;
        if (mBeatDetectionEngine != null) {
            mBeatDetectionEngine.setHapticSensitivity(sensitivity);
        }
    }

    public void setHapticBeatGamma(float gamma) {
        mHapticBeatGamma = gamma;
        if (mBeatDetectionEngine != null) {
            mBeatDetectionEngine.setHapticGamma(gamma);
        }
    }

    public void setFlashlightEnabled(boolean enabled) {
        mFlashlightEnabled = hasFlashlight(this) && enabled;
        if (!mFlashlightEnabled && mFlashlightEngine != null) {
            mFlashlightEngine.stopFlashlight();
        }
        requestWidgetRefresh();
    }

    public void setFlashlightFreqRange(float minHz, float maxHz) {
        mFlashlightMinHz = minHz;
        mFlashlightMaxHz = maxHz;
        mHapticSettingsVersion.incrementAndGet(); 
    }

    public void setFlashlightThreshold(float threshold) {
        mFlashlightThreshold = threshold;
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setFlashlightThreshold(threshold);
        }
    }

    public void setFlashlightMode(TorchMode mode) {
        mFlashlightMode = mode;
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setTorchMode(mode);
        }
    }

    public void setFlashlightBeatSensitivity(float sensitivity) {
        mFlashlightBeatSensitivity = sensitivity;
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setFlashlightBeatSensitivity(sensitivity);
        }
    }

    public void setFlashlightSpeedMs(float speedMs) {
        mFlashlightSpeedMs = speedMs;
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setFlashlightSpeedMs(speedMs);
        }
    }

    public void setFlashlightMultiIntensityForced(boolean forced) {
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setForceMultiIntensity(forced);
            mFlashlightIntensityLevels = mFlashlightEngine.getTorchIntensityLevels();
            // We might need to reload speeds or notify UI
            if (sInstance != null) {
                // Ideally we'd have a way to notify the ViewModel
                // For now, let's just make sure the local value is updated
            }
        }
    }

    public int getFlashlightIntensityLevels() {
        if (mFlashlightEngine != null) {
            return mFlashlightEngine.getTorchIntensityLevels();
        }
        return mFlashlightIntensityLevels > 0 ? mFlashlightIntensityLevels : 1;
    }

    public int getFlashlightCurrentLevel() {
        if (mFlashlightEngine != null) {
            return mFlashlightEngine.getCurrentLevel();
        }
        return 0;
    }

    public void startCapture(int resultCode, Intent data) {
        startCaptureInternal(CaptureSource.INTERNAL, resultCode, data);
    }

    public void startMicCapture() {
        startCaptureInternal(CaptureSource.MIC, 0, null);
    }

    public void startVizualizerCapture() {
        startCaptureInternal(CaptureSource.VIZUALIZER, 0, null);
    }

    private void startCaptureInternal(CaptureSource source, int resultCode, Intent data) {
        mAnalyticsHelper.logEvent("capture_start_attempt_" + source.name().toLowerCase(), null);
        mCaptureSource = source;
        MediaProjectionManager projectionManager = null;
        if (source == CaptureSource.INTERNAL) {
            projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (projectionManager == null) {
                Log.e(TAG, "MediaProjectionManager is unavailable");
                setRunning(false);
                return;
            }
        }

        synchronized (mCaptureLock) {
            stopCaptureLocked();

            if (source == CaptureSource.INTERNAL) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }
                MediaProjection projection = projectionManager.getMediaProjection(resultCode, data);
                if (projection == null) {
                    Log.e(TAG, "MediaProjection token was denied or expired");
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    setRunning(false);
                    return;
                }
                mProjection = projection;
            } else if (source == CaptureSource.SHIZUKU) {
                mProjection = getShizukuProjection();
                if (mProjection == null) {
                    Log.e(TAG, "Failed to obtain Shizuku MediaProjection");
                    setRunning(false);
                    return;
                }
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), type);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }
            }

            if (mProjection != null && mWorkerHandler != null) {
                mProjection.registerCallback(mProjectionCallback, mWorkerHandler);
            }

            mCapturing = true;
            setRunning(true);
            updateOverlayVisibility();
            mCaptureStartTimeMs = SystemClock.elapsedRealtime();
            ensureCaptureExecutor();
            requestTileRefresh();
            Log.d(TAG, "Capture started successfully via source: " + source);

            mCaptureExecutor.execute(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                try {
                    SystemClock.sleep(PROJECTION_SETTLE_DELAY_MS);

                    AudioRecord localRecord = null;
                    int captureSampleRate = (source == CaptureSource.MIC) ? SAMPLE_RATE : 48000;
                    
                    int minBufSize = AudioRecord.getMinBufferSize(
                            captureSampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
                    int bufferSize = Math.max(minBufSize, 2048 * 2);

                    if (source == CaptureSource.INTERNAL || source == CaptureSource.SHIZUKU) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }

                        if (source == CaptureSource.SHIZUKU) {
                            // Try SCRCPY-style REMOTE_SUBMIX (Source 8) which captures all audio including opt-outs
                            try {
                                localRecord = new AudioRecord(8, captureSampleRate,
                                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                                if (localRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                                    localRecord.release();
                                    localRecord = null;
                                } else {
                                    Log.d(TAG, "Successfully initialized REMOTE_SUBMIX record (SCRCPY method)");
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "REMOTE_SUBMIX initialization failed, falling back to MediaProjection", e);
                            }
                        }

                        if (localRecord == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            AudioPlaybackCaptureConfiguration config =
                                    new AudioPlaybackCaptureConfiguration.Builder(mProjection)
                                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                                            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                            .build();

                            localRecord = new AudioRecord.Builder()
                                    .setAudioPlaybackCaptureConfig(config)
                                    .setAudioFormat(new AudioFormat.Builder()
                                            .setSampleRate(captureSampleRate)
                                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                            .build())
                                    .setBufferSizeInBytes(bufferSize)
                                    .build();
                        }
                    } else if (source == CaptureSource.VIZUALIZER) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        setupVisualizerCapture();
                    } else {
                        // Use UNPROCESSED for the lowest possible latency and to bypass all system-level DSP
                        localRecord = new AudioRecord(
                                MediaRecorder.AudioSource.UNPROCESSED,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize);
                    }

                if (localRecord != null) {
                    if (localRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        localRecord.release();
                        Log.e(TAG, "AudioRecord failed to initialize: state=" + localRecord.getState());
                        return;
                    }

                    synchronized (mCaptureLock) {
                        if (!mCapturing) {
                            localRecord.release();
                            return;
                        }
                        mAudioRecord = localRecord;
                    }

                    try {
                        localRecord.startRecording();
                        if (localRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                            Log.e(TAG, "AudioRecord failed to start recording: state=" + localRecord.getRecordingState());
                            return;
                        }
                        Log.d(TAG, "AudioRecord started successfully: source=" + localRecord.getAudioSource() + ", rate=" + localRecord.getSampleRate());
                        runCaptureLoop(localRecord);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Failed to start AudioRecord", e);
                    }
                }

                } catch (Exception e) {
                    Log.e(TAG, "Audio capture failed", e);
                    if (mWorkerHandler != null) {
                        mWorkerHandler.post(this::stopSelf);
                    }
                } finally {
                    synchronized (mCaptureLock) {
                        releaseAudioRecord();
                    }
                }
            });
        }

        refreshNotification();
        requestTileRefresh();
    }
    public void stopCapture() {
        synchronized (mCaptureLock) {
            stopCaptureLocked();
        }
    }

    private void stopCaptureLocked() {
        mAnalyticsHelper.logEvent("capture_stopped", null);
        mCapturing = false;
        setRunning(false);
        updateOverlayVisibility();
        mCaptureStartTimeMs = 0;
        shutdownCaptureExecutor();
        releaseAudioRecord();
        releaseVisualizer();
        releaseProjection();
        turnOffGlyphs();
        resetVisualizerState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        requestTileRefresh();
    }

    private void ensureCaptureExecutor() {
        if (mCaptureExecutor != null && !mCaptureExecutor.isShutdown()) {
            return;
        }
        mCaptureExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "GlyphVizCapture");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void shutdownCaptureExecutor() {
        if (mCaptureExecutor != null) {
            mCaptureExecutor.shutdownNow();
            mCaptureExecutor = null;
        }
    }

    private void runCaptureLoop(AudioRecord record) {
        try {
            mVisualizerConfig = loadVisualizerConfig(mPresetKey, record.getSampleRate());
            mPresetConfigVersion.incrementAndGet();
        } catch (Exception e) {
            Log.e(TAG, "Failed to rebuild config for sample rate", e);
        }

        AudioProcessor.VisualizerConfig initialConfig = mVisualizerConfig;
        if (initialConfig == null) {
            return;
        }

        mAudioProcessor.updateFFTSize(record.getSampleRate());
        float currentHzPerBin = mAudioProcessor.getHzPerBin();
        int fftSize = mAudioProcessor.getFFTSize();
        mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz, currentHzPerBin, fftSize);
        mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz, currentHzPerBin, fftSize);

        int currentHopSize = Math.round(record.getSampleRate() / (float) FPS);
        short[] hop = new short[currentHopSize];
        ArrayDeque<PendingFrame> pendingFrames = new ArrayDeque<>();

        int appliedLatencyVersion = mLatencySettingsVersion.get();
        int appliedPresetVersion = mPresetConfigVersion.get();
        int appliedHapticVersion = mHapticSettingsVersion.get();

        while (mCapturing && !Thread.currentThread().isInterrupted()) {
            try {
                AudioProcessor.VisualizerConfig config = mVisualizerConfig;
                int presetVersion = mPresetConfigVersion.get();
                int latencyVersion = mLatencySettingsVersion.get();
                int hapticVersion = mHapticSettingsVersion.get();

                if (config == null) {
                    SystemClock.sleep(100); // Wait for config to be loaded
                    continue;
                }

                if (presetVersion != appliedPresetVersion || latencyVersion != appliedLatencyVersion || hapticVersion != appliedHapticVersion) {
                    pendingFrames.clear();
                    appliedPresetVersion = presetVersion;
                    appliedLatencyVersion = latencyVersion;
                    appliedHapticVersion = hapticVersion;

                    mAudioProcessor.updateFFTSize(record.getSampleRate());
                    float hzPerBin = mAudioProcessor.getHzPerBin();
                    int currentFftSize = mAudioProcessor.getFFTSize();
                    mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz, hzPerBin, currentFftSize);
                    mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz, hzPerBin, currentFftSize);
                    
                    currentHopSize = Math.round(record.getSampleRate() / (float) FPS);
                    hop = new short[currentHopSize];
                }

                int read = record.read(hop, 0, currentHopSize, AudioRecord.READ_BLOCKING);
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord read error: " + read);
                        break;
                    }
                    continue;
                }

                AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, config, mHapticEnabled ? mHapticRange : null, mCaptureSource != CaptureSource.MIC);
                if (result == null) continue;

                float flashlightPeak = 0f;
                if (mFlashlightEnabled) {
                    flashlightPeak = mAudioProcessor.computeRangeMagnitude(mFlashlightRange, result.magnitude);
                }

                if (presetVersion != mPresetConfigVersion.get() || config != mVisualizerConfig) continue;

                int delay = mCaptureSource == CaptureSource.MIC ? 0 : mLatencyCompensationMs;
                pendingFrames.addLast(new PendingFrame(
                        result.uniqueMagnitudes,
                        result.magnitude.clone(),
                        result.hapticPeak,
                        flashlightPeak,
                        config,
                        presetVersion,
                        SystemClock.elapsedRealtime() + delay
                ));
                dispatchDueFrames(pendingFrames);
            } catch (Exception e) {
                Log.e(TAG, "Error in capture loop", e);
                SystemClock.sleep(50); // Prevent tight error loop
            }
        }
    }

    private void dispatchDueFrames(ArrayDeque<PendingFrame> pendingFrames) {
        if (pendingFrames == null) return;
        long nowMs = SystemClock.elapsedRealtime();
        
        PendingFrame latestDueFrame = null;
        while (!pendingFrames.isEmpty()) {
            PendingFrame frame = pendingFrames.peekFirst();
            if (frame == null || frame.dueAtMs > nowMs) {
                break;
            }
            latestDueFrame = pendingFrames.removeFirst();
        }

        if (latestDueFrame != null) {
            try {
                synchronized (mFftLock) {
                    mLatestMagnitudes = latestDueFrame.magnitude;
                }

                if (mOverlayView != null) {
                    try {
                        mOverlayView.updateMagnitudes(latestDueFrame.magnitude);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating overlay magnitudes", e);
                    }
                }

                if (mHapticEnabled) {
                    try {
                        if (mHapticMode == HapticMode.BASS_TO_AMPLITUDE) {
                            if (mContinuousHapticEngine != null) {
                                mContinuousHapticEngine.performHapticFeedback(latestDueFrame.hapticPeak, latestDueFrame.config);
                            }
                        } else {
                            if (mBeatDetectionEngine != null) {
                                mBeatDetectionEngine.performHapticFeedback(latestDueFrame.magnitude, mHapticRange);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error performing haptic feedback", e);
                    }
                }

                if (mFlashlightEnabled && mFlashlightEngine != null) {
                    try {
                        mFlashlightEngine.performFlashlightFeedback(
                                latestDueFrame.flashlightPeak,
                                latestDueFrame.config,
                                latestDueFrame.magnitude,
                                mFlashlightRange != null ? mFlashlightRange.binLo : 0,
                                mFlashlightRange != null ? mFlashlightRange.binHi : 0
                        );
                    } catch (Exception e) {
                        Log.e(TAG, "Error performing flashlight feedback", e);
                    }
                }

                processFrame(latestDueFrame.uniqueMagnitudes, latestDueFrame.hapticPeak, latestDueFrame.config, latestDueFrame.configVersion);
            } catch (Exception e) {
                Log.e(TAG, "Error dispatching frame", e);
            }
        }
    }

    private void processFrame(float[] uniqueMagnitudes, float hapticPeak, AudioProcessor.VisualizerConfig config, int configVersion) {
        if (config == null || configVersion != mPresetConfigVersion.get()) return;

        try {
            long now = SystemClock.elapsedRealtime();
            
            float gain = mGlyphRenderer.getSpectrumGain();

            boolean hasActivity = false;
            if (uniqueMagnitudes != null && uniqueMagnitudes.length > 0) {
                for (float mag : uniqueMagnitudes) {
                    if (mag * gain > 0.0005f) { // More sensitive activity check
                        hasActivity = true;
                        break;
                    }
                }
            }
            if (!hasActivity && hapticPeak * gain > 0.0005f) hasActivity = true;

            // If we are currently "breathing" or have audio, ensure session is open
            if (hasActivity || (mIdleBreathingEnabled && (mMaxBrightness > 0))) {
                mLastAudioActivityMs = now;
                if (!mSessionOpen) {
                    Log.d(TAG, "Audio activity detected, ensuring session...");
                    ensureGlyphSession();
                }
            } else {
                if (mDisableGlyphsWhenSilent && mSessionOpen && (now - mLastAudioActivityMs > 3000)) {
                    Log.d(TAG, "Silence timeout, closing session");
                    clearGlyphSession();
                }
            }

            if (now - mLastSendMs < MIN_SEND_INTERVAL_MS) return;

            // Apply local gain directly to uniqueMagnitudes before renderer
            float[] boostedMagnitudes = new float[uniqueMagnitudes.length];
            for (int i = 0; i < uniqueMagnitudes.length; i++) boostedMagnitudes[i] = uniqueMagnitudes[i] * gain;

            // The renderer's own spectrumGain will be 1.0 because we've applied it here
            // We temporarily swap it to ensure processFrame works as expected
            float originalRendererGain = mGlyphRenderer.getSpectrumGain();
            mGlyphRenderer.setSpectrumGain(1.0f);
            int[] frameColors = null;
            try {
                frameColors = mGlyphRenderer.processFrame(boostedMagnitudes, config, now);
            } catch (Exception e) {
                Log.e(TAG, "GlyphRenderer.processFrame failed", e);
            } finally {
                mGlyphRenderer.setSpectrumGain(originalRendererGain);
            }

            if (frameColors == null) return;

            if (!canPushGlyphFrames()) return;

            try {
                if (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0) {
                    if (mGMM != null) {
                        try {
                            mGMM.setAppMatrixFrame(frameColors); //Thank you Frank
                        } catch (GlyphException e) {
                            Log.w(TAG, "Failed to push matrix frame colors", e);
                        }
                    }
                } else {
                    if (mGM != null) {
                        try {
                            mGM.setFrameColors(frameColors);
                        } catch (GlyphException e) {
                            Log.w(TAG, "Failed to push glyph frame colors", e);
                        }
                    }
                }
                mLastSendMs = now;
            } catch (Exception e) {
                Log.w(TAG, "Failed to push frame colors", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "processFrame fatal error", e);
        }
    }

    public float[] getCurrentLightState() {
        synchronized (mCaptureLock) {
            return mGlyphRenderer.getCurrentLightState();
        }
    }

    private void resetVisualizerState() {
        if (mContinuousHapticEngine != null) mContinuousHapticEngine.stopHaptics();
        if (mBeatDetectionEngine != null) mBeatDetectionEngine.stopHaptics();
        if (mFlashlightEngine != null) mFlashlightEngine.stopFlashlight();
        mGlyphRenderer.resetState(mVisualizerConfig);
        mLastSendMs = 0L;
    }

    private void applyPresetSelection(String presetSelection) {
        try {
            refreshPresetCatalog();
            String resolvedPresetKey = resolvePresetKey(presetSelection, mAvailablePresetKeys);
            if (!resolvedPresetKey.equals(mPresetKey) || mVisualizerConfig == null) {
                mVisualizerConfig = loadVisualizerConfig(resolvedPresetKey, mAudioRecord != null ? mAudioRecord.getSampleRate() : SAMPLE_RATE);
                mPresetKey = resolvedPresetKey;
                mPresetConfigVersion.incrementAndGet();
                resetVisualizerState();
                refreshNotification();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply preset: " + presetSelection, e);
            mVisualizerConfig = null;
            resetVisualizerState();
            refreshNotification();
        }
    }

    private void nextPreset() {
        if (mAvailablePresetKeys.isEmpty()) return;
        int currentIndex = mAvailablePresetKeys.indexOf(mPresetKey);
        int nextIndex = (currentIndex + 1) % mAvailablePresetKeys.size();
        applyPresetSelection(mAvailablePresetKeys.get(nextIndex));
    }

    private void prevPreset() {
        if (mAvailablePresetKeys.isEmpty()) return;
        int currentIndex = mAvailablePresetKeys.indexOf(mPresetKey);
        int prevIndex = (currentIndex - 1 + mAvailablePresetKeys.size()) % mAvailablePresetKeys.size();
        applyPresetSelection(mAvailablePresetKeys.get(prevIndex));
    }

    private String resolvePresetKey(String presetSelection, List<String> availablePresetKeys) {
        if (availablePresetKeys == null || availablePresetKeys.isEmpty()) return DEFAULT_PRESET_KEY;
        if (availablePresetKeys.contains(presetSelection)) return presetSelection;
        String preferred = chooseDefaultPresetKey(phoneModelForDevice(mSelectedDevice), availablePresetKeys);
        if (availablePresetKeys.contains(preferred)) return preferred;
        return availablePresetKeys.get(0);
    }

    private AudioProcessor.VisualizerConfig loadVisualizerConfig(String presetKey, int sampleRate) throws IOException, JSONException {
        JSONObject root = loadZonesConfigRoot(this);
        JSONObject preset = root.optJSONObject(presetKey);
        if (preset == null) throw new JSONException("Preset not found");
        JSONArray zonesArray = preset.optJSONArray("zones");
        if (zonesArray == null || zonesArray.length() == 0) throw new JSONException("No zones");

        double decayAlpha = preset.has("decay-alpha") ? preset.optDouble("decay-alpha", 0.8) : root.optDouble("decay-alpha", 0.8);
        AudioProcessor.ZoneSpec[] zones = parseZoneSpecs(zonesArray);
        
        int fftSize = mAudioProcessor != null ? mAudioProcessor.getFFTSize() : 2048;
        float hzPerBin = (float) sampleRate / fftSize;
        
        return buildVisualizerConfig(presetKey, preset.optString("description", presetKey), decayAlpha, zones, hzPerBin, fftSize);
    }

    private AudioProcessor.VisualizerConfig buildVisualizerConfig(String presetKey, String description, double decayAlpha, AudioProcessor.ZoneSpec[] zones, float hzPerBin, int fftSize) {
        float adjustedDecay = 0.86f + ((float) decayAlpha / 10f);
        List<float[]> uniquePairs = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        for (AudioProcessor.ZoneSpec zone : zones) {
            String key = String.format(Locale.US, "%.4f|%.4f", zone.lowHz, zone.highHz);
            if (seenPairs.add(key)) uniquePairs.add(new float[]{zone.lowHz, zone.highHz});
        }
        uniquePairs.sort((left, right) -> Float.compare(left[0], right[0]));

        AudioProcessor.FrequencyRange[] uniqueRanges = new AudioProcessor.FrequencyRange[uniquePairs.size()];
        for (int i = 0; i < uniquePairs.size(); i++) {
            uniqueRanges[i] = new AudioProcessor.FrequencyRange(uniquePairs.get(i)[0], uniquePairs.get(i)[1], hzPerBin, fftSize);
        }

        int[][] zoneToRangeIndices = new int[zones.length][];
        for (int zoneIndex = 0; zoneIndex < zones.length; zoneIndex++) {
            AudioProcessor.ZoneSpec zone = zones[zoneIndex];
            ArrayList<Integer> overlaps = new ArrayList<>();
            for (int rangeIndex = 0; rangeIndex < uniqueRanges.length; rangeIndex++) {
                if (!(uniqueRanges[rangeIndex].highHz < zone.lowHz || uniqueRanges[rangeIndex].lowHz > zone.highHz)) overlaps.add(rangeIndex);
            }
            int[] mapping = new int[overlaps.size()];
            for (int i = 0; i < overlaps.size(); i++) mapping[i] = overlaps.get(i);
            zoneToRangeIndices[zoneIndex] = mapping;
        }
        return new AudioProcessor.VisualizerConfig(presetKey, description, adjustedDecay, zones, uniqueRanges, zoneToRangeIndices);
    }

    private AudioProcessor.ZoneSpec[] parseZoneSpecs(JSONArray zonesArray) throws JSONException {
        AudioProcessor.ZoneSpec[] zones = new AudioProcessor.ZoneSpec[zonesArray.length()];
        for (int i = 0; i < zonesArray.length(); i++) {
            JSONArray zoneArray = zonesArray.getJSONArray(i);
            float lowHz = (float) zoneArray.getDouble(0);
            float highHz = (float) zoneArray.getDouble(1);
            if (lowHz > highHz) { float tmp = lowHz; lowHz = highHz; highHz = tmp; }
            zones[i] = new AudioProcessor.ZoneSpec(lowHz, highHz, parseOptionalPercent(zoneArray, 3), parseOptionalPercent(zoneArray, 4));
        }
        return zones;
    }

    private void releaseAudioRecord() {
        if (mAudioRecord != null) {
            try { mAudioRecord.stop(); } catch (Exception ignored) {}
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    private void releaseProjection() {
        if (mProjection != null) {
            try { mProjection.unregisterCallback(mProjectionCallback); } catch (Exception ignored) {}
            try { mProjection.stop(); } catch (Exception ignored) {}
            mProjection = null;
        }
    }

    private void releaseVisualizer() {
        if (mVisualizer != null) {
            try { mVisualizer.setEnabled(false); } catch (Exception ignored) {}
            try { mVisualizer.release(); } catch (Exception ignored) {}
            mVisualizer = null;
        }
        synchronized (mVisualizerPendingFrames) {
            mVisualizerPendingFrames.clear();
        }
    }

    private void setupVisualizerCapture() {
        releaseVisualizer();
        // Give the system a short moment to fully release resources from any previous instance
        SystemClock.sleep(250);

        try {
            mAudioProcessor.updateFFTSize();
            float currentHzPerBin = mAudioProcessor.getHzPerBin();
            int fftSize = mAudioProcessor.getFFTSize();
            mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz, currentHzPerBin, fftSize);
            mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz, currentHzPerBin, fftSize);

            // Attempt to initialize Visualizer with retry
            RuntimeException lastException = null;
            for (int i = 0; i < 3; i++) {
                try {
                    mVisualizer = new Visualizer(0);
                    break;
                } catch (RuntimeException e) {
                    lastException = e;
                    Log.w(TAG, "Visualizer init attempt " + (i + 1) + " failed: " + e.getMessage());
                    SystemClock.sleep(200);
                }
            }

            if (mVisualizer == null) {
                if (lastException != null) throw lastException;
                throw new RuntimeException("Visualizer engine failed to initialize after retries");
            }

            int captureSize = Math.min(Visualizer.getCaptureSizeRange()[1], 1024);
            mVisualizer.setCaptureSize(captureSize);
            int maxRate = Visualizer.getMaxCaptureRate();
            mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                    processVisualizerWaveform(waveform, samplingRate);
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    // Using waveform captures instead of FFT directly so the existing audio processor can consume PCM-like data.
                }
            }, maxRate, true, false);
            mVisualizer.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Visualizer capture", e);
            releaseVisualizer();
        }
    }

    private void processVisualizerWaveform(byte[] waveform, int samplingRate) {
        if (!mCapturing || mVisualizerConfig == null) return;
        
        Log.v(TAG, "Waveform received. Size: " + waveform.length + ", Rate: " + (samplingRate / 1000.0f) + " Hz");

        mAudioProcessor.updateFFTSize(samplingRate / 1000); // Visualizer API samplingRate is in mHz

        short[] hop = new short[waveform.length];
        for (int i = 0; i < waveform.length; i++) {
            hop[i] = (short) (((waveform[i] & 0xFF) - 128) << 8);
        }

        AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, mVisualizerConfig, mHapticEnabled ? mHapticRange : null, false);
        if (result == null) return;

        float flashlightPeak = 0f;
        if (mFlashlightEnabled) {
            flashlightPeak = mAudioProcessor.computeRangeMagnitude(mFlashlightRange, result.magnitude);
        }

        int delay = mCaptureSource == CaptureSource.MIC ? 0 : mLatencyCompensationMs;
        PendingFrame pendingFrame = new PendingFrame(
                result.uniqueMagnitudes,
                result.magnitude.clone(),
                result.hapticPeak,
                flashlightPeak,
                mVisualizerConfig,
                mPresetConfigVersion.get(),
                SystemClock.elapsedRealtime() + delay
        );

        synchronized (mVisualizerPendingFrames) {
            mVisualizerPendingFrames.addLast(pendingFrame);
            dispatchDueFrames(mVisualizerPendingFrames);
        }
    }

    private void turnOffGlyphs() {
        if (mGM != null && mSessionOpen) {
            int glyphCount = resolveGlyphCount();
            if (glyphCount > 0) try { mGM.setFrameColors(new int[glyphCount]); } catch (Exception ignored) {}
            try { mGM.turnOff(); } catch (Exception ignored) {}
        }
        if (mGMM != null) {
            int matrixSize = DeviceProfile.getMatrixWidth(mSelectedDevice) * DeviceProfile.getMatrixHeight(mSelectedDevice);
            if (matrixSize > 0) try { mGMM.setAppMatrixFrame(new int[matrixSize]); } catch (Exception ignored) {}
        }
    }

    private void ensureGlyphSession() {
        if (mGM == null) {
            Log.w(TAG, "ensureGlyphSession: GlyphManager is null");
            return;
        }
        if (mSessionOpen) return;
        
        try { 
            Log.d(TAG, "Opening Glyph session...");
            mGM.openSession(); 
            mSessionOpen = true; 
            Log.d(TAG, "Glyph session opened successfully");
        } catch (GlyphException e) {
            Log.e(TAG, "Failed to open Glyph session", e);
            mSessionOpen = false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error opening Glyph session", e);
            mSessionOpen = false;
        }
    }

    private void clearGlyphSession() {
        try {
            turnOffGlyphs();
            if (mGM != null && mSessionOpen) {
                Log.d(TAG, "Closing Glyph session...");
                try { 
                    mGM.closeSession(); 
                } catch (GlyphException e) {
                    Log.e(TAG, "Error closing Glyph session", e);
                }
                
                if (mGMM != null) {
                    try { 
                        mGMM.closeAppMatrix(); 
                    } catch (GlyphException e) {
                        Log.e(TAG, "Error closing Glyph Matrix app matrix", e);
                    }
                }
                mSessionOpen = false;
                Log.d(TAG, "Glyph session cleared");
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in clearGlyphSession", e);
        }
    }

    private boolean canPushGlyphFrames() {
        if (mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return false;
        if (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0) return mGMM != null;
        return mGM != null && mSessionOpen;
    }

    private static int clampGlyphBrightness(int brightness) { return Math.max(0, Math.min(MAX_GLYPH_BRIGHTNESS, brightness)); }

    private int resolveGlyphCount() {
        return mVisualizerConfig != null ? mVisualizerConfig.zones.length : DeviceProfile.getLedCount(mSelectedDevice);
    }

    private Notification buildNotification() {
        ensureNotificationChannel();
        SharedPreferences appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        String buttonSet = appPrefs.getString("notification_button_set", "presets");

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        long duration = getCaptureDurationMs();
        String timeStr = formatDuration(duration);

        String presetInfo = "";
        if (mMaxBrightness > 0 && mVisualizerConfig != null) {
            presetInfo = mVisualizerConfig.description + " • ";
        }

        String content = presetInfo + timeStr;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Glyph Visualizer")
                .setContentText(content)
                .setSmallIcon(com.better.nothing.music.vizualizer.R.drawable.ic_notif_monochrome)
                .setContentIntent(contentIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true);

        if ("controls".equals(buttonSet)) {
            // Glyphs toggle
            String glyphsLabel = mMaxBrightness > 0 ? "GLYPHS" : "glyphs";
            PendingIntent glyphsIntent = PendingIntent.getService(this, 10,
                    new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_GLYPHS),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(0, glyphsLabel, glyphsIntent);

            // Haptics toggle
            String hapticsLabel = mHapticEnabled ? "HAPTICS" : "haptics";
            PendingIntent hapticsIntent = PendingIntent.getService(this, 11,
                    new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_HAPTICS),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(0, hapticsLabel, hapticsIntent);

            // Flash toggle
            String flashLabel = mFlashlightEnabled ? "FLASH" : "flash";
            PendingIntent flashIntent = PendingIntent.getService(this, 12,
                    new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_TORCH),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(0, flashLabel, flashIntent);
        } else {
            // Previous preset
            PendingIntent prevIntent = PendingIntent.getService(this, 2,
                    new Intent(this, AudioCaptureService.class).setAction(ACTION_PREV_PRESET),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent);

            // Stop
            PendingIntent stopIntent = PendingIntent.getService(this, 1,
                    createStopIntent(this),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent);

            // Next preset
            PendingIntent nextIntent = PendingIntent.getService(this, 3,
                    new Intent(this, AudioCaptureService.class).setAction(ACTION_NEXT_PRESET),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_media_next, "Next", nextIntent);
        }

        return builder.build();
    }

    private String formatDuration(long ms) {
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours = (ms / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    private void ensureNotificationChannel() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null || notificationManager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Glyph Visualizer", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the visualizer alive while audio capture is active");
        notificationManager.createNotificationChannel(channel);
    }

    public static boolean hasHapticMotor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null && vm.getDefaultVibrator().hasVibrator();
        } else {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            return v != null && v.hasVibrator();
        }
    }

    public static boolean hasFlashlight(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    private void refreshNotification() {
        if (!mCapturing) return;
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) notificationManager.notify(NOTIF_ID, buildNotification());
    }

    private void requestTileRefresh() {
        TileService.requestListeningState(this, new ComponentName(this, VisualizerTileService.class));
        TileService.requestListeningState(this, new ComponentName(this, HapticsTileService.class));
    }

    public static void requestWidgetRefresh(Context context) {
        Intent intent = new Intent("com.better.nothing.music.vizualizer.REFRESH_WIDGET");
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void requestWidgetRefresh() {
        requestWidgetRefresh(this);
    }

    private void refreshPresetCatalog() throws IOException, JSONException {
        mDetectedPhoneModel = detectPhoneModel();
        String selectedPhoneModel = phoneModelForDevice(mSelectedDevice);
        String phoneModelForCatalog = PHONE_MODEL_UNKNOWN.equals(selectedPhoneModel) ? mDetectedPhoneModel : selectedPhoneModel;
        JSONObject root = loadZonesConfigRoot(this);
        List<String> matching = getPresetKeysForPhoneModel(root, phoneModelForCatalog);
        if (matching.isEmpty() && !PHONE_MODEL_UNKNOWN.equals(mDetectedPhoneModel) && PHONE_MODEL_UNKNOWN.equals(selectedPhoneModel)) {
            matching = getPresetKeysForPhoneModel(root, mDetectedPhoneModel);
        }
        if (matching.isEmpty() && mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) {
            matching = getAllPresetKeys(root);
        }
        mAvailablePresetKeys = matching;
    }

    private static SharedPreferences getPreferences(Context context) { return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); }
    private static String latencyPreferenceKey(int device) { return PREF_LATENCY_PREFIX + Math.max(DeviceProfile.DEVICE_UNKNOWN, device); }
    private static String routeLatencyPreferenceKey(int device, String routeKey) { return PREF_LATENCY_ROUTE_PREFIX + Math.max(DeviceProfile.DEVICE_UNKNOWN, device) + "_" + routeKey.trim().replaceAll("[^A-Za-z0-9._-]", "_"); }

    public static String loadZonesConfigVersion(Context context) { try { return loadZonesConfigRoot(context).optString("version", "Unknown"); } catch (Exception e) { return "Unknown"; } }
    private static JSONObject loadZonesConfigRoot(Context context) throws IOException, JSONException { return new JSONObject(loadZonesConfigText(context)); }
    public static String loadZonesConfigText(Context context) throws IOException {
        File[] candidates = { new File(context.getFilesDir(), "zones.config"), context.getExternalFilesDir(null) == null ? null : new File(context.getExternalFilesDir(null), "zones.config"), new File(context.getApplicationInfo().dataDir, "zones.config") };
        for (File candidate : candidates) if (candidate != null && candidate.isFile()) return readFile(candidate);
        InputStream is = context.getAssets().open("zones.config");
        try { return readFully(is); } finally { closeQuietly(is); }
    }

    private static String readFile(File file) throws IOException { FileInputStream is = new FileInputStream(file); try { return readFully(is); } finally { closeQuietly(is); } }
    private static String readFully(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
        return os.toString("UTF-8");
    }
    private static void closeQuietly(Closeable c) { if (c != null) try { c.close(); } catch (IOException ignored) {} }
    private static List<String> getAllPresetKeys(JSONObject root) { ArrayList<String> res = new ArrayList<>(); JSONArray names = root.names(); if (names != null) for (int i = 0; i < names.length(); i++) { String key = names.optString(i, ""); if (isPresetEntry(root, key)) res.add(key); } Collections.sort(res); return res; }
    private static List<PresetInfo> buildPresetInfos(JSONObject root, List<String> keys) { ArrayList<PresetInfo> res = new ArrayList<>(); for (String key : keys) { JSONObject p = root.optJSONObject(key); if (p != null) res.add(new PresetInfo(key, p.optString("description", key))); } return res; }
    private static List<String> getPresetKeysForPhoneModel(JSONObject root, String phoneModel) { ArrayList<String> res = new ArrayList<>(); if (PHONE_MODEL_UNKNOWN.equals(phoneModel)) return res; JSONArray names = root.names(); if (names != null) for (int i = 0; i < names.length(); i++) { String key = names.optString(i, ""); if (!isPresetEntry(root, key)) continue; JSONObject p = root.optJSONObject(key); if (p != null && phoneModel.equalsIgnoreCase(p.optString("phone_model", ""))) res.add(key); } Collections.sort(res); return res; }
    private static boolean isPresetEntry(JSONObject root, String key) { if (key == null || key.isEmpty() || "version".equals(key) || "amp".equals(key) || key.startsWith("decay")) return false; JSONObject p = root.optJSONObject(key); return p != null && p.optJSONArray("zones") != null; }
    private static String chooseDefaultPresetKey(String phoneModel, List<String> presetKeys) {
        if (presetKeys == null || presetKeys.isEmpty()) return DEFAULT_PRESET_KEY;
        List<String> prefs = switch (phoneModel) { case PHONE_MODEL_PHONE1 -> Arrays.asList("np1s", "np1"); case PHONE_MODEL_PHONE2 -> Collections.singletonList("np2"); case PHONE_MODEL_PHONE2A -> Collections.singletonList("np2a"); case PHONE_MODEL_PHONE3A -> Arrays.asList("np3as", "np3a"); case PHONE_MODEL_PHONE3 -> Collections.singletonList("np3test"); case PHONE_MODEL_PHONE4A -> Collections.singletonList("np4a"); case PHONE_MODEL_PHONE4A_PRO -> Collections.singletonList("np4ap-test"); case PHONE_MODEL_PHONE4B -> Collections.singletonList("np4b"); default -> Collections.emptyList(); };
        for (String p : prefs) if (presetKeys.contains(p)) return p;
        return presetKeys.get(0);
    }
    private static String phoneModelForDevice(int device) { return switch (device) { case DeviceProfile.DEVICE_NP1 -> PHONE_MODEL_PHONE1; case DeviceProfile.DEVICE_NP2 -> PHONE_MODEL_PHONE2; case DeviceProfile.DEVICE_NP2A -> PHONE_MODEL_PHONE2A; case DeviceProfile.DEVICE_NP3A -> PHONE_MODEL_PHONE3A; case DeviceProfile.DEVICE_NP4A -> PHONE_MODEL_PHONE4A; case DeviceProfile.DEVICE_NP4APRO -> PHONE_MODEL_PHONE4A_PRO; case DeviceProfile.DEVICE_NP3 -> PHONE_MODEL_PHONE3; case DeviceProfile.DEVICE_NP4B -> PHONE_MODEL_PHONE4B; default -> PHONE_MODEL_UNKNOWN; }; }
    private static String detectPhoneModel() {
        if (Common.is20111()) return PHONE_MODEL_PHONE1; if (Common.is22111()) return PHONE_MODEL_PHONE2; if (Common.is23111() || Common.is23113()) return PHONE_MODEL_PHONE2A; if (Common.is24111()) return PHONE_MODEL_PHONE3A; if (Common.is25111p()) return PHONE_MODEL_PHONE4A_PRO; if (Common.is25111()) return PHONE_MODEL_PHONE4A; if (Common.is23112()) return PHONE_MODEL_PHONE3;
        String b = (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL + " " + Build.DEVICE + " " + Build.PRODUCT).toLowerCase(Locale.US);
        if (b.contains("phone 4b")) return PHONE_MODEL_PHONE4B; if (b.contains("phone 4a pro")) return PHONE_MODEL_PHONE4A_PRO; if (b.contains("phone 4a")) return PHONE_MODEL_PHONE4A; if (b.contains("phone 3a")) return PHONE_MODEL_PHONE3A; if (b.contains("phone 3")) return PHONE_MODEL_PHONE3; if (b.contains("phone 2a")) return PHONE_MODEL_PHONE2A; if (b.contains("phone 2")) return PHONE_MODEL_PHONE2; if (b.contains("phone 1")) return PHONE_MODEL_PHONE1;
        return PHONE_MODEL_UNKNOWN;
    }
    private static float parseOptionalPercent(JSONArray arr, int idx) { if (idx >= arr.length()) return Float.NaN; Object r = arr.opt(idx); if (r == null || r == JSONObject.NULL) return Float.NaN; try { float v; if (r instanceof Number n) v = n.floatValue(); else { String t = String.valueOf(r).trim(); if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim(); v = Float.parseFloat(t); } if (v >= 0f && v <= 1f) v *= 100f; return v; } catch (Exception ignored) { return Float.NaN; } }
    private void refreshLatencyForCurrentAudioRoute() { SharedPreferences p = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE); if (!p.getBoolean("auto_device_enabled", true)) return; AudioRouteInfo r = resolveCurrentAudioRoute(); setLatencyCompensationMs(loadLatencyCompensationMs(this, mSelectedDevice, r != null ? r.storageKey : null)); }
    private AudioRouteInfo resolveCurrentAudioRoute() {
        if (mAudioManager == null) return null;
        AudioDeviceInfo[] os = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo pref = null;
        for (AudioDeviceInfo d : os) if (isBluetoothOutput(d)) { pref = d; break; }
        if (pref == null) for (AudioDeviceInfo d : os) if (isWiredOutput(d)) { pref = d; break; }
        if (pref == null) for (AudioDeviceInfo d : os) if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { pref = d; break; }
        if (pref == null && os.length > 0) pref = os[0];
        return pref != null ? toAudioRouteInfo(pref) : null;
    }
    private static boolean isBluetoothOutput(AudioDeviceInfo d) { int t = d.getType(); return t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER || t == AudioDeviceInfo.TYPE_BLE_BROADCAST; }
    private static boolean isWiredOutput(AudioDeviceInfo d) { int t = d.getType(); return t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_USB_HEADSET; }
    private static AudioRouteInfo toAudioRouteInfo(AudioDeviceInfo d) {
        String n = d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ? "Internal Speaker" : String.valueOf(d.getProductName());
        String nn = n.toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        if (nn.isEmpty()) nn = "unknown_output";
        String a = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            a = d.getAddress();
        }
        String na = null;
        if (a != null && !a.isEmpty()) na = a.toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        return new AudioRouteInfo(d.getType() + "_" + (na != null && !na.isEmpty() ? na : nn), n);
    }
}
