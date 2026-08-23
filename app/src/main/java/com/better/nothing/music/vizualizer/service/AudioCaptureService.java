package com.better.nothing.music.vizualizer.service;

import com.better.nothing.music.vizualizer.model.DeviceProfile;
import com.better.nothing.music.vizualizer.model.HapticMode;
import com.better.nothing.music.vizualizer.model.BeatEngineMode;
import com.better.nothing.music.vizualizer.model.TorchMode;
import com.better.nothing.music.vizualizer.model.AudioRouteInfo;
import com.better.nothing.music.vizualizer.logic.AudioProcessor;
import com.better.nothing.music.vizualizer.logic.GlyphRenderer;
import com.better.nothing.music.vizualizer.logic.AudioDeviceManager;
import com.better.nothing.music.vizualizer.logic.ContinuousHapticEngine;
import com.better.nothing.music.vizualizer.logic.BeatDetectionHapticEngine;
import com.better.nothing.music.vizualizer.logic.FlashlightEngine;
import com.better.nothing.music.vizualizer.logic.UdpNetworkSync;
import com.better.nothing.music.vizualizer.ui.MainActivity;
import com.better.nothing.music.vizualizer.R;

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
import android.view.Display;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;

import com.google.firebase.analytics.FirebaseAnalytics;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.better.nothing.music.vizualizer.ui.MainViewModel;
import com.better.nothing.music.vizualizer.ui.VisualizerStyle;
import com.better.nothing.music.vizualizer.ui.UnifiedVisualizerView;

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
import java.net.InetAddress;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
    public enum CaptureSource { INTERNAL, MIC, VIZUALIZER, NETWORK }
    private volatile CaptureSource mCaptureSource = CaptureSource.INTERNAL;

    public static final String ACTION_STOP = "com.better.nothing.music.vizualizer.action.STOP";
    private String mNetworkHostIp = null;
    private int mNetworkHostPort = 8889;

    public static final String ACTION_START = "com.better.nothing.music.vizualizer.action.START";
    public static final String ACTION_TOGGLE_HAPTICS = "com.better.nothing.music.vizualizer.action.TOGGLE_HAPTICS";
    public static final String ACTION_TOGGLE_TORCH = "com.better.nothing.music.vizualizer.action.TOGGLE_TORCH";
    public static final String ACTION_TOGGLE_GLYPHS = "com.better.nothing.music.vizualizer.action.TOGGLE_GLYPHS";
    public static final String ACTION_TOGGLE_BROADCAST = "com.better.nothing.music.vizualizer.action.TOGGLE_BROADCAST";
    public static final String ACTION_TOGGLE_OVERLAY = "com.better.nothing.music.vizualizer.action.TOGGLE_OVERLAY";
    public static final String ACTION_TOGGLE_EDGE = "com.better.nothing.music.vizualizer.action.TOGGLE_EDGE";
    public static final String ACTION_TOGGLE_LENS = "com.better.nothing.music.vizualizer.action.TOGGLE_LENS";
    public static final String ACTION_SET_SOURCE = "com.better.nothing.music.vizualizer.action.SET_SOURCE";
    public static final String ACTION_REFRESH_SETTINGS = "com.better.nothing.music.vizualizer.action.REFRESH_SETTINGS";
    public static final String ACTION_SET_PRESET = "com.better.nothing.music.vizualizer.action.SET_PRESET";
    public static final String ACTION_CONNECT_UDP = "com.better.nothing.music.vizualizer.action.CONNECT_UDP";

    public static final String EXTRA_SOURCE = "extra_source";
    public static final String EXTRA_PRESET_KEY = "preset_key";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_START_SOURCE = "start_source";
    public static final String EXTRA_IP = "extra_ip";
    public static final String EXTRA_PORT = "extra_port";
    public static final float DEFAULT_GAMMA = 2.2f;
    private volatile float mGlyphThreshold = 0.0f;
    private volatile float mGlyphDecaySpeed = 0.75f;


    private static final String APP_PREFS_NAME = "viz_prefs";
    private static final int MAX_GLYPH_BRIGHTNESS = 4095;

    private static final String DEFAULT_PRESET_KEY = "np1";
    private static final int SAMPLE_RATE = 44100;
    private static final int[] EMPTY_FFT = new int[512];
    private int mCurrentSampleRate = SAMPLE_RATE;
    private static final int FPS = 60;
    private static final long MIN_SEND_INTERVAL_MS = 16L;
    private static final long PROJECTION_SETTLE_DELAY_MS = 500L;

    private static final String PREF_KEY_SHOULD_BE_RUNNING = "should_be_running";
    private static final String PREF_KEY_LAST_CAPTURE_SOURCE = "last_capture_source";

    private static volatile boolean sIsRunning = false;
    private static final MutableStateFlow<Boolean> sIsRunningFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Boolean> sHapticEnabledFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Boolean> sFlashlightEnabledFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Boolean> sGlyphsEnabledFlow = StateFlowKt.MutableStateFlow(true);
    private static final MutableStateFlow<Boolean> sBroadcastEnabledFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Boolean> sOverlayEnabledFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Boolean> sEdgeEnabledFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Boolean> sLensEnabledFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Float> sHapticMotorIntensityFlow = StateFlowKt.MutableStateFlow(0f);
    private static final MutableStateFlow<Float> sFlashlightMotorIntensityFlow = StateFlowKt.MutableStateFlow(0f);
    private static final MutableStateFlow<Float> sHapticRawPeakFlow = StateFlowKt.MutableStateFlow(0f);
    private static final MutableStateFlow<Float> sFlashlightRawPeakFlow = StateFlowKt.MutableStateFlow(0f);
    private static final MutableStateFlow<Boolean> sHapticBeatFlow = StateFlowKt.MutableStateFlow(false);
    private static final MutableStateFlow<Boolean> sFlashlightBeatFlow = StateFlowKt.MutableStateFlow(false);

    public StateFlow<Boolean> isRunningFlow() { return sIsRunningFlow; }
    public StateFlow<Boolean> hapticEnabledFlow() { return sHapticEnabledFlow; }
    public StateFlow<Boolean> flashlightEnabledFlow() { return sFlashlightEnabledFlow; }
    public StateFlow<Boolean> glyphsEnabledFlow() { return sGlyphsEnabledFlow; }
    public StateFlow<Boolean> broadcastEnabledFlow() { return sBroadcastEnabledFlow; }
    public StateFlow<Boolean> overlayEnabledFlow() { return sOverlayEnabledFlow; }
    public StateFlow<Boolean> edgeEnabledFlow() { return sEdgeEnabledFlow; }
    public StateFlow<Boolean> lensEnabledFlow() { return sLensEnabledFlow; }
    public static StateFlow<Float> hapticMotorIntensityFlow() { return sHapticMotorIntensityFlow; }
    public static StateFlow<Float> flashlightMotorIntensityFlow() { return sFlashlightMotorIntensityFlow; }
    public static StateFlow<Float> hapticRawPeakFlow() { return sHapticRawPeakFlow; }
    public static StateFlow<Float> flashlightRawPeakFlow() { return sFlashlightRawPeakFlow; }
    public static StateFlow<Boolean> hapticBeatFlow() { return sHapticBeatFlow; }
    public static StateFlow<Boolean> flashlightBeatFlow() { return sFlashlightBeatFlow; }
    public static boolean isRunning() { return sIsRunning; }

    public StateFlow<java.util.Map<InetAddress, Integer>> getConnectedClientsFlow() {
        if (mUdpSync != null) {
            return mUdpSync.getClientIps();
        }
        return StateFlowKt.MutableStateFlow(java.util.Collections.emptyMap());
    }

    private void setRunning(boolean running) {
        boolean wasRunning = sIsRunning;
        sIsRunning = running;
        sIsRunningFlow.setValue(running);
        requestWidgetRefresh(this);
        requestTileRefresh(this);

        SharedPreferences prefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_KEY_SHOULD_BE_RUNNING, running).apply();
        if (running) {
            prefs.edit().putString(PREF_KEY_LAST_CAPTURE_SOURCE, mCaptureSource.name()).apply();
        }

        if (mWorkerHandler != null) {
            if (running && !wasRunning) {
                mWorkerHandler.post(this::ensureGlyphSession);
            } else if (!running && wasRunning) {
                mWorkerHandler.post(this::clearGlyphSession);
            }
        }

        if (!running) {
            sHapticMotorIntensityFlow.setValue(0f);
            sFlashlightMotorIntensityFlow.setValue(0f);
            sHapticRawPeakFlow.setValue(0f);
            sFlashlightRawPeakFlow.setValue(0f);
            sHapticBeatFlow.setValue(false);
            sFlashlightBeatFlow.setValue(false);
        }
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
        @Override public void onServiceConnected(ComponentName name) {
            mGMConnected = true;
            registerGlyphManager();
            ensureGlyphSession();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            mGMConnected = false;
            mSessionOpen = false;
        }
    };

    private final GlyphMatrixManager.Callback mGlyphMatrixCallback = new GlyphMatrixManager.Callback() {
        @Override public void onServiceConnected(ComponentName name) {
            mGMMConnected = true;
            registerGlyphMatrixManager();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            mGMMConnected = false;
        }
    };

    private void registerGlyphManager() {
        if (mGM == null || !mGMConnected || mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return;
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
        try {
            mGM.register(deviceStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register GlyphManager", e);
        }
    }

    private void registerGlyphMatrixManager() {
        if (mGMM == null || !mGMMConnected || mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return;
        try {
            if (mSelectedDevice == DeviceProfile.DEVICE_NP3) mGMM.register(Glyph.DEVICE_23112);
            else if (mSelectedDevice == DeviceProfile.DEVICE_NP4APRO) mGMM.register(Glyph.DEVICE_25111p);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register GlyphMatrixManager", e);
        }
    }

    private HandlerThread mWorkerThread;
    private Handler mWorkerHandler;
    private AudioManager mAudioManager;
    private GlyphManager mGM;
    private GlyphMatrixManager mGMM;
    private volatile boolean mGMConnected = false;
    private volatile boolean mGMMConnected = false;
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
    private volatile int mMaxBrightness = 4095;

    private boolean mIdleBreathingEnabled = false;
    private boolean mOverlayEnabled = false;
    private boolean mEdgeVisualizerEnabled = false;
    private boolean mLensVisualizerEnabled = false;
    public volatile float mLensVisualizerRadius = 40f;
    public volatile float mLensVisualizerWidth = 0f;
    public volatile float mLensVisualizerX = 540f;
    public volatile float mLensVisualizerY = 72f;
    public volatile float mLensVisualizerBarWidth = 3f;
    public volatile float mLensVisualizerMaxHeight = 20f;
    public volatile int mLensVisualizerBarCount = 24;
    public volatile float mLensVisualizerSensitivity = 1.0f;
    public volatile float mLensGlowBlurRadius = 24f;
    public volatile int mLensColor = android.graphics.Color.WHITE;
    public volatile float mLensOpacity = 1.0f;

    public void setLensVisualizerEnabled(boolean enabled) {
        mLensVisualizerEnabled = enabled;
        sLensEnabledFlow.setValue(enabled);
        updateOverlayVisibility();
        requestWidgetRefresh();
    }
    public void setLensVisualizerRadius(float r) { mLensVisualizerRadius = r; updateUnifiedProperties(); }
    public void setLensVisualizerWidth(float w) { mLensVisualizerWidth = w; updateUnifiedProperties(); }
    public void setLensVisualizerX(float x) { mLensVisualizerX = x; updateUnifiedProperties(); }
    public void setLensVisualizerY(float y) { mLensVisualizerY = y; updateUnifiedProperties(); }
    public void setLensVisualizerBarWidth(float w) { mLensVisualizerBarWidth = w; updateUnifiedProperties(); }
    public void setLensVisualizerMaxHeight(float h) { mLensVisualizerMaxHeight = h; updateUnifiedProperties(); }
    public void setLensVisualizerBarCount(int c) { mLensVisualizerBarCount = c; updateUnifiedProperties(); }
    public void setLensVisualizerSensitivity(float s) { mLensVisualizerSensitivity = s; updateUnifiedProperties(); }
    public void setLensGlowBlurRadius(float radius) { mLensGlowBlurRadius = radius; updateUnifiedProperties(); }
    public void setLensColor(int color) { mLensColor = color; updateUnifiedProperties(); }
    public void setLensOpacity(float opacity) { mLensOpacity = opacity; updateUnifiedProperties(); }
    
    private int mOverlayWidth = 120;
    private float mEmulateHdrOpacity = 0f;
    private int mOverlayHeight = 12;
    private int mOverlayHeightBottom = 12;
    private int mOverlayYOffset = 2;
    private float mOverlaySensitivity = 1.0f;
    private float mOverlaySensitivityBottom = 1.0f;
    private float mOverlayGlowBlurRadius = 24f;
    private boolean mOverlayTopEnabled = true;
    private boolean mOverlayBottomEnabled = false;
    private int mOverlayColor = android.graphics.Color.WHITE;
    private float mOverlayOpacity = 1.0f;
    public boolean mRoundedBarsEnabled = false;
    private VisualizerStyle mOverlayStyle = VisualizerStyle.BARS;
    private VisualizerStyle mEdgeStyle = VisualizerStyle.BARS;
    private VisualizerStyle mLensStyle = VisualizerStyle.BARS;

    private int mEdgeThickness = 12;
    private float mEdgeSensitivity = 1.0f;
    private float mEdgeGlowBlurRadius = 24f;
    private int mEdgeBarCountHoriz = 20;
    private int mEdgeBarCountVert = 40;
    private float mEdgeCornerRadius = 2f;
    private boolean mEdgeTopEnabled = true;
    private boolean mEdgeBottomEnabled = true;
    private int mEdgeColor = android.graphics.Color.WHITE;
    private float mEdgeOpacity = 1.0f;
    private UnifiedVisualizerView mUnifiedVisualizerView;
    private WindowManager.LayoutParams mUnifiedLayoutParams;
    private WindowManager mWindowManager;

    private volatile boolean mHapticEnabled = false;
    private volatile HapticMode mHapticMode = HapticMode.BASS_TO_AMPLITUDE;
    private volatile BeatEngineMode mHapticBeatEngineMode = BeatEngineMode.SMOOTH;
    private volatile int mHapticPulseDurationMs = 40;
    private volatile float mHapticMinHz = 60;
    private volatile float mHapticMaxHz = 250;
    private volatile AudioProcessor.FrequencyRange mHapticRange;
    private volatile float mHapticAudioGain = 1.0f;
    private volatile float mHapticBeatSensitivity = 1.0f;
    private volatile float mHapticBeatGamma = 8.0f;

    private AudioProcessor.FrequencyRange mUiRange;

    private volatile boolean mFlashlightEnabled = false;
    private volatile TorchMode mFlashlightMode = TorchMode.AMPLITUDE;
    private volatile BeatEngineMode mFlashlightBeatEngineMode = BeatEngineMode.SMOOTH;
    private volatile int mFlashlightPulseDurationMs = 40;
    private volatile float mFlashlightMinHz = 60;
    private volatile float mFlashlightMaxHz = 250;
    private volatile AudioProcessor.FrequencyRange mFlashlightRange;
    private volatile float mFlashlightThreshold = 0.15f;
    private volatile float mFlashlightBeatSensitivity = 1.0f;
    private volatile float mFlashlightSpeedMs = 90f;
    private volatile float mFlashlightBeatGamma = 8.0f;
    private volatile int mFlashlightIntensityLevels = 1;
    private volatile Integer mFlashlightSpoofLevels = null;
    private volatile int mFlashlightMaxIntensity = -1;

    private ContinuousHapticEngine mContinuousHapticEngine;
    private BeatDetectionHapticEngine mBeatDetectionEngine;
    private FlashlightEngine mFlashlightEngine;
    private AudioProcessor mAudioProcessor;
    private GlyphRenderer mGlyphRenderer;
    private UdpNetworkSync mUdpSync;
    private boolean mBroadcastEnabled = false;
    private long mLastSendMs = 0L;
    private long mCaptureStartTimeMs = 0L;
    private volatile int[] mLatestRawFFT = new int[512];
    private int[] mPreviousRawFFT = new int[512];
    private volatile float mLatestUiPeakDiff = 0f;
    private final Object mFftLock = new Object();

    public int[] getLatestRawFFT() { synchronized (mFftLock) { return mLatestRawFFT; } }
    public int[] getLatestMagnitudes() { return getLatestRawFFT(); }

    public float[] getCurrentLightState() {
        if (mGlyphRenderer != null) return mGlyphRenderer.getCurrentLightState();
        return new float[0];
    }
    public boolean isVisualizerRunning() { return sIsRunning; }

    private boolean mIsAppInForeground = false;
    public void setAppInForeground(boolean foreground) {
        mIsAppInForeground = foreground;
        if (mWorkerHandler != null) {
            mWorkerHandler.post(() -> {
                if (foreground) {
                    if (!mSessionOpen && mMaxBrightness > 0) ensureGlyphSession();
                }
            });
        }
    }

    public float getLatestHapticPeak() {
        int[] fft = getLatestRawFFT();
        if (mHapticRange == null) return 0f;
        int max = 0;
        for (int i = mHapticRange.logBinLo; i <= mHapticRange.logBinHi; i++) if (fft[i] > max) max = fft[i];
        return max / 4095f;
    }

    public float getLatestUiPeak() {
        return mLatestUiPeakDiff;
    }

    public float getLatestFlashlightPeak() {
        int[] fft = getLatestRawFFT();
        if (mFlashlightRange == null) return 0f;
        int max = 0;
        for (int i = mFlashlightRange.logBinLo; i <= mFlashlightRange.logBinHi; i++) if (fft[i] > max) max = fft[i];
        return max / 4095f;
    }

    public long getCaptureDurationMs() { if (!sIsRunning || mCaptureStartTimeMs == 0) return 0; return SystemClock.elapsedRealtime() - mCaptureStartTimeMs; }
    private long mLastNotifUpdateMs = 0L;
    private final Handler mMainHandler = new Handler(android.os.Looper.getMainLooper());
    private final Runnable mIdlePulseRunnable = new Runnable() {
        @Override public void run() {
            if (sIsRunning) {
                long now = SystemClock.elapsedRealtime();
                if (now - mLastNotifUpdateMs >= 1000) { refreshNotification(); mLastNotifUpdateMs = now; }

                synchronized (mVisualizerPendingFrames) {
                    dispatchDueFrames(mVisualizerPendingFrames);
                }

                if (now - mLastSendMs >= 16 && mVisualizerConfig != null) {
                    UnifiedVisualizerView v = mUnifiedVisualizerView;
                    if (v != null) v.updateMagnitudes(mLatestRawFFT);

                    processFrame(mLatestRawFFT, mVisualizerConfig, mPresetConfigVersion.get());
                }

                mMainHandler.postDelayed(this, 16);
            }
        }
    };

    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { refreshLatencyForCurrentAudioRoute(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { refreshLatencyForCurrentAudioRoute(); }
    };

    private void applyEffectiveMaxBrightness() {
        int effective = (mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) ? 0 : mMaxBrightness;
        if (mGlyphRenderer != null) mGlyphRenderer.setMaxBrightness(effective);
    }

    private static final class PendingFrame {
        final int[] fftraw;
        final AudioProcessor.VisualizerConfig config;
        final int configVersion;
        final long dueAtMs;

        PendingFrame(int[] fftraw, AudioProcessor.VisualizerConfig config, int configVersion, long dueAtMs) {
            this.fftraw = fftraw;
            this.config = config;
            this.configVersion = configVersion;
            this.dueAtMs = dueAtMs;
        }
    }

    public static final class PresetInfo {
        public final String key;
        public final String name;
        public final String description;
        public PresetInfo(String key, String name, String description) {
            this.key = key;
            this.name = name;
            this.description = description;
        }
    }

    public static List<PresetInfo> loadPresetInfos(Context c, int deviceType) {
        if (deviceType == DeviceProfile.DEVICE_UNKNOWN) return Collections.emptyList();
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
        mUdpSync = new UdpNetworkSync(this);
        mAudioDeviceManager = new AudioDeviceManager(this, this::refreshLatencyForCurrentAudioRoute);
        mSelectedDevice = DeviceProfile.detectDevice();
        mUiRange = new AudioProcessor.FrequencyRange(70f, 120f);
        mLatencyCompensationMs = loadLatencyCompensationMs(this, mSelectedDevice);
        mGamma = loadGamma(this);
        SharedPreferences appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        mMaxBrightness = clampGlyphBrightness(appPrefs.getInt("max_brightness", MAX_GLYPH_BRIGHTNESS));
        mGlyphThreshold = appPrefs.getFloat("glyph_threshold", 0.0f);
        mGlyphDecaySpeed = appPrefs.getFloat("glyph_decay_speed", 0.75f);
        try {
            mCaptureSource = CaptureSource.valueOf(appPrefs.getString("capture_source", CaptureSource.INTERNAL.name()));
        } catch (Exception e) {
            mCaptureSource = CaptureSource.INTERNAL;
        }
        mIdleBreathingEnabled = appPrefs.getBoolean("idle_breathing_enabled", false);
        if (mGlyphRenderer != null) mGlyphRenderer.setAlternateMode(appPrefs.getBoolean("alternate_glyph_viz_enabled", false));
        mBroadcastEnabled = appPrefs.getBoolean("broadcast_enabled", false);
        mOverlayEnabled = appPrefs.getBoolean("overlay_enabled", false);
        mRoundedBarsEnabled = appPrefs.getBoolean("rounded_bars_enabled", false);
        try {
            mOverlayStyle = VisualizerStyle.valueOf(appPrefs.getString("overlay_style", VisualizerStyle.BARS.name()));
            mEdgeStyle = VisualizerStyle.valueOf(appPrefs.getString("edge_style", VisualizerStyle.BARS.name()));
            mLensStyle = VisualizerStyle.valueOf(appPrefs.getString("lens_style", VisualizerStyle.BARS.name()));
        } catch (Exception e) {
            mOverlayStyle = VisualizerStyle.BARS;
            mEdgeStyle = VisualizerStyle.BARS;
            mLensStyle = VisualizerStyle.BARS;
        }
        mOverlayWidth = appPrefs.getInt("overlay_width", 120);
        mEmulateHdrOpacity = appPrefs.getFloat("emulate_hdr_opacity", 0f);
        mOverlayHeight = appPrefs.getInt("overlay_height", 12);
        mOverlayYOffset = appPrefs.getInt("overlay_y_offset", 2);
        mOverlaySensitivity = appPrefs.getFloat("overlay_sensitivity", 1.0f);
        mLensVisualizerWidth = appPrefs.getFloat("lens_visualizer_width", 0f);

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
        refreshSettingsFromPrefs();
        mMainHandler.post(mIdlePulseRunnable);
    }

    private void refreshSettingsFromPrefs() {
        SharedPreferences appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        
        String sourceName = appPrefs.getString("capture_source", CaptureSource.INTERNAL.name());
        try {
            CaptureSource source = CaptureSource.valueOf(sourceName);
            if (mCaptureSource != source) {
                mCaptureSource = source;
                if (sIsRunning) restartCapture();
            }
        } catch (Exception ignored) {}

        setMaxBrightness(appPrefs.getInt("max_brightness", MAX_GLYPH_BRIGHTNESS));
        mGlyphThreshold = appPrefs.getFloat("glyph_threshold", 0.0f);
        mGlyphDecaySpeed = appPrefs.getFloat("glyph_decay_speed", 0.75f);
        setHapticMotorEnabled(appPrefs.getBoolean("haptic_motor_enabled", false));
        setFlashlightEnabled(appPrefs.getBoolean("flashlight_enabled", false));
        setFlashlightMaxIntensity(appPrefs.getInt("flashlight_max_intensity", -1));
        setFlashlightBeatGamma(appPrefs.getFloat("flashlight_beat_gamma", 8.0f));
        
        mIdleBreathingEnabled = appPrefs.getBoolean("idle_breathing_enabled", false);
        if (mGlyphRenderer != null) {
            mGlyphRenderer.setAlternateMode(appPrefs.getBoolean("alternate_glyph_viz_enabled", false));
            mGlyphRenderer.setIdleBreathingEnabled(mIdleBreathingEnabled);
        }
        setHighQualityAnalysis(appPrefs.getBoolean("high_quality_analysis", false));
        setBroadcastEnabled(appPrefs.getBoolean("broadcast_enabled", false));
        
        setOverlayEnabled(appPrefs.getBoolean("overlay_enabled", false));
        mOverlayColor = appPrefs.getInt("overlay_color", android.graphics.Color.WHITE);
        setEdgeVisualizerEnabled(appPrefs.getBoolean("edge_visualizer_enabled", false));
        mEdgeColor = appPrefs.getInt("edge_color", android.graphics.Color.WHITE);
        setLensVisualizerEnabled(appPrefs.getBoolean("lens_visualizer_enabled", false));
        mLensColor = appPrefs.getInt("lens_color", android.graphics.Color.WHITE);
    }

    @Override public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateOverlaySize();
    }

    private void updateOverlaySize() {
        mMainHandler.post(() -> {
            if (mUnifiedVisualizerView != null && mUnifiedLayoutParams != null && mWindowManager != null) {
                Point screenSize = new Point();
                mWindowManager.getDefaultDisplay().getRealSize(screenSize);
                mUnifiedLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
                mUnifiedLayoutParams.height = screenSize.y;
                try {
                    mWindowManager.updateViewLayout(mUnifiedVisualizerView, mUnifiedLayoutParams);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        SharedPreferences prefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        boolean shouldBeRunning = prefs.getBoolean(PREF_KEY_SHOULD_BE_RUNNING, false);

        if (Build.VERSION.SDK_INT >= 34) {
            boolean isStartingProjection = intent != null && intent.hasExtra(EXTRA_RESULT_CODE) && intent.hasExtra(EXTRA_DATA);
            boolean isStartAction = ACTION_START.equals(action);

            if (!ACTION_STOP.equals(action)) {
                if (isStartingProjection) {
                    startForegroundWithTypes(mCaptureSource, true);
                } else if (isStartAction || (intent == null && shouldBeRunning)) {
                    if (!sIsRunning) {
                        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                    }
                }
            }
        }

        if (intent == null) {
            if (shouldBeRunning) {
                if (!sIsRunning) {
                    try {
                        mCaptureSource = CaptureSource.valueOf(prefs.getString(PREF_KEY_LAST_CAPTURE_SOURCE, CaptureSource.INTERNAL.name()));
                    } catch (Exception e) { mCaptureSource = CaptureSource.INTERNAL; }
                    startVisualizer();
                }
                return START_STICKY;
            } else {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        if (intent != null) {
            String intentAction = intent.getAction();
            if (ACTION_STOP.equals(intentAction)) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
            else if (ACTION_START.equals(intentAction)) {
                String startSource = intent.getStringExtra(EXTRA_START_SOURCE);
                if (startSource != null) {
                    try {
                        FirebaseAnalytics.getInstance(this).logEvent(startSource, null);
                    } catch (Exception ignored) {}
                }
                startVisualizer();
            }
            else if (ACTION_REFRESH_SETTINGS.equals(intentAction)) refreshSettingsFromPrefs();
            else if (ACTION_SET_SOURCE.equals(intentAction)) {
                String sourceName = intent.getStringExtra(EXTRA_SOURCE);
                if (sourceName != null) {
                    try {
                        CaptureSource source = CaptureSource.valueOf(sourceName);
                        mCaptureSource = source;
                        getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putString("capture_source", source.name()).apply();
                        if (sIsRunning) restartCapture();
                        else requestWidgetRefresh();
                    } catch (Exception ignored) {}
                }
            }
            else if (ACTION_TOGGLE_HAPTICS.equals(intentAction)) {
                boolean nextEnabled = intent.hasExtra(EXTRA_ENABLED) ? intent.getBooleanExtra(EXTRA_ENABLED, false) : !mHapticEnabled;
                setHapticMotorEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("haptic_motor_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_TORCH.equals(intentAction)) {
                boolean nextEnabled = intent.hasExtra(EXTRA_ENABLED) ? intent.getBooleanExtra(EXTRA_ENABLED, false) : !mFlashlightEnabled;
                setFlashlightEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("flashlight_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_BROADCAST.equals(intentAction)) {
                boolean nextEnabled = intent.hasExtra(EXTRA_ENABLED) ? intent.getBooleanExtra(EXTRA_ENABLED, false) : !mBroadcastEnabled;
                setBroadcastEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("broadcast_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_OVERLAY.equals(intentAction)) {
                boolean nextEnabled = intent.hasExtra(EXTRA_ENABLED) ? intent.getBooleanExtra(EXTRA_ENABLED, false) : !mOverlayEnabled;
                setOverlayEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("overlay_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_EDGE.equals(intentAction)) {
                boolean nextEnabled = intent.hasExtra(EXTRA_ENABLED) ? intent.getBooleanExtra(EXTRA_ENABLED, false) : !mEdgeVisualizerEnabled;
                setEdgeVisualizerEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("edge_visualizer_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_LENS.equals(intentAction)) {
                boolean nextEnabled = intent.hasExtra(EXTRA_ENABLED) ? intent.getBooleanExtra(EXTRA_ENABLED, false) : !mLensVisualizerEnabled;
                setLensVisualizerEnabled(nextEnabled);
                getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putBoolean("lens_visualizer_enabled", nextEnabled).apply();
            }
            else if (ACTION_TOGGLE_GLYPHS.equals(intentAction)) {
                int nextVal;
                if (intent.hasExtra(EXTRA_ENABLED)) {
                    boolean enable = intent.getBooleanExtra(EXTRA_ENABLED, false);
                    if (enable) nextVal = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getInt("max_brightness_last", MAX_GLYPH_BRIGHTNESS);
                    else nextVal = 0;
                } else {
                    if (mMaxBrightness > 0) nextVal = 0;
                    else nextVal = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getInt("max_brightness_last", MAX_GLYPH_BRIGHTNESS);
                }
                
                setMaxBrightness(nextVal);
                SharedPreferences.Editor editor = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit();
                editor.putInt("max_brightness", nextVal);
                editor.putBoolean("glyphs_enabled", nextVal > 0);
                if (nextVal > 0) editor.putInt("max_brightness_last", nextVal);
                editor.apply();
            }
            else if (ACTION_SET_PRESET.equals(intentAction)) {
                String presetKey = intent.getStringExtra(EXTRA_PRESET_KEY);
                if (presetKey != null) {
                    setPreset(presetKey);
                    getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putString("selected_preset", presetKey).apply();
                }
            }
            else if (ACTION_CONNECT_UDP.equals(intentAction)) {
                String ip = intent.getStringExtra(EXTRA_IP);
                int port = intent.getIntExtra(EXTRA_PORT, 8888);
                if (ip != null) {
                    connectUdp(ip, port);
                }
            }
        }
        if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE) && intent.hasExtra(EXTRA_DATA)) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            if (data != null) startCapture(resultCode, data);
        }
        return sIsRunning ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sInstance = null; stopCapture(); clearGlyphSession();
        if (mUdpSync != null) mUdpSync.stopBroadcasting();
        if (mGM != null) mGM.unInit(); if (mGMM != null) mGMM.unInit();
        if (mAudioManager != null) mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        if (mWorkerThread != null) mWorkerThread.quitSafely();
        super.onDestroy();
    }

    public void startVisualizer() {
        if (mCaptureSource == CaptureSource.MIC) startMicCapture();
        else if (mCaptureSource == CaptureSource.VIZUALIZER) startVizualizerCapture();
        else if (mCaptureSource == CaptureSource.NETWORK) startNetworkCapture();
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
            else if (mCaptureSource == CaptureSource.NETWORK) startNetworkCapture();
        });
    }

    public void setDevice(int device) {
        boolean changed = (mSelectedDevice != device);
        mSelectedDevice = device;
        if (mGlyphRenderer != null) mGlyphRenderer.setDeviceType(device);
        if (device != DeviceProfile.DEVICE_UNKNOWN && Build.VERSION.SDK_INT >= 31) ensureGlyphManagerInitialized();
        registerGlyphManager();
        registerGlyphMatrixManager();
        setLatencyCompensationMs(loadLatencyCompensationMs(this, device));
        reloadConfig();
        if (sIsRunning && changed) restartCapture();
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
    public void setGlyphThreshold(float threshold) { mGlyphThreshold = threshold; }
    public void setGlyphDecaySpeed(float speed) { 
        if (mGlyphDecaySpeed != speed) {
            mGlyphDecaySpeed = speed; 
            reloadConfig();
        }
    }
    public void setSpectrumGain(float gain) { if (mAudioProcessor != null) mAudioProcessor.setManualGain(gain); }
    public void setSelectedPreset(String presetKey) { applyPresetSelection(presetKey); }
    public void setHapticMotorEnabled(boolean enabled) { setHapticEnabled(enabled); }
    public void setHapticMode(HapticMode mode) { mHapticMode = mode; requestWidgetRefresh(); }
    public void setHapticBeatEngineMode(BeatEngineMode mode) { mHapticBeatEngineMode = mode; if (mBeatDetectionEngine != null) mBeatDetectionEngine.setBeatEngineMode(mode); requestWidgetRefresh(); }
    public void setHapticPulseDurationMs(int ms) { mHapticPulseDurationMs = ms; if (mBeatDetectionEngine != null) mBeatDetectionEngine.setPulseDurationMs(ms); }

    public void setMaxBrightness(int brightness) {
        if (mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) brightness = 0;
        int clamped = clampGlyphBrightness(brightness);
        final int targetBrightness = clamped;
        final boolean reopeningAfterEnable = mMaxBrightness <= 0 && targetBrightness > 0;
        mMaxBrightness = clamped;
        sGlyphsEnabledFlow.setValue(clamped > 0);
        if (mWorkerHandler == null) return;
        mWorkerHandler.post(() -> {
            applyEffectiveMaxBrightness();
            if (targetBrightness <= 0) { clearGlyphSession(); }
            else if (reopeningAfterEnable) { clearGlyphSession(); ensureGlyphSession(); mLastSendMs = 0; } 
            else ensureGlyphSession();
            refreshNotification();
        });
        requestWidgetRefresh();
    }

    public void setIdleBreathingEnabled(boolean enabled) {
        mIdleBreathingEnabled = enabled;
        if (mGlyphRenderer != null) mGlyphRenderer.setIdleBreathingEnabled(enabled);
    }

    public void setIdlePattern(String pattern) { if (mGlyphRenderer != null) mGlyphRenderer.setIdlePattern(pattern); }
    public void setIdleBrightness(float b) { if (mGlyphRenderer != null) mGlyphRenderer.setIdleBrightness(b); }
    public void setIdleBackgroundBrightness(float b) { if (mGlyphRenderer != null) mGlyphRenderer.setIdleBackgroundBrightness(b); }

    public void setBroadcastEnabled(boolean enabled) {
        mBroadcastEnabled = enabled;
        sBroadcastEnabledFlow.setValue(enabled);
        if (enabled) {
            mUdpSync.startBroadcasting(Build.MODEL); // Or any device name
        } else {
            mUdpSync.stopBroadcasting();
        }
        requestWidgetRefresh();
    }

    public void startNetworkCapture() {
        Log.d(TAG, "startNetworkCapture: starting client mode targeting " + mNetworkHostIp);
        synchronized (mCaptureLock) {
            stopCaptureLocked(false);
            startForegroundWithTypes(CaptureSource.NETWORK, false);
            mCapturing = true; setRunning(true); updateOverlayVisibility(); mCaptureStartTimeMs = SystemClock.elapsedRealtime();
            
            // Ensure ranges are initialized for networking mode
            mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz);
            mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz);
            
            // Ensure we have some config, even if it's a default one
            if (mVisualizerConfig == null) {
                reloadConfig();
            }

            mUdpSync.startListening(mNetworkHostIp, fft -> {
                if (mCapturing && mCaptureSource == CaptureSource.NETWORK) {
                    PendingFrame frame = new PendingFrame(fft, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
                    synchronized (mVisualizerPendingFrames) {
                        mVisualizerPendingFrames.addLast(frame);
                        dispatchDueFrames(mVisualizerPendingFrames);
                    }
                }
                return null;
            });
        }
    }
    
    public void discoverHosts(kotlin.jvm.functions.Function1<? super UdpNetworkSync.HostInfo, kotlin.Unit> callback) {
        mUdpSync.discoverHosts(callback);
    }

    public void setOverlayEnabled(boolean enabled) {
        mOverlayEnabled = enabled;
        sOverlayEnabledFlow.setValue(enabled);
        if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility);
        requestWidgetRefresh();
    }
    public void setOverlayTopEnabled(boolean enabled) { mOverlayTopEnabled = enabled; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); requestWidgetRefresh(); }
    public void setOverlayBottomEnabled(boolean enabled) { mOverlayBottomEnabled = enabled; if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility); requestWidgetRefresh(); }
    public void setOverlayWidth(int width) { mOverlayWidth = width; updateUnifiedProperties(); }
    public void setEmulateHdrOpacity(float opacity) { mEmulateHdrOpacity = opacity; updateUnifiedProperties(); }
    public void setOverlayHeight(int height) { mOverlayHeight = height; updateUnifiedProperties(); }
    public void setOverlayHeightBottom(int height) { mOverlayHeightBottom = height; updateUnifiedProperties(); }
    public void setOverlayYOffset(int offset) { mOverlayYOffset = offset; updateUnifiedProperties(); }
    public void setOverlaySensitivity(float s) { mOverlaySensitivity = s; updateUnifiedProperties(); }
    public void setOverlaySensitivityBottom(float s) { mOverlaySensitivityBottom = s; updateUnifiedProperties(); }
    public void setOverlayGlowBlurRadius(float radius) { mOverlayGlowBlurRadius = radius; updateUnifiedProperties(); }
    public void setOverlayOpacity(float opacity) { mOverlayOpacity = opacity; updateUnifiedProperties(); }

    public void setEdgeVisualizerEnabled(boolean enabled) {
        mEdgeVisualizerEnabled = enabled;
        sEdgeEnabledFlow.setValue(enabled);
        if (mWorkerHandler != null) mWorkerHandler.post(this::updateOverlayVisibility);
        requestWidgetRefresh();
    }
    public void setEdgeThickness(int thickness) { mEdgeThickness = thickness; updateUnifiedProperties(); }
    public void setEdgeSensitivity(float sensitivity) { mEdgeSensitivity = sensitivity; updateUnifiedProperties(); }
    public void setEdgeGlowBlurRadius(float radius) { mEdgeGlowBlurRadius = radius; updateUnifiedProperties(); }
    public void setEdgeBarCounts(int horiz, int vert) { mEdgeBarCountHoriz = horiz; mEdgeBarCountVert = vert; updateUnifiedProperties(); }
    public void setEdgeCornerRadius(float radius) { mEdgeCornerRadius = radius; updateUnifiedProperties(); }
    public void setEdgeColor(int color) { mEdgeColor = color; updateUnifiedProperties(); }
    public void setEdgeTopEnabled(boolean enabled) { mEdgeTopEnabled = enabled; updateUnifiedProperties(); }
    public void setEdgeBottomEnabled(boolean enabled) { mEdgeBottomEnabled = enabled; updateUnifiedProperties(); }
    public void setEdgeOpacity(float opacity) { mEdgeOpacity = opacity; updateUnifiedProperties(); }

    public void reloadConfig() {
        if (mWorkerHandler != null) {
            mWorkerHandler.post(() -> {
                try {
                    refreshPresetCatalog();
                    if (!mAvailablePresetKeys.isEmpty() && !mAvailablePresetKeys.contains(mPresetKey)) {
                        mPresetKey = chooseDefaultPresetKey(phoneModelForDevice(mSelectedDevice), mAvailablePresetKeys);
                    }
                    mVisualizerConfig = loadVisualizerConfig(mPresetKey, mCurrentSampleRate);
                    mPresetConfigVersion.incrementAndGet();
                    resetVisualizerState();
                } catch (Exception e) { Log.e(TAG, "Failed to reload config", e); }
            });
        }
    }

    public void setOverlayColor(int color) { mOverlayColor = color; updateUnifiedProperties(); }
    public void setRoundedBarsEnabled(boolean enabled) {
        mRoundedBarsEnabled = enabled;
        updateUnifiedProperties();
    }

    public void setOverlayStyle(VisualizerStyle style) {
        mOverlayStyle = style;
        updateUnifiedProperties();
    }

    public void setEdgeStyle(VisualizerStyle style) {
        mEdgeStyle = style;
        updateUnifiedProperties();
    }

    public void setLensStyle(VisualizerStyle style) {
        mLensStyle = style;
        updateUnifiedProperties();
    }

    public VisualizerStyle getLensStyle() { return mLensStyle; }

    private void updateVisualizerService() { }

    public void setHapticEnabled(boolean enabled) {
        mHapticEnabled = hasHapticMotor(this) && enabled;
        sHapticEnabledFlow.setValue(mHapticEnabled);
        if (!mHapticEnabled) { if (mContinuousHapticEngine != null) mContinuousHapticEngine.stopHaptics(); if (mBeatDetectionEngine != null) mBeatDetectionEngine.stopHaptics(); }
        requestTileRefresh(); requestWidgetRefresh(); refreshNotification();
    }

    public void setHapticFreqRange(float min, float max) { mHapticMinHz = min; mHapticMaxHz = max; if (mBeatDetectionEngine != null) mBeatDetectionEngine.resetDetectionState(); }
    public void setHapticMultiplier(float m) { if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticMultiplier(m); if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticMultiplier(m); }
    public void setHapticAudioGain(float g) { mHapticAudioGain = g; if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticAudioGain(g); }
    public void setHapticGamma(float g) { if (mContinuousHapticEngine != null) mContinuousHapticEngine.setHapticGamma(g); }
    public void setHapticBeatSensitivity(float s) { mHapticBeatSensitivity = s; if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticSensitivity(s); }
    public void setHapticBeatGamma(float g) { mHapticBeatGamma = g; if (mBeatDetectionEngine != null) mBeatDetectionEngine.setHapticGamma(g); }

    private int mMicrophoneMode = MediaRecorder.AudioSource.UNPROCESSED;

    public void setMicrophoneMode(int mode) {
        if (mMicrophoneMode != mode) {
            mMicrophoneMode = mode;
            if (sIsRunning && mCaptureSource == CaptureSource.MIC) {
                if (mWorkerHandler != null) {
                    mWorkerHandler.post(() -> startCaptureInternal(CaptureSource.MIC, 0, null));
                }
            }
        }
    }

    public void setFlashlightEnabled(boolean enabled) {
        mFlashlightEnabled = (hasFlashlight(this) || mFlashlightSpoofLevels != null) && enabled;
        sFlashlightEnabledFlow.setValue(mFlashlightEnabled);
        if (!mFlashlightEnabled && mFlashlightEngine != null) mFlashlightEngine.stopFlashlight();
        requestWidgetRefresh(); refreshNotification();
    }
    public void setFlashlightFreqRange(float min, float max) { mFlashlightMinHz = min; mFlashlightMaxHz = max; }
    public void setFlashlightThreshold(float t) { mFlashlightThreshold = t; if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightThreshold(t); }
    public void setFlashlightMode(TorchMode m) { mFlashlightMode = m; if (mFlashlightEngine != null) mFlashlightEngine.setTorchMode(m); }
    public void setFlashlightBeatEngineMode(BeatEngineMode m) { mFlashlightBeatEngineMode = m; if (mFlashlightEngine != null) mFlashlightEngine.setBeatEngineMode(m); requestWidgetRefresh(); }
    public void setFlashlightPulseDurationMs(int ms) { mFlashlightPulseDurationMs = ms; if (mFlashlightEngine != null) mFlashlightEngine.setPulseDurationMs(ms); }
    public void setFlashlightBeatSensitivity(float s) { mFlashlightBeatSensitivity = s; if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightBeatSensitivity(s); }
    public void setFlashlightBeatGamma(float g) { mFlashlightBeatGamma = g; if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightBeatGamma(g); }
    public void setFlashlightSpeedMs(float s) { mFlashlightSpeedMs = s; if (mFlashlightEngine != null) mFlashlightEngine.setFlashlightSpeedMs(s); }
    public void setFlashlightMaxIntensity(int intensity) {
        mFlashlightMaxIntensity = intensity;
        if (mFlashlightEngine != null) mFlashlightEngine.setUserMaxIntensity(intensity);
    }

    public void setFlashlightSpoofLevels(Integer levels) {
        mFlashlightSpoofLevels = levels;
        if (mFlashlightEngine != null) {
            mFlashlightEngine.setSpoofIntensityLevels(levels);
        }
        // Re-evaluate if we can keep the flashlight enabled if it was disabled due to no hardware
        SharedPreferences appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE);
        if (appPrefs.getBoolean("flashlight_enabled", false)) {
            setFlashlightEnabled(true);
        }
    }

    public int getFlashlightIntensityLevels() { return mFlashlightEngine != null ? mFlashlightEngine.getTorchIntensityLevels() : (mFlashlightIntensityLevels > 0 ? mFlashlightIntensityLevels : 1); }
    public int getFlashlightCurrentLevel() { return mFlashlightEngine != null ? mFlashlightEngine.getCurrentLevel() : 0; }

    public void startCapture(int resultCode, Intent data) { startCaptureInternal(CaptureSource.INTERNAL, resultCode, data); }
    public void startMicCapture() { startCaptureInternal(CaptureSource.MIC, 0, null); }
    public void startVizualizerCapture() { startCaptureInternal(CaptureSource.VIZUALIZER, 0, null); }

    private void startCaptureInternal(CaptureSource source, int resultCode, Intent data) {
        mCaptureSource = source;

        synchronized (mCaptureLock) {
            // 1. Explicitly teardown previous audio capture session & projection handle
            stopCaptureLocked(false);

            if (source == CaptureSource.INTERNAL) {
                // Give the system a moment to release the previous audio policy
                SystemClock.sleep(500);

                MediaProjectionManager pm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                if (pm == null) return;

                // 2. Start Foreground Service WITH mediaProjection type FIRST (Crucial for Android 14+)
                startForegroundWithTypes(CaptureSource.INTERNAL, true);

                // 3. Obtain MediaProjection token strictly AFTER service is elevated
                mProjection = pm.getMediaProjection(resultCode, data);
                if (mProjection == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    setRunning(false);
                    return;
                }

                mProjection.registerCallback(mProjectionCallback, mWorkerHandler);
            } else {
                startForegroundWithTypes(source, false);
            }

            mCapturing = true;
            setRunning(true);
            updateOverlayVisibility();
            mCaptureStartTimeMs = SystemClock.elapsedRealtime();

            ensureCaptureExecutor();
            mCaptureExecutor.execute(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
                
                if (source == CaptureSource.INTERNAL) {
                    // Increased settle delay to ensure native audio policy registration is ready
                    SystemClock.sleep(500);
                }
                
                AudioRecord lr = null;

                try {
                    mCurrentSampleRate = (source == CaptureSource.MIC) ? SAMPLE_RATE : 48000;
                    int csr = mCurrentSampleRate;
                    int minBs = AudioRecord.getMinBufferSize(csr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    int bs = Math.max(minBs * 2, 8192);

                    if (source == CaptureSource.INTERNAL) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mProjection != null) {
                            AudioPlaybackCaptureConfiguration cfg = new AudioPlaybackCaptureConfiguration.Builder(mProjection)
                                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                                    .excludeUid(Process.myUid())
                                    .build();

                            AudioFormat format = new AudioFormat.Builder()
                                    .setSampleRate(csr)
                                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .build();

                            // Retry loop to handle native AudioFlinger registration delays
                            // Using more attempts and longer backoff
                            int maxRetries = 5;
                            for (int i = 0; i < maxRetries; i++) {
                                try {
                                    AudioRecord.Builder arb = new AudioRecord.Builder()
                                            .setAudioPlaybackCaptureConfig(cfg)
                                            .setAudioFormat(format)
                                            .setBufferSizeInBytes(bs);

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        arb.setContext(createAttributionContext("AudioVisualizerTag"));
                                    }

                                    lr = arb.build();
                                    if (lr != null && lr.getState() == AudioRecord.STATE_INITIALIZED) {
                                        break; // Success
                                    }

                                    if (lr != null) {
                                        lr.release();
                                        lr = null;
                                    }
                                } catch (UnsupportedOperationException | SecurityException e) {
                                    Log.w(TAG, "AudioRecord build attempt " + (i + 1) + " failed: " + e.getMessage());
                                    if (lr != null) {
                                        lr.release();
                                        lr = null;
                                    }
                                    if (i == maxRetries - 1) throw e;
                                    SystemClock.sleep(500); // Give AudioFlinger time to release policy state
                                }
                            }
                        }
                    } else if (source == CaptureSource.VIZUALIZER) {
                        mWorkerHandler.post(this::setupVisualizerCapture);
                        return;
                    } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        lr = new AudioRecord(mMicrophoneMode, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bs);
                    }

                    if (lr != null && lr.getState() == AudioRecord.STATE_INITIALIZED) {
                        mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz);
                        mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz);

                        synchronized (mCaptureLock) {
                            if (!mCapturing) {
                                lr.release();
                                return;
                            }
                            mAudioRecord = lr;
                        }

                        lr.startRecording();
                        runCaptureLoop(lr);
                    } else {
                        Log.e(TAG, "AudioRecord not initialized, state: " + (lr != null ? lr.getState() : "null"));
                        stopSelf();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Capture failed: " + e.getMessage(), e);
                    stopSelf();
                } finally {
                    synchronized (mCaptureLock) {
                        if (mAudioRecord == lr) {
                            mAudioRecord = null;
                        }
                    }
                    if (lr != null) {
                        try {
                            if (lr.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) lr.stop();
                        } catch (Exception ignored) {}
                        lr.release();
                    }
                }
            });
        }
        refreshNotification();
    }

    private void startForegroundWithTypes(CaptureSource source, boolean forceMediaProjection) {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            if (source == CaptureSource.INTERNAL) {
                if (forceMediaProjection || mProjection != null) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                }
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
            } else if (source == CaptureSource.MIC || source == CaptureSource.VIZUALIZER) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
            }
            startForeground(NOTIF_ID, notification, type);
        } else if (Build.VERSION.SDK_INT >= 29) {
            int type = 0;
            if (source == CaptureSource.INTERNAL) {
                if (forceMediaProjection || mProjection != null) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                }
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
            } else if (source == CaptureSource.MIC || source == CaptureSource.VIZUALIZER) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
            }
            if (type != 0) startForeground(NOTIF_ID, notification, type);
            else startForeground(NOTIF_ID, notification);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    public void stopCapture() { synchronized (mCaptureLock) { stopCaptureLocked(true); } }
    private void stopCaptureLocked(boolean stopService) {
        mCapturing = false;
        releaseVisualizer();
        if (mUdpSync != null) mUdpSync.stopListening();
        if (mFlashlightEngine != null) mFlashlightEngine.stopFlashlight();
        if (mContinuousHapticEngine != null) mContinuousHapticEngine.stopHaptics();
        if (mBeatDetectionEngine != null) mBeatDetectionEngine.stopHaptics();

        synchronized (mFftLock) {
            mLatestRawFFT = EMPTY_FFT;
        }

        if (mAudioRecord != null) {
            try {
                if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    mAudioRecord.stop();
                }
            } catch (Exception ignored) {}
            mAudioRecord.release();
            mAudioRecord = null;
        }

        if (mProjection != null) {
            mProjection.unregisterCallback(mProjectionCallback);
            mProjection.stop(); // CRITICAL: Invalidate native token so AudioFlinger cleans up policy
            mProjection = null;
        }

        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            setRunning(false);
            clearGlyphSession();
        }
        updateOverlayVisibility();
    }
    private void releaseAudioRecord() { if (mAudioRecord != null) { try { mAudioRecord.stop(); } catch (Exception ignored) {} mAudioRecord.release(); mAudioRecord = null; } }
    private void releaseProjection() { if (mProjection != null) { try { mProjection.stop(); } catch (Exception ignored) {} mProjection = null; } }
    private void releaseVisualizer() { if (mVisualizer != null) { try { mVisualizer.release(); } catch (Exception ignored) {} mVisualizer = null; } synchronized (mVisualizerPendingFrames) { mVisualizerPendingFrames.clear(); } }
    private void ensureCaptureExecutor() { if (mCaptureExecutor == null || mCaptureExecutor.isShutdown()) mCaptureExecutor = Executors.newSingleThreadExecutor(); }
    private void shutdownCaptureExecutor() { if (mCaptureExecutor != null) { mCaptureExecutor.shutdownNow(); mCaptureExecutor = null; } }

    public void setAlternateGlyphVizEnabled(boolean enabled) {
        if (mGlyphRenderer != null) mGlyphRenderer.setAlternateMode(enabled);
    }

    public void setHighQualityAnalysis(boolean enabled) {
        if (mAudioProcessor != null) {
            mAudioProcessor.setHighQualityAnalysis(enabled);
        }
    }

    private void processFrame(int[] fftraw, AudioProcessor.VisualizerConfig config, int configVersion) {
        if (config == null || configVersion != mPresetConfigVersion.get()) return;
        try {
            long now = SystemClock.elapsedRealtime(); 

            // Independent broadcast logic
            if (mBroadcastEnabled && (now - mLastSendMs >= MIN_SEND_INTERVAL_MS) && fftraw != null) {
                mUdpSync.sendFft(fftraw);
            }

            boolean hasActivity = false;
            if (fftraw != null && fftraw.length > 0) { for (int val : fftraw) if (val > 20) { hasActivity = true; break; } }
            
            // Only interact with Glyph library if output is enabled (brightness > 0)
            if (mMaxBrightness > 0) {
                // Apply threshold
                int[] processedFft = fftraw;
                if (mGlyphThreshold > 0.001f) {
                    int thresholdFft = (int) (mGlyphThreshold * 4095f);
                    processedFft = fftraw.clone();
                    for (int i = 0; i < processedFft.length; i++) {
                        if (processedFft[i] < thresholdFft) processedFft[i] = 0;
                    }
                }

                // Determine if we should maintain the glyph session.
                // We keep it open if there's audio activity, or the app is in foreground, 
                // or if idle breathing is enabled.
                boolean shouldMaintain = hasActivity || mIsAppInForeground || mIdleBreathingEnabled;
                
                if (shouldMaintain) { 
                    if (!mSessionOpen) ensureGlyphSession(); 
                }

                if (mSessionOpen && (now - mLastSendMs >= MIN_SEND_INTERVAL_MS)) {
                    int[] frameColors = mGlyphRenderer.processFrame(processedFft, config, now);
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
            } else {
                if (mSessionOpen) clearGlyphSession();
                // If glyphs are off, we still need to update mLastSendMs for broadcast
                if (mBroadcastEnabled && (now - mLastSendMs >= MIN_SEND_INTERVAL_MS) && fftraw != null) {
                    mUdpSync.sendFft(fftraw);
                    mLastSendMs = now;
                }
            }
        } catch (Exception e) { Log.e(TAG, "processFrame error", e); }
    }

    private void dispatchDueFrames(ArrayDeque<PendingFrame> pendingFrames) {
        if (pendingFrames == null) return; long nowMs = SystemClock.elapsedRealtime(); PendingFrame latestDueFrame = null;
        while (!pendingFrames.isEmpty()) { PendingFrame frame = pendingFrames.peekFirst(); if (frame == null || frame.dueAtMs > nowMs) break; latestDueFrame = pendingFrames.removeFirst(); }
        if (latestDueFrame != null) {
            try {
                synchronized (mFftLock) {
                    mPreviousRawFFT = mLatestRawFFT.clone();
                    mLatestRawFFT = latestDueFrame.fftraw;
                }

                if (mUiRange != null) {
                    int maxDiff = 0;
                    for (int i = mUiRange.logBinLo; i <= mUiRange.logBinHi && i < mLatestRawFFT.length; i++) {
                        int diff = Math.max(0, mLatestRawFFT[i] - mPreviousRawFFT[i]);
                        if (diff > maxDiff) maxDiff = diff;
                    }
                    mLatestUiPeakDiff = maxDiff / 2047f; // Use 2047 for diff scaling similar to GlyphRenderer
                }

                UnifiedVisualizerView v = mUnifiedVisualizerView;
                if (v != null) v.updateMagnitudes(mLatestRawFFT);

                float hRawPeak = getLatestHapticPeak();
                float fRawPeak = getLatestFlashlightPeak();
                sHapticRawPeakFlow.setValue(hRawPeak);
                sFlashlightRawPeakFlow.setValue(fRawPeak);

                if (mCapturing && mHapticEnabled) {
                    if (mHapticMode == HapticMode.BASS_TO_AMPLITUDE) {
                        if (mContinuousHapticEngine != null) {
                            float intensity = mContinuousHapticEngine.performHapticFeedback(hRawPeak, latestDueFrame.config);
                            sHapticMotorIntensityFlow.setValue(intensity);
                        }
                        sHapticBeatFlow.setValue(false);
                    } else if (mBeatDetectionEngine != null) {
                        mBeatDetectionEngine.performHapticFeedback(mLatestRawFFT, mHapticRange);
                        sHapticMotorIntensityFlow.setValue(mBeatDetectionEngine.getCurrentIntensity());
                        sHapticBeatFlow.setValue(mBeatDetectionEngine.isBeatTriggeredThisFrame());
                    }
                } else {
                    sHapticMotorIntensityFlow.setValue(0f);
                    sHapticBeatFlow.setValue(false);
                }

                if (mCapturing && mFlashlightEnabled && mFlashlightEngine != null) {
                    mFlashlightEngine.performFlashlightFeedback(fRawPeak, latestDueFrame.config, mLatestRawFFT, mFlashlightRange != null ? mFlashlightRange.logBinLo : 0, mFlashlightRange != null ? mFlashlightRange.logBinHi : 0);
                    int levels = mFlashlightEngine.getTorchIntensityLevels();
                    float intensity = (levels > 1) ? (float) mFlashlightEngine.getCurrentLevel() / levels : (mFlashlightEngine.getCurrentLevel() > 0 ? 1f : 0f);
                    sFlashlightMotorIntensityFlow.setValue(intensity);
                    sFlashlightBeatFlow.setValue(mFlashlightMode == TorchMode.BEAT_DETECTION && mFlashlightEngine.isBeatTriggeredThisFrame());
                } else {
                    sFlashlightMotorIntensityFlow.setValue(0f);
                    sFlashlightBeatFlow.setValue(false);
                }

                processFrame(latestDueFrame.fftraw, latestDueFrame.config, latestDueFrame.configVersion);
            } catch (Exception e) { Log.e(TAG, "Error dispatching frame", e); }
        }
    }
    private void setupVisualizerCapture() {
        releaseVisualizer(); SystemClock.sleep(250);
        try {
            mAudioProcessor.updateFFTSize();
            mHapticRange = new AudioProcessor.FrequencyRange(mHapticMinHz, mHapticMaxHz);
            mFlashlightRange = new AudioProcessor.FrequencyRange(mFlashlightMinHz, mFlashlightMaxHz);
            
            Log.d(TAG, "setupVisualizerCapture: initializing Android Visualizer (session 0)");
            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create Visualizer(0), trying again in 500ms", e);
                SystemClock.sleep(500);
                mVisualizer = new Visualizer(0);
            }

            if (mVisualizer != null) {
                int captureSize = Math.min(Visualizer.getCaptureSizeRange()[1], 1024); 
                mVisualizer.setCaptureSize(captureSize);
                
                int captureRate = Math.min(Visualizer.getMaxCaptureRate(), 50000);
                mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                    @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int sr) { processVisualizerWaveform(w, sr); }
                    @Override public void onFftDataCapture(Visualizer v, byte[] f, int sr) {}
                }, captureRate, true, false);
                
                int result = mVisualizer.setEnabled(true);
                if (result != Visualizer.SUCCESS) {
                    Log.e(TAG, "setupVisualizerCapture: failed to enable visualizer, error code: " + result);
                    releaseVisualizer();
                } else {
                    Log.d(TAG, "setupVisualizerCapture: visualizer enabled successfully with rate " + captureRate);
                }
            }
        } catch (Exception e) { 
            Log.e(TAG, "setupVisualizerCapture: exception during initialization", e);
            releaseVisualizer(); 
        }
    }

    private void processVisualizerWaveform(byte[] waveform, int samplingRate) {
        if (!mCapturing) return;
        
        // Cheap signal check if we are in idle breathing to save CPU
        boolean deepSilence = mGlyphRenderer != null && mGlyphRenderer.isDeeplySilent();
        if (deepSilence) {
            boolean hasAnySignal = false;
            for (byte b : waveform) {
                if (Math.abs((b & 0xFF) - 128) > 3) {
                    hasAnySignal = true;
                    break;
                }
            }
            if (!hasAnySignal) {
                // Still silent. Skip expensive FFT/mapping but still dispatch to keep breathing animation smooth.
                PendingFrame frame = new PendingFrame(EMPTY_FFT, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
                synchronized (mVisualizerPendingFrames) { mVisualizerPendingFrames.addLast(frame); dispatchDueFrames(mVisualizerPendingFrames); }
                return;
            }
        }

        // Robust sampling rate detection: handle both Hz (most devices) and mHz (docs)
        int hz = (samplingRate > 1000000) ? (samplingRate / 1000) : samplingRate;
        if (hz < 8000) hz = 44100; // Fallback for invalid values

        mAudioProcessor.updateFFTSize(hz);
        short[] hop = new short[waveform.length]; for (int i = 0; i < waveform.length; i++) hop[i] = (short) (((waveform[i] & 0xFF) - 128) << 8);
        AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, AudioProcessor.SourceType.VIZUALIZER, mVisualizerConfig != null ? mVisualizerConfig.decay : 0.85f);
        if (result == null) return;
        PendingFrame frame = new PendingFrame(result.fftraw, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
        synchronized (mVisualizerPendingFrames) { mVisualizerPendingFrames.addLast(frame); dispatchDueFrames(mVisualizerPendingFrames); }
    }

    private void runCaptureLoop(AudioRecord record) {
        mAudioProcessor.updateFFTSize(record.getSampleRate());
        int hopSize = Math.round(record.getSampleRate() / (float) FPS);
        short[] hop = new short[hopSize];
        while (mCapturing) {
            int read = record.read(hop, 0, hopSize, AudioRecord.READ_BLOCKING);
            if (read < 0) {
                Log.e(TAG, "AudioRecord read error: " + read);
                break;
            }
            if (read == 0) continue;

            // Cheap signal check if we are in idle breathing
            boolean deepSilence = mGlyphRenderer != null && mGlyphRenderer.isDeeplySilent();
            if (deepSilence) {
                boolean hasAnySignal = false;
                for (short s : hop) {
                    if (Math.abs(s) > 750) { // Approx 2.3% of 32768
                        hasAnySignal = true;
                        break;
                    }
                }
                if (!hasAnySignal) {
                    PendingFrame frame = new PendingFrame(EMPTY_FFT, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
                    synchronized(mVisualizerPendingFrames) { mVisualizerPendingFrames.addLast(frame); dispatchDueFrames(mVisualizerPendingFrames); }
                    continue;
                }
            }

            AudioProcessor.SourceType type = (mCaptureSource == CaptureSource.MIC) ? AudioProcessor.SourceType.MIC : AudioProcessor.SourceType.INTERNAL;
            AudioProcessor.AudioFrameResult result = mAudioProcessor.processAudioFrame(hop, type, mVisualizerConfig != null ? mVisualizerConfig.decay : 0.85f);
            if (result == null) continue;
            PendingFrame frame = new PendingFrame(result.fftraw, mVisualizerConfig, mPresetConfigVersion.get(), SystemClock.elapsedRealtime() + mLatencyCompensationMs);
            synchronized(mVisualizerPendingFrames) { mVisualizerPendingFrames.addLast(frame); dispatchDueFrames(mVisualizerPendingFrames); }
        }
    }

    private void turnOffGlyphs() {
        if (mGM != null && mSessionOpen) { int count = resolveGlyphCount(); if (count > 0) try { mGM.setFrameColors(new int[count]); } catch (Exception ignored) {} try { mGM.turnOff(); } catch (Exception ignored) {} }
        if (mGMM != null && mSessionOpen) { int size = DeviceProfile.getMatrixWidth(mSelectedDevice) * DeviceProfile.getMatrixHeight(mSelectedDevice); if (size > 0) try { mGMM.setAppMatrixFrame(new int[size]); } catch (Exception ignored) {} }
    }

    private void ensureGlyphSession() {
        if (mMaxBrightness <= 0 || mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN || !sIsRunning) return;

        if (mGM == null || mGMM == null || !mGMConnected || !mGMMConnected) {
            ensureGlyphManagerInitialized();
            return; // Callback will re-invoke this
        }

        if (mSessionOpen) return;
        try {
            if (mGM != null) {
                mGM.openSession();
                mSessionOpen = true;
            }
        } catch (GlyphException | NullPointerException e) {
            Log.e(TAG, "Failed to open Glyph session", e);
        }
    }

    private void clearGlyphSession() {
        try {
            turnOffGlyphs();
            if (mSessionOpen) {
                if (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0) {
                    if (mGMM != null) {
                        try { mGMM.closeAppMatrix(); } catch (Exception ignored) {}
                    }
                }
                if (mGM != null) {
                    try { mGM.closeSession(); } catch (Exception ignored) {}
                }
                mSessionOpen = false;
            }
        } catch (Exception ignored) {}
    }

    private boolean canPushGlyphFrames() { if (mSelectedDevice == DeviceProfile.DEVICE_UNKNOWN) return false; if (DeviceProfile.getMatrixWidth(mSelectedDevice) > 0) return mGMM != null; return mGM != null && mSessionOpen; }

    private int resolveGlyphCount() { return mVisualizerConfig != null ? mVisualizerConfig.zones.length : DeviceProfile.getLedCount(mSelectedDevice); }

    private Notification buildNotification() {
        ensureNotificationChannel();
        
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String content = (mMaxBrightness > 0 && mVisualizerConfig != null ? mVisualizerConfig.name + " • " : "") + formatDuration(getCaptureDurationMs());
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notif_monochrome)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true);
            
        int addedButtons = 0;
        if (mSelectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
            String label = getString(R.string.notification_action_glyphs);
            builder.addAction(0, mMaxBrightness > 0 ? label.toUpperCase(Locale.ROOT) : label, PendingIntent.getService(this, 10, new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_GLYPHS), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            addedButtons++;
        }
        if (hasHapticMotor(this)) {
            String label = getString(R.string.notification_action_haptics);
            builder.addAction(0, mHapticEnabled ? label.toUpperCase(Locale.ROOT) : label, PendingIntent.getService(this, 11, new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_HAPTICS), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            addedButtons++;
        }
        if (hasFlashlight(this)) {
            String label = getString(R.string.notification_action_flash);
            builder.addAction(0, mFlashlightEnabled ? label.toUpperCase(Locale.ROOT) : label, PendingIntent.getService(this, 12, new Intent(this, AudioCaptureService.class).setAction(ACTION_TOGGLE_TORCH), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            addedButtons++;
        }
        
        if (addedButtons <= 2) {
            builder.addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_action_stop), PendingIntent.getService(this, 1, createStopIntent(this), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }

        return builder.build();
    }

    private String formatDuration(long ms) { long s = (ms / 1000) % 60; long m = (ms / 60000) % 60; long h = (ms / 3600000); return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%02d:%02d", m, s); }
    private void ensureNotificationChannel() { NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)); }
    private void refreshNotification() { if (mCapturing) { NotificationManager nm = getSystemService(NotificationManager.class); if (nm != null) nm.notify(NOTIF_ID, buildNotification()); } }

    private void updateOverlayVisibility() {
        mMainHandler.post(() -> {
            if (mWindowManager == null) mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            
            boolean anyEnabled = (mEdgeVisualizerEnabled || mOverlayEnabled || mLensVisualizerEnabled) && mCapturing;

            if (anyEnabled) {
                if (mUnifiedVisualizerView == null) {
                    mUnifiedVisualizerView = new UnifiedVisualizerView(this);
                    mUnifiedVisualizerView.setAlpha(0f);
                    Point screenSize = new Point();
                    mWindowManager.getDefaultDisplay().getRealSize(screenSize);

                    mUnifiedLayoutParams = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            screenSize.y,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                            PixelFormat.TRANSLUCENT
                    );
                    mUnifiedLayoutParams.gravity = Gravity.TOP | Gravity.START;
                    if (Build.VERSION.SDK_INT >= 28) {
                        mUnifiedLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }
                    if (Build.VERSION.SDK_INT >= 30) {
                        mUnifiedLayoutParams.setFitInsetsTypes(0);
                        mUnifiedLayoutParams.setFitInsetsIgnoringVisibility(true);
                    }
                    try { 
                        mWindowManager.addView(mUnifiedVisualizerView, mUnifiedLayoutParams); 
                    } catch (Exception ignored) {}
                }
                mUnifiedVisualizerView.animate().alpha(1f).setDuration(250).start();
                updateUnifiedProperties();
            } else if (mUnifiedVisualizerView != null) {
                mUnifiedVisualizerView.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    if (mUnifiedVisualizerView != null && mUnifiedVisualizerView.getAlpha() < 0.01f) {
                        try { mWindowManager.removeView(mUnifiedVisualizerView); } catch (Exception ignored) {}
                        mUnifiedVisualizerView = null;
                        mUnifiedLayoutParams = null;
                    }
                }).start();
            }
        });
    }

    private void updateUnifiedProperties() {
        mMainHandler.post(() -> {
            if (mUnifiedVisualizerView == null) return;
            float density = getResources().getDisplayMetrics().density;
            
            mUnifiedVisualizerView.setRoundedBarsEnabled(mRoundedBarsEnabled);
            
            mUnifiedVisualizerView.setEdgeProperties(
                    mEdgeVisualizerEnabled && mCapturing,
                    (int) (mEdgeThickness * density),
                    mEdgeSensitivity,
                    mEdgeBarCountHoriz,
                    mEdgeBarCountVert,
                    mEdgeCornerRadius * density,
                    mEdgeTopEnabled,
                    mEdgeBottomEnabled,
                    mEdgeColor,
                    mEdgeOpacity,
                    mEdgeGlowBlurRadius,
                    mEdgeStyle
            );

            int extraPaddingPx = (int) (64 * density);
            int glowInsetPx = (mOverlayStyle == VisualizerStyle.GLOW)
                    ? (int) Math.max(extraPaddingPx, mOverlayGlowBlurRadius * 4f)
                    : extraPaddingPx;
            
            mUnifiedVisualizerView.setOverlayProperties(
                    mOverlayEnabled && mCapturing,
                    (int) (mOverlayWidth * density) + glowInsetPx * 2,
                    (int) (mOverlayHeight * density),
                    (int) (mOverlayHeightBottom * density),
                    (int) (mOverlayYOffset * density) - glowInsetPx,
                    mOverlaySensitivity,
                    mOverlaySensitivityBottom,
                    mOverlayTopEnabled,
                    mOverlayBottomEnabled,
                    mOverlayColor,
                    mOverlayOpacity,
                    mOverlayGlowBlurRadius,
                    mOverlayStyle,
                    glowInsetPx,
                    mEmulateHdrOpacity
            );

            mUnifiedVisualizerView.setLensProperties(
                    mLensVisualizerEnabled && mCapturing,
                    mLensVisualizerRadius * density,
                    mLensVisualizerWidth * density,
                    mLensVisualizerX,
                    mLensVisualizerY,
                    mLensVisualizerBarWidth * density,
                    mLensVisualizerMaxHeight * density,
                    mLensVisualizerBarCount,
                    mLensVisualizerSensitivity,
                    mLensColor,
                    mLensOpacity,
                    mLensGlowBlurRadius,
                    mLensStyle
            );
        });
    }

    public static void requestTileRefresh(Context context) {
        TileService.requestListeningState(context, new ComponentName(context, VisualizerTileService.class));
    }

    private void requestTileRefresh() {
        requestTileRefresh(this);
    }

    public static void requestWidgetRefresh(Context context) { Intent intent = new Intent("com.better.nothing.music.vizualizer.REFRESH_WIDGET"); intent.setPackage(context.getPackageName()); context.sendBroadcast(intent); }
    private void requestWidgetRefresh() { requestWidgetRefresh(this); }
    public static int loadLatencyCompensationMs(Context context, int device) { return context.getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getInt("latency_device_" + device, 0); }
    public static int loadLatencyCompensationMs(Context context, int device, String routeKey) { if (routeKey == null || routeKey.isEmpty()) return loadLatencyCompensationMs(context, device); return context.getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getInt("latency_" + routeKey, loadLatencyCompensationMs(context, device)); }
    public static float loadGamma(Context context) { return context.getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getFloat("gamma_value", 2.2f); }

    public static boolean isHapticEnabledGlobal(Context context) { return context.getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).getBoolean("haptic_motor_enabled", false); }
    public static Intent createStopIntent(Context context) { Intent intent = new Intent(context, AudioCaptureService.class); intent.setAction(ACTION_STOP); return intent; }
    private void refreshPresetCatalog() throws IOException, JSONException {
        JSONObject root = loadZonesConfigRoot(this); mAvailablePresetKeys = getPresetKeysForPhoneModel(root, phoneModelForDevice(mSelectedDevice)); if (mAvailablePresetKeys.isEmpty()) mAvailablePresetKeys = getAllPresetKeys(root);
    }

    private AudioProcessor.VisualizerConfig loadVisualizerConfig(String presetKey, int sampleRate) throws IOException, JSONException {
        JSONObject root = loadZonesConfigRoot(this); JSONObject p = root.optJSONObject(presetKey); if (p == null) throw new JSONException("Preset not found"); JSONArray za = p.optJSONArray("zones"); if (za == null || za.length() == 0) throw new JSONException("No zones");
        double da = p.has("decay-alpha") ? p.optDouble("decay-alpha", 0.8) : root.optDouble("decay-alpha", 0.8);
        da *= mGlyphDecaySpeed;
        AudioProcessor.ZoneSpec[] zs = parseZoneSpecs(za); return buildVisualizerConfig(presetKey, p.optString("preset_name", presetKey), p.optString("description", presetKey), da, zs);
    }

    private AudioProcessor.VisualizerConfig buildVisualizerConfig(String pk, String name, String d, double da, AudioProcessor.ZoneSpec[] zs) {
        float ad = 0.86f + ((float) da / 10f); List<float[]> up = new ArrayList<>(); Set<String> sp = new HashSet<>();
        for (AudioProcessor.ZoneSpec z : zs) { String key = String.format(Locale.US, "%.4f|%.4f", z.lowHz, z.highHz); if (sp.add(key)) up.add(new float[]{z.lowHz, z.highHz}); }
        up.sort((l, r) -> Float.compare(l[0], r[0])); AudioProcessor.FrequencyRange[] ur = new AudioProcessor.FrequencyRange[up.size()];
        for (int i = 0; i < up.size(); i++) ur[i] = new AudioProcessor.FrequencyRange(up.get(i)[0], up.get(i)[1]);
        int[][] zr = new int[zs.length][];
        for (int z = 0; z < zs.length; z++) {
            ArrayList<Integer> os = new ArrayList<>();
            for (int r = 0; r < ur.length; r++)
                if (ur[r].lowHz == zs[z].lowHz && ur[r].highHz == zs[z].highHz) os.add(r);
            int[] m = new int[os.size()];
            for (int i = 0; i < os.size(); i++) m[i] = os.get(i);
            zr[z] = m;
        }
        return new AudioProcessor.VisualizerConfig(pk, name, d, ad, zs, ur, zr);
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
    private static List<PresetInfo> buildPresetInfos(JSONObject root, List<String> keys) { ArrayList<PresetInfo> res = new ArrayList<>(); for (String k : keys) { JSONObject p = root.optJSONObject(k); if (p != null) res.add(new PresetInfo(k, p.optString("preset_name", k), p.optString("description", k))); } return res; }
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
        return mCurrentAudioRoute != null ? mCurrentAudioRoute.displayName : getString(R.string.audio_route_none);
    }

    private void refreshLatencyForCurrentAudioRoute() {
        mCurrentAudioRoute = resolveCurrentAudioRoute();
        int latency = loadLatencyCompensationMs(this, mSelectedDevice, getActiveAudioRouteKey());
        setLatencyCompensationMs(latency);
        Log.d(TAG, "Refreshed latency for route: " + getActiveAudioRouteName() + " -> " + latency + "ms");
    }

    private boolean isBluetoothOutput(AudioDeviceInfo device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                       device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                       device.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                       device.getType() == AudioDeviceInfo.TYPE_BLE_BROADCAST;
            } else {
                return device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
            }
        } else {
            return device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
        }
    }

    private boolean isWiredOutput(AudioDeviceInfo device) {
        return device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
               device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
               device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET;
    }

    public static boolean hasHapticMotor(Context c) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { VibratorManager vm = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE); return vm != null && vm.getDefaultVibrator().hasVibrator(); } Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE); return v != null && v.hasVibrator(); }
    public static boolean hasFlashlight(Context c) { return c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH); }
    private AudioRouteInfo resolveCurrentAudioRoute() {
        if (mAudioManager == null) return null;
        AudioDeviceInfo[] outputs = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo preferred = null;
        for (AudioDeviceInfo device : outputs) {
            if (isBluetoothOutput(device)) {
                preferred = device;
                break;
            }
        }
        if (preferred == null) {
            for (AudioDeviceInfo device : outputs) {
                if (isWiredOutput(device)) {
                    preferred = device;
                    break;
                }
            }
        }
        if (preferred == null) {
            for (AudioDeviceInfo device : outputs) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    preferred = device;
                    break;
                }
            }
        }

        if (preferred != null) {
            String name = (preferred.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) ?
                    getString(R.string.internal_speaker) :
                    preferred.getProductName().toString();
            return new AudioRouteInfo(preferred.getType() + "_" + name, name);
        }
        return null;
    }

    private void applyPresetSelection(String pk) { mPresetKey = pk; reloadConfig(); }
    public void setPreset(String p) { mPresetKey = p; restartCapture(); }

    public void connectUdp(String ip, int port) {
        Log.d(TAG, "Connecting to external UDP source: " + ip + ":" + port);
        mNetworkHostIp = ip;
        mNetworkHostPort = port;
        mCaptureSource = CaptureSource.NETWORK;
        getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE).edit().putString("capture_source", CaptureSource.NETWORK.name()).apply();
        
        if (!sIsRunning) {
            startNetworkCapture();
        } else {
            restartCapture();
        }
        
        if (mUdpSync != null) {
            mUdpSync.sendHandshake(ip, port);
        }
    }

    private int clampGlyphBrightness(int b) { return Math.max(0, Math.min(4095, b)); }
    private void resetVisualizerState() { if (mGlyphRenderer != null) mGlyphRenderer.resetState(mVisualizerConfig); }
}
