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
import com.better.nothing.music.vizualizer.server.AudioServer;
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
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
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
import android.view.View;
import android.view.WindowManager;
import android.graphics.PixelFormat;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.better.nothing.music.vizualizer.ui.VisualizerOverlayView;
import com.better.nothing.music.vizualizer.ui.EdgeVisualizerView;
import com.better.nothing.music.vizualizer.ui.LensVisualizerView;

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
    private int mCurrentSampleRate = SAMPLE_RATE;
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
            if (mGM == null) return;
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
            if (mGMM == null) return;
            Log.d(TAG, "Glyph Matrix service connected");
            registerGlyphMatrixManager();
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    private void registerGlyphManager() {
        if (mGM == null || mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return;
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
    private java.lang.Process mShizukuProcess;
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
    private boolean mEdgeVisualizerEnabled = false;
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
    public void setLensVisualizerRadius(float radius) { mLensVisualizerRadius = radius; if (mLensVisualizerView != null) mLensVisualizerView.setRadius(radius * 4); }
    public void setLensVisualizerX(float x) { mLensVisualizerX = x; if (mLensVisualizerView != null) mLensVisualizerView.setCenterPosition(x, mLensVisualizerY); }
    public void setLensVisualizerY(float y) { mLensVisualizerY = y; if (mLensVisualizerView != null) mLensVisualizerView.setCenterPosition(mLensVisualizerX, y); }
    public void setLensVisualizerBarWidth(float width) { mLensVisualizerBarWidth = width; if (mLensVisualizerView != null) mLensVisualizerView.setBarWidth(width * 4); }
    public void setLensVisualizerMaxHeight(float height) { mLensVisualizerMaxHeight = height; if (mLensVisualizerView != null) mLensVisualizerView.setMaxHeight(height * 4); }
    public void setLensVisualizerBarCount(int count) { mLensVisualizerBarCount = count; if (mLensVisualizerView != null) mLensVisualizerView.setBarCount(count); }
    public void setLensVisualizerSensitivity(float sensitivity) { mLensVisualizerSensitivity = sensitivity; if (mLensVisualizerView != null) mLensVisualizerView.setSensitivity(sensitivity); }
    
    private int mOverlayWidth = 120;
    private int mOverlayHeight = 12;
    private int mOverlayHeightBottom = 12;
    private int mOverlayYOffset = 2;
    private float mOverlaySensitivity = 1.0f;
    private float mOverlaySensitivityBottom = 1.0f;
    private boolean mOverlayTopEnabled = true;
    private boolean mOverlayBottomEnabled = false;
    private int mOverlayColor = android.graphics.Color.WHITE;

    private int mEdgeThickness = 12;
    private float mEdgeSensitivity = 1.0f;
    private int mEdgeBarCountHoriz = 20;
    private int mEdgeBarCountVert = 40;
    private float mEdgeCornerRadius = 2f;
    private boolean mEdgeTopEnabled = true;
    private boolean mEdgeBottomEnabled = true;
    private EdgeVisualizerView mEdgeVisualizerView;

    private WindowManager mWindowManager;
    private VisualizerOverlayView mOverlayView;
    private LensVisualizerView mLensVisualizerView;

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
        synchronized (mFftLock) { return mLatestMagnitudes; }
    }

    private float[] mCurrentLightState = new float[0];
    public float[] getCurrentLightState() {
        return mCurrentLightState;
    }

    public boolean isVisualizerRunning() {
        return sIsRunning;
    }

    public void setDevice(int device) {
        if (mSelectedDevice != device) {
            mSelectedDevice = device;
            mLatencyCompensationMs = loadLatencyCompensationMs(this, device);
            if (mGlyphRenderer != null) {
                mGlyphRenderer.setDeviceType(device);
            }
            if (sIsRunning) restartCapture();
        }
    }

    public void setLatencyMs(int ms) {
        mLatencyCompensationMs = ms;
    }

    public void setGamma(float gamma) {
        mGamma = gamma;
        if (mGlyphRenderer != null) mGlyphRenderer.setGamma(gamma);
    }

    public void setSpectrumGain(float gain) {
        if (mGlyphRenderer != null) mGlyphRenderer.setSpectrumGain(gain);
    }

    public void setSelectedPreset(String presetKey) {
        if (presetKey != null && !presetKey.equals(mPresetKey)) {
            mPresetKey = presetKey;
            if (sIsRunning) restartCapture();
        }
    }

    public void setHapticMotorEnabled(boolean enabled) {
        mHapticEnabled = enabled;
        if (!enabled) {
            if (mContinuousHapticEngine != null) mContinuousHapticEngine.stopHaptics();
            if (mBeatDetectionEngine != null) mBeatDetectionEngine.stopHaptics();
        }
    }

    public void setHapticMode(HapticMode mode) {
        mHapticMode = mode;
        if (mBeatDetectionEngine != null) mBeatDetectionEngine.resetDetectionState();
    }

    public void setHapticFreqRange(float min, float max) {
        mHapticMinHz = min;
        mHapticMaxHz = max;
        if (mAudioProcessor != null) {
            mHapticRange = new AudioProcessor.FrequencyRange(min, max, mAudioProcessor.getHzPerBin(), mAudioProcessor.getFFTSize());
        }
    }

    public void setHapticMultiplier(float multiplier) {
        if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticMultiplier(multiplier);
        if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticMultiplier(multiplier);
    }

    public void setHapticAudioGain(float gain) {
        mHapticAudioGain = gain;
        if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticAudioGain(gain);
    }

    public void setHapticGamma(float gamma) {
        if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticGamma(gamma);
        if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticGamma(gamma);
    }

    public void setHapticBeatSensitivity(float sensitivity) {
        mHapticBeatSensitivity = sensitivity;
        if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticSensitivity(sensitivity);
    }

    public void setHapticBeatGamma(float gamma) {
        mHapticBeatGamma = gamma;
    }

    public void setFlashlightMode(TorchMode mode) {
        mFlashlightMode = mode;
        if (mFlashlightEngine != null) mFlashlightEngine.setTorchMode(mode);
    }

    public void setFlashlightFreqRange(float min, float max) {
        mFlashlightMinHz = min;
        mFlashlightMaxHz = max;
        if (mAudioProcessor != null) {
            mFlashlightRange = new AudioProcessor.FrequencyRange(min, max, mAudioProcessor.getHzPerBin(), mAudioProcessor.getFFTSize());
        }
    }

    public void setFlashlightThreshold(float threshold) {
        mFlashlightThreshold = threshold;
        if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightThreshold(threshold);
    }

    public void setFlashlightSpeedMs(float speedMs) {
        mFlashlightSpeedMs = speedMs;
        if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightSpeedMs(speedMs);
    }

    public void setFlashlightBeatSensitivity(float sensitivity) {
        mFlashlightBeatSensitivity = sensitivity;
        if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightBeatSensitivity(sensitivity);
    }

    public void setFlashlightMultiIntensityForced(boolean forced) {
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setForceMultiIntensity(forced);
            mFlashlightIntensityLevels = mFlashlightEngine.getTorchIntensityLevels();
        }
    }

    public int getFlashlightIntensityLevels() {
        return mFlashlightIntensityLevels;
    }

    public int getFlashlightCurrentLevel() {
        return (mFlashlightEngine != null) ? mFlashlightEngine.getCurrentLevel() : 0;
    }

    public void setIdlePattern(String pattern) {
        // Implement if needed
    }

    public void setStrobeEnabled(boolean enabled) {
        // Implement if needed
    }

    public void setDisableGlyphsWhenSilent(boolean enabled) {
        mDisableGlyphsWhenSilent = enabled;
    }

    public void setOverlayEnabled(boolean enabled) {
        mOverlayEnabled = enabled;
        updateOverlayVisibility();
    }

    public void setOverlayTopEnabled(boolean enabled) { mOverlayTopEnabled = enabled; }
    public void setOverlayBottomEnabled(boolean enabled) { mOverlayBottomEnabled = enabled; }
    public void setOverlayWidth(int width) { mOverlayWidth = width; updateOverlayVisibility(); }
    public void setOverlayHeight(int height) { mOverlayHeight = height; updateOverlayVisibility(); }
    public void setOverlayHeightBottom(int height) { mOverlayHeightBottom = height; updateOverlayVisibility(); }
    public void setOverlayYOffset(int offset) { mOverlayYOffset = offset; updateOverlayVisibility(); }
    public void setOverlaySensitivity(float sensitivity) { mOverlaySensitivity = sensitivity; if (mOverlayView != null) mOverlayView.setTopSensitivity(sensitivity); }
    public void setOverlaySensitivityBottom(float sensitivity) { mOverlaySensitivityBottom = sensitivity; if (mOverlayView != null) mOverlayView.setBottomSensitivity(sensitivity); }

    public void setEdgeVisualizerEnabled(boolean enabled) {
        mEdgeVisualizerEnabled = enabled;
        updateOverlayVisibility();
    }

    public void setEdgeThickness(int thickness) { mEdgeThickness = thickness; if (mEdgeVisualizerView != null) mEdgeVisualizerView.setThickness(thickness); }
    public void setEdgeSensitivity(float sensitivity) { mEdgeSensitivity = sensitivity; if (mEdgeVisualizerView != null) mEdgeVisualizerView.setSensitivity(sensitivity); }
    public void setEdgeBarCounts(int horiz, int vert) { mEdgeBarCountHoriz = horiz; mEdgeBarCountVert = vert; if (mEdgeVisualizerView != null) mEdgeVisualizerView.setBarCounts(horiz, vert); }
    public void setEdgeCornerRadius(float radius) { mEdgeCornerRadius = radius; if (mEdgeVisualizerView != null) mEdgeVisualizerView.setScreenRadius(radius); }
    public void setEdgeTopEnabled(boolean enabled) { mEdgeTopEnabled = enabled; if (mEdgeVisualizerView != null) mEdgeVisualizerView.setTopEnabled(enabled); }
    public void setEdgeBottomEnabled(boolean enabled) { mEdgeBottomEnabled = enabled; if (mEdgeVisualizerView != null) mEdgeVisualizerView.setBottomEnabled(enabled); }

    private String mActiveAudioRouteKey = "default";
    private String mActiveAudioRouteName = "Internal Speaker";

    public void setAudioRoute(Object route) {
        // Handle both AudioRouteInfo and the UI AudioRoute class via reflection if needed, 
        // or just accept it as anything that has storageKey and displayName
        try {
            java.lang.reflect.Field skField = route.getClass().getDeclaredField("storageKey");
            skField.setAccessible(true);
            mActiveAudioRouteKey = (String) skField.get(route);
            
            java.lang.reflect.Field dnField = route.getClass().getDeclaredField("displayName");
            dnField.setAccessible(true);
            mActiveAudioRouteName = (String) dnField.get(route);
            
            refreshLatencyForCurrentAudioRoute();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set audio route", e);
        }
    }

    public String getActiveAudioRouteKey() { return mActiveAudioRouteKey; }
    public String getActiveAudioRouteName() { return mActiveAudioRouteName; }

    public void setDynamicGainEnabled(boolean enabled) {}
    public void reloadConfig() {
        try {
            refreshPresetCatalog();
            mVisualizerConfig = loadVisualizerConfig(mPresetKey, SAMPLE_RATE);
            resetVisualizerState();
        } catch (Exception e) {
            Log.e(TAG, "Reload config failed", e);
        }
    }

    public static String loadZonesConfigVersion(Context context) {
        try {
            JSONObject root = loadZonesConfigRoot(context);
            return root.optString("version", "Unknown");
        } catch (Exception e) {
            return "Error";
        }
    }

    public static List<PresetInfo> loadPresetInfos(Context context, int device) {
        try {
            JSONObject root = loadZonesConfigRoot(context);
            String model = phoneModelForDevice(device);
            List<String> keys = getPresetKeysForPhoneModel(root, model);
            if (keys.isEmpty()) keys = getAllPresetKeys(root);
            return buildPresetInfos(root, keys);
        } catch (Exception e) {
            return Collections.emptyList();
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
                if (now - mLastNotifUpdateMs >= 1000) { refreshNotification(); mLastNotifUpdateMs = now; }
                if (mCaptureSource == CaptureSource.VIZUALIZER) {
                    synchronized (mVisualizerPendingFrames) { dispatchDueFrames(mVisualizerPendingFrames); }
                    if (now - mLastSendMs >= 16 && mVisualizerConfig != null) processFrame(new float[0], 0f, mVisualizerConfig, mPresetConfigVersion.get());
                } else if (mIdleBreathingEnabled && mSessionOpen && mVisualizerConfig != null) {
                    if (now - mLastAudioActivityMs > 100) processFrame(new float[0], 0f, mVisualizerConfig, mPresetConfigVersion.get());
                }
                mMainHandler.postDelayed(this, 16);
            }
        }
    };

    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) { refreshLatencyForCurrentAudioRoute(); }
        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) { refreshLatencyForCurrentAudioRoute(); }
    };

    private void applyEffectiveMaxBrightness() { if (mGlyphRenderer != null) mGlyphRenderer.setMaxBrightness(mMaxBrightness); }

    private static final class PendingFrame {
        final float[] uniqueMagnitudes;
        final float[] magnitude;
        final float hapticPeak;
        final float flashlightPeak;
        final AudioProcessor.VisualizerConfig config;
        final int configVersion;
        final long dueAtMs;
        PendingFrame(float[] uniqueMagnitudes, float[] magnitude, float hapticPeak, float flashlightPeak, AudioProcessor.VisualizerConfig config, int configVersion, long dueAtMs) {
            this.uniqueMagnitudes = uniqueMagnitudes; this.magnitude = magnitude; this.hapticPeak = hapticPeak; this.flashlightPeak = flashlightPeak; this.config = config; this.configVersion = configVersion; this.dueAtMs = dueAtMs;
        }
    }

    public static final class PresetInfo {
        public final String key;
        public final String description;
        public PresetInfo(String key, String description) { this.key = key; this.description = description; }
    }

    public class LocalBinder extends Binder { public AudioCaptureService getService() { return AudioCaptureService.this; } }

    private AudioDeviceManager mAudioDeviceManager;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mAnalyticsHelper = new com.better.nothing.music.vizualizer.util.AnalyticsHelper(this);
        mWorkerThread = new HandlerThread("GlyphVizWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper());
        mAudioManager = getSystemService(AudioManager.class);
        if (mAudioManager != null) mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, mWorkerHandler);
        mContinuousHapticEngine = new ContinuousHapticEngine(this);
        mBeatDetectionEngine = new BeatDetectionHapticEngine(this);
        mFlashlightEngine = new FlashlightEngine(this);
        mAudioProcessor = new AudioProcessor();
        mAudioProcessor.setAutoGainEnabled(true);
        mAudioDeviceManager = new AudioDeviceManager(this, this::refreshLatencyForCurrentAudioRoute);
        mSelectedDevice = DeviceProfile.detectDevice();
        if (mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) mSelectedDevice = DeviceProfile.DEVICE_NP2;
        mLatencyCompensationMs = loadLatencyCompensationMs(this, mSelectedDevice);
        mGamma = loadGamma(this);
        SharedPreferences appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        mMaxBrightness = clampGlyphBrightness(appPrefs.getInt("max_brightness", MAX_GLYPH_BRIGHTNESS));
        mIdleBreathingEnabled = appPrefs.getBoolean("idle_breathing_enabled", false);
        mDisableGlyphsWhenSilent = appPrefs.getBoolean("disable_glyphs_when_silent", false);
        mOverlayEnabled = appPrefs.getBoolean("overlay_enabled", false);
        mEdgeVisualizerEnabled = appPrefs.getBoolean("edge_visualizer_enabled", false);
        mGlyphRenderer = new GlyphRenderer(mGamma, mIdleBreathingEnabled, mSelectedDevice);
        mGlyphRenderer.setMaxBrightness(mMaxBrightness);
        mGlyphRenderer.setSpectrumGain(appPrefs.getFloat("spectrum_gain", 4.0f));
        mHapticEnabled = hasHapticMotor(this) && appPrefs.getBoolean("haptic_motor_enabled", false);
        mFlashlightEnabled = hasFlashlight(this) && appPrefs.getBoolean("flashlight_enabled", false);
        refreshLatencyForCurrentAudioRoute();
        try {
            refreshPresetCatalog();
            if (!mAvailablePresetKeys.isEmpty()) {
                mPresetKey = chooseDefaultPresetKey(phoneModelForDevice(mSelectedDevice), mAvailablePresetKeys);
                mVisualizerConfig = loadVisualizerConfig(mPresetKey, SAMPLE_RATE);
            }
        } catch (Exception ignored) {}
        resetVisualizerState();
        if (mSelectedDevice != DeviceProfile.DEVICE_UNKNOWN && Build.VERSION.SDK_INT >= 31) ensureGlyphManagerInitialized();
        mMainHandler.post(mIdlePulseRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
            else if (ACTION_START.equals(action)) startVisualizer();
            else if (ACTION_SET_SOURCE.equals(action)) {
                String sourceName = intent.getStringExtra(EXTRA_SOURCE);
                if (sourceName != null) {
                    try {
                        CaptureSource source = CaptureSource.valueOf(sourceName);
                        mCaptureSource = source;
                        getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putString("capture_source", source.name()).apply();
                        if (sIsRunning) restartCapture();
                    } catch (Exception ignored) {}
                }
            }
            else if (ACTION_TOGGLE_HAPTICS.equals(action)) {
                setHapticMotorEnabled(!mHapticEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("haptic_motor_enabled", mHapticEnabled).apply();
                HapticsTileService.requestRefresh(this);
            }
            else if (ACTION_TOGGLE_TORCH.equals(action)) {
                setFlashlightEnabled(!mFlashlightEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("flashlight_enabled", mFlashlightEnabled).apply();
            }
            else if (ACTION_TOGGLE_GLYPHS.equals(action)) {
                if (mMaxBrightness > 0) setMaxBrightness(0);
                else setMaxBrightness(getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getInt("max_brightness", MAX_GLYPH_BRIGHTNESS));
            }
            else if (ACTION_NEXT_PRESET.equals(action)) {
                if (!mAvailablePresetKeys.isEmpty()) {
                    int idx = (mAvailablePresetKeys.indexOf(mPresetKey) + 1) % mAvailablePresetKeys.size();
                    setPreset(mAvailablePresetKeys.get(idx));
                }
            }
            else if (ACTION_PREV_PRESET.equals(action)) {
                if (!mAvailablePresetKeys.isEmpty()) {
                    int idx = (mAvailablePresetKeys.indexOf(mPresetKey) - 1 + mAvailablePresetKeys.size()) % mAvailablePresetKeys.size();
                    setPreset(mAvailablePresetKeys.get(idx));
                }
            }
        }
        if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE) && intent.hasExtra(EXTRA_DATA)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            if (data != null) startCapture(resultCode, data);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sInstance = null; stopCapture(); clearGlyphSession();
        if (mGM != null) mGM.unInit(); if (mGMM != null) mGMM.unInit();
        if (mAudioManager != null) mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        if (mWorkerThread != null) mWorkerThread.quitSafely();
        super.onDestroy();
    }

    public static boolean isRunning() { return sIsRunning; }
    public void startVisualizer() {
        if (mCaptureSource == CaptureSource.MIC) startMicCapture();
        else if (mCaptureSource == CaptureSource.VIZUALIZER) startVizualizerCapture();
        else if (mCaptureSource == CaptureSource.SHIZUKU) startShizukuCapture();
    }
    public void stopVisualizer() { stopCapture(); }

    public void setCaptureSource(CaptureSource source) {
        if (mCaptureSource != source) { mCaptureSource = source; if (sIsRunning) restartCapture(); requestWidgetRefresh(); }
    }

    private void restartCapture() {
        if (mWorkerHandler != null) mWorkerHandler.post(() -> {
            stopCapture();
            if (mCaptureSource == CaptureSource.MIC) startMicCapture();
            else if (mCaptureSource == CaptureSource.VIZUALIZER) startVizualizerCapture();
            else if (mCaptureSource == CaptureSource.SHIZUKU) startShizukuCapture();
        });
    }

    public void startShizukuCapture() { startCaptureInternal(CaptureSource.SHIZUKU, 0, null); }
    public void startMicCapture() { startCaptureInternal(CaptureSource.MIC, 0, null); }
    public void startVizualizerCapture() { startCaptureInternal(CaptureSource.VIZUALIZER, 0, null); }
    public void startCapture(int resultCode, Intent data) { startCaptureInternal(CaptureSource.INTERNAL, resultCode, data); }

    private void startCaptureInternal(CaptureSource source, int resultCode, Intent data) {
        mCaptureSource = source;
        MediaProjectionManager projectionManager = null;
        if (source == CaptureSource.INTERNAL) projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        synchronized (mCaptureLock) {
            stopCaptureLocked();
            if (source == CaptureSource.INTERNAL) {
                if (projectionManager == null) return;
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                else startForeground(NOTIF_ID, buildNotification());
                mProjection = projectionManager.getMediaProjection(resultCode, data);
                if (mProjection == null) { stopForeground(STOP_FOREGROUND_REMOVE); setRunning(false); return; }
                mProjection.registerCallback(mProjectionCallback, mWorkerHandler);
            } else {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                else startForeground(NOTIF_ID, buildNotification());
            }
            mCapturing = true; setRunning(true); updateOverlayVisibility(); mCaptureStartTimeMs = SystemClock.elapsedRealtime();
            ensureCaptureExecutor();
            mCaptureExecutor.execute(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                if (source == CaptureSource.SHIZUKU) { startShizukuProcessCapture(); return; }
                SystemClock.sleep(PROJECTION_SETTLE_DELAY_MS);
                AudioRecord localRecord = null;
                try {
                    mCurrentSampleRate = (source == CaptureSource.MIC) ? SAMPLE_RATE : 48000;
                    int captureSampleRate = mCurrentSampleRate;
                    int bufSize = Math.max(AudioRecord.getMinBufferSize(captureSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096);
                    if (source == CaptureSource.INTERNAL) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mProjection != null) {
                            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mProjection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
                                localRecord = new AudioRecord.Builder().setAudioPlaybackCaptureConfig(config).setAudioFormat(new AudioFormat.Builder().setSampleRate(captureSampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(bufSize).build();
                            }
                        }
                    } else if (source == CaptureSource.VIZUALIZER) { setupVisualizerCapture(); return; }
                    else {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            localRecord = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
                        }
                    }
                    if (localRecord != null && localRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        synchronized (mCaptureLock) { if (!mCapturing) { localRecord.release(); return; } mAudioRecord = localRecord; }
                        localRecord.startRecording(); runCaptureLoop(localRecord);
                    }
                } catch (Exception e) { Log.e(TAG, "Capture failed", e); stopSelf(); }
                finally { synchronized (mCaptureLock) { releaseAudioRecord(); } }
            });
        }
        refreshNotification();
    }

    private void startShizukuProcessCapture() {
        try {
            String path = getApplicationInfo().sourceDir;
            // Use standard app_process launch via sh -c
            String fullCmd = "export CLASSPATH=" + path + " && exec app_process /system/bin " + AudioServer.class.getName();
            String[] cmd = {"sh", "-c", fullCmd};
            Log.d(TAG, "Launching Shizuku AudioServer: " + fullCmd);
            
            java.lang.reflect.Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
            mShizukuProcess = (java.lang.Process) newProcessMethod.invoke(null, cmd, null, null);
            
            if (mShizukuProcess == null) {
                Log.e(TAG, "Shizuku.newProcess returned null");
                showToast("Shizuku: Failed to start process");
                stopCapture();
                return;
            }

            // Start a thread to consume stderr so it doesn't block the process
            new Thread(() -> {
                try (InputStream es = mShizukuProcess.getErrorStream()) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(es, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.e("AudioServer:Err", line);
                    }
                } catch (IOException e) {
                    Log.e("AudioServer:Err", "Error reading stderr: " + e.getMessage());
                }
            }, "ShizukuStderr").start();

            InputStream is = mShizukuProcess.getInputStream();
            byte[] magic = new byte[4];
            int readTotal = 0;
            
            // Blocking read for magic bytes
            while (readTotal < 4) {
                int r = is.read(magic, readTotal, 4 - readTotal);
                if (r < 0) break;
                readTotal += r;
            }
            
            if (readTotal != 4 || magic[0] != 0x42 || magic[1] != 0x4E || magic[2] != 0x4D || magic[3] != 0x56) {
                Log.e(TAG, "Shizuku handshake failed. Read " + readTotal + " bytes. Content: " + Arrays.toString(magic));
                try {
                    int exitCode = mShizukuProcess.exitValue();
                    Log.e(TAG, "Process exited early with code: " + exitCode);
                } catch (IllegalThreadStateException ignored) {}
                showToast("Shizuku: Server handshake failed");
                stopCapture();
                return;
            }
            Log.d(TAG, "Shizuku handshake successful");
            mCurrentSampleRate = 48000;
            runShizukuStreamLoop(is);
        } catch (Exception e) {
            Log.e(TAG, "Shizuku capture failed", e);
            showToast("Shizuku error: " + e.getMessage());
            stopCapture();
        }
    }

    private void runShizukuStreamLoop(InputStream is) throws IOException {
        int sampleRate = 48000;
        try { mVisualizerConfig = loadVisualizerConfig(mPresetKey, sampleRate); } catch (Exception e) { Log.e(TAG, "Config load failed", e); }
        
        mAudioProcessor.updateFFTSize(sampleRate);
        float hzPerBin = mAudioProcessor.getHzPerBin();
        int fftSize = mAudioProcessor.getFFTSize();
        mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz, hzPerBin, fftSize);
        mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz, hzPerBin, fftSize);

        int hopSize = Math.round(sampleRate / (float) FPS);
        short[] hop = new short[hopSize];
        byte[] byteBuffer = new byte[hopSize * 2];
        ArrayDeque<PendingFrame> pendingFrames = new ArrayDeque<>();
        long totalBytesRead = 0;
        while (mCapturing) {
            int readTotal = 0;
            while (readTotal < byteBuffer.length && mCapturing) {
                int r = is.read(byteBuffer, readTotal, byteBuffer.length - readTotal);
                if (r < 0) throw new IOException("EOF");
                readTotal += r;
            }
            if (!mCapturing) break;
            
            totalBytesRead += readTotal;
            if (totalBytesRead % (byteBuffer.length * 100) == 0) {
                Log.d(TAG, "Shizuku: Read " + totalBytesRead + " bytes");
            }
            for (int i = 0; i < hopSize; i++) hop[i] = (short) ((byteBuffer[i * 2] & 0xFF) | (byteBuffer[i * 2 + 1] << 8));
            AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, mVisualizerConfig, mHapticEnabled ? mHapticRange : null, true);
            if (result == null) continue;

            float flashlightPeak = 0f;
            if (mFlashlightEnabled && mFlashlightEngine != null) {
                flashlightPeak = mAudioProcessor.computeRangeMagnitude(mFlashlightRange, result.magnitude);
            }

            pendingFrames.addLast(new PendingFrame(result.uniqueMagnitudes, result.magnitude.clone(), result.hapticPeak, flashlightPeak, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs));
            dispatchDueFrames(pendingFrames);
        }
    }

    public void stopCapture() { synchronized (mCaptureLock) { stopCaptureLocked(); } }
    private void stopCaptureLocked() {
        mCapturing = false; setRunning(false); updateOverlayVisibility();
        shutdownCaptureExecutor(); releaseAudioRecord(); releaseShizukuProcess(); releaseVisualizer(); releaseProjection();
        turnOffGlyphs(); resetVisualizerState(); stopForeground(STOP_FOREGROUND_REMOVE);
    }
    private void releaseShizukuProcess() { if (mShizukuProcess != null) { mShizukuProcess.destroy(); mShizukuProcess = null; } }
    private void releaseAudioRecord() { if (mAudioRecord != null) { try { mAudioRecord.stop(); } catch (Exception ignored) {} mAudioRecord.release(); mAudioRecord = null; } }
    private void releaseProjection() { if (mProjection != null) { try { mProjection.stop(); } catch (Exception ignored) {} mProjection = null; } }
    private void releaseVisualizer() { if (mVisualizer != null) { try { mVisualizer.release(); } catch (Exception ignored) {} mVisualizer = null; } synchronized (mVisualizerPendingFrames) { mVisualizerPendingFrames.clear(); } }
    private void ensureCaptureExecutor() { if (mCaptureExecutor == null || mCaptureExecutor.isShutdown()) mCaptureExecutor = Executors.newSingleThreadExecutor(); }
    private void shutdownCaptureExecutor() { if (mCaptureExecutor != null) { mCaptureExecutor.shutdownNow(); mCaptureExecutor = null; } }
    private void showToast(String msg) { mMainHandler.post(() -> android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()); }

    private long lastFrameLogMs = 0;
    private void processFrame(float[] uniqueMagnitudes, float hapticPeak, AudioProcessor.VisualizerConfig config, int configVersion) {
        if (config == null || configVersion != mPresetConfigVersion.get()) return;

        try {
            long now = SystemClock.elapsedRealtime();
            float gain = mGlyphRenderer.getSpectrumGain();

            boolean hasActivity = false;
            float maxUnique = 0;
            if (uniqueMagnitudes != null && uniqueMagnitudes.length > 0) {
                for (float mag : uniqueMagnitudes) {
                    if (mag > maxUnique) maxUnique = mag;
                    if (mag * gain > 0.0005f) {
                        hasActivity = true;
                    }
                }
            }
            if (!hasActivity && hapticPeak * gain > 0.0005f) hasActivity = true;

            if (now - lastFrameLogMs > 2000) {
                Log.d(TAG, "processFrame: activity=" + hasActivity + ", maxUnique=" + maxUnique + ", gain=" + gain + ", hapticPeak=" + hapticPeak + ", sessionOpen=" + mSessionOpen);
                lastFrameLogMs = now;
            }

            if (hasActivity || (mIdleBreathingEnabled && (mMaxBrightness > 0))) {
                mLastAudioActivityMs = now;
                if (!mSessionOpen) ensureGlyphSession();
            } else {
                if (mDisableGlyphsWhenSilent && mSessionOpen && (now - mLastAudioActivityMs > 3000)) {
                    clearGlyphSession();
                }
            }

            if (now - mLastSendMs < MIN_SEND_INTERVAL_MS) return;

            float[] boostedMagnitudes = (uniqueMagnitudes != null) ? new float[uniqueMagnitudes.length] : new float[0];
            if (uniqueMagnitudes != null) {
                for (int i = 0; i < uniqueMagnitudes.length; i++) boostedMagnitudes[i] = uniqueMagnitudes[i] * gain;
            }

            float originalRendererGain = mGlyphRenderer.getSpectrumGain();
            mGlyphRenderer.setSpectrumGain(1.0f);
            int[] frameColors;
            try {
                frameColors = mGlyphRenderer.processFrame(boostedMagnitudes, config, now);
            } finally {
                mGlyphRenderer.setSpectrumGain(originalRendererGain);
            }

            if (frameColors == null) return;
            
            // Update mCurrentLightState for UI
            float[] lightState = new float[frameColors.length];
            for (int i = 0; i < frameColors.length; i++) {
                // frameColors contains 12-bit brightness (0-4095)
                lightState[i] = frameColors[i] / 4095f;
            }
            mCurrentLightState = lightState;

            if (!canPushGlyphFrames()) return;

            try {
                if (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0) {
                    if (mGMM != null) mGMM.setAppMatrixFrame(frameColors);
                } else {
                    if (mGM != null) mGM.setFrameColors(frameColors);
                }
                mLastSendMs = now;
            } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "processFrame error", e);
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
                        mOverlayView.updateMagnitudes(latestDueFrame.magnitude, mCurrentSampleRate);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating overlay magnitudes", e);
                    }
                }

                if (mEdgeVisualizerView != null) {
                    try {
                        mEdgeVisualizerView.updateMagnitudes(latestDueFrame.magnitude, mCurrentSampleRate);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating edge magnitudes", e);
                    }
                }

                if (mLensVisualizerView != null) {
                    try {
                        mLensVisualizerView.updateMagnitudes(latestDueFrame.magnitude, mCurrentSampleRate);
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating lens magnitudes", e);
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
    private void setupVisualizerCapture() {
        try {
            mVisualizer = new Visualizer(0); mVisualizer.setCaptureSize(1024);
            mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] w, int sr) {
                    mCurrentSampleRate = sr / 1000;
                    mAudioProcessor.updateFFTSize(mCurrentSampleRate);
                    short[] h = new short[w.length]; for (int i=0; i<w.length; i++) h[i] = (short)(((w[i] & 0xFF) - 128) << 8);
                    AudioProcessor.AudioFrameResult r = mAudioProcessor.processAudioFrame(h, mVisualizerConfig, null, false);
                    if (r != null) synchronized(mVisualizerPendingFrames) {
                        mVisualizerPendingFrames.addLast(new PendingFrame(r.uniqueMagnitudes, r.magnitude.clone(), 0f, 0f, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs));
                        dispatchDueFrames(mVisualizerPendingFrames);
                    }
                }
                @Override public void onFftDataCapture(Visualizer v, byte[] f, int sr) {}
            }, Visualizer.getMaxCaptureRate(), true, false);
            mVisualizer.setEnabled(true);
        } catch (Exception e) { Log.e(TAG, "Visualizer failed", e); }
    }
    private void runCaptureLoop(AudioRecord record) {
        int hopSize = Math.round(record.getSampleRate() / (float) FPS);
        short[] hop = new short[hopSize];
        while (mCapturing) {
            int read = record.read(hop, 0, hopSize, AudioRecord.READ_BLOCKING);
            if (read <= 0) continue;
            AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, mVisualizerConfig, mHapticEnabled ? mHapticRange : null, true);
            if (result == null) continue;
            PendingFrame frame = new PendingFrame(result.uniqueMagnitudes, result.magnitude.clone(), result.hapticPeak, 0f, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
            synchronized(mVisualizerPendingFrames) { mVisualizerPendingFrames.addLast(frame); dispatchDueFrames(mVisualizerPendingFrames); }
        }
    }
    private void turnOffGlyphs() { try { if (mGM != null) mGM.turnOff(); } catch (Exception ignored) {} }
    private void ensureGlyphSession() { try { if (mGM != null && !mSessionOpen) { mGM.openSession(); mSessionOpen = true; } } catch (Exception ignored) {} }
    private void clearGlyphSession() { try { if (mGM != null && mSessionOpen) { mGM.closeSession(); mSessionOpen = false; } } catch (Exception ignored) {} }
    private boolean canPushGlyphFrames() { return mSelectedDevice != DeviceProfile.DEVICE_UNKNOWN && (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0 ? mGMM != null : (mGM != null && mSessionOpen)); }
    private Notification buildNotification() {
        ensureNotificationChannel();
        return new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Glyph Visualizer").setContentText("Capturing audio...").setSmallIcon(com.better.nothing.music.vizualizer.R.drawable.ic_notif_monochrome).setOngoing(true).setSilent(true).build();
    }
    private void ensureNotificationChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Glyph Visualizer", NotificationManager.IMPORTANCE_LOW));
    }
    private void refreshNotification() { if (mCapturing) { NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null) nm.notify(NOTIF_ID, buildNotification()); } }
    private void updateOverlayVisibility() {
        mMainHandler.post(() -> {
            if (mWindowManager == null) mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            
            // Edge Visualizer
            if (mEdgeVisualizerEnabled && mCapturing) {
                if (mEdgeVisualizerView == null) {
                    mEdgeVisualizerView = new EdgeVisualizerView(this);
                    mEdgeVisualizerView.setThickness(mEdgeThickness);
                    mEdgeVisualizerView.setSensitivity(mEdgeSensitivity);
                    mEdgeVisualizerView.setBarCounts(mEdgeBarCountHoriz, mEdgeBarCountVert);
                    mEdgeVisualizerView.setTopEnabled(mEdgeTopEnabled);
                    mEdgeVisualizerView.setBottomEnabled(mEdgeBottomEnabled);
                    
                    // Force the view to ignore system bar insets
                    mEdgeVisualizerView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    );
                    
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                    WindowManager.LayoutParams.TYPE_PHONE,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.TOP | Gravity.START;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        params.setFitInsetsTypes(0);
                        params.setFitInsetsSides(0);
                    }
                    try { mWindowManager.addView(mEdgeVisualizerView, params); } catch (Exception ignored) {}
                }
            } else {
                if (mEdgeVisualizerView != null) {
                    try { mWindowManager.removeView(mEdgeVisualizerView); } catch (Exception ignored) {}
                    mEdgeVisualizerView = null;
                }
            }

            // Overlay Visualizer
            if (mOverlayEnabled && mCapturing) {
                if (mOverlayView == null) {
                    mOverlayView = new VisualizerOverlayView(this);
                    
                    // Force the view to ignore system bar insets
                    mOverlayView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    );

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            mOverlayWidth * 4, // Simple scaling for width
                            (mOverlayHeight + mOverlayHeightBottom) * 4,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                    WindowManager.LayoutParams.TYPE_PHONE,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    params.y = mOverlayYOffset * 4;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        params.setFitInsetsTypes(0);
                        params.setFitInsetsSides(0);
                    }
                    
                    mOverlayView.setTopEnabled(mOverlayTopEnabled);
                    mOverlayView.setBottomEnabled(mOverlayBottomEnabled);
                    mOverlayView.setHeights(mOverlayHeight, mOverlayHeightBottom);
                    mOverlayView.setTopSensitivity(mOverlaySensitivity);
                    mOverlayView.setBottomSensitivity(mOverlaySensitivityBottom);
                    
                    try { mWindowManager.addView(mOverlayView, params); } catch (Exception ignored) {}
                } else {
                    // Update existing overlay params
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) mOverlayView.getLayoutParams();
                    params.width = mOverlayWidth * 4;
                    params.height = (mOverlayHeight + mOverlayHeightBottom) * 4;
                    params.y = mOverlayYOffset * 4;
                    mOverlayView.setTopEnabled(mOverlayTopEnabled);
                    mOverlayView.setBottomEnabled(mOverlayBottomEnabled);
                    mOverlayView.setHeights(mOverlayHeight, mOverlayHeightBottom);
                    try { mWindowManager.updateViewLayout(mOverlayView, params); } catch (Exception ignored) {}
                }
            } else {
                if (mOverlayView != null) {
                    try { mWindowManager.removeView(mOverlayView); } catch (Exception ignored) {}
                    mOverlayView = null;
                }
            }

            // Lens Visualizer
            if (mLensVisualizerEnabled && mCapturing) {
                if (mLensVisualizerView == null) {
                    mLensVisualizerView = new LensVisualizerView(this);
                    
                    // Force the view to ignore system bar insets
                    mLensVisualizerView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    );

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                    WindowManager.LayoutParams.TYPE_PHONE,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.TOP | Gravity.START;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        params.setFitInsetsTypes(0);
                        params.setFitInsetsSides(0);
                    }

                    mLensVisualizerView.setRadius(mLensVisualizerRadius * 4); // dp to px approx
                    mLensVisualizerView.setBarWidth(mLensVisualizerBarWidth * 4);
                    mLensVisualizerView.setMaxHeight(mLensVisualizerMaxHeight * 4);
                    mLensVisualizerView.setBarCount(mLensVisualizerBarCount);
                    mLensVisualizerView.setSensitivity(mLensVisualizerSensitivity);
                    mLensVisualizerView.setCenterPosition(mLensVisualizerX, mLensVisualizerY);

                    try { mWindowManager.addView(mLensVisualizerView, params); } catch (Exception ignored) {}
                } else {
                    mLensVisualizerView.setRadius(mLensVisualizerRadius * 4);
                    mLensVisualizerView.setBarWidth(mLensVisualizerBarWidth * 4);
                    mLensVisualizerView.setMaxHeight(mLensVisualizerMaxHeight * 4);
                    mLensVisualizerView.setBarCount(mLensVisualizerBarCount);
                    mLensVisualizerView.setSensitivity(mLensVisualizerSensitivity);
                    mLensVisualizerView.setCenterPosition(mLensVisualizerX, mLensVisualizerY);
                }
            } else {
                if (mLensVisualizerView != null) {
                    try { mWindowManager.removeView(mLensVisualizerView); } catch (Exception ignored) {}
                    mLensVisualizerView = null;
                }
            }
        });
    }
    private void ensureGlyphManagerInitialized() {
        if (mGM == null) { mGM = GlyphManager.getInstance(this); if (mGM != null) mGM.init(mGlyphCallback); }
        if (mGMM == null) { mGMM = GlyphMatrixManager.getInstance(this); if (mGMM != null) mGMM.init(mGlyphMatrixCallback); }
    }
    private void resetVisualizerState() { mGlyphRenderer.resetState(mVisualizerConfig); }
    private int clampGlyphBrightness(int b) { return Math.max(0, Math.min(4500, b)); }
    
    // RESTORING MISSING METHODS
    public static int loadLatencyCompensationMs(Context context, int device) { return getPreferences(context).getInt("latency_device_" + device, 0); }
    public static int loadLatencyCompensationMs(Context context, int device, String routeKey) { if (routeKey == null || routeKey.isEmpty()) return loadLatencyCompensationMs(context, device); return getPreferences(context).getInt("latency_route_" + device + "_" + routeKey, loadLatencyCompensationMs(context, device)); }
    public static float loadGamma(Context context) { return getPreferences(context).getFloat("gamma", 2.2f); }
    private static SharedPreferences getPreferences(Context context) { return context.getSharedPreferences("glyph_visualizer_prefs", Context.MODE_PRIVATE); }

    public static boolean isHapticEnabledGlobal(Context context) {
        return context.getSharedPreferences("viz_prefs", MODE_PRIVATE).getBoolean("haptic_motor_enabled", false);
    }

    public static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, AudioCaptureService.class);
        intent.setAction(ACTION_STOP);
        return intent;
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
    private static List<String> getAllPresetKeys(JSONObject root) { ArrayList<String> res = new ArrayList<>(); JSONArray names = root.names(); if (names != null) for (int i = 0; i < names.length(); i++) { String key = names.optString(i, ""); res.add(key); } Collections.sort(res); return res; }
    private static List<PresetInfo> buildPresetInfos(JSONObject root, List<String> keys) { ArrayList<PresetInfo> res = new ArrayList<>(); for (String key : keys) { JSONObject p = root.optJSONObject(key); if (p != null) res.add(new PresetInfo(key, p.optString("description", key))); } return res; }
    private static List<String> getPresetKeysForPhoneModel(JSONObject root, String phoneModel) { ArrayList<String> res = new ArrayList<>(); if (PHONE_MODEL_UNKNOWN.equals(phoneModel)) return res; JSONArray names = root.names(); if (names != null) for (int i = 0; i < names.length(); i++) { String key = names.optString(i, ""); JSONObject p = root.optJSONObject(key); if (p != null && phoneModel.equalsIgnoreCase(p.optString("phone_model", ""))) res.add(key); } Collections.sort(res); return res; }

    private static float parseOptionalPercent(JSONArray arr, int idx) { if (idx >= arr.length()) return Float.NaN; Object r = arr.opt(idx); if (r == null || r == JSONObject.NULL) return Float.NaN; try { float v; if (r instanceof Number n) v = n.floatValue(); else { String t = String.valueOf(r).trim(); if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim(); v = Float.parseFloat(t); } if (v >= 0f && v <= 1f) v *= 100f; return v; } catch (Exception ignored) { return Float.NaN; } }
    private void refreshLatencyForCurrentAudioRoute() {}
    public static boolean hasHapticMotor(Context context) { return true; }
    public static boolean hasFlashlight(Context context) { return true; }
    public static void requestWidgetRefresh(Context context) { Intent intent = new Intent("com.better.nothing.music.vizualizer.REFRESH_WIDGET"); intent.setPackage(context.getPackageName()); context.sendBroadcast(intent); }
    private void requestWidgetRefresh() { requestWidgetRefresh(this); }

    public void setHapticEnabled(boolean enabled) { mHapticEnabled = enabled; }
    public void setFlashlightEnabled(boolean enabled) { mFlashlightEnabled = enabled; }
    public void setMaxBrightness(int brightness) { mMaxBrightness = brightness; applyEffectiveMaxBrightness(); }
    public void setIdleBreathingEnabled(boolean enabled) { mIdleBreathingEnabled = enabled; }
    public void setPreset(String preset) { mPresetKey = preset; restartCapture(); }
}
