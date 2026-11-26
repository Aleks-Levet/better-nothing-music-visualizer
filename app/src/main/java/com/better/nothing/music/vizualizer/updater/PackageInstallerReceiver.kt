package com.better.nothing.music.vizualizer.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.better.nothing.music.vizualizer.R

class PackageInstallerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_COMPLETE) {
            return
        }

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)

        Log.d(TAG, "PackageInstaller status received: $status (message: $message, apkPath: $apkPath)")

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Package update installed successfully.")
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "Silent update requires user action, launching system installer dialog.")
                val confirmationIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmationIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch user action intent", e)
                        if (apkPath != null) {
                            SilentUpdateInstaller.fallbackToStandardInstaller(context, apkPath)
                        }
                    }
                } else if (apkPath != null) {
                    SilentUpdateInstaller.fallbackToStandardInstaller(context, apkPath)
                }
            }

            else -> {
                Log.e(TAG, "Package installation failed with status $status: $message")
                val displayMsg = message ?: context.getString(R.string.unknown_error)
                Toast.makeText(
                    context,
                    context.getString(R.string.installation_failed, displayMsg),
                    Toast.LENGTH_LONG
                ).show()

                if (apkPath != null) {
                    Log.d(TAG, "Attempting fallback to standard installer for: $apkPath")
                    SilentUpdateInstaller.fallbackToStandardInstaller(context, apkPath)
                }
            }
        }
    }

    companion object {
        const val TAG = "PackageInstallerReceiver"
        const val ACTION_INSTALL_COMPLETE = "com.better.nothing.music.vizualizer.ACTION_INSTALL_COMPLETE"
        const val EXTRA_APK_PATH = "com.better.nothing.music.vizualizer.EXTRA_APK_PATH"
    }
}
