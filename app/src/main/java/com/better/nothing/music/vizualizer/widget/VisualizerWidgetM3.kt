package com.better.nothing.music.vizualizer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.RemoteViews
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.MainActivity

class VisualizerWidgetM3 : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.better.nothing.music.vizualizer.REFRESH_WIDGET"
        const val ACTION_SET_SOURCE = "com.better.nothing.music.vizualizer.WIDGET_M3_SET_SOURCE"
        const val ACTION_TOGGLE_HAPTIC = "com.better.nothing.music.vizualizer.WIDGET_M3_TOGGLE_HAPTIC"
        const val ACTION_TOGGLE_GLYPHS = "com.better.nothing.music.vizualizer.WIDGET_M3_TOGGLE_GLYPHS"
        const val ACTION_TOGGLE_TORCH = "com.better.nothing.music.vizualizer.WIDGET_M3_TOGGLE_TORCH"
        
        private const val PREFS_NAME = "viz_prefs"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        
        if (action == ACTION_REFRESH) {
            refreshAllWidgets(context)
        } else if (action.startsWith("com.better.nothing.music.vizualizer.WIDGET_M3_")) {
            performHapticFeedback(context)
            handleWidgetAction(context, intent)
            refreshAllWidgets(context)
        }
    }

    private fun handleWidgetAction(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val action = intent.action
        
        when (action) {
            ACTION_SET_SOURCE -> {
                val source = intent.getStringExtra(AudioCaptureService.EXTRA_SOURCE) ?: return
                prefs.edit().putString("capture_source", source).apply()
                notifyService(context, AudioCaptureService.ACTION_REFRESH_SETTINGS)
            }
            ACTION_TOGGLE_HAPTIC -> {
                val current = prefs.getBoolean("haptic_motor_enabled", false)
                prefs.edit().putBoolean("haptic_motor_enabled", !current).apply()
                notifyService(context, AudioCaptureService.ACTION_REFRESH_SETTINGS)
            }
            ACTION_TOGGLE_GLYPHS -> {
                val currentMax = prefs.getInt("max_brightness", 4095)
                val nextVal = if (currentMax > 0) 0 else prefs.getInt("max_brightness_last", 4095)
                prefs.edit().apply {
                    putInt("max_brightness", nextVal)
                    if (nextVal > 0) putInt("max_brightness_last", nextVal)
                    apply()
                }
                notifyService(context, AudioCaptureService.ACTION_REFRESH_SETTINGS)
            }
            ACTION_TOGGLE_TORCH -> {
                val current = prefs.getBoolean("flashlight_enabled", false)
                prefs.edit().putBoolean("flashlight_enabled", !current).apply()
                notifyService(context, AudioCaptureService.ACTION_REFRESH_SETTINGS)
            }
        }
    }

    private fun notifyService(context: Context, serviceAction: String) {
        val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
            action = serviceAction
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {}
    }

    private fun performHapticFeedback(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibrator.vibrate(10)
            }
        }
    }

    private fun refreshAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, VisualizerWidgetM3::class.java))
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.visualizer_widget_m3)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isRunning = AudioCaptureService.isRunning()
        
        val currentSource = prefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
        val hapticEnabled = prefs.getBoolean("haptic_motor_enabled", false)
        val flashlightEnabled = prefs.getBoolean("flashlight_enabled", false)
        val maxBrightness = prefs.getInt("max_brightness", 4095)
        val glyphsEnabled = maxBrightness > 0

        views.setBoolean(R.id.btn_source_internal, "setSelected", currentSource == AudioCaptureService.CaptureSource.INTERNAL.name)
        views.setBoolean(R.id.btn_source_mic, "setSelected", currentSource == AudioCaptureService.CaptureSource.MIC.name)
        views.setBoolean(R.id.btn_source_viz, "setSelected", currentSource == AudioCaptureService.CaptureSource.VIZUALIZER.name)

        views.setOnClickPendingIntent(R.id.btn_source_internal, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.INTERNAL))
        views.setOnClickPendingIntent(R.id.btn_source_mic, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.MIC))
        views.setOnClickPendingIntent(R.id.btn_source_viz, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.VIZUALIZER))

        val hasHaptic = AudioCaptureService.hasHapticMotor(context)
        val hasFlashlight = AudioCaptureService.hasFlashlight(context)

        views.setViewVisibility(R.id.btn_viz_haptics, if (hasHaptic) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btn_viz_torch, if (hasFlashlight) android.view.View.VISIBLE else android.view.View.GONE)

        views.setBoolean(R.id.btn_viz_haptics, "setSelected", hapticEnabled)
        views.setBoolean(R.id.btn_viz_glyphs, "setSelected", glyphsEnabled)
        views.setBoolean(R.id.btn_viz_torch, "setSelected", flashlightEnabled)

        views.setOnClickPendingIntent(R.id.btn_viz_haptics, createActionPendingIntent(context, ACTION_TOGGLE_HAPTIC, 110))
        views.setOnClickPendingIntent(R.id.btn_viz_glyphs, createActionPendingIntent(context, ACTION_TOGGLE_GLYPHS, 111))
        views.setOnClickPendingIntent(R.id.btn_viz_torch, createActionPendingIntent(context, ACTION_TOGGLE_TORCH, 112))

        val startStopPI = if (isRunning) {
            val stopIntent = Intent(context, AudioCaptureService::class.java).apply { action = AudioCaptureService.ACTION_STOP }
            PendingIntent.getService(context, 120, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            if (currentSource == AudioCaptureService.CaptureSource.INTERNAL.name) {
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("request_start", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                PendingIntent.getActivity(context, 120, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else {
                val startIntent = Intent(context, AudioCaptureService::class.java).apply { action = AudioCaptureService.ACTION_START }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(context, 120, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                } else {
                    PendingIntent.getService(context, 120, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            }
        }
        
        views.setOnClickPendingIntent(R.id.btn_start_stop, startStopPI)
        views.setImageViewResource(R.id.img_start_stop, if (isRunning) R.drawable.ic_stop else R.drawable.ic_play)
        views.setTextViewText(R.id.txt_start_stop, if (isRunning) "STOP BNMV" else "START BNMV")
        views.setBoolean(R.id.btn_start_stop, "setSelected", isRunning)
        views.setBoolean(R.id.img_start_stop, "setSelected", isRunning)
        views.setBoolean(R.id.txt_start_stop, "setSelected", isRunning)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createSourcePendingIntent(context: Context, source: AudioCaptureService.CaptureSource): PendingIntent {
        val intent = Intent(context, VisualizerWidgetM3::class.java).apply {
            action = ACTION_SET_SOURCE
            putExtra(AudioCaptureService.EXTRA_SOURCE, source.name)
        }
        return PendingIntent.getBroadcast(context, source.ordinal + 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, VisualizerWidgetM3::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
