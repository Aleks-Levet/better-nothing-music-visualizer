package com.better.nothing.music.vizualizer.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.better.nothing.music.vizualizer.service.AudioCaptureService

import androidx.core.content.edit

class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        val prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE)
        val sourceStr = prefs.getString("capture_source", "INTERNAL")
        val needsMic = "MIC" == sourceStr || "VIZUALIZER" == sourceStr
        val hasMicPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        // Notification permission is optional, only redirect to MainActivity if we haven't asked yet
        val needsNotifPrompt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            // If we don't have permission and haven't been denied before (shouldShowRequestPermissionRationale is false 
            // before any prompt, but we can't easily check "never asked" without a pref).
            // Let's use a simple pref to only prompt once from the Tile.
            !hasPerm && !prefs.getBoolean("notif_prompt_shown", false)
        } else {
            false
        }

        if (sourceStr == "INTERNAL" || (needsMic && !hasMicPerm) || needsNotifPrompt) {
            if (needsNotifPrompt) {
                prefs.edit { putBoolean("notif_prompt_shown", true) }
            }
            // Need MainActivity for projection prompt or permission request
            val intentToMain = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_REQUEST_START, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intentToMain)
            finish()
        } else {
            // We have permissions and it's a direct start source (MIC/NETWORK)
            // Start the service directly from here (foreground) to satisfy Android 14 requirements
            val startIntent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
            }
            startForegroundService(startIntent)
            
            // On Android 14+, finishing too quickly might cause the foreground service 
            // to fail starting with microphone type (SecurityException).
            // We stay alive for a brief moment to ensure we are "in foreground".
            if (Build.VERSION.SDK_INT >= 34) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    finish()
                }, 500)
            } else {
                finish()
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
