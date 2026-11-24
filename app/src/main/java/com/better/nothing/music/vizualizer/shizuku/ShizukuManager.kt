package com.better.nothing.music.vizualizer.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Result object representing the execution outcome of a shell command via Shizuku.
 */
data class ShizukuExecResult(
    val exitCode: Int,
    val output: String,
    val error: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Helper manager to interface with Shizuku for checking permissions,
 * managing binder lifecycle listeners, and executing shell commands with ADB privileges.
 */
object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    private val newProcessMethod: Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to access Shizuku newProcess method via reflection", e)
            null
        }
    }

    /**
     * Checks if the Shizuku application is installed on the device.
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Returns true if Shizuku service is running and binder is available.
     */
    fun isBinderAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to ping Shizuku binder", e)
            false
        }
    }

    /**
     * Checks if Shizuku permission has been granted to this app.
     */
    fun hasPermission(): Boolean {
        if (!isBinderAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to check Shizuku permission", e)
            false
        }
    }

    /**
     * Checks if rationale should be shown before requesting Shizuku permission.
     */
    fun shouldShowRequestPermissionRationale(): Boolean {
        if (!isBinderAvailable()) return false
        return try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Requests Shizuku permission with the given request code.
     */
    fun requestPermission(requestCode: Int) {
        if (isBinderAvailable()) {
            try {
                Shizuku.requestPermission(requestCode)
            } catch (e: Throwable) {
                Log.e(TAG, "Error requesting Shizuku permission", e)
            }
        }
    }

    /**
     * Registers a listener for Shizuku permission request results.
     */
    fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Error adding permission result listener", e)
        }
    }

    /**
     * Unregisters a listener for Shizuku permission request results.
     */
    fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Error removing permission result listener", e)
        }
    }

    /**
     * Registers a listener for when Shizuku binder is received (service becomes ready).
     */
    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        try {
            Shizuku.addBinderReceivedListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Error adding binder received listener", e)
        }
    }

    /**
     * Unregisters a listener for Shizuku binder received events.
     */
    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        try {
            Shizuku.removeBinderReceivedListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Error removing binder received listener", e)
        }
    }

    /**
     * Registers a listener for when Shizuku binder dies (service stops).
     */
    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        try {
            Shizuku.addBinderDeadListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Error adding binder dead listener", e)
        }
    }

    /**
     * Unregisters a listener for Shizuku binder dead events.
     */
    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        try {
            Shizuku.removeBinderDeadListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Error removing binder dead listener", e)
        }
    }

    /**
     * Asynchronously executes a command string via Shizuku (`sh -c <cmdString>`).
     */
    suspend fun execCommand(cmdString: String): ShizukuExecResult = withContext(Dispatchers.IO) {
        execCommandSync(arrayOf("sh", "-c", cmdString))
    }

    /**
     * Asynchronously executes a command array via Shizuku.
     */
    suspend fun execCommand(command: Array<String>): ShizukuExecResult = withContext(Dispatchers.IO) {
        execCommandSync(command)
    }

    /**
     * Synchronously executes a command string via Shizuku (`sh -c <cmdString>`).
     */
    fun execCommandSync(cmdString: String): ShizukuExecResult {
        return execCommandSync(arrayOf("sh", "-c", cmdString))
    }

    /**
     * Synchronously executes a command array via Shizuku.
     */
    fun execCommandSync(command: Array<String>): ShizukuExecResult {
        if (!hasPermission()) {
            return ShizukuExecResult(
                exitCode = -1,
                output = "",
                error = "Shizuku binder is not available or permission is not granted."
            )
        }

        val method = newProcessMethod
            ?: return ShizukuExecResult(
                exitCode = -1,
                output = "",
                error = "Shizuku newProcess method could not be accessed."
            )

        return try {
            val process = method.invoke(null, command, null, null) as Process
            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            val outThread = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (outputBuilder.isNotEmpty()) outputBuilder.append("\n")
                            outputBuilder.append(line)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading stdout", e)
                }
            }

            val errThread = Thread {
                try {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (errorBuilder.isNotEmpty()) errorBuilder.append("\n")
                            errorBuilder.append(line)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading stderr", e)
                }
            }

            outThread.start()
            errThread.start()

            val exitCode = process.waitFor()
            outThread.join()
            errThread.join()

            ShizukuExecResult(
                exitCode = exitCode,
                output = outputBuilder.toString(),
                error = errorBuilder.toString()
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Exception while executing Shizuku command: ${command.joinToString(" ")}", e)
            ShizukuExecResult(
                exitCode = -1,
                output = "",
                error = e.localizedMessage ?: "Unknown Shizuku error"
            )
        }
    }
}
