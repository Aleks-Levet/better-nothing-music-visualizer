package com.better.nothing.music.vizualizer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService

class TrampolineActivity : AppCompatActivity() {

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val startSource = intent.getStringExtra(AudioCaptureService.EXTRA_START_SOURCE)
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val startIntent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_START_SOURCE, startSource)
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(AudioCaptureService.EXTRA_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                Handler(Looper.getMainLooper()).postDelayed({
                    finishWithNoAnimation()
                }, 500)
            } else {
                finishWithNoAnimation()
            }
        } else {
            AudioCaptureService.requestTileRefresh(this)
            finishWithNoAnimation()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val startSource = intent.getStringExtra(AudioCaptureService.EXTRA_START_SOURCE)
        if (isGranted) {
            startServiceDirectly(startSource)
        } else {
            Toast.makeText(this, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
            AudioCaptureService.requestTileRefresh(this)
            finishWithNoAnimation()
        }
    }

    private val localNetworkPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val startSource = intent.getStringExtra(AudioCaptureService.EXTRA_START_SOURCE)
        if (isGranted) {
            startServiceDirectly(startSource)
        } else {
            Toast.makeText(this, "Local network permission is required", Toast.LENGTH_SHORT).show()
            AudioCaptureService.requestTileRefresh(this)
            finishWithNoAnimation()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        proceedWithCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        overridePendingTransitions()

        if (AudioCaptureService.isRunning()) {
            val stopIntent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_STOP
            }
            startService(stopIntent)
            finishWithNoAnimation()
            return
        }

        val prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE)
        val needsNotifPrompt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            !hasPerm && !prefs.getBoolean("notif_prompt_shown", false)
        } else {
            false
        }

        if (needsNotifPrompt) {
            prefs.edit { putBoolean("notif_prompt_shown", true) }
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            proceedWithCapture()
        }
    }

    private fun proceedWithCapture() {
        val prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE)
        val savedSource = prefs.getString("capture_source", AudioCaptureService.defaultCaptureSource(this).name)
        val sourceStr = savedSource ?: "INTERNAL"
        val startSource = intent.getStringExtra(AudioCaptureService.EXTRA_START_SOURCE)

        when (sourceStr) {
            "INTERNAL" -> {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                if (projectionManager != null) {
                    try {
                        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to launch screen capture prompt", Toast.LENGTH_SHORT).show()
                        AudioCaptureService.requestTileRefresh(this)
                        finishWithNoAnimation()
                    }
                } else {
                    AudioCaptureService.requestTileRefresh(this)
                    finishWithNoAnimation()
                }
            }
            "MIC", "VIZUALIZER" -> {
                val hasMicPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMicPerm) {
                    startServiceDirectly(startSource)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            "NETWORK" -> {
                if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                    localNetworkPermissionLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK")
                } else {
                    startServiceDirectly(startSource)
                }
            }
            else -> {
                startServiceDirectly(startSource)
            }
        }
    }

    private fun startServiceDirectly(startSource: String?) {
        val startIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START
            putExtra(AudioCaptureService.EXTRA_START_SOURCE, startSource)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(startIntent)
        } else {
            startService(startIntent)
        }

        if (Build.VERSION.SDK_INT >= 34) {
            Handler(Looper.getMainLooper()).postDelayed({
                finishWithNoAnimation()
            }, 500)
        } else {
            finishWithNoAnimation()
        }
    }

    private fun overridePendingTransitions() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun finishWithNoAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
