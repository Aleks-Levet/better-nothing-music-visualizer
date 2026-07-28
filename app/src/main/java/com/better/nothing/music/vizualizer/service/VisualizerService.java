package com.better.nothing.music.vizualizer.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;

import com.better.nothing.music.vizualizer.ui.LensVisualizerView;

public class VisualizerService extends Service {
    private WindowManager mWindowManager;
    private LensVisualizerView mVisualizerView;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVisualizerView != null && AudioCaptureService.sInstance != null) {
                float[] magnitudes = AudioCaptureService.sInstance.getLatestMagnitudes();
                mVisualizerView.updateMagnitudes(magnitudes);
                
                // Update properties from service instance
                mVisualizerView.setRadius(AudioCaptureService.sInstance.mLensVisualizerRadius);
                mVisualizerView.setXPosition(AudioCaptureService.sInstance.mLensVisualizerX);
                mVisualizerView.setYPosition(AudioCaptureService.sInstance.mLensVisualizerY);
                mVisualizerView.setBarWidth(AudioCaptureService.sInstance.mLensVisualizerBarWidth);
                mVisualizerView.setMaxHeight(AudioCaptureService.sInstance.mLensVisualizerMaxHeight);
                mVisualizerView.setBarCount(AudioCaptureService.sInstance.mLensVisualizerBarCount);
                mVisualizerView.setSensitivity(AudioCaptureService.sInstance.mLensVisualizerSensitivity);
            }
            mHandler.postDelayed(this, 16); // ~60 FPS
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mVisualizerView = new LensVisualizerView(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        mWindowManager.addView(mVisualizerView, params);
        mHandler.post(mUpdateRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mUpdateRunnable);
        if (mVisualizerView != null) {
            mWindowManager.removeView(mVisualizerView);
            mVisualizerView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
