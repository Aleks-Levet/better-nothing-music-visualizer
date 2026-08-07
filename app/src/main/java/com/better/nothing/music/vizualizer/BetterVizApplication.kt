package com.better.nothing.music.vizualizer

import android.app.Application
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

class BetterVizApplication : Application() {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Force initialize Firebase if not automatically done
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("BetterVizApp", "Firebase initialized manually")
            } else {
                Log.d("BetterVizApp", "Firebase initialized automatically")
            }
            
            firebaseAnalytics = FirebaseAnalytics.getInstance(this)
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
