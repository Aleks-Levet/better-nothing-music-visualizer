package com.better.nothing.music.vizualizer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.better.nothing.music.vizualizer.widget.VisualizerWidget
import com.better.nothing.music.vizualizer.widget.VisualizerWidgetM3
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BetterVizApplication : Application() {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        
        appScope.launch {
            // Pre-warm SharedPreferences on background thread to prevent UI thread disk lock
            this@BetterVizApplication.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)

            try {
                // Force initialize Firebase off the main thread
                if (FirebaseApp.getApps(this@BetterVizApplication).isEmpty()) {
                    FirebaseApp.initializeApp(this@BetterVizApplication)
                    Log.d("BetterVizApp", "Firebase initialized manually")
                } else {
                    Log.d("BetterVizApp", "Firebase initialized automatically")
                }
                
                firebaseAnalytics = FirebaseAnalytics.getInstance(this@BetterVizApplication)
                firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
                
                // Set user properties for basic tracking
                firebaseAnalytics?.setUserProperty("app_version_custom", BuildConfig.VERSION_NAME)
                firebaseAnalytics?.setUserProperty("device_model", Build.MODEL)
                
                // Log an app open event
                firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
                Log.d("BetterVizApp", "Firebase Analytics event logged")
            } catch (e: Exception) {
                Log.e("BetterVizApp", "Failed to initialize Firebase Analytics", e)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Refresh widgets when system theme changes
        val refreshIntent = Intent(VisualizerWidget.ACTION_REFRESH).apply {
            setPackage(packageName)
        }
        sendBroadcast(refreshIntent)
    }
}
