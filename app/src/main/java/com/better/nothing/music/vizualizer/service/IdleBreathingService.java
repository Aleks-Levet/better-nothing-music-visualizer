package com.better.nothing.music.vizualizer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.service.quicksettings.TileService;
import android.util.Log;

import com.better.nothing.music.vizualizer.R;
import com.better.nothing.music.vizualizer.logic.AudioProcessor;
import com.better.nothing.music.vizualizer.logic.GlyphRenderer;
import com.better.nothing.music.vizualizer.model.DeviceProfile;
import com.better.nothing.music.vizualizer.ui.MainActivity;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphManager;
import com.nothing.ketchum.GlyphMatrixManager;

public class IdleBreathingService extends Service {
    private static final String TAG = "IdleBreathingService";
    private static final String CHANNEL_ID = "idle_breathing";
    private static final int NOTIFICATION_ID = 4102;
    private static final int FRAME_INTERVAL_MS = 33;
    private static volatile boolean sActive;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int[] mSilentFft = new int[512];
    private volatile boolean mStopping;
    private int mDeviceType = DeviceProfile.DEVICE_UNKNOWN;
    private boolean mSessionOpen;
    private boolean mMatrixConnected;
    private GlyphManager mGlyphManager;
    private GlyphMatrixManager mMatrixManager;
    private GlyphRenderer mRenderer;
    private AudioProcessor.VisualizerConfig mIdleConfig;

    private final Runnable mFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (mStopping) return;
            if (AudioCaptureService.isRunning()
                    || getSharedPreferences("viz_prefs", MODE_PRIVATE).getBoolean("should_be_running", false)) {
                stopSelf();
                return;
            }

            if (mSessionOpen && mRenderer != null) {
                int[] colors = mRenderer.processFrame(mSilentFft, mIdleConfig, SystemClock.elapsedRealtime(), true);
                if (colors != null) {
                    try {
                        if (DeviceProfile.getMatrixWidth(mDeviceType) > 0) {
                            if (mMatrixConnected && mMatrixManager != null) {
                                mMatrixManager.setAppMatrixFrame(colors);
                            }
                        } else if (mGlyphManager != null) {
                            mGlyphManager.setFrameColors(colors);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to update idle Glyph frame", e);
                        stopSelf();
                        return;
                    }
                }
            }
            mHandler.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    private final GlyphManager.Callback mGlyphCallback = new GlyphManager.Callback() {
        @Override
        public void onServiceConnected(ComponentName name) {
            try {
                if (mGlyphManager == null || mStopping) return;
                mGlyphManager.register(getGlyphDeviceName());
                mGlyphManager.openSession();
                mSessionOpen = true;
                maybeStartAnimation();
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to Glyph service", e);
                stopSelf();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSessionOpen = false;
            stopSelf();
        }
    };

    private final GlyphMatrixManager.Callback mMatrixCallback = new GlyphMatrixManager.Callback() {
        @Override
        public void onServiceConnected(ComponentName name) {
            try {
                if (mMatrixManager == null || mStopping) return;
                mMatrixManager.register(getGlyphDeviceName());
                mMatrixConnected = true;
                maybeStartAnimation();
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to Glyph Matrix service", e);
                stopSelf();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMatrixConnected = false;
            stopSelf();
        }
    };

    public static boolean isActive() {
        return sActive;
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, IdleBreathingService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void initializeAnimation() {
        mDeviceType = DeviceProfile.detectDevice();
        if (mDeviceType == DeviceProfile.DEVICE_UNKNOWN) {
            Log.w(TAG, "Idle breathing is unavailable on this device");
            stopSelf();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE);
        int brightness = prefs.getInt("max_brightness", 4095);
        if (brightness <= 0) {
            Log.i(TAG, "Idle breathing is disabled because Glyph brightness is off");
            stopSelf();
            return;
        }

        int count = DeviceProfile.getLedCount(mDeviceType);
        if (count <= 0) {
            Log.e(TAG, "No Glyph zones are available for device " + mDeviceType);
            stopSelf();
            return;
        }

        AudioProcessor.ZoneSpec[] zones = new AudioProcessor.ZoneSpec[count];
        int[][] zoneToRanges = new int[count][];
        for (int i = 0; i < count; i++) {
            zones[i] = new AudioProcessor.ZoneSpec(1f, 1f, Float.NaN, Float.NaN);
            zoneToRanges[i] = new int[0];
        }
        mIdleConfig = new AudioProcessor.VisualizerConfig(
                "idle", "Idle Breathing", "", 0.85f, zones,
                new AudioProcessor.FrequencyRange[0], zoneToRanges);

        mRenderer = new GlyphRenderer(AudioCaptureService.loadGamma(this), true, mDeviceType);
        mRenderer.resetState(mIdleConfig);
        mRenderer.setMaxBrightness(brightness);
        mRenderer.setIdlePattern(prefs.getString("idle_pattern", "pulse"));
        mRenderer.setIdleBrightness(prefs.getFloat("idle_brightness", 0.4f));
        mRenderer.setIdleBackgroundBrightness(prefs.getFloat("idle_background_brightness", 0.02f));
        mRenderer.setIdleAnimationSpeed(prefs.getFloat("idle_animation_speed", 1.0f));
        mRenderer.startIdleBreathingAnimation(SystemClock.elapsedRealtime());

        initializeGlyphManagers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (AudioCaptureService.isRunning()
                || getSharedPreferences("viz_prefs", MODE_PRIVATE).getBoolean("should_be_running", false)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (sActive) return START_NOT_STICKY;

        startAsForegroundService();
        sActive = true;
        requestTileRefresh();
        initializeAnimation();
        return START_NOT_STICKY;
    }

    private void startAsForegroundService() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.idle_breathing_title), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.idle_breathing_title));
            notificationManager.createNotificationChannel(channel);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.idle_breathing_title))
                .setContentText(getString(R.string.tile_running))
                .setSmallIcon(R.drawable.ic_notif_monochrome)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setSilent(true);
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void initializeGlyphManagers() {
        try {
            mGlyphManager = GlyphManager.getInstance(getApplicationContext());
            if (mGlyphManager == null) throw new IllegalStateException("GlyphManager is unavailable");
            mGlyphManager.init(mGlyphCallback);

            if (DeviceProfile.getMatrixWidth(mDeviceType) > 0) {
                mMatrixManager = GlyphMatrixManager.getInstance(getApplicationContext());
                if (mMatrixManager == null) throw new IllegalStateException("GlyphMatrixManager is unavailable");
                mMatrixManager.init(mMatrixCallback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Glyph managers", e);
            stopSelf();
        }
    }

    private void maybeStartAnimation() {
        if (!mSessionOpen || (DeviceProfile.getMatrixWidth(mDeviceType) > 0 && !mMatrixConnected)) return;
        mHandler.removeCallbacks(mFrameRunnable);
        mHandler.post(mFrameRunnable);
    }

    private String getGlyphDeviceName() {
        return switch (mDeviceType) {
            case DeviceProfile.DEVICE_NP1 -> Glyph.DEVICE_20111;
            case DeviceProfile.DEVICE_NP2 -> Glyph.DEVICE_22111;
            case DeviceProfile.DEVICE_NP2A -> Build.MODEL.contains("23113") ? "23113" : Glyph.DEVICE_23111;
            case DeviceProfile.DEVICE_NP3A -> Glyph.DEVICE_24111;
            case DeviceProfile.DEVICE_NP4A -> Glyph.DEVICE_25111;
            case DeviceProfile.DEVICE_NP4APRO -> Glyph.DEVICE_25111p;
            case DeviceProfile.DEVICE_NP3 -> Glyph.DEVICE_23112;
            case DeviceProfile.DEVICE_NP4B -> "26111";
            default -> throw new IllegalStateException("Unsupported Glyph device");
        };
    }

    private void requestTileRefresh() {
        TileService.requestListeningState(
                this, new ComponentName(this, IdleBreathingTileService.class));
    }

    @Override
    public void onDestroy() {
        mStopping = true;
        mHandler.removeCallbacks(mFrameRunnable);
        if (mSessionOpen && mGlyphManager != null) {
            try {
                int count = DeviceProfile.getLedCount(mDeviceType);
                if (DeviceProfile.getMatrixWidth(mDeviceType) > 0 && mMatrixManager != null) {
                    mMatrixManager.setAppMatrixFrame(new int[count]);
                    mMatrixManager.closeAppMatrix();
                } else {
                    mGlyphManager.setFrameColors(new int[count]);
                    mGlyphManager.turnOff();
                }
                mGlyphManager.closeSession();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close idle Glyph session", e);
            }
            mSessionOpen = false;
        }
        if (mGlyphManager != null) {
            try {
                mGlyphManager.unInit();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release Glyph manager", e);
            }
        }
        if (mMatrixManager != null) {
            try {
                mMatrixManager.unInit();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release Glyph Matrix manager", e);
            }
        }
        sActive = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        requestTileRefresh();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
