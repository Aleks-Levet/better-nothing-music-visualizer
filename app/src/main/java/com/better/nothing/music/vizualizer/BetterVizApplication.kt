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
            }
            
            firebaseAnalytics = Firebase.analytics
            
            // Set user properties for basic tracking
            firebaseAnalytics?.setUserProperty("app_version", BuildConfig.VERSION_NAME)
            firebaseAnalytics?.setUserProperty("device_model", Build.MODEL)
            
            // Log an app open event
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
        } catch (e: Exception) {
            Log.e("BetterVizApp", "Failed to initialize Firebase Analytics", e)
        }
    }
}
