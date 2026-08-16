package com.better.nothing.music.vizualizer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.TrampolineActivity

class ExternalControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "com.better.nothing.music.vizualizer.ACTION_START"
        const val ACTION_STOP = "com.better.nothing.music.vizualizer.ACTION_STOP"
        const val ACTION_TOGGLE = "com.better.nothing.music.vizualizer.ACTION_TOGGLE"
        
        const val ACTION_SET_SOURCE = "com.better.nothing.music.vizualizer.ACTION_SET_SOURCE"
        const val ACTION_SET_PRESET = "com.better.nothing.music.vizualizer.ACTION_SET_PRESET"
        
        const val ACTION_TOGGLE_GLYPHS = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_GLYPHS"
        const val ACTION_TOGGLE_HAPTICS = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_HAPTICS"
        const val ACTION_TOGGLE_TORCH = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_TORCH"
        const val ACTION_TOGGLE_BROADCAST = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_BROADCAST"
        
        const val ACTION_TOGGLE_OVERLAY = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_OVERLAY"
        const val ACTION_TOGGLE_EDGE = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_EDGE"
        const val ACTION_TOGGLE_LENS = "com.better.nothing.music.vizualizer.ACTION_TOGGLE_LENS"
        
        const val EXTRA_SOURCE = "source" // INTERNAL, MIC, VIZUALIZER, NETWORK
        const val EXTRA_PRESET = "preset"
        const val EXTRA_ENABLED = "enabled" // Boolean for toggles (optional)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        when (action) {
            ACTION_START -> startVisualizer(context)
            ACTION_STOP -> stopVisualizer(context)
            ACTION_TOGGLE -> {
                if (AudioCaptureService.isRunning()) stopVisualizer(context)
                else startVisualizer(context)
            }
            ACTION_SET_SOURCE -> {
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: intent.getStringExtra(AudioCaptureService.EXTRA_SOURCE)
                if (source != null) {
                    val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
                        this.action = AudioCaptureService.ACTION_SET_SOURCE
                        putExtra(AudioCaptureService.EXTRA_SOURCE, source)
                    }
                    context.startService(serviceIntent)
                }
            }
            ACTION_SET_PRESET -> {
                val preset = intent.getStringExtra(EXTRA_PRESET) ?: intent.getStringExtra(AudioCaptureService.EXTRA_PRESET_KEY)
                if (preset != null) {
                    val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
                        this.action = AudioCaptureService.ACTION_SET_PRESET
                        putExtra(AudioCaptureService.EXTRA_PRESET_KEY, preset)
                    }
                    context.startService(serviceIntent)
                }
            }
            ACTION_TOGGLE_GLYPHS -> forwardToService(context, intent, AudioCaptureService.ACTION_TOGGLE_GLYPHS)
            ACTION_TOGGLE_HAPTICS -> forwardToService(context, intent, AudioCaptureService.ACTION_TOGGLE_HAPTICS)
            ACTION_TOGGLE_TORCH -> forwardToService(context, intent, AudioCaptureService.ACTION_TOGGLE_TORCH)
            ACTION_TOGGLE_BROADCAST -> forwardToService(context, intent, AudioCaptureService.ACTION_TOGGLE_BROADCAST)
            ACTION_TOGGLE_OVERLAY -> forwardToService(context, intent, AudioCaptureService.ACTION_TOGGLE_OVERLAY)
            ACTION_TOGGLE_EDGE -> forwardToService(context, intent, AudioCaptureService.ACTION_TOGGLE_EDGE)
            ACTION_TOGGLE_LENS -> forwardToService(context, intent, AudioCaptureService.ACTION_TOGGLE_LENS)
        }
    }

    private fun startVisualizer(context: Context) {
        if (AudioCaptureService.isRunning()) return
        
        val intent = Intent(context, TrampolineActivity::class.java).apply {
            putExtra(AudioCaptureService.EXTRA_START_SOURCE, "viz_started_external")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    private fun stopVisualizer(context: Context) {
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun forwardToService(context: Context, originalIntent: Intent, serviceAction: String) {
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = serviceAction
            if (originalIntent.hasExtra(EXTRA_ENABLED)) {
                putExtra(AudioCaptureService.EXTRA_ENABLED, originalIntent.getBooleanExtra(EXTRA_ENABLED, false))
            } else if (originalIntent.hasExtra(AudioCaptureService.EXTRA_ENABLED)) {
                putExtra(AudioCaptureService.EXTRA_ENABLED, originalIntent.getBooleanExtra(AudioCaptureService.EXTRA_ENABLED, false))
            }
        }
        context.startService(intent)
    }
}
