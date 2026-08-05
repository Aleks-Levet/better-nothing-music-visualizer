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
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.graphics.PixelFormat;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.better.nothing.music.vizualizer.ui.VisualizerOverlayView;
import com.better.nothing.music.vizualizer.ui.EdgeVisualizerView;
import com.better.nothing.music.vizualizer.ui.LensVisualizerView;

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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
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
    public enum CaptureSource { INTERNAL, MIC, VIZUALIZER }
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
    public static final float DEFAULT_GAMMA = 2.2f;

    private static final String PREFS_NAME = "glyph_visualizer_prefs";
    private static final String APP_PREFS_NAME = "viz_prefs";
    private static final int MAX_GLYPH_BRIGHTNESS = 10000;

    private static final String DEFAULT_PRESET_KEY = "np1";
    private static final int SAMPLE_RATE = 44100;
    private static final int[] EMPTY_FFT = new int[512];
    private int mCurrentSampleRate = SAMPLE_RATE;
    private static final int FPS = 60;
    private static final long MIN_SEND_INTERVAL_MS = 16L;
    private static final long PROJECTION_SETTLE_DELAY_MS = 500L;

    private static volatile boolean sIsRunning = false;
    private static final MutableStateFlow<Boolean> sIsRunningFlow = StateFlowKt.MutableStateFlow(false);
    
    public StateFlow<Boolean> isRunningFlow() { return sIsRunningFlow; }

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

    private final IBinder mBinder = new LocalBinder();
    private final Object mCaptureLock = new Object();
    private final MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() { stopCapture(); stopSelf(); }
    };
    private final GlyphManager.Callback mGlyphCallback = new GlyphManager.Callback() {
        @Override public void onServiceConnected(ComponentName name) { registerGlyphManager(); ensureGlyphSession(); }
        @Override public void onServiceDisconnected(ComponentName name) { mSessionOpen = false; }
    };

    private final GlyphMatrixManager.Callback mGlyphMatrixCallback = new GlyphMatrixManager.Callback() {
        @Override public void onServiceConnected(ComponentName name) { registerGlyphMatrixManager(); }
        @Override public void onServiceDisconnected(ComponentName name) {}
    };

    private void registerGlyphManager() {
        if (mGM == null || mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return;
        String deviceStr = switch (mSelectedDevice) {
            case DeviceProfile.DEVICE_NP1 -> Glyph.DEVICE_20111;
            case DeviceProfile.DEVICE_NP2 -> Glyph.DEVICE_22111;
            case DeviceProfile.DEVICE_NP2A -> Build.MODEL.contains("23113") ? "23113" : Glyph.DEVICE_23111;
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
        if (mSelectedDevice == DeviceProfile.DEVICE_NP3) mGMM.register(Glyph.DEVICE_23112);
        else if (mSelectedDevice == DeviceProfile.DEVICE_NP4APRO) mGMM.register(Glyph.DEVICE_25111p);
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
    private List<String> mAvailablePresetKeys = Collections.emptyList();
    private int mSelectedDevice = DeviceProfile.DEVICE_UNKNOWN;
    private volatile int mLatencyCompensationMs = 0;
    private final AtomicInteger mPresetConfigVersion = new AtomicInteger(0);
    private volatile float mGamma = DEFAULT_GAMMA;
    private volatile int mMaxBrightness = 10000;

    private boolean mIdleBreathingEnabled = false;
    private boolean mDisableGlyphsWhenSilent = false;
    private boolean mGlyphDecayEnabled = true;
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

    public void setLensVisualizerEnabled(boolean enabled) { mLensVisualizerEnabled = enabled; updateOverlayVisibility(); requestWidgetRefresh(); }
    public void setLensVisualizerRadius(float r) { mLensVisualizerRadius = r; if (mLensVisualizerView != null) mLensVisualizerView.setRadius(r * 4); }
    public void setLensVisualizerX(float x) { mLensVisualizerX = x; if (mLensVisualizerView != null) mLensVisualizerView.setXPosition(x); }
    public void setLensVisualizerY(float y) { mLensVisualizerY = y; if (mLensVisualizerView != null) mLensVisualizerView.setYPosition(y); }
    public void setLensVisualizerBarWidth(float w) { mLensVisualizerBarWidth = w; if (mLensVisualizerView != null) mLensVisualizerView.setBarWidth(w * 4); }
    public void setLensVisualizerMaxHeight(float h) { mLensVisualizerMaxHeight = h; if (mLensVisualizerView != null) mLensVisualizerView.setMaxHeight(h * 4); }
    public void setLensVisualizerBarCount(int c) { mLensVisualizerBarCount = c; if (mLensVisualizerView != null) mLensVisualizerView.setBarCount(c); }
    public void setLensVisualizerSensitivity(float s) { mLensVisualizerSensitivity = s; if (mLensVisualizerView != null) mLensVisualizerView.setSensitivity(s); }
    
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
    private volatile int[] mLatestRawFFT = new int[512];
    private volatile int[] mLatestDecayedFFT = new int[512];
    private final Object mFftLock = new Object();

    public int[] getLatestRawFFT() { synchronized (mFftLock) { return mLatestRawFFT; } }
    public int[] getLatestDecayedFFT() { synchronized (mFftLock) { return mLatestDecayedFFT; } }
    public int[] getLatestMagnitudes() { return getLatestDecayedFFT(); }

    public float[] getCurrentLightState() {
        if (mGlyphRenderer != null) return mGlyphRenderer.getCurrentLightState();
        return new float[0];
    }
    public boolean isVisualizerRunning() { return sIsRunning; }

    public float getLatestHapticPeak() {
        int[] fft = getLatestRawFFT();
        if (mHapticRange == null) return 0f;
        int max = 0;
        for (int i = mHapticRange.logBinLo; i <= mHapticRange.logBinHi; i++) if (fft[i] > max) max = fft[i];
        return max / 4095f;
    }

    public float getLatestUiPeak() {
        int[] fft = getLatestDecayedFFT();
        int max = 0;
        int binLo = AudioProcessor.findLogBinIndex(80f);
        int binHi = AudioProcessor.findLogBinIndex(120f);
        for (int i = binLo; i <= binHi; i++) if (fft[i] > max) max = fft[i];
        return max / 4095f;
    }

    public float getLatestFlashlightPeak() {
        int[] fft = getLatestRawFFT();
        if (mFlashlightRange == null) return 0f;
        int max = 0;
        for (int i = mFlashlightRange.logBinLo; i <= mFlashlightRange.logBinHi; i++) if (fft[i] > max) max = fft[i];
        return max / 4095f;
    }

    public long getCaptureDurationMs() { if (!sIsRunning || mCaptureStartTimeMs == 0) return 0; return SystemClock.elapsedRealtime() - mCaptureStartTimeMs; }
    private long mLastAudioActivityMs = 0L;
    private long mLastNotifUpdateMs = 0L;
    private final Handler mMainHandler = new Handler(android.os.Looper.getMainLooper());
    private final Runnable mIdlePulseRunnable = new Runnable() {
        @Override public void run() {
            if (sIsRunning) {
                long now = SystemClock.elapsedRealtime();
                if (now - mLastNotifUpdateMs >= 1000) { refreshNotification(); mLastNotifUpdateMs = now; }
                if (mCaptureSource == CaptureSource.VIZUALIZER) {
                    synchronized (mVisualizerPendingFrames) { dispatchDueFrames(mVisualizerPendingFrames); }
                    if (now - mLastSendMs >= 16 && mVisualizerConfig != null) processFrame(EMPTY_FFT, EMPTY_FFT, mVisualizerConfig, mPresetConfigVersion.get());
                } else if (mIdleBreathingEnabled && mSessionOpen && mVisualizerConfig != null) {
                    if (now - mLastAudioActivityMs > 100) processFrame(EMPTY_FFT, EMPTY_FFT, mVisualizerConfig, mPresetConfigVersion.get());
                }
                mMainHandler.postDelayed(this, 16);
            }
        }
    };

    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { refreshLatencyForCurrentAudioRoute(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { refreshLatencyForCurrentAudioRoute(); }
    };

    private void applyEffectiveMaxBrightness() { if (mGlyphRenderer != null) mGlyphRenderer.setMaxBrightness(mMaxBrightness); }

    private static final class PendingFrame {
        final int[] fftraw;
        final int[] fftdecayed;
        final AudioProcessor.VisualizerConfig config;
        final int configVersion;
        final long dueAtMs;

        PendingFrame(int[] fftraw, int[] fftdecayed, AudioProcessor.VisualizerConfig config, int configVersion, long dueAtMs) {
            this.fftraw = fftraw;
            this.fftdecayed = fftdecayed;
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

    public static List<PresetInfo> loadPresetInfos(Context c, int deviceType) {
        try {
            JSONObject root = loadZonesConfigRoot(c);
            String pm = phoneModelForDevice(deviceType);
            List<String> keys = getPresetKeysForPhoneModel(root, pm);
            if (keys.isEmpty()) {
                keys = getAllPresetKeys(root);
            }
            return buildPresetInfos(root, keys);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load preset infos", e);
            return Collections.emptyList();
        }
    }

    public class LocalBinder extends Binder { public AudioCaptureService getService() { return AudioCaptureService.this; } }

    private AudioDeviceManager mAudioDeviceManager;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mWorkerThread = new HandlerThread("GlyphVizWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper());
        mAudioManager = getSystemService(AudioManager.class);
        if (mAudioManager != null) mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, mWorkerHandler);
        mContinuousHapticEngine = new ContinuousHapticEngine(this);
        mBeatDetectionEngine = new BeatDetectionHapticEngine(this);
        mFlashlightEngine = new FlashlightEngine(this);
        mAudioProcessor = new AudioProcessor();
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
        mOverlayWidth = appPrefs.getInt("overlay_width", 120);
        mOverlayHeight = appPrefs.getInt("overlay_height", 12);
        mOverlayYOffset = appPrefs.getInt("overlay_y_offset", 2);
        mOverlaySensitivity = appPrefs.getFloat("overlay_sensitivity", 1.0f);

        AudioProcessor.ReadMethod readMethod;
        try {
            readMethod = AudioProcessor.ReadMethod.valueOf(appPrefs.getString("fft_read_method", AudioProcessor.ReadMethod.MAX.name()));
        } catch (Exception e) {
            readMethod = AudioProcessor.ReadMethod.MAX;
        }
        mAudioProcessor.setManualGain(appPrefs.getFloat("spectrum_gain", 4.0f));

        mGlyphRenderer = new GlyphRenderer(mGamma, mIdleBreathingEnabled, mSelectedDevice);
        mGlyphRenderer.setMaxBrightness(mMaxBrightness);
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

    @Override public IBinder onBind(Intent intent) { return mBinder; }

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
                boolean nextEnabled = !mHapticEnabled;
                setHapticMotorEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("haptic_motor_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_TORCH.equals(action)) {
                boolean nextEnabled = !mFlashlightEnabled;
                setFlashlightEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("flashlight_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_GLYPHS.equals(action)) {
                int nextVal;
                if (mMaxBrightness > 0) nextVal = 0;
                else nextVal = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getInt("max_brightness_last", MAX_GLYPH_BRIGHTNESS);
                
                setMaxBrightness(nextVal);
                SharedPreferences.Editor editor = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit();
                editor.putInt("max_brightness", nextVal);
                if (nextVal > 0) editor.putInt("max_brightness_last", nextVal);
                editor.apply();
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
        });
    }

    public void setDevice(int device) {
        if (mSelectedDevice != device) {
            mSelectedDevice = device;
            if (mGlyphRenderer != null) mGlyphRenderer.setDeviceType(device);
            if (device != DeviceProfile.DEVICE_UNKNOWN && Build.VERSION.SDK_INT >= 31) ensureGlyphManagerInitialized();
            registerGlyphManager();
            registerGlyphMatrixManager();
            setLatencyCompensationMs(loadLatencyCompensationMs(this, device));
            reloadConfig();
            if (sIsRunning) restartCapture();
        }
    }

    private void ensureGlyphManagerInitialized() {
        if (mGM == null && Build.VERSION.SDK_INT >= 31) {
            try {
                mGM = GlyphManager.getInstance(getApplicationContext());
                if (mGM != null) mGM.init(mGlyphCallback);
            } catch (Exception e) { Log.e(TAG, "Failed to initialize GlyphManager", e); }
        }
        if (mGMM == null && Build.VERSION.SDK_INT >= 31) {
            try {
                mGMM = GlyphMatrixManager.getInstance(getApplicationContext());
                if (mGMM != null) mGMM.init(mGlyphMatrixCallback);
            } catch (Exception e) { Log.e(TAG, "Failed to initialize GlyphMatrixManager", e); }
        }
    }

    public void setLatencyMs(int latencyMs) { setLatencyCompensationMs(latencyMs); }
    public void setReadMethod(AudioProcessor.ReadMethod method) { }
    public void setLatencyCompensationMs(int latencyMs) { if (mLatencyCompensationMs != latencyMs) { mLatencyCompensationMs = latencyMs; mPresetConfigVersion.incrementAndGet(); } }
    public void setGamma(float gamma) { mGamma = gamma; if (mGlyphRenderer != null) mGlyphRenderer.setGamma(gamma); }
    public void setSpectrumGain(float gain) { if (mAudioProcessor != null) mAudioProcessor.setManualGain(gain); }
    public void setSelectedPreset(String presetKey) { applyPresetSelection(presetKey); }
    public void setHapticMotorEnabled(boolean enabled) { mHapticEnabled = hasHapticMotor(this) && enabled; requestWidgetRefresh(); }
    public void setHapticMode(HapticMode mode) { mHapticMode = mode; requestWidgetRefresh(); }

    public void setMaxBrightness(int brightness) {
        int clamped = clampGlyphBrightness(brightness);
        final int targetBrightness = clamped;
        final boolean reopeningAfterEnable = mMaxBrightness <= 0 && targetBrightness > 0;
        mMaxBrightness = clamped;
        if (mWorkerHandler == null) return;
        mWorkerHandler.post(() -> {
            applyEffectiveMaxBrightness();
            if (targetBrightness <= 0) { clearGlyphSession(); return; }
            if (reopeningAfterEnable) { clearGlyphSession(); ensureGlyphSession(); mLastSendMs = 0; } else ensureGlyphSession();
        });
        requestWidgetRefresh();
    }

    public void setIdleBreathingEnabled(boolean enabled) {
        mIdleBreathingEnabled = enabled;
        if (mGlyphRenderer != null) mGlyphRenderer.setIdleBreathingEnabled(enabled);
    }

    public void setIdlePattern(String pattern) { if (mGlyphRenderer != null) mGlyphRenderer.setIdlePattern(pattern); }
    public void setDisableGlyphsWhenSilent(boolean enabled) {
        mDisableGlyphsWhenSilent = enabled;
        if (!enabled && !mSessionOpen && mGM != null) mWorkerHandler.post(this::ensureGlyphSession);
    }

    public void setGlyphDecayEnabled(boolean enabled) {
        mGlyphDecayEnabled = enabled;
    }

    public void setOverlayEnabled(boolean enabled) { mOverlayEnabled = enabled; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); requestWidgetRefresh(); }
    public void setOverlayTopEnabled(boolean enabled) { mOverlayTopEnabled = enabled; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); requestWidgetRefresh(); }
    public void setOverlayBottomEnabled(boolean enabled) { mOverlayBottomEnabled = enabled; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); requestWidgetRefresh(); }
    public void setOverlayWidth(int width) { mOverlayWidth = width; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); }
    public void setOverlayHeight(int height) { mOverlayHeight = height; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); }
    public void setOverlayHeightBottom(int height) { mOverlayHeightBottom = height; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); }
    public void setOverlayYOffset(int offset) { mOverlayYOffset = offset; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); }
    public void setOverlaySensitivity(float s) { mOverlaySensitivity = s; if (mOverlayView != null) mMainHandler.post(() -> mOverlayView.setTopSensitivity(s)); }
    public void setOverlaySensitivityBottom(float s) { mOverlaySensitivityBottom = s; if (mOverlayView != null) mMainHandler.post(() -> mOverlayView.setBottomSensitivity(s)); }

    public void setEdgeVisualizerEnabled(boolean enabled) { mEdgeVisualizerEnabled = enabled; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); requestWidgetRefresh(); }
    public void setEdgeThickness(int thickness) { mEdgeThickness = thickness; if (mEdgeVisualizerView != null) mMainHandler.post(() -> mEdgeVisualizerView.setThickness(thickness)); }
    public void setEdgeSensitivity(float sensitivity) { mEdgeSensitivity = sensitivity; if (mEdgeVisualizerView != null) mMainHandler.post(() -> mEdgeVisualizerView.setSensitivity(sensitivity)); }
    public void setEdgeBarCounts(int horiz, int vert) { mEdgeBarCountHoriz = horiz; mEdgeBarCountVert = vert; if (mEdgeVisualizerView != null) mMainHandler.post(() -> mEdgeVisualizerView.setBarCounts(horiz, vert)); }
    public void setEdgeCornerRadius(float radius) { mEdgeCornerRadius = radius; if (mEdgeVisualizerView != null) mMainHandler.post(() -> mEdgeVisualizerView.setScreenRadius(radius * 4)); }
    public void setEdgeTopEnabled(boolean enabled) { mEdgeTopEnabled = enabled; if (mEdgeVisualizerView != null) mMainHandler.post(() -> mEdgeVisualizerView.setTopEnabled(enabled)); }
    public void setEdgeBottomEnabled(boolean enabled) { mEdgeBottomEnabled = enabled; if (mEdgeVisualizerView != null) mMainHandler.post(() -> mEdgeVisualizerView.setBottomEnabled(enabled)); }

    public void reloadConfig() {
        if (mWorkerHandler != null) {
            mWorkerHandler.post(() -> {
                try {
                    refreshPresetCatalog();
                    mVisualizerConfig = loadVisualizerConfig(mPresetKey, mCurrentSampleRate);
                    mPresetConfigVersion.incrementAndGet();
                    resetVisualizerState();
                } catch (Exception e) { Log.e(TAG, "Failed to reload config", e); }
            });
        }
    }

    public void setOverlayColor(int color) { mOverlayColor = color; if (mOverlayView != null) mMainHandler.post(() -> mOverlayView.setColor(color)); }
    private void updateVisualizerService() { Intent intent = new Intent(this, VisualizerService.class); if (mLensVisualizerEnabled && sIsRunning) startService(intent); else stopService(intent); }

    public void setHapticEnabled(boolean enabled) {
        mHapticEnabled = hasHapticMotor(this) && enabled;
        if (!mHapticEnabled) { if (mContinuousHapticEngine != null) mContinuousHapticEngine.stopHaptics(); if (mBeatDetectionEngine != null) mBeatDetectionEngine.stopHaptics(); }
        requestTileRefresh(); requestWidgetRefresh();
    }

    public void setHapticFreqRange(float min, float max) { mHapticMinHz = min; mHapticMaxHz = max; if (mBeatDetectionEngine != null) mBeatDetectionEngine.resetDetectionState(); }
    public void setHapticMultiplier(float m) { if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticMultiplier(m); if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticMultiplier(m); }
    public void setHapticAudioGain(float g) { mHapticAudioGain = g; if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticAudioGain(g); }
    public void setHapticGamma(float g) { if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticGamma(g); }
    public void setHapticBeatSensitivity(float s) { mHapticBeatSensitivity = s; if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticSensitivity(s); }
    public void setHapticBeatGamma(float g) { mHapticBeatGamma = g; if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticGamma(g); }

    public void setFlashlightEnabled(boolean enabled) { mFlashlightEnabled = hasFlashlight(this) && enabled; if (!mFlashlightEnabled && mFlashlightEngine != null) mFlashlightEngine.stopFlashlight(); requestWidgetRefresh(); }
    public void setFlashlightFreqRange(float min, float max) { mFlashlightMinHz = min; mFlashlightMaxHz = max; }
    public void setFlashlightThreshold(float t) { mFlashlightThreshold = t; if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightThreshold(t); }
    public void setFlashlightMode(TorchMode m) { mFlashlightMode = m; if (mFlashlightEngine != null) mFlashlightEngine.setTorchMode(m); }
    public void setFlashlightBeatSensitivity(float s) { mFlashlightBeatSensitivity = s; if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightBeatSensitivity(s); }
    public void setFlashlightSpeedMs(float s) { mFlashlightSpeedMs = s; if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightSpeedMs(s); }

    public void setFlashlightMultiIntensityForced(boolean forced) { if (mFlashlightEngine != null) { mFlashlightEngine.setForceMultiIntensity(forced); mFlashlightIntensityLevels = mFlashlightEngine.getTorchIntensityLevels(); } }
    public int getFlashlightIntensityLevels() { return mFlashlightEngine != null ? mFlashlightEngine.getTorchIntensityLevels() : (mFlashlightIntensityLevels > 0 ? mFlashlightIntensityLevels : 1); }
    public int getFlashlightCurrentLevel() { return mFlashlightEngine != null ? mFlashlightEngine.getCurrentLevel() : 0; }

    public void startCapture(int resultCode, Intent data) { startCaptureInternal(CaptureSource.INTERNAL, resultCode, data); }
    public void startMicCapture() { startCaptureInternal(CaptureSource.MIC, 0, null); }
    public void startVizualizerCapture() { startCaptureInternal(CaptureSource.VIZUALIZER, 0, null); }

    private void startCaptureInternal(CaptureSource source, int resultCode, Intent data) {
        mCaptureSource = source;
        MediaProjectionManager pm = null;
        if (source == CaptureSource.INTERNAL) pm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        synchronized (mCaptureLock) {
            stopCaptureLocked();
            if (source == CaptureSource.INTERNAL) {
                if (pm == null) return;
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION); else startForeground(NOTIF_ID, buildNotification());
                mProjection = pm.getMediaProjection(resultCode, data);
                if (mProjection == null) { stopForeground(STOP_FOREGROUND_REMOVE); setRunning(false); return; }
                mProjection.registerCallback(mProjectionCallback, mWorkerHandler);
            } else if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); else startForeground(NOTIF_ID, buildNotification());
            mCapturing = true; setRunning(true); updateOverlayVisibility(); mCaptureStartTimeMs = SystemClock.elapsedRealtime();
            ensureCaptureExecutor();
            mCaptureExecutor.execute(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                SystemClock.sleep(PROJECTION_SETTLE_DELAY_MS);
                AudioRecord lr = null;
                try {
                    mCurrentSampleRate = (source == CaptureSource.MIC) ? SAMPLE_RATE : 48000;
                    int csr = mCurrentSampleRate;
                    int bs = Math.max(AudioRecord.getMinBufferSize(csr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096);
                    if (source == CaptureSource.INTERNAL) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mProjection != null) {
                            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                AudioPlaybackCaptureConfiguration cfg = new AudioPlaybackCaptureConfiguration.Builder(mProjection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
                                lr = new AudioRecord.Builder().setAudioPlaybackCaptureConfig(cfg).setAudioFormat(new AudioFormat.Builder().setSampleRate(csr).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(bs).build();
                            }
                        }
                    } else if (source == CaptureSource.VIZUALIZER) { setupVisualizerCapture(); return; }
                    else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) lr = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bs);
                    if (lr != null && lr.getState() == AudioRecord.STATE_INITIALIZED) {
                        mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz);
                        mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz);
                        synchronized (mCaptureLock) { if (!mCapturing) { lr.release(); return; } mAudioRecord = lr; }
                        lr.startRecording(); runCaptureLoop(lr);
                    }
                } catch (Exception e) { Log.e(TAG, "Capture failed", e); stopSelf(); }
                finally { synchronized (mCaptureLock) { releaseAudioRecord(); } }
            });
        }
        refreshNotification();
    }

    public void stopCapture() { synchronized (mCaptureLock) { stopCaptureLocked(); } }
    private void stopCaptureLocked() {
        mCapturing = false; setRunning(false); updateOverlayVisibility();
        shutdownCaptureExecutor(); releaseAudioRecord(); releaseVisualizer(); releaseProjection();
        turnOffGlyphs(); resetVisualizerState(); stopForeground(STOP_FOREGROUND_REMOVE);
    }
    private void releaseAudioRecord() { if (mAudioRecord != null) { try { mAudioRecord.stop(); } catch (Exception ignored) {} mAudioRecord.release(); mAudioRecord = null; } }
    private void releaseProjection() { if (mProjection != null) { try { mProjection.stop(); } catch (Exception ignored) {} mProjection = null; } }
    private void releaseVisualizer() { if (mVisualizer != null) { try { mVisualizer.release(); } catch (Exception ignored) {} mVisualizer = null; } synchronized (mVisualizerPendingFrames) { mVisualizerPendingFrames.clear(); } }
    private void ensureCaptureExecutor() { if (mCaptureExecutor == null || mCaptureExecutor.isShutdown()) mCaptureExecutor = Executors.newSingleThreadExecutor(); }
    private void shutdownCaptureExecutor() { if (mCaptureExecutor != null) { mCaptureExecutor.shutdownNow(); mCaptureExecutor = null; } }

    private void processFrame(int[] fftraw, int[] fftdecayed, AudioProcessor.VisualizerConfig config, int configVersion) {
        if (config == null || configVersion != mPresetConfigVersion.get()) return;
        try {
            long now = SystemClock.elapsedRealtime(); 
            boolean hasActivity = false;
            if (fftraw != null && fftraw.length > 0) { for (int val : fftraw) if (val > 20) { hasActivity = true; break; } }
            
            // Only interact with Glyph library if output is enabled (brightness > 0)
            if (mMaxBrightness > 0) {
                if (hasActivity || mIdleBreathingEnabled) { 
                    mLastAudioActivityMs = now; 
                    if (!mSessionOpen) ensureGlyphSession(); 
                } else if (mDisableGlyphsWhenSilent && mSessionOpen && (now - mLastAudioActivityMs > 3000)) {
                    clearGlyphSession();
                }

                if (mSessionOpen && (now - mLastSendMs >= MIN_SEND_INTERVAL_MS)) {
                    int[] fftToUse = mGlyphDecayEnabled ? fftdecayed : fftraw;
                    int[] frameColors = mGlyphRenderer.processFrame(fftToUse, config, now);
                    if (frameColors != null) {
                        try {
                            if (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0) {
                                if (mGMM != null) mGMM.setAppMatrixFrame(frameColors);
                            } else if (mGM != null) {
                                mGM.setFrameColors(frameColors);
                            }
                            mLastSendMs = now;
                        } catch (Exception ignored) {}
                    }
                }
            } else if (mSessionOpen) {
                clearGlyphSession();
            }
        } catch (Exception e) { Log.e(TAG, "processFrame error", e); }
    }

    private void dispatchDueFrames(ArrayDeque<PendingFrame> pendingFrames) {
        if (pendingFrames == null) return; long nowMs = SystemClock.elapsedRealtime(); PendingFrame latestDueFrame = null;
        while (!pendingFrames.isEmpty()) { PendingFrame frame = pendingFrames.peekFirst(); if (frame == null || frame.dueAtMs > nowMs) break; latestDueFrame = pendingFrames.removeFirst(); }
        if (latestDueFrame != null) {
            try {
                synchronized (mFftLock) {
                    mLatestRawFFT = latestDueFrame.fftraw;
                    mLatestDecayedFFT = latestDueFrame.fftdecayed;
                }

                if (mOverlayView != null) mOverlayView.updateMagnitudes(mLatestDecayedFFT);
                if (mEdgeVisualizerView != null) mEdgeVisualizerView.updateMagnitudes(mLatestDecayedFFT);
                if (mLensVisualizerView != null) mLensVisualizerView.updateMagnitudes(mLatestDecayedFFT);
                
                if (mHapticEnabled) {
                    if (mHapticMode == HapticMode.BASS_TO_AMPLITUDE) {
                        if (mContinuousHapticEngine != null) mContinuousHapticEngine.performHapticFeedback(getLatestHapticPeak(), latestDueFrame.config);
                    } else if (mBeatDetectionEngine != null) {
                        mBeatDetectionEngine.performHapticFeedback(mLatestRawFFT, mHapticRange);
                    }
                }
                if (mFlashlightEnabled && mFlashlightEngine != null) {
                    mFlashlightEngine.performFlashlightFeedback(getLatestFlashlightPeak(), latestDueFrame.config, mLatestRawFFT, mFlashlightRange != null ? mFlashlightRange.logBinLo : 0, mFlashlightRange != null ? mFlashlightRange.logBinHi : 0);
                }
                processFrame(latestDueFrame.fftraw, latestDueFrame.fftdecayed, latestDueFrame.config, latestDueFrame.configVersion);
            } catch (Exception e) { Log.e(TAG, "Error dispatching frame", e); }
        }
    }
    private void setupVisualizerCapture() {
        releaseVisualizer(); SystemClock.sleep(250);
        try {
            mAudioProcessor.updateFFTSize();
            mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz);
            mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz);
            mVisualizer = new Visualizer(0); int captureSize = Math.min(Visualizer.getCaptureSizeRange()[1], 1024); mVisualizer.setCaptureSize(captureSize);
            mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int sr) { processVisualizerWaveform(w, sr); }
                @Override public void onFftDataCapture(Visualizer v, byte[] f, int sr) {}
            }, Visualizer.getMaxCaptureRate(), true, false);
            mVisualizer.setEnabled(true);
        } catch (Exception e) { releaseVisualizer(); }
    }

    private void processVisualizerWaveform(byte[] waveform, int samplingRate) {
        if (!mCapturing || mVisualizerConfig == null) return;
        mAudioProcessor.updateFFTSize(samplingRate / 1000);
        short[] hop = new short[waveform.length]; for (int i = 0; i < waveform.length; i++) hop[i] = (short) (((waveform[i] & 0xFF) - 128) << 8);
        AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, false, mVisualizerConfig.decay);
        if (result == null) return;
        PendingFrame frame = new PendingFrame(result.fftraw, result.fftdecayed, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
        synchronized (mVisualizerPendingFrames) { mVisualizerPendingFrames.addLast(frame); dispatchDueFrames(mVisualizerPendingFrames); }
    }

    private void runCaptureLoop(AudioRecord record) {
        mAudioProcessor.updateFFTSize(record.getSampleRate());
        int hopSize = Math.round(record.getSampleRate() / (float) FPS); short[] hop = new short[hopSize];
        while (mCapturing) {
            int read = record.read(hop, 0, hopSize, AudioRecord.READ_BLOCKING); if (read <= 0) continue;
            AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, true, mVisualizerConfig != null ? mVisualizerConfig.decay : 0.85f);
            if (result == null) continue;
            PendingFrame frame = new PendingFrame(result.fftraw, result.fftdecayed, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
            synchronized(mVisualizerPendingFrames) { mVisualizerPendingFrames.addLast(frame); dispatchDueFrames(mVisualizerPendingFrames); }
        }
    }

    private void turnOffGlyphs() {
        if (mGM != null && mSessionOpen) { int count = resolveGlyphCount(); if (count > 0) try { mGM.setFrameColors(new int[count]); } catch (Exception ignored) {} try { mGM.turnOff(); } catch (Exception ignored) {} }
        if (mGMM != null) { int size = DeviceProfile.getMatrixWidth(mSelectedDevice) * DeviceProfile.getMatrixHeight(mSelectedDevice); if (size > 0) try { mGMM.setAppMatrixFrame(new int[size]); } catch (Exception ignored) {} }
    }

    private void ensureGlyphSession() { if (mGM == null || mSessionOpen || mMaxBrightness <= 0) return; try { mGM.openSession(); mSessionOpen = true; } catch (Exception e) { Log.e(TAG, "Failed to open Glyph session", e); } }

    private void clearGlyphSession() { try { turnOffGlyphs(); if (mGM != null && mSessionOpen) { try { mGM.closeSession(); } catch (Exception ignored) {} mSessionOpen = false; } } catch (Exception ignored) {} }

    private boolean canPushGlyphFrames() { if (mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return false; if (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0) return mGMM != null; return mGM != null && mSessionOpen; }

    private int resolveGlyphCount() { return mVisualizerConfig != null ? mVisualizerConfig.zones.length : DeviceProfile.getLedCount(mSelectedDevice); }

    private Notification buildNotification() {
        ensureNotificationChannel(); SharedPreferences appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE); String buttonSet = appPrefs.getString("notification_button_set", "presets"); PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT); String content = (mMaxBrightness > 0 && mVisualizerConfig != null ? mVisualizerConfig.description + " • " : "") + formatDuration(getCaptureDurationMs()); NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Glyph Visualizer").setContentText(content).setSmallIcon(com.better.nothing.music.vizualizer.R.drawable.ic_notif_monochrome).setContentIntent(contentIntent).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).setCategory(NotificationCompat.CATEGORY_SERVICE).setOnlyAlertOnce(true).setOngoing(true).setSilent(true);
        if ("controls".equals(buttonSet)) {
            builder.addAction(0, mMaxBrightness > 0 ? "GLYPHS" : "glyphs", PendingIntent.getService(this, 10, new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_GLYPHS), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            builder.addAction(0, mHapticEnabled ? "HAPTICS" : "haptics", PendingIntent.getService(this, 11, new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_HAPTICS), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            builder.addAction(0, mFlashlightEnabled ? "FLASH" : "flash", PendingIntent.getService(this, 12, new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_TORCH), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            builder.addAction(android.R.drawable.ic_media_previous, "Prev", PendingIntent.getService(this, 2, new Intent(this, AudioCaptureService.class).setAction(ACTION_PREV_PRESET), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            builder.addAction(android.R.drawable.ic_media_pause, "Stop", PendingIntent.getService(this, 1, createStopIntent(this), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            builder.addAction(android.R.drawable.ic_media_next, "Next", PendingIntent.getService(this, 3, new Intent(this, AudioCaptureService.class).setAction(ACTION_NEXT_PRESET), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        return builder.build();
    }

    private String formatDuration(long ms) { long s = (ms / 1000) % 60; long m = (ms / 60000) % 60; long h = (ms / 3600000); return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%02d:%02d", m, s); }
    private void ensureNotificationChannel() { NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Glyph Visualizer", NotificationManager.IMPORTANCE_LOW)); }
    private void refreshNotification() { if (mCapturing) { NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null) nm.notify(NOTIF_ID, buildNotification()); } }

    private void updateOverlayVisibility() {
        mMainHandler.post(() -> {
            if (mWindowManager == null) mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (mEdgeVisualizerEnabled && mCapturing) {
                if (mEdgeVisualizerView == null) {
                    mEdgeVisualizerView = new EdgeVisualizerView(this);
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.TOP | Gravity.START;
                    if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    try { mWindowManager.addView(mEdgeVisualizerView, params); } catch (Exception ignored) {}
                }
                mEdgeVisualizerView.setThickness(mEdgeThickness); mEdgeVisualizerView.setSensitivity(mEdgeSensitivity); mEdgeVisualizerView.setBarCounts(mEdgeBarCountHoriz, mEdgeBarCountVert); mEdgeVisualizerView.setTopEnabled(mEdgeTopEnabled); mEdgeVisualizerView.setBottomEnabled(mEdgeBottomEnabled); mEdgeVisualizerView.setScreenRadius(mEdgeCornerRadius * 4);
            } else if (mEdgeVisualizerView != null) { try { mWindowManager.removeView(mEdgeVisualizerView); } catch (Exception ignored) {} mEdgeVisualizerView = null; }
            if (mOverlayEnabled && mCapturing) {
                if (mOverlayView == null) {
                    mOverlayView = new VisualizerOverlayView(this);
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(mOverlayWidth * 4, (mOverlayHeight + mOverlayHeightBottom) * 4, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; params.y = mOverlayYOffset * 4;
                    try { mWindowManager.addView(mOverlayView, params); } catch (Exception ignored) {}
                } else {
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) mOverlayView.getLayoutParams();
                    params.width = mOverlayWidth * 4; params.height = (mOverlayHeight + mOverlayHeightBottom) * 4; params.y = mOverlayYOffset * 4;
                    try { mWindowManager.updateViewLayout(mOverlayView, params); } catch (Exception ignored) {}
                }
                mOverlayView.setTopEnabled(mOverlayTopEnabled); mOverlayView.setBottomEnabled(mOverlayBottomEnabled); mOverlayView.setHeights(mOverlayHeight, mOverlayHeightBottom); mOverlayView.setTopSensitivity(mOverlaySensitivity); mOverlayView.setBottomSensitivity(mOverlaySensitivityBottom);
            } else if (mOverlayView != null) { try { mWindowManager.removeView(mOverlayView); } catch (Exception ignored) {} mOverlayView = null; }
            if (mLensVisualizerEnabled && mCapturing) {
                if (mLensVisualizerView == null) {
                    mLensVisualizerView = new LensVisualizerView(this);
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.TOP | Gravity.START;
                    if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    try { mWindowManager.addView(mLensVisualizerView, params); } catch (Exception ignored) {}
                }
                mLensVisualizerView.setRadius(mLensVisualizerRadius * 4); mLensVisualizerView.setBarWidth(mLensVisualizerBarWidth * 4); mLensVisualizerView.setMaxHeight(mLensVisualizerMaxHeight * 4); mLensVisualizerView.setBarCount(mLensVisualizerBarCount); mLensVisualizerView.setSensitivity(mLensVisualizerSensitivity); mLensVisualizerView.setXPosition(mLensVisualizerX); mLensVisualizerView.setYPosition(mLensVisualizerY);
            } else if (mLensVisualizerView != null) { try { mWindowManager.removeView(mLensVisualizerView); } catch (Exception ignored) {} mLensVisualizerView = null; }
            updateVisualizerService();
        });
    }

    private void requestTileRefresh() { TileService.requestListeningState(this, new ComponentName(this, "com.better.nothing.music.vizualizer.service.VisualizerTileService")); }
    public static void requestWidgetRefresh(Context context) { Intent intent = new Intent("com.better.nothing.music.vizualizer.REFRESH_WIDGET"); intent.setPackage(context.getPackageName()); context.sendBroadcast(intent); }
    private void requestWidgetRefresh() { requestWidgetRefresh(this); }
    public static int loadLatencyCompensationMs(Context context, int device) { return getPreferences(context).getInt("latency_device_" + device, 0); }
    public static int loadLatencyCompensationMs(Context context, int device, String routeKey) { if (routeKey == null || routeKey.isEmpty()) return loadLatencyCompensationMs(context, device); return getPreferences(context).getInt("latency_route_" + device + "_" + routeKey, loadLatencyCompensationMs(context, device)); }
    public static float loadGamma(Context context) { return getPreferences(context).getFloat("gamma", 2.2f); }
    private static SharedPreferences getPreferences(Context context) { return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); }
    public static boolean isHapticEnabledGlobal(Context context) { return context.getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getBoolean("haptic_motor_enabled", false); }
    public static Intent createStopIntent(Context context) { Intent intent = new Intent(context, AudioCaptureService.class); intent.setAction(ACTION_STOP); return intent; }
    private void refreshPresetCatalog() throws IOException, JSONException { JSONObject root = loadZonesConfigRoot(this); mAvailablePresetKeys = getPresetKeysForPhoneModel(root, phoneModelForDevice(mSelectedDevice)); if (mAvailablePresetKeys.isEmpty()) mAvailablePresetKeys = getAllPresetKeys(root); }

    private AudioProcessor.VisualizerConfig loadVisualizerConfig(String presetKey, int sampleRate) throws IOException, JSONException {
        JSONObject root = loadZonesConfigRoot(this); JSONObject p = root.optJSONObject(presetKey); if (p == null) throw new JSONException("Preset not found"); JSONArray za = p.optJSONArray("zones"); if (za == null || za.length() == 0) throw new JSONException("No zones"); double da = p.has("decay-alpha") ? p.optDouble("decay-alpha", 0.8) : root.optDouble("decay-alpha", 0.8); AudioProcessor.ZoneSpec[] zs = parseZoneSpecs(za); return buildVisualizerConfig(presetKey, p.optString("description", presetKey), da, zs);
    }

    private AudioProcessor.VisualizerConfig buildVisualizerConfig(String pk, String d, double da, AudioProcessor.ZoneSpec[] zs) {
        float ad = 0.86f + ((float) da / 10f); List<float[]> up = new ArrayList<>(); Set<String> sp = new HashSet<>();
        for (AudioProcessor.ZoneSpec z : zs) { String key = String.format(Locale.US, "%.4f|%.4f", z.lowHz, z.highHz); if (sp.add(key)) up.add(new float[]{z.lowHz, z.highHz}); }
        up.sort((l, r) -> Float.compare(l[0], r[0])); AudioProcessor.FrequencyRange[] ur = new AudioProcessor.FrequencyRange[up.size()];
        for (int i = 0; i < up.size(); i++) ur[i] = new AudioProcessor.FrequencyRange(up.get(i)[0], up.get(i)[1]);
        int[][] zr = new int[zs.length][];
        for (int z = 0; z < zs.length; z++) { ArrayList<Integer> os = new ArrayList<>(); for (int r = 0; r < ur.length; r++) if (!(ur[r].highHz < zs[z].lowHz || ur[r].lowHz > zs[z].highHz)) os.add(r); int[] m = new int[os.size()]; for (int i = 0; i < os.size(); i++) m[i] = os.get(i); zr[z] = m; }
        return new AudioProcessor.VisualizerConfig(pk, d, ad, zs, ur, zr);
    }

    private AudioProcessor.ZoneSpec[] parseZoneSpecs(JSONArray za) throws JSONException {
        AudioProcessor.ZoneSpec[] zs = new AudioProcessor.ZoneSpec[za.length()];
        for (int i = 0; i < za.length(); i++) { JSONArray z = za.getJSONArray(i); float lh = (float) z.getDouble(0); float hh = (float) z.getDouble(1); zs[i] = new AudioProcessor.ZoneSpec(Math.min(lh, hh), Math.max(lh, hh), parseOptionalPercent(z, 3), parseOptionalPercent(z, 4)); }
        return zs;
    }

    private static String chooseDefaultPresetKey(String pm, List<String> pks) {
        if (pks == null || pks.isEmpty()) return DEFAULT_PRESET_KEY;
        List<String> prefs = switch (pm) { case "PHONE1" -> Arrays.asList("np1s", "np1"); case "PHONE2" -> Collections.singletonList("np2"); case "PHONE2A" -> Collections.singletonList("np2a"); case "PHONE3A" -> Arrays.asList("np3as", "np3a"); case "PHONE3" -> Collections.singletonList("np3test"); case "PHONE4A" -> Collections.singletonList("np4a"); case "PHONE4A_PRO" -> Collections.singletonList("np4ap-test"); case "PHONE4B" -> Collections.singletonList("np4b"); default -> Collections.emptyList(); };
        for (String p : prefs) if (pks.contains(p)) return p; return pks.get(0);
    }

    private static String phoneModelForDevice(int d) { return switch (d) { case DeviceProfile.DEVICE_NP1 -> "PHONE1"; case DeviceProfile.DEVICE_NP2 -> "PHONE2"; case DeviceProfile.DEVICE_NP2A -> "PHONE2A"; case DeviceProfile.DEVICE_NP3A -> "PHONE3A"; case DeviceProfile.DEVICE_NP4A -> "PHONE4A"; case DeviceProfile.DEVICE_NP4APRO -> "PHONE4A_PRO"; case DeviceProfile.DEVICE_NP3 -> "PHONE3"; case DeviceProfile.DEVICE_NP4B -> "PHONE4B"; default -> "UNKNOWN"; }; }
    public static String loadZonesConfigVersion(Context c) { try { return loadZonesConfigRoot(c).optString("version", "Unknown"); } catch (Exception e) { return "Unknown"; } }
    private static JSONObject loadZonesConfigRoot(Context c) throws IOException, JSONException { return new JSONObject(loadZonesConfigText(c)); }
    public static String loadZonesConfigText(Context c) throws IOException {
        File f = new File(c.getFilesDir(), "zones.config"); if (f.isFile()) { try (FileInputStream is = new FileInputStream(f)) { return readFully(is); } }
        try (InputStream is = c.getAssets().open("zones.config")) { return readFully(is); }
    }
    private static String readFully(InputStream is) throws IOException { ByteArrayOutputStream os = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int r; while ((r = is.read(buf)) != -1) os.write(buf, 0, r); return os.toString("UTF-8"); }
    private static List<String> getAllPresetKeys(JSONObject root) { ArrayList<String> res = new ArrayList<>(); JSONArray names = root.names(); if (names != null) for (int i = 0; i < names.length(); i++) res.add(names.optString(i, "")); Collections.sort(res); return res; }
    private static List<PresetInfo> buildPresetInfos(JSONObject root, List<String> keys) { ArrayList<PresetInfo> res = new ArrayList<>(); for (String k : keys) { JSONObject p = root.optJSONObject(k); if (p != null) res.add(new PresetInfo(k, p.optString("description", k))); } return res; }
    private static List<String> getPresetKeysForPhoneModel(JSONObject root, String pm) { ArrayList<String> res = new ArrayList<>(); if ("UNKNOWN".equals(pm)) return res; JSONArray names = root.names(); if (names != null) for (int i = 0; i < names.length(); i++) { String k = names.optString(i, ""); JSONObject p = root.optJSONObject(k); if (p != null && pm.equalsIgnoreCase(p.optString("phone_model", ""))) res.add(k); } Collections.sort(res); return res; }
    private static float parseOptionalPercent(JSONArray arr, int idx) { if (idx >= arr.length()) return Float.NaN; Object r = arr.opt(idx); if (r == null || r == JSONObject.NULL) return Float.NaN; try { float v; if (r instanceof Number n) v = n.floatValue(); else { String t = String.valueOf(r).trim(); if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim(); v = Float.parseFloat(t); } if (v >= 0f && v <= 1f) v *= 100f; return v; } catch (Exception ignored) { return Float.NaN; } }
    private AudioRouteInfo mCurrentAudioRoute = null;

    public void setAudioRoute(com.better.nothing.music.vizualizer.ui.AudioRoute route) {
        if (route == null) {
            mCurrentAudioRoute = null;
        } else {
            mCurrentAudioRoute = new AudioRouteInfo(route.getStorageKey(), route.getDisplayName());
        }
        refreshLatencyForCurrentAudioRoute();
    }

    public String getActiveAudioRouteKey() {
        return mCurrentAudioRoute != null ? mCurrentAudioRoute.storageKey : null;
    }

    public String getActiveAudioRouteName() {
        return mCurrentAudioRoute != null ? mCurrentAudioRoute.displayName : "None";
    }

    private void refreshLatencyForCurrentAudioRoute() {}
    public static boolean hasHapticMotor(Context c) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { VibratorManager vm = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE); return vm != null && vm.getDefaultVibrator().hasVibrator(); } Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE); return v != null && v.hasVibrator(); }
    public static boolean hasFlashlight(Context c) { return c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH); }
    private AudioRouteInfo resolveCurrentAudioRoute() { return null; }
    private void applyPresetSelection(String pk) { mPresetKey = pk; reloadConfig(); }
    public void setPreset(String p) { mPresetKey = p; restartCapture(); }
    private int clampGlyphBrightness(int b) { return Math.max(0, Math.min(10000, b)); }
    private void resetVisualizerState() { if (mGlyphRenderer != null) mGlyphRenderer.resetState(mVisualizerConfig); }
}
