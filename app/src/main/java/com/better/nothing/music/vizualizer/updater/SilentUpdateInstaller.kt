package com.better.nothing.music.vizualizer.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.better.nothing.music.vizualizer.R
import java.io.File

object SilentUpdateInstaller {

    private const val TAG = "SilentUpdateInstaller"

    /**
     * Attempts to silently install the APK file using PackageInstaller.
     * Performs signature and package name verification prior to installation.
     * Fallbacks to standard user-interactive installer if silent install cannot proceed.
     */
    fun install(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.e(TAG, "APK file does not exist or is empty: ${apkFile.absolutePath}")
            Toast.makeText(
                context,
                context.getString(R.string.installation_failed, "APK file missing or empty"),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        // Verify package name and signing certificate signature
        if (!verifyApk(context, apkFile)) {
            Log.e(TAG, "APK verification failed. Aborting installation.")
            Toast.makeText(
                context,
                context.getString(R.string.apk_verification_failed),
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        // Check if app has permission to install unknown apps
        val canRequestInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

        if (!canRequestInstall) {
            Log.w(TAG, "App does not hold REQUEST_INSTALL_PACKAGES permission. Falling back to standard installer.")
            fallbackToStandardInstaller(context, apkFile.absolutePath)
            return true
        }

        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
            }

            val sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("package", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val intent = Intent(context, PackageInstallerReceiver::class.java).apply {
                    action = PackageInstallerReceiver.ACTION_INSTALL_COMPLETE
                    putExtra(PackageInstallerReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                        )
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

                session.commit(pendingIntent.intentSender)
                Log.d(TAG, "PackageInstaller session $sessionId committed successfully.")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Silent PackageInstaller session failed, falling back to standard installer", e)
            fallbackToStandardInstaller(context, apkFile.absolutePath)
            true
        }
    }

    /**
     * Verifies that the APK's package name matches the installed application
     * and that its signing certificate signature matches the installed app.
     */
    fun verifyApk(context: Context, apkFile: File): Boolean {
        return try {
            val pm = context.packageManager
            val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
            if (pkgInfo == null) {
                Log.e(TAG, "Failed to parse APK archive info")
                return false
            }

            if (pkgInfo.packageName != context.packageName) {
                Log.e(TAG, "Package name mismatch: APK is '${pkgInfo.packageName}', expected '${context.packageName}'")
                return false
            }

            val currentAppInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val apkSignatures = extractSignatures(pkgInfo)
            val appSignatures = extractSignatures(currentAppInfo)

            if (apkSignatures.isEmpty() || appSignatures.isEmpty()) {
                Log.e(TAG, "Failed to extract signing certificates for comparison")
                return false
            }

            val signaturesMatch = apkSignatures.any { apkSig ->
                appSignatures.any { appSig -> apkSig.contentEquals(appSig) }
            }

            if (!signaturesMatch) {
                Log.e(TAG, "Signing certificate mismatch between update APK and installed app!")
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "APK verification threw exception", e)
            false
        }
    }

    private fun extractSignatures(packageInfo: PackageInfo): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures?.forEach { list.add(it.toByteArray()) }
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.forEach { list.add(it.toByteArray()) }
        }
        return list
    }

    /**
     * Fallback to launching Android's normal package installer prompt.
     */
    fun fallbackToStandardInstaller(context: Context, apkPath: String) {
        try {
            val file = File(apkPath)
            if (!file.exists()) {
                Log.e(TAG, "Fallback failed: APK file does not exist at $apkPath")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback standard installation failed", e)
            Toast.makeText(
                context,
                context.getString(R.string.installation_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
