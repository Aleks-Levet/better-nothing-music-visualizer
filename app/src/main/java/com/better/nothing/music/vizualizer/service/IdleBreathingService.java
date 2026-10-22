package com.better.nothing.music.vizualizer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
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

import androidx.core.app.NotificationCompat;

import com.better.nothing.music.vizualizer.R;
import com.better.nothing.music.vizualizer.logic.IdleBreathingRenderer;
import com.better.nothing.music.vizualizer.model.DeviceProfile;
import com.better.nothing.music.vizualizer.ui.MainActivity;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphManager;
import com.nothing.ketchum.GlyphMatrixManager;

public class IdleBreathingService extends Service {
    private static final String TAG = "IdleBreathingService";
    private static final String CHANNEL_ID = "idle_breathing";
    private static final int NOTIFICATION_ID = 4102;
    private static final String ACTION_PREVIOUS_PATTERN =
            "com.better.nothing.music.vizualizer.action.IDLE_PREVIOUS_PATTERN";
    private static final String ACTION_NEXT_PATTERN =
            "com.better.nothing.music.vizualizer.action.IDLE_NEXT_PATTERN";
    private static final String[] IDLE_PATTERNS = {
            "pulse", "wave", "rain", "zebra", "orbit", "heartbeat", "scanner"
    };
    private static volatile boolean sActive;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener mSettingsListener =
            this::onSettingsChanged;
    private volatile boolean mStopping;
    private int mDeviceType = DeviceProfile.DEVICE_UNKNOWN;
    private boolean mSessionOpen;
    private boolean mMatrixConnected;
    private GlyphManager mGlyphManager;
    private GlyphMatrixManager mMatrixManager;
    private IdleBreathingRenderer mRenderer;
    private int mMaxBrightness;
    private long mFrameEpochUptimeMs;
    private long mNextFrameNumber;

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
                int[] colors = mRenderer.renderFrame(
                        DeviceProfile.getLedCount(mDeviceType),
                        mMaxBrightness,
                        SystemClock.elapsedRealtime());
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
            scheduleNextFrame();
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

    @Override
    public void onCreate() {
        super.onCreate();
        getSharedPreferences("viz_prefs", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(mSettingsListener);
    }

    private void onSettingsChanged(SharedPreferences prefs, String key) {
        if (mRenderer == null) return;

        switch (key) {
            case "idle_pattern" -> {
                mRenderer.setPattern(prefs.getString("idle_pattern", "pulse"));
                refreshNotification(prefs);
            }
            case "idle_brightness" ->
                    mRenderer.setBrightness(prefs.getFloat("idle_brightness", 0.4f));
            case "idle_background_brightness" ->
                    mRenderer.setBackgroundBrightness(
                            prefs.getFloat("idle_background_brightness", 0.02f));
            case "idle_animation_speed" ->
                    mRenderer.setAnimationSpeed(prefs.getFloat("idle_animation_speed", 1.0f));
            case "max_brightness" -> {
                int brightness = prefs.getInt("max_brightness", 4095);
                if (brightness <= 0) {
                    stopSelf();
                } else {
                    mMaxBrightness = Math.max(50, Math.min(5000, brightness));
                }
            }
            default -> { }
        }
    }

    private boolean initializeAnimation() {
        mDeviceType = DeviceProfile.detectDevice();
        if (mDeviceType == DeviceProfile.DEVICE_UNKNOWN) {
            Log.w(TAG, "Idle breathing is unavailable on this device");
            return false;
        }

        SharedPreferences prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE);
        int brightness = prefs.getInt("max_brightness", 4095);
        if (brightness <= 0) {
            Log.i(TAG, "Idle breathing is disabled because Glyph brightness is off");
            return false;
        }

        int count = DeviceProfile.getLedCount(mDeviceType);
        if (count <= 0) {
            Log.e(TAG, "No Glyph zones are available for device " + mDeviceType);
            return false;
        }

        mMaxBrightness = Math.max(50, Math.min(5000, brightness));
        mRenderer = new IdleBreathingRenderer(mDeviceType, true);
        mRenderer.setPattern(prefs.getString("idle_pattern", "pulse"));
        mRenderer.setBrightness(prefs.getFloat("idle_brightness", 0.4f));
        mRenderer.setBackgroundBrightness(prefs.getFloat("idle_background_brightness", 0.02f));
        mRenderer.setAnimationSpeed(prefs.getFloat("idle_animation_speed", 1.0f));
        mRenderer.start(SystemClock.elapsedRealtime());

        return initializeGlyphManagers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PREVIOUS_PATTERN.equals(action) || ACTION_NEXT_PATTERN.equals(action)) {
            if (sActive) {
                changePattern(ACTION_PREVIOUS_PATTERN.equals(action) ? -1 : 1);
            } else {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }

        if (AudioCaptureService.isRunning()
                || getSharedPreferences("viz_prefs", MODE_PRIVATE).getBoolean("should_be_running", false)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (sActive) return START_NOT_STICKY;

        startAsForegroundService();
        if (!initializeAnimation()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        sActive = true;
        requestTileRefresh();
        return START_NOT_STICKY;
    }

    private void changePattern(int direction) {
        SharedPreferences prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE);
        String currentPattern = prefs.getString("idle_pattern", IDLE_PATTERNS[0]);
        int currentIndex = 0;
        for (int i = 0; i < IDLE_PATTERNS.length; i++) {
            if (IDLE_PATTERNS[i].equals(currentPattern)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + direction + IDLE_PATTERNS.length) % IDLE_PATTERNS.length;
        String nextPattern = IDLE_PATTERNS[nextIndex];
        prefs.edit().putString("idle_pattern", nextPattern).apply();
        if (mRenderer != null) mRenderer.setPattern(nextPattern);

        refreshNotification(prefs);
    }

    private void refreshNotification(SharedPreferences prefs) {
        if (!sActive) return;
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is unavailable; cannot refresh idle notification");
            return;
        }
        notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(prefs.getString("idle_pattern", IDLE_PATTERNS[0])));
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
        SharedPreferences prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE);
        String currentPattern = prefs.getString("idle_pattern", IDLE_PATTERNS[0]);
        Notification notification = buildNotification(contentIntent, currentPattern);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String currentPattern) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return buildNotification(contentIntent, currentPattern);
    }

    private Notification buildNotification(PendingIntent contentIntent, String currentPattern) {
        PendingIntent previousIntent = PendingIntent.getService(
                this,
                1,
                new Intent(this, IdleBreathingService.class).setAction(ACTION_PREVIOUS_PATTERN),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent nextIntent = PendingIntent.getService(
                this,
                2,
                new Intent(this, IdleBreathingService.class).setAction(ACTION_NEXT_PATTERN),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.idle_breathing_title))
                .setContentText(getString(R.string.idle_pattern) + ": " + getPatternLabel(currentPattern))
                .setSmallIcon(R.drawable.ic_notif_monochrome)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_previous, getString(R.string.notification_action_previous), previousIntent)
                .addAction(android.R.drawable.ic_media_next, getString(R.string.notification_action_next), nextIntent)
                .build();
    }

    private String getPatternLabel(String pattern) {
        int labelResource = switch (pattern) {
            case "wave" -> R.string.idle_pattern_wave;
            case "rain" -> R.string.idle_pattern_rain;
            case "zebra" -> R.string.idle_pattern_zebra;
            case "orbit" -> R.string.idle_pattern_orbit;
            case "heartbeat" -> R.string.idle_pattern_heartbeat;
            case "scanner" -> R.string.idle_pattern_cylon;
            default -> R.string.idle_pattern_pulse;
        };
        return getString(labelResource);
    }

    private boolean initializeGlyphManagers() {
        try {
            mGlyphManager = GlyphManager.getInstance(getApplicationContext());
            if (mGlyphManager == null) throw new IllegalStateException("GlyphManager is unavailable");
            mGlyphManager.init(mGlyphCallback);

            if (DeviceProfile.getMatrixWidth(mDeviceType) > 0) {
                mMatrixManager = GlyphMatrixManager.getInstance(getApplicationContext());
                if (mMatrixManager == null) throw new IllegalStateException("GlyphMatrixManager is unavailable");
                mMatrixManager.init(mMatrixCallback);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Glyph managers", e);
            return false;
        }
    }

    private void maybeStartAnimation() {
        if (!mSessionOpen || (DeviceProfile.getMatrixWidth(mDeviceType) > 0 && !mMatrixConnected)) return;
        mHandler.removeCallbacks(mFrameRunnable);
        mFrameEpochUptimeMs = SystemClock.uptimeMillis();
        mNextFrameNumber = 0;
        mHandler.post(mFrameRunnable);
    }

    private void scheduleNextFrame() {
        long now = SystemClock.uptimeMillis();
        mNextFrameNumber = Math.max(
                mNextFrameNumber + 1,
                ((now - mFrameEpochUptimeMs) * 60 / 1000) + 1);
        mHandler.postAtTime(
                mFrameRunnable,
                mFrameEpochUptimeMs + mNextFrameNumber * 1000 / 60);
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
        getSharedPreferences("viz_prefs", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(mSettingsListener);
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
