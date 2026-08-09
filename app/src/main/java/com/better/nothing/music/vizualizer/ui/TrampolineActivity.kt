package com.better.nothing.music.vizualizer.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.better.nothing.music.vizualizer.service.AudioCaptureService

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

        if (sourceStr == "INTERNAL" || (needsMic && !hasMicPerm)) {
            // Need MainActivity for projection prompt or permission request
            val intentToMain = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_REQUEST_START, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intentToMain)
        } else {
            // We have permissions and it's a direct start source (MIC/NETWORK)
            // Start the service directly from here (foreground) to satisfy Android 14 requirements
            val startIntent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
            }
            startForegroundService(startIntent)
        }
        finish()
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
