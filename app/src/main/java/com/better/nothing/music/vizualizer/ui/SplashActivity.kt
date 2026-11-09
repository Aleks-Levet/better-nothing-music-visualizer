package com.better.nothing.music.vizualizer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) {
                darkColorScheme(background = Color.Black, onBackground = Color.White)
            } else {
                lightColorScheme(background = Color.White, onBackground = Color.Black)
            }

            MaterialTheme(colorScheme = colorScheme) {
                AnimatedSplashScreen {
                    val nextIntent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                        if (intent?.extras != null) {
                            putExtras(intent.extras!!)
                        }
                    }
                    startActivity(nextIntent)
                    finish()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }
        }
    }
}
