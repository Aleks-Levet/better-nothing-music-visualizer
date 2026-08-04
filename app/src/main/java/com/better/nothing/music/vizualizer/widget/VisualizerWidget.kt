package com.better.nothing.music.vizualizer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.MainActivity

class VisualizerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Refresh all widgets if service state changes
        if (intent.action == "com.better.nothing.music.vizualizer.REFRESH_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, VisualizerWidget::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.visualizer_widget)

        val prefs = context.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        val isRunning = AudioCaptureService.isRunning()
        
        // Current states
        val currentSource = prefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
        val hapticEnabled = prefs.getBoolean("haptic_motor_enabled", false)
        val flashlightEnabled = prefs.getBoolean("flashlight_enabled", false)
        val maxBrightness = prefs.getInt("max_brightness", 4500)
        val glyphsEnabled = maxBrightness > 0

        // Source buttons
        updateButtonState(views, R.id.btn_source_internal, currentSource == AudioCaptureService.CaptureSource.INTERNAL.name)
        updateButtonState(views, R.id.btn_source_mic, currentSource == AudioCaptureService.CaptureSource.MIC.name)
        updateButtonState(views, R.id.btn_source_viz, currentSource == AudioCaptureService.CaptureSource.VIZUALIZER.name)

        views.setOnClickPendingIntent(R.id.btn_source_internal, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.INTERNAL))
        views.setOnClickPendingIntent(R.id.btn_source_mic, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.MIC))
        views.setOnClickPendingIntent(R.id.btn_source_viz, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.VIZUALIZER))

        // Viz output buttons
        val hasHaptic = AudioCaptureService.hasHapticMotor(context)
        val hasFlashlight = AudioCaptureService.hasFlashlight(context)

        views.setViewVisibility(R.id.btn_viz_haptics, if (hasHaptic) android.view.View.VISIBLE else android.view.View.GONE)
        views.setViewVisibility(R.id.btn_viz_torch, if (hasFlashlight) android.view.View.VISIBLE else android.view.View.GONE)

        updateButtonState(views, R.id.btn_viz_haptics, hapticEnabled)
        updateButtonState(views, R.id.btn_viz_glyphs, glyphsEnabled)
        updateButtonState(views, R.id.btn_viz_torch, flashlightEnabled)

        views.setOnClickPendingIntent(R.id.btn_viz_haptics, createActionPendingIntent(context, AudioCaptureService.ACTION_TOGGLE_HAPTICS, 10))
        views.setOnClickPendingIntent(R.id.btn_viz_glyphs, createActionPendingIntent(context, AudioCaptureService.ACTION_TOGGLE_GLYPHS, 11))
        views.setOnClickPendingIntent(R.id.btn_viz_torch, createActionPendingIntent(context, AudioCaptureService.ACTION_TOGGLE_TORCH, 12))

        // Start/Stop button
        val startStopPI = if (isRunning) {
            val stopIntent = Intent(context, AudioCaptureService::class.java).apply { action = AudioCaptureService.ACTION_STOP }
            PendingIntent.getService(context, 20, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            if (currentSource == AudioCaptureService.CaptureSource.INTERNAL.name) {
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_REQUEST_START, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                PendingIntent.getActivity(context, 20, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else {
                val startIntent = Intent(context, AudioCaptureService::class.java).apply { action = AudioCaptureService.ACTION_START }
                PendingIntent.getForegroundService(context, 20, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
        }
        
        views.setOnClickPendingIntent(R.id.btn_start_stop, startStopPI)
        views.setImageViewResource(R.id.btn_start_stop, if (isRunning) R.drawable.ic_stop else R.drawable.ic_play)
        updateButtonState(views, R.id.btn_start_stop, isRunning)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateButtonState(views: RemoteViews, viewId: Int, isActive: Boolean) {
        if (isActive) {
            views.setInt(viewId, "setBackgroundResource", R.drawable.widget_button_bg_selected)
            views.setInt(viewId, "setColorFilter", android.graphics.Color.BLACK)
        } else {
            views.setInt(viewId, "setBackgroundResource", R.drawable.widget_button_bg)
            views.setInt(viewId, "setColorFilter", android.graphics.Color.WHITE)
        }
    }

    private fun createSourcePendingIntent(context: Context, source: AudioCaptureService.CaptureSource): PendingIntent {
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_SET_SOURCE
            putExtra(AudioCaptureService.EXTRA_SOURCE, source.name)
        }
        return PendingIntent.getService(context, source.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
