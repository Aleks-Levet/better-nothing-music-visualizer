package com.better.nothing.music.vizualizer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.logic.*
import com.better.nothing.music.vizualizer.model.*
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.util.AnalyticsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.AuthCredential
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONException
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow
import kotlin.math.sqrt

enum class Tab(val label: String, val labelRes: Int) {
    Audio("Audio", R.string.tab_audio), 
    Glyphs("Glyphs", R.string.tab_glyphs), 
    Visuals("Visuals", R.string.tab_visuals),
    Haptics("Haptics", R.string.tab_haptics), 
    Flashlight("Flashlight", R.string.tab_flashlight), 
    Settings("Settings", R.string.tab_settings);
}

data class AudioRoute(
    val storageKey: String,
    val displayName: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        var instance: MainViewModel? = null
            private set
    }

    init {
        instance = this
    }

    val ctx = application
    val communityRepository = CommunityRepository()
    val announcementRepository = AnnouncementRepository()
    val leaderboardRepository = LeaderboardRepository()
    val userRepository = UserRepository()
    val analytics = AnalyticsHelper(application)

    val hasHapticMotor: Boolean by lazy {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.hasVibrator() == true
    }

    val hasFlashlight: Boolean by lazy {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    val _flashlightIntensityLevels = MutableStateFlow(1)
    val flashlightIntensityLevels = _flashlightIntensityLevels.asStateFlow()

    val _flashlightLevel = MutableStateFlow(0)
    val flashlightLevel = _flashlightLevel.asStateFlow()

    val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    val _totalVisualizedTime = MutableStateFlow(0L)
    val totalVisualizedTime = _totalVisualizedTime.asStateFlow()

    val _totalIdleTime = MutableStateFlow(0L)
    val totalIdleTime = _totalIdleTime.asStateFlow()

    val _totalActiveTime = MutableStateFlow(0L)
    val totalActiveTime = _totalActiveTime.asStateFlow()

    val _totalGlyphTime = MutableStateFlow(0L)
    val totalGlyphTime = _totalGlyphTime.asStateFlow()

    val _totalHapticTime = MutableStateFlow(0L)
    val totalHapticTime = _totalHapticTime.asStateFlow()

    val _totalFlashlightTime = MutableStateFlow(0L)
    val totalFlashlightTime = _totalFlashlightTime.asStateFlow()

    val _userNickname = MutableStateFlow("Anonymous")
    val userNickname = _userNickname.asStateFlow()

    val _userId = MutableStateFlow<String?>(null)
    val userId = _userId.asStateFlow()

    val _isAnonymous = MutableStateFlow(true)
    val isAnonymous = _isAnonymous.asStateFlow()

    sealed class AppUpdateStatus {
        object Idle : AppUpdateStatus()
        object Checking : AppUpdateStatus()
        data class Available(val version: String, val url: String, val apkUrl: String? = null) : AppUpdateStatus()
        data class Downloading(val progress: Float) : AppUpdateStatus()
        object UpToDate : AppUpdateStatus()
        data class Error(val message: String) : AppUpdateStatus()
    }
    private val _appUpdateStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Idle)
    val appUpdateStatus = _appUpdateStatus.asStateFlow()

    sealed class LicenseStatus {
        object Loading : LicenseStatus()
        data class Success(val content: String) : LicenseStatus()
        data class Error(val message: String) : LicenseStatus()
    }
    private val _licenseStatus = MutableStateFlow<LicenseStatus>(LicenseStatus.Loading)
    val licenseStatus = _licenseStatus.asStateFlow()

    private val _isShowingAbout = MutableStateFlow(false)
    val isShowingAbout = _isShowingAbout.asStateFlow()
    fun showAbout() { _isShowingAbout.value = true }
    fun hideAbout() { _isShowingAbout.value = false }

    private val _isShowingLicense = MutableStateFlow(false)
    val isShowingLicense = _isShowingLicense.asStateFlow()
    fun showLicense() { 
        _isShowingLicense.value = true 
        if (_licenseStatus.value is LicenseStatus.Loading || _licenseStatus.value is LicenseStatus.Error) {
            fetchLicense()
        }
    }
    fun hideLicense() { _isShowingLicense.value = false }

    fun fetchLicense() {
        viewModelScope.launch(Dispatchers.IO) {
            _licenseStatus.value = LicenseStatus.Loading
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://raw.githubusercontent.com/aleks-levet/better-nothing-music-visualizer/main/LICENSE")
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    _licenseStatus.value = LicenseStatus.Success(content)
                } else {
                    _licenseStatus.value = LicenseStatus.Error("Failed to load license: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                _licenseStatus.value = LicenseStatus.Error(e.message ?: "Unknown error")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private val _isShowingCommunity = MutableStateFlow(false)
    val isShowingCommunity = _isShowingCommunity.asStateFlow()
    fun showCommunity() { _isShowingCommunity.value = true }
    fun hideCommunity() { _isShowingCommunity.value = false }

    private val _isShowingEditor = MutableStateFlow(false)
    val isShowingEditor = _isShowingEditor.asStateFlow()
    fun showEditor() { _isShowingEditor.value = true }
    fun hideEditor() { _isShowingEditor.value = false }

    private val _isShowingProfileSetup = MutableStateFlow(false)
    val isShowingProfileSetup = _isShowingProfileSetup.asStateFlow()
    fun showProfileSetup() { _isShowingProfileSetup.value = true }
    fun hideProfileSetup() { _isShowingProfileSetup.value = false }


    private val _flashlightMultiIntensityForced = MutableStateFlow(false)
    val flashlightMultiIntensityForced = _flashlightMultiIntensityForced.asStateFlow()
    fun setFlashlightMultiIntensityForced(forced: Boolean) {
        _flashlightMultiIntensityForced.value = forced
        MainActivity.serviceStatic?.setFlashlightMultiIntensityForced(forced)
        MainActivity.serviceStatic?.let {
            setFlashlightIntensityLevels(it.flashlightIntensityLevels)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("flashlight_multi_intensity_forced", forced) }
        }
    }

    private val _m3eEnabled = MutableStateFlow(true)
    val m3eEnabled = _m3eEnabled.asStateFlow()
    fun setM3EEnabled(enabled: Boolean) {
        _m3eEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("m3e_enabled", enabled) }
        }
    }

    private val _uiAmplitudeSyncEnabled = MutableStateFlow(true)
    val uiAmplitudeSyncEnabled = _uiAmplitudeSyncEnabled.asStateFlow()
    fun setUiAmplitudeSyncEnabled(enabled: Boolean) {
        _uiAmplitudeSyncEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("ui_amplitude_sync_enabled", enabled) }
        }
    }

    private val _overlayWidth = MutableStateFlow(120)
    val overlayWidth = _overlayWidth.asStateFlow()
    fun setOverlayWidth(width: Int) {
        _overlayWidth.value = width
        MainActivity.serviceStatic?.setOverlayWidth(width)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_width", width) }
        }
    }

    private val _overlayTopEnabled = MutableStateFlow(true)
    val overlayTopEnabled = _overlayTopEnabled.asStateFlow()
    fun setOverlayTopEnabled(enabled: Boolean) {
        _overlayTopEnabled.value = enabled
        MainActivity.serviceStatic?.setOverlayTopEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("overlay_top_enabled", enabled) }
        }
    }

    private val _overlayBottomEnabled = MutableStateFlow(false)
    val overlayBottomEnabled = _overlayBottomEnabled.asStateFlow()
    fun setOverlayBottomEnabled(enabled: Boolean) {
        _overlayBottomEnabled.value = enabled
        MainActivity.serviceStatic?.setOverlayBottomEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("overlay_bottom_enabled", enabled) }
        }
    }

    private val _overlayHeight = MutableStateFlow(12)
    val overlayHeight = _overlayHeight.asStateFlow()
    fun setOverlayHeight(height: Int) {
        _overlayHeight.value = height
        MainActivity.serviceStatic?.setOverlayHeight(height)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_height", height) }
        }
    }

    private val _overlayHeightBottom = MutableStateFlow(12)
    val overlayHeightBottom = _overlayHeightBottom.asStateFlow()
    fun setOverlayHeightBottom(height: Int) {
        _overlayHeightBottom.value = height
        MainActivity.serviceStatic?.setOverlayHeightBottom(height)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_height_bottom", height) }
        }
    }

    private val _overlayYOffset = MutableStateFlow(2)
    val overlayYOffset = _overlayYOffset.asStateFlow()
    fun setOverlayYOffset(offset: Int) {
        _overlayYOffset.value = offset
        MainActivity.serviceStatic?.setOverlayYOffset(offset)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_y_offset", offset) }
        }
    }

    private val _overlaySensitivity = MutableStateFlow(1.0f)
    val overlaySensitivity = _overlaySensitivity.asStateFlow()
    fun setOverlaySensitivity(sensitivity: Float) {
        _overlaySensitivity.value = sensitivity
        MainActivity.serviceStatic?.setOverlaySensitivity(sensitivity)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("overlay_sensitivity", sensitivity) }
        }
    }

    private val _overlaySensitivityBottom = MutableStateFlow(1.0f)
    val overlaySensitivityBottom = _overlaySensitivityBottom.asStateFlow()
    fun setOverlaySensitivityBottom(sensitivity: Float) {
        _overlaySensitivityBottom.value = sensitivity
        MainActivity.serviceStatic?.setOverlaySensitivityBottom(sensitivity)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("overlay_sensitivity_bottom", sensitivity) }
        }
    }

    private val _edgeVisualizerEnabled = MutableStateFlow(false)
    val edgeVisualizerEnabled = _edgeVisualizerEnabled.asStateFlow()
    fun setEdgeVisualizerEnabled(enabled: Boolean) {
        _edgeVisualizerEnabled.value = enabled
        MainActivity.serviceStatic?.setEdgeVisualizerEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("edge_visualizer_enabled", enabled) }
        }
    }

    private val _edgeThickness = MutableStateFlow(12)
    val edgeThickness = _edgeThickness.asStateFlow()
    fun setEdgeThickness(thickness: Int) {
        _edgeThickness.value = thickness
        MainActivity.serviceStatic?.setEdgeThickness(thickness)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("edge_thickness", thickness) }
        }
    }

    private val _edgeSensitivity = MutableStateFlow(1.0f)
    val edgeSensitivity = _edgeSensitivity.asStateFlow()
    fun setEdgeSensitivity(sensitivity: Float) {
        _edgeSensitivity.value = sensitivity
        MainActivity.serviceStatic?.setEdgeSensitivity(sensitivity)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("edge_sensitivity", sensitivity) }
        }
    }

    private val _edgeBarCountHoriz = MutableStateFlow(20)
    val edgeBarCountHoriz = _edgeBarCountHoriz.asStateFlow()
    fun setEdgeBarCountHoriz(count: Int) {
        _edgeBarCountHoriz.value = count
        MainActivity.serviceStatic?.setEdgeBarCounts(count, _edgeBarCountVert.value)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("edge_bar_count_horiz", count) }
        }
    }

    private val _edgeBarCountVert = MutableStateFlow(40)
    val edgeBarCountVert = _edgeBarCountVert.asStateFlow()
    fun setEdgeBarCountVert(count: Int) {
        _edgeBarCountVert.value = count
        MainActivity.serviceStatic?.setEdgeBarCounts(_edgeBarCountHoriz.value, count)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("edge_bar_count_vert", count) }
        }
    }

    private val _edgeCornerRadius = MutableStateFlow(2f)
    val edgeCornerRadius = _edgeCornerRadius.asStateFlow()
    fun setEdgeCornerRadius(radius: Float) {
        _edgeCornerRadius.value = radius
        MainActivity.serviceStatic?.setEdgeCornerRadius(radius)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("edge_corner_radius", radius) }
        }
    }

    private val _edgeTopEnabled = MutableStateFlow(true)
    val edgeTopEnabled = _edgeTopEnabled.asStateFlow()
    fun setEdgeTopEnabled(enabled: Boolean) {
        _edgeTopEnabled.value = enabled
        MainActivity.serviceStatic?.setEdgeTopEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("edge_top_enabled", enabled) }
        }
    }

    private val _edgeBottomEnabled = MutableStateFlow(true)
    val edgeBottomEnabled = _edgeBottomEnabled.asStateFlow()
    fun setEdgeBottomEnabled(enabled: Boolean) {
        _edgeBottomEnabled.value = enabled
        MainActivity.serviceStatic?.setEdgeBottomEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("edge_bottom_enabled", enabled) }
        }
    }

    private val _lensVisualizerEnabled = MutableStateFlow(false)
    val lensVisualizerEnabled = _lensVisualizerEnabled.asStateFlow()
    fun setLensVisualizerEnabled(enabled: Boolean) {
        _lensVisualizerEnabled.value = enabled
        MainActivity.serviceStatic?.setLensVisualizerEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("lens_visualizer_enabled", enabled) }
        }
    }

    private val _lensVisualizerRadius = MutableStateFlow(16f)
    val lensVisualizerRadius = _lensVisualizerRadius.asStateFlow()
    fun setLensVisualizerRadius(radius: Float) {
        _lensVisualizerRadius.value = radius
        MainActivity.serviceStatic?.setLensVisualizerRadius(radius)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("lens_visualizer_radius", radius) }
        }
    }

    private val _lensVisualizerX = MutableStateFlow(0.50f)
    val lensVisualizerX = _lensVisualizerX.asStateFlow()
    fun setLensVisualizerX(x: Float) {
        _lensVisualizerX.value = x
        MainActivity.serviceStatic?.setLensVisualizerX(x)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("lens_visualizer_x", x) }
        }
    }

    private val _lensVisualizerY = MutableStateFlow(0.03f)
    val lensVisualizerY = _lensVisualizerY.asStateFlow()
    fun setLensVisualizerY(y: Float) {
        _lensVisualizerY.value = y
        MainActivity.serviceStatic?.setLensVisualizerY(y)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("lens_visualizer_y", y) }
        }
    }

    private val _lensVisualizerBarWidth = MutableStateFlow(1f)
    val lensVisualizerBarWidth = _lensVisualizerBarWidth.asStateFlow()
    fun setLensVisualizerBarWidth(width: Float) {
        _lensVisualizerBarWidth.value = width
        MainActivity.serviceStatic?.setLensVisualizerBarWidth(width)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("lens_visualizer_bar_width", width) }
        }
    }

    private val _lensVisualizerMaxHeight = MutableStateFlow(5f)
    val lensVisualizerMaxHeight = _lensVisualizerMaxHeight.asStateFlow()
    fun setLensVisualizerMaxHeight(height: Float) {
        _lensVisualizerMaxHeight.value = height
        MainActivity.serviceStatic?.setLensVisualizerMaxHeight(height)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("lens_visualizer_max_height", height) }
        }
    }

    private val _lensVisualizerBarCount = MutableStateFlow(35)
    val lensVisualizerBarCount = _lensVisualizerBarCount.asStateFlow()
    fun setLensVisualizerBarCount(count: Int) {
        _lensVisualizerBarCount.value = count
        MainActivity.serviceStatic?.setLensVisualizerBarCount(count)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("lens_visualizer_bar_count", count) }
        }
    }

    private val _lensVisualizerSensitivity = MutableStateFlow(0.32f)
    val lensVisualizerSensitivity = _lensVisualizerSensitivity.asStateFlow()
    fun setLensVisualizerSensitivity(sensitivity: Float) {
        _lensVisualizerSensitivity.value = sensitivity
        MainActivity.serviceStatic?.setLensVisualizerSensitivity(sensitivity)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("lens_visualizer_sensitivity", sensitivity) }
        }
    }

    private val _selectedTheme = MutableStateFlow("Default")
    val selectedTheme = _selectedTheme.asStateFlow()
    fun setSelectedTheme(theme: String) {
        _selectedTheme.value = theme
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("selected_theme", theme) }
        }
    }

    private val _selectedFont = MutableStateFlow("Default")
    val selectedFont = _selectedFont.asStateFlow()
    fun setSelectedFont(font: String) {
        _selectedFont.value = font
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("selected_font", font) }
        }
    }

    fun checkAppUpdate() {
        _appUpdateStatus.value = AppUpdateStatus.UpToDate
    }

    fun downloadAndInstallUpdate(apkUrl: String, versionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                Log.d("MainViewModel", "Starting update download from $apkUrl")
                val url = URL(apkUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 60000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val fileLength = connection.contentLength
                    val destinationFile = File(ctx.externalCacheDir, "update_$versionName.apk")
                    
                    if (destinationFile.exists()) {
                        destinationFile.delete()
                    }

                    connection.inputStream.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (fileLength > 0) {
                                    val progress = totalBytesRead.toFloat() / fileLength.toFloat()
                                    _appUpdateStatus.value = AppUpdateStatus.Downloading(progress)
                                }
                            }
                        }
                    }

                    if (destinationFile.exists() && destinationFile.length() > 0) {
                        Log.d("MainViewModel", "Update downloaded successfully to ${destinationFile.absolutePath}")
                        withContext(Dispatchers.Main) {
                            installApk(destinationFile)
                        }
                    } else {
                        throw Exception("Downloaded file is missing or empty")
                    }
                } else {
                    Log.e("MainViewModel", "Download failed with HTTP $responseCode")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Download failed: HTTP $responseCode", Toast.LENGTH_SHORT).show()
                        _appUpdateStatus.value = AppUpdateStatus.Error("Download failed: HTTP $responseCode")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Download failed with error", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = e.message ?: "Unknown download error"
                    Toast.makeText(ctx, "Download error: $errorMsg", Toast.LENGTH_SHORT).show()
                    _appUpdateStatus.value = AppUpdateStatus.Error(errorMsg)
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Installation failed", e)
            Toast.makeText(ctx, "Installation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val _isShowingLeaderboard = MutableStateFlow(false)
    val isShowingLeaderboard = _isShowingLeaderboard.asStateFlow()
    fun showLeaderboard() { _isShowingLeaderboard.value = true }
    fun hideLeaderboard() { _isShowingLeaderboard.value = false }

    private val _isShowingStats = MutableStateFlow(false)
    val isShowingStats = _isShowingStats.asStateFlow()
    fun showStats() { _isShowingStats.value = true }
    fun hideStats() { _isShowingStats.value = false }

    fun deleteCustomPreset(key: String) {
        viewModelScope.launch {
            try {
                communityRepository.deletePreset(key)
                analytics.logEvent("preset_deleted", android.os.Bundle().apply { putString("preset_id", key) })
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete preset", e)
            }
        }
    }

    fun saveCustomPreset(name: String, zones: List<AudioProcessor.ZoneSpec>, presetKey: String? = null) {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                val preset = CommunityPreset(
                    name = name,
                    author = _userNickname.value,
                    authorId = uid,
                    phoneModel = phoneModelForDevice(selectedDevice.value),
                    zones = zones.map { ZoneData.fromZoneSpec(it) },
                    timestamp = System.currentTimeMillis()
                )
                communityRepository.uploadPreset(preset)
                analytics.logPresetShared(name)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Preset uploaded to community!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to upload preset", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun checkRemoteConfigVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                Log.d("MainViewModel", "Checking remote config version...")
                val url =
                    URL("https://raw.githubusercontent.com/Aleks-Levet/better-nothing-music-visualizer/main/zones.config?t=${System.currentTimeMillis()}")
                connection = url.openConnection() as HttpURLConnection
                connection.useCaches = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    if (content.isBlank()) {
                        Log.w("MainViewModel", "Remote config content is empty")
                        return@launch
                    }
                    val json = JSONObject(content)
                    val remoteVersion = json.optString("version", "Unknown")
                    Log.d("MainViewModel", "Remote config version: $remoteVersion")
                    _remoteConfigVersion.value = remoteVersion
                } else {
                    Log.w("MainViewModel", "Failed to check remote version: HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to check remote version", e)
            } finally {
                connection?.disconnect()
            }
        }
    }
    fun importZonesConfig(uri: Uri) {
        _configUpdateStatus.value = ConfigUpdateStatus.Updating
        viewModelScope.launch {
            announcementRepository.getLatestAnnouncement().collect { announcement ->
                _latestAnnouncement.value = announcement
                if (announcement != null) {
                    val sharedPrefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    val lastSeenId = sharedPrefs.getString("last_seen_announcement_id", "")
                    if (announcement.id.toString() != lastSeenId) {
                        _showAnnouncementModal.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val content = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content == null) return@withContext false

                    // Basic validation
                    JSONObject(content)

                    val file = File(ctx.filesDir, "zones.config")
                    file.writeText(content)

                    // Refresh presets (file IO)
                    refreshPresetsInternal()

                    val newVersion = AudioCaptureService.loadZonesConfigVersion(ctx)
                    _configVersion.value = newVersion
                    _remoteConfigVersion.value = null // Clear remote version since we are on local

                    // Force running service to reload its config from disk
                    MainActivity.serviceStatic?.reloadConfig()
                    true
                }

                if (success) {
                    _configUpdateStatus.value = ConfigUpdateStatus.Success(ctx.getString(R.string.config_import_success))
                } else {
                    _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_import_error))
                }
            } catch (e: Exception) {
                _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_error_importing, e.message))
            }
        }
    }
    fun updateZonesConfig() {
        // 1. Set loading state immediately on Main Thread
        _configUpdateStatus.value = ConfigUpdateStatus.Updating

        viewModelScope.launch {
            announcementRepository.getLatestAnnouncement().collect { announcement ->
                _latestAnnouncement.value = announcement
                if (announcement != null) {
                    val sharedPrefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    val lastSeenId = sharedPrefs.getString("last_seen_announcement_id", "")
                    if (announcement.id.toString() != lastSeenId) {
                        _showAnnouncementModal.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            try {
                // 2. Perform network/download on IO Thread
                val success = withContext(Dispatchers.IO) {
                    performUpdateAction()
                }

                // 3. Back on Main Thread automatically after withContext
                if (success) {
                    _configUpdateStatus.value = ConfigUpdateStatus.Success(ctx.getString(R.string.config_update_success))
                }
                // Errors are handled inside performUpdateAction setting the status directly now,
                // or we could return Result object. To keep it simple with existing code:
            } catch (e: Exception) {
                // Catch unexpected errors
                _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_error_updating, e.message))
            }
        }
    }
    private suspend fun performUpdateAction(): Boolean {
        // This runs on Dispatchers.IO (called from withContext(IO) above)
        var connection: HttpURLConnection? = null
        return try {
            Log.d("MainViewModel", "Performing zones.config update...")
            val url = URL("https://raw.githubusercontent.com/Aleks-Levet/better-nothing-music-visualizer/main/zones.config?t=${System.currentTimeMillis()}")
            connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpURLConnection
            connection.useCaches = false
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                if (content.isBlank()) {
                    throw Exception("Downloaded content is empty")
                }
                
                // Basic validation
                try {
                    JSONObject(content)
                } catch (e: Exception) {
                    throw Exception("Invalid JSON format in zones.config")
                }

                val file = File(ctx.filesDir, "zones.config")
                file.writeText(content)
                Log.d("MainViewModel", "zones.config updated and saved to ${file.absolutePath}")

                // Refresh presets (file IO)
                refreshPresetsInternal()

                val newVersion = AudioCaptureService.loadZonesConfigVersion(ctx)
                _configVersion.value = newVersion
                _remoteConfigVersion.value = newVersion

                // Force running service to reload its config from disk
                MainActivity.serviceStatic?.reloadConfig()
                true
            } else {
                Log.e("MainViewModel", "Update failed with HTTP $responseCode")
                withContext(Dispatchers.Main) {
                    _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_download_error, responseCode))
                }
                false
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error during performUpdateAction", e)
            withContext(Dispatchers.Main) {
                _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_error_updating, e.message))
            }
            false
        } finally {
            connection?.disconnect()
        }
    }
    // Profile logic now managed via Google Sign-in / Firebase

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled = _overlayEnabled.asStateFlow()

    fun setOverlayEnabled(enabled: Boolean) {
        _overlayEnabled.value = enabled
        MainActivity.serviceStatic?.setOverlayEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("overlay_enabled", enabled) }
        }
    }

    val _idleBreathingEnabled = MutableStateFlow(false)
    val idleBreathingEnabled = _idleBreathingEnabled.asStateFlow()

    fun setIdleBreathingEnabled(enabled: Boolean) {
        _idleBreathingEnabled.value = enabled
        MainActivity.serviceStatic?.setIdleBreathingEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("idle_breathing_enabled", enabled) }
        }
    }

    val _idlePattern = MutableStateFlow("pulse")
    val idlePattern = _idlePattern.asStateFlow()

    fun setIdlePattern(pattern: String) {
        _idlePattern.value = pattern
        MainActivity.serviceStatic?.setIdlePattern(pattern)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("idle_pattern", pattern) }
        }
    }

    val _strobeEnabled = MutableStateFlow(false)
    val strobeEnabled = _strobeEnabled.asStateFlow()

    fun setStrobeEnabled(enabled: Boolean) {
        _strobeEnabled.value = enabled
        MainActivity.serviceStatic?.setStrobeEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("strobe_enabled", enabled) }
        }
    }

    val _disableGlyphsWhenSilent = MutableStateFlow(false)
    val disableGlyphsWhenSilent = _disableGlyphsWhenSilent.asStateFlow()

    fun setDisableGlyphsWhenSilent(enabled: Boolean) {
        _disableGlyphsWhenSilent.value = enabled
        MainActivity.serviceStatic?.setDisableGlyphsWhenSilent(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("disable_glyphs_when_silent", enabled) }
        }
    }

    val _musicThemeColor = MutableStateFlow(Color(0xFFD71921))
    val musicThemeColor = _musicThemeColor.asStateFlow()

    fun setMusicArtwork(bitmap: Bitmap?) {
        if (bitmap == null) {
            _musicThemeColor.value = Color(0xFFD71921)
            return
        }
        Palette.from(bitmap).generate { palette ->
            // Try to get a good color in order of preference
            val extracted = palette?.let { p ->
                p.getVibrantColor(0).takeIf { it != 0 }
                    ?: p.getDarkVibrantColor(0).takeIf { it != 0 }
                    ?: p.getLightVibrantColor(0).takeIf { it != 0 }
                    ?: p.getMutedColor(0).takeIf { it != 0 }
                    ?: p.getDominantColor(0).takeIf { it != 0 }
            } ?: 0xFFD71921.toInt()

            _musicThemeColor.value = Color(extracted)
        }
    }

    val _isAdmin = MutableStateFlow(false)
    val isAdmin = _isAdmin.asStateFlow()

    fun syncStats(explicitUid: String? = null) {
        val uid = explicitUid ?: _userId.value ?: return
        viewModelScope.launch {
            try {
                val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                val localTime = _totalVisualizedTime.value
                var profile = userRepository.getUserProfile(uid)
                
                if (profile == null) {
                    profile = UserProfile(
                        userId = uid,
                        displayName = _userNickname.value,
                        totalVisualizedTime = localTime,
                        createdAt = System.currentTimeMillis()
                    )
                    userRepository.saveUserProfile(profile)
                } else {
                    // Reconcile: Take the maximum to avoid progress loss
                    if (localTime > profile.totalVisualizedTime) {
                        profile = profile.copy(totalVisualizedTime = localTime)
                        userRepository.saveUserProfile(profile)
                    } else if (profile.totalVisualizedTime > localTime) {
                        _totalVisualizedTime.value = profile.totalVisualizedTime
                        prefs.edit().putLong("total_visualized_time", profile.totalVisualizedTime).apply()
                    }
                }
                
                _userProfile.value = profile
                _userNickname.value = profile.displayName ?: "Anonymous"
                
                // If the user has signed in but hasn't set up their profile yet, show the setup dialog
                if (!_isAnonymous.value && (profile.displayName.isNullOrBlank() || profile.displayName == "Anonymous")) {
                    showProfileSetup()
                }

                // Update internal UID if needed (though AuthListener should handle it)
                if (explicitUid != null) {
                    _userId.value = explicitUid
                }

                // Check if admin
                _isAdmin.value = uid == "acLuGkDEBNNhtIkuWDzlKuFhDI92" || uid == "hOQwgxRFB2fjko5mUaZraIcYRnl1"
                
                analytics.logStatsSynced(
                    _totalVisualizedTime.value,
                    0, 0, 0 
                )
                
                updateLeaderboard()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to sync stats", e)
            }
        }
    }

    fun updateProfile(nickname: String, profilePictureBase64: String? = null) {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                val currentProfile = _userProfile.value ?: UserProfile(
                    userId = uid,
                    displayName = _userNickname.value,
                    createdAt = System.currentTimeMillis()
                )
                val updatedProfile = currentProfile.copy(
                    displayName = nickname,
                    profilePictureUrl = profilePictureBase64 ?: currentProfile.profilePictureUrl
                )
                userRepository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile
                _userNickname.value = nickname
                
                // Update leaderboard as well
                updateLeaderboard()
                
                analytics.logProfileUpdate(if (profilePictureBase64 != null) "avatar" else "nickname")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to update profile", e)
            }
        }
    }

    fun uploadProfilePicture(uri: android.net.Uri) {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                val base64 = userRepository.uploadProfilePicture(uid, uri, ctx)
                updateProfile(_userNickname.value, base64)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to upload profile picture", e)
            }
        }
    }

    fun updateLeaderboard() {
        val uid = _userId.value ?: return
        if (_isAnonymous.value) return 

        viewModelScope.launch {
            try {
                val currentProfile = _userProfile.value ?: return@launch
                val updatedTime = _totalVisualizedTime.value
                
                // Update User Profile as well so it's persistent
                val updatedProfile = currentProfile.copy(totalVisualizedTime = updatedTime)
                userRepository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile

                val entry = LeaderboardEntry(
                    userId = uid,
                    name = nicknameForLeaderboard(updatedProfile.displayName),
                    profilePictureUrl = updatedProfile.profilePictureUrl,
                    totalTimeMs = updatedTime,
                    lastUpdated = System.currentTimeMillis()
                )
                leaderboardRepository.updateScore(entry)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to update leaderboard", e)
            }
        }
    }

    fun saveStatsLocally() {
        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit()
                .putLong("total_visualized_time", _totalVisualizedTime.value)
                .putLong("total_idle_time", _totalIdleTime.value)
                .putLong("total_active_time", _totalActiveTime.value)
                .putLong("total_glyph_time", _totalGlyphTime.value)
                .putLong("total_haptic_time", _totalHapticTime.value)
                .putLong("total_flashlight_time", _totalFlashlightTime.value)
                .apply()
        }
    }

    private fun nicknameForLeaderboard(name: String): String {
        return if (name.isBlank() || name == "Anonymous") "Anonymous User" else name
    }

    fun isNotificationAccessGranted(): Boolean {
        val flat = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        return flat?.contains(ctx.packageName) == true
    }


    private val _notificationButtonSet = MutableStateFlow("presets")
    val notificationButtonSet = _notificationButtonSet.asStateFlow()

    fun setNotificationButtonSet(set: String) {
        _notificationButtonSet.value = set
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("notification_button_set", set) }
        }
        MainActivity.serviceStatic?.reloadConfig()
    }

    val _devPassword = MutableStateFlow<String?>(null)

    fun verifyDeveloperPassword(input: String): Boolean {
        if (input.isBlank()) return false
        val encrypted = _devPassword.value ?: return false
        val decrypted = try {
            String(Base64.decode(encrypted, Base64.DEFAULT))
        } catch (e: Exception) {
            ""
        }
        return input == decrypted
    }

    val _thanksMessage = MutableStateFlow<String?>(null)
    val thanksQueue = mutableListOf<String>()

    fun dismissThanksMessage() {
        if (thanksQueue.isNotEmpty()) {
            _thanksMessage.value = thanksQueue.removeAt(0)
        } else {
            _thanksMessage.value = null
        }
    }

    fun showThanks(message: String) {
        if (_thanksMessage.value == null) {
            _thanksMessage.value = message
        } else {
            thanksQueue.add(message)
        }
    }

    val _favoritePresets = MutableStateFlow<Set<String>>(emptySet())
    val favoritePresets = _favoritePresets.asStateFlow()

    val _captureSource = MutableStateFlow(AudioCaptureService.CaptureSource.INTERNAL)
    val captureSource = _captureSource.asStateFlow()

    val _latestAnnouncement = MutableStateFlow<Announcement?>(null)
    val latestAnnouncement = _latestAnnouncement.asStateFlow()

    val _showAnnouncementModal = MutableStateFlow(false)
    val showAnnouncementModal = _showAnnouncementModal.asStateFlow()

    val _showAnnouncementEditor = MutableStateFlow(false)
    val showAnnouncementEditor = _showAnnouncementEditor.asStateFlow()

    val _showAnnouncementHistory = MutableStateFlow(false)
    val showAnnouncementHistory = _showAnnouncementHistory.asStateFlow()

    val _showSpoofingSettings = MutableStateFlow(false)
    val showSpoofingSettings = _showSpoofingSettings.asStateFlow()

    val _shizukuSourceUnlocked = MutableStateFlow(false)
    val shizukuSourceUnlocked = _shizukuSourceUnlocked.asStateFlow()

    fun setShizukuSourceUnlocked(unlocked: Boolean) {
        _shizukuSourceUnlocked.value = unlocked
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("shizuku_source_unlocked", unlocked) }
        }
    }

    fun setShowSpoofingSettings(show: Boolean) {
        _showSpoofingSettings.value = show
        analytics.logSettingChanged("show_spoofing_settings", show)
    }

    fun showAnnouncementEditor() { 
        _showAnnouncementEditor.value = true
        analytics.logScreenView("announcement_editor")
    }
    fun hideAnnouncementEditor() { _showAnnouncementEditor.value = false }

    fun showAnnouncementHistory() { 
        _showAnnouncementHistory.value = true
        analytics.logScreenView("announcement_history")
    }
    fun hideAnnouncementHistory() { _showAnnouncementHistory.value = false }

    fun dismissAnnouncement() {
        val announcement = _latestAnnouncement.value ?: return
        _showAnnouncementModal.value = false
        analytics.logAnnouncementClicked(announcement.id.toString(), "dismiss")
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("last_seen_announcement_id", announcement.id.toString()) }
        }
    }

    fun postAnnouncement(title: String, message: String, style: String, link: String? = null, linkText: String? = null) {
        viewModelScope.launch {
            try {
                val announcement = Announcement(
                    id = System.currentTimeMillis().toString(),
                    title = title,
                    message = message,
                    style = style,
                    link = link.takeIf { it?.isNotBlank() == true },
                    linkText = linkText.takeIf { it?.isNotBlank() == true },
                    timestamp = System.currentTimeMillis()
                )
                announcementRepository.postAnnouncement(announcement)
                _showAnnouncementEditor.value = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.announcement_posted), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.failed_to_post, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val announcementHistory = announcementRepository.getAnnouncementHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val leaderboardEntries = leaderboardRepository.getTopUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val _spoofLocale = MutableStateFlow<String?>(null)
    val spoofLocale = _spoofLocale.asStateFlow()

    init {
        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        _favoritePresets.value = prefs.getStringSet("favorite_presets", emptySet()) ?: emptySet()
        
        val savedSource = prefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
        _captureSource.value = AudioCaptureService.CaptureSource.valueOf(savedSource ?: AudioCaptureService.CaptureSource.INTERNAL.name)

        _shizukuSourceUnlocked.value = prefs.getBoolean("shizuku_source_unlocked", false)
        _uiAmplitudeSyncEnabled.value = prefs.getBoolean("ui_amplitude_sync_enabled", true)

        _totalVisualizedTime.value = prefs.getLong("total_visualized_time", 0L)
        _totalIdleTime.value = prefs.getLong("total_idle_time", 0L)
        _totalActiveTime.value = prefs.getLong("total_active_time", 0L)
        _totalGlyphTime.value = prefs.getLong("total_glyph_time", 0L)
        _totalHapticTime.value = prefs.getLong("total_haptic_time", 0L)
        _totalFlashlightTime.value = prefs.getLong("total_flashlight_time", 0L)
        _userNickname.value = prefs.getString("user_nickname", "Anonymous") ?: "Anonymous"
        _spoofLocale.value = prefs.getString("spoof_locale", null)

        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _userId.value = user.uid
                _isAnonymous.value = user.isAnonymous
                analytics.setUserId(user.uid)
                prefs.edit().putString("user_id", user.uid).apply()

                // If not anonymous, fetch/sync profile
                if (!user.isAnonymous) {
                    syncStats(user.uid)
                }
            } else {
                // User signed out (or never signed in). Clear all stale state so
                // the UI doesn't keep showing the previous account.
                _userId.value = null
                _isAnonymous.value = true
                _userProfile.value = null
                _userNickname.value = "Anonymous"
                analytics.setUserId(null)
                prefs.edit().remove("user_id").apply()
            }
        }

        if (auth.currentUser == null) {
            // Defer authing-in anonymously until after we've had a chance to
            // sign in with a real provider. This avoids race conditions where
            // the init logic immediately re-creates an anonymous account
            // before the user gets a chance to log in again.
            viewModelScope.launch {
                delay(500)
                // Only auto sign-in if the listener still hasn't reported a user.
                if (FirebaseAuth.getInstance().currentUser == null) {
                    auth.signInAnonymously().addOnFailureListener { e ->
                        Log.e("MainViewModel", "Firebase Auth failed", e)
                        var uId = prefs.getString("user_id", null)
                        if (uId == null) {
                            uId = java.util.UUID.randomUUID().toString()
                            prefs.edit().putString("user_id", uId).apply()
                        }
                        _userId.value = uId
                    }
                }
            }
        } else {
            _userId.value = auth.currentUser?.uid
        }

        // Disable Haptic Tile if no motor
        try {
            val hapticTileComponent = ComponentName(ctx, "com.better.nothing.music.vizualizer.service.HapticsTileService")
            ctx.packageManager.setComponentEnabledSetting(
                hapticTileComponent,
                if (hasHapticMotor) PackageManager.COMPONENT_ENABLED_STATE_ENABLED 
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to disable HapticsTileService", e)
        }

        // Track app openings and show thanks messages
        val openCount = prefs.getInt("app_open_count", 0) + 1
        prefs.edit().putInt("app_open_count", openCount).apply()
        analytics.logAppOpen(openCount)

        // Time tracking
        viewModelScope.launch {
            var lastUpdate = SystemClock.elapsedRealtime()
            while (true) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastUpdate
                lastUpdate = now

                if (_runningState.value) {
                    _totalVisualizedTime.value += delta
                    
                    val magnitudes = MainActivity.serviceStatic?.latestMagnitudes ?: _fftState.value
                    val hasActivity = magnitudes.any { it > 0.001f }
                    
                    if (hasActivity) {
                        _totalActiveTime.value += delta
                        
                        if (_hapticMotorEnabled.value) {
                            _totalHapticTime.value += delta
                        }
                        if (_flashlightEnabled.value) {
                            _totalFlashlightTime.value += delta
                        }
                        if (_maxBrightness.value > 0) {
                            _totalGlyphTime.value += delta
                        }
                    } else {
                        // Only count as idle if it's actually running but quiet
                        _totalIdleTime.value += delta
                    }

                    // Save periodically (every 5 seconds to reduce IO, every 60s for leaderboard)
                    val timestamp = SystemClock.elapsedRealtime()
                    if (timestamp % 5000 < 1100) {
                        saveStatsLocally()
                    }
                    if (timestamp % 60000 < 1100) {
                        updateLeaderboard()
                    }
                }
            }
        }

        viewModelScope.launch {
            if (!hasFlashlight) return@launch
            while (true) {
                MainActivity.serviceStatic?.let { s ->
                    _flashlightIntensityLevels.value = s.flashlightIntensityLevels
                    _flashlightLevel.value = s.flashlightCurrentLevel
                }
                delay(100)
            }
        }
    }

    // ── Tab ───────────────────────────────────────────────────────────────────
    val _selectedTab = MutableStateFlow(Tab.Audio)
    val selectedTab = _selectedTab.asStateFlow()
    private val tabHistory = mutableListOf<Tab>()

    fun selectTab(tab: Tab, recordHistory: Boolean = true) {
        if (selectedDevice.value == DeviceProfile.DEVICE_UNKNOWN && tab == Tab.Glyphs) return
        if (!hasHapticMotor && tab == Tab.Haptics) return
        if (!hasFlashlight && tab == Tab.Flashlight) return
        
        if (recordHistory && _selectedTab.value != tab) {
            tabHistory.add(_selectedTab.value)
            if (tabHistory.size > 20) tabHistory.removeAt(0)
        }
        
        _selectedTab.value = tab
        analytics.logTabSelected(tab.name)
    }

    fun navigateBack(): Boolean {
        // First check for overlays (order by visual precedence - top-most first)
        if (_showAnnouncementModal.value) { dismissAnnouncement(); return true }
        if (_showAnnouncementEditor.value) { hideAnnouncementEditor(); return true }
        if (_isShowingLeaderboard.value) { hideLeaderboard(); return true }
        if (_showAnnouncementHistory.value) { hideAnnouncementHistory(); return true }
        if (_isShowingCommunity.value) { hideCommunity(); return true }
        if (_isShowingProfileSetup.value) { hideProfileSetup(); return true }
        if (_isShowingStats.value) { hideStats(); return true }
        if (_isShowingLicense.value) { hideLicense(); return true }
        if (_isShowingAbout.value) { hideAbout(); return true }
        if (_isShowingEditor.value) { hideEditor(); return true }

        // Then check tab history
        if (tabHistory.isNotEmpty()) {
            val previousTab = tabHistory.removeAt(tabHistory.size - 1)
            selectTab(previousTab, recordHistory = false)
            return true
        }
        return false
    }

    fun setCaptureSource(source: AudioCaptureService.CaptureSource) {
        if (source == AudioCaptureService.CaptureSource.SHIZUKU) {
            if (!checkShizukuPermission()) {
                return
            }
        }
        _captureSource.value = source
        MainActivity.serviceStatic?.setCaptureSource(source)
        analytics.logCaptureSourceChanged(source.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("capture_source", source.name) }
        }
    }

    fun checkShizukuPermission(): Boolean {
        try {
            if (Shizuku.isPreV11()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                analytics.logShizukuPermissionResult(true)
                return true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(ctx, ctx.getString(R.string.shizuku_permission_required), Toast.LENGTH_LONG).show()
                analytics.logShizukuPermissionResult(false)
                return false
            } else {
                Shizuku.requestPermission(1001)
                return false
            }
        } catch (e: Exception) {
            analytics.logError("shizuku_error", e.message ?: "Unknown Shizuku error")
            Toast.makeText(ctx, ctx.getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show()
            return false
        }
    }

    // ── Device ────────────────────────────────────────────────────────────────
    // Exposed as MutableStateFlow (not just a val) so the Activity can always
    // read the latest device synchronously when binding the service.
    val selectedDevice = MutableStateFlow(DeviceProfile.DEVICE_NP2)

    val _developerModeEnabled = MutableStateFlow(false)
    val developerModeEnabled = _developerModeEnabled.asStateFlow()

    val _spoofedDevice = MutableStateFlow(DeviceProfile.DEVICE_NP1)
    val spoofedDevice = _spoofedDevice.asStateFlow()

    fun setDeveloperModeEnabled(enabled: Boolean) {
        _developerModeEnabled.value = enabled
        analytics.logSettingChanged("developer_mode", enabled)
        updateSelectedDevice()
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("developer_mode_v2", enabled) }
        }
    }

    fun setSpoofedDevice(device: Int) {
        _spoofedDevice.value = device
        analytics.logDeviceSpoofed(phoneModelForDevice(device))
        if (_developerModeEnabled.value) {
            updateSelectedDevice()
        }
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("spoofed_device", device) }
        }
    }

    fun setSpoofLocale(localeTag: String?) {
        _spoofLocale.value = localeTag
        val appLocales = if (localeTag == null) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(localeTag)
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocales)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("spoof_locale", localeTag) }
        }
    }

    fun updateSelectedDevice() {
        val actualDevice = DeviceProfile.detectDevice()
        val targetDevice = if (_developerModeEnabled.value) _spoofedDevice.value else actualDevice

        selectedDevice.value = targetDevice
        if (targetDevice == DeviceProfile.DEVICE_UNKNOWN && _selectedTab.value == Tab.Glyphs) {
            _selectedTab.value = Tab.Audio
        }
        refreshPresets()
        reloadLatencyForCurrentRoute()
        // Forward to service if bound
        MainActivity.serviceStatic?.setDevice(targetDevice)
    }

    // ── Latency ───────────────────────────────────────────────────────────────
    private val latencyWizard = LatencyWizard()
    private val _latencyWizardState = MutableStateFlow<LatencyWizard.State>(LatencyWizard.State.Idle)
    val latencyWizardState = _latencyWizardState.asStateFlow()

    fun runLatencyWizard() {
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        
        viewModelScope.launch {
            val result = latencyWizard.measureLatency(audioManager) { state ->
                _latencyWizardState.value = state
            }
            _latencyWizardState.value = result
            if (result is LatencyWizard.State.Success) {
                setLatencyMs(result.latencyMs)
            }
        }
    }

    fun resetLatencyWizard() {
        _latencyWizardState.value = LatencyWizard.State.Idle
    }

    val _latencyMs = MutableStateFlow(0)
    val latencyMs = _latencyMs.asStateFlow()

    val _latencyPresets = MutableStateFlow(listOf(0, 150, 300, 500))
    val latencyPresets = _latencyPresets.asStateFlow()

    /**
     * Updates the current system latency and persists it to disk.
     */
    fun setLatencyMs(value: Int) {
        _latencyMs.value = value
        analytics.logLatencyChanged(value, activeLatencyRouteKey())
        viewModelScope.launch(Dispatchers.IO) {
            val key = activeLatencyRouteKey()
            if (key != null) {
                ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    .edit { putInt("latency_$key", value) }
            }
        }
        MainActivity.serviceStatic?.setLatencyMs(value)
    }

    val _autoDeviceMemorize = MutableStateFlow(true)
    val autoDeviceMemorize = _autoDeviceMemorize.asStateFlow()

    fun setAutoDeviceMemorize(enabled: Boolean) {
        _autoDeviceMemorize.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("auto_device_memorize", enabled) }
        }
    }

    // ── Gamma ─────────────────────────────────────────────────────────────────
    val _gammaValue = MutableStateFlow(1.0f)
    val gammaValue = _gammaValue.asStateFlow()

    fun setGammaValue(value: Float) {
        _gammaValue.value = value
        MainActivity.serviceStatic?.setGamma(value)
    }

    val _spectrumGain = MutableStateFlow(1.0f)
    val spectrumGain = _spectrumGain.asStateFlow()

    fun setSpectrumGain(value: Float) {
        _spectrumGain.value = value
        MainActivity.serviceStatic?.setSpectrumGain(value)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("spectrum_gain", value) }
        }
    }

    val _fftReadMethod = MutableStateFlow(AudioProcessor.ReadMethod.MAX)
    val fftReadMethod = _fftReadMethod.asStateFlow()

    fun setFftReadMethod(method: AudioProcessor.ReadMethod) {
        _fftReadMethod.value = method
        MainActivity.serviceStatic?.setReadMethod(method)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("fft_read_method", method.name) }
        }
    }

    val _maxBrightness = MutableStateFlow(4095)
    val maxBrightness = _maxBrightness.asStateFlow()

    val _glyphsEnabled = MutableStateFlow(true)
    val glyphsEnabled = _glyphsEnabled.asStateFlow()

    fun setGlyphsEnabled(enabled: Boolean) {
        _glyphsEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("glyphs_enabled", enabled) }
        }
        // If disabling, we might want to also tell the service to stop pushing frames
        // but the tab visibility change will be the main effect for the user
    }

    fun setMaxBrightness(value: Int) {
        val clamped = value.coerceIn(0, 4500)
        _maxBrightness.value = clamped
        MainActivity.serviceStatic?.setMaxBrightness(clamped)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("max_brightness", clamped) }
        }
    }

    val _runningState = MutableStateFlow(false)
    val runningState = _runningState.asStateFlow()

    fun setRunning(running: Boolean) {
        _runningState.value = running
        if (!running) {
            saveStatsLocally()
            updateLeaderboard()
        }
    }

    val _selectedPreset = MutableStateFlow("Default")
    val selectedPreset = _selectedPreset.asStateFlow()

    fun currentPreset() = _selectedPreset.value

    fun setSelectedPreset(preset: String) {
        _selectedPreset.value = preset
        analytics.logPresetSelected(preset, false)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("selected_preset", preset) }
        }
        MainActivity.serviceStatic?.setSelectedPreset(preset)
    }

    val _presetInfos = MutableStateFlow<List<AudioCaptureService.PresetInfo>>(emptyList())
    val presetInfos = _presetInfos.asStateFlow()

    // ── Haptics ───────────────────────────────────────────────────────────────
    val _hapticMotorEnabled = MutableStateFlow(false)
    val hapticMotorEnabled = _hapticMotorEnabled.asStateFlow()

    val _hapticMode = MutableStateFlow(HapticMode.BASS_TO_AMPLITUDE)
    val hapticMode = _hapticMode.asStateFlow()

    val _hapticFreqMin = MutableStateFlow(20f)
    val hapticFreqMin = _hapticFreqMin.asStateFlow()

    val _hapticFreqMax = MutableStateFlow(250f)
    val hapticFreqMax = _hapticFreqMax.asStateFlow()

    val _hapticMultiplier = MutableStateFlow(1.0f)
    val hapticMultiplier = _hapticMultiplier.asStateFlow()

    val _hapticAudioGain = MutableStateFlow(1.0f)
    val hapticAudioGain = _hapticAudioGain.asStateFlow()

    val _hapticGamma = MutableStateFlow(2.0f)
    val hapticGamma = _hapticGamma.asStateFlow()

    val _hapticBeatSensitivity = MutableStateFlow(1.5f)
    val hapticBeatSensitivity = _hapticBeatSensitivity.asStateFlow()

    val _hapticBeatGamma = MutableStateFlow(8.0f)
    val hapticBeatGamma = _hapticBeatGamma.asStateFlow()

    fun setHapticMotorEnabled(enabled: Boolean) {
        _hapticMotorEnabled.value = enabled
        analytics.logSettingChanged("haptic_motor_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("haptic_motor_enabled", enabled) }
        }
        MainActivity.serviceStatic?.setHapticMotorEnabled(enabled)
    }

    fun setHapticMode(mode: HapticMode) {
        _hapticMode.value = mode
        analytics.logSettingChanged("haptic_mode", mode.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("haptic_mode", mode.name) }
        }
        MainActivity.serviceStatic?.setHapticMode(mode)
    }

    fun setHapticFreqRange(min: Float, max: Float) {
        _hapticFreqMin.value = min
        _hapticFreqMax.value = max
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit {
                    putInt("haptic_freq_min", min.toInt())
                    putInt("haptic_freq_max", max.toInt())
                }
        }
        MainActivity.serviceStatic?.setHapticFreqRange(min, max)
    }

    fun setHapticMultiplier(value: Float) {
        _hapticMultiplier.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_multiplier", value) }
        }
        MainActivity.serviceStatic?.setHapticMultiplier(value)
    }

    fun setHapticAudioGain(value: Float) {
        _hapticAudioGain.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_audio_gain", value) }
        }
        MainActivity.serviceStatic?.setHapticAudioGain(value)
    }

    fun setHapticGamma(value: Float) {
        _hapticGamma.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_gamma", value) }
        }
        MainActivity.serviceStatic?.setHapticGamma(value)
    }

    fun setHapticBeatSensitivity(value: Float) {
        _hapticBeatSensitivity.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_beat_sensitivity", value) }
        }
        MainActivity.serviceStatic?.setHapticBeatSensitivity(value)
    }

    fun setHapticBeatGamma(value: Float) {
        _hapticBeatGamma.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_beat_gamma", value) }
        }
        MainActivity.serviceStatic?.setHapticBeatGamma(value)
    }

    // ── Flashlight ────────────────────────────────────────────────────────────
    val _flashlightEnabled = MutableStateFlow(false)
    val flashlightEnabled = _flashlightEnabled.asStateFlow()

    val _flashlightMode = MutableStateFlow(TorchMode.AMPLITUDE)
    val flashlightMode = _flashlightMode.asStateFlow()

    val _flashlightFreqMin = MutableStateFlow(20f)
    val flashlightFreqMin = _flashlightFreqMin.asStateFlow()

    val _flashlightFreqMax = MutableStateFlow(250f)
    val flashlightFreqMax = _flashlightFreqMax.asStateFlow()

    val _flashlightThreshold = MutableStateFlow(0.15f)
    val flashlightThreshold = _flashlightThreshold.asStateFlow()

    val _flashlightSpeedMs = MutableStateFlow(80f)
    val flashlightSpeedMs = _flashlightSpeedMs.asStateFlow()

    val _flashlightBeatSensitivity = MutableStateFlow(1.5f)
    val flashlightBeatSensitivity = _flashlightBeatSensitivity.asStateFlow()

    fun setFlashlightEnabled(enabled: Boolean) {
        _flashlightEnabled.value = enabled
        analytics.logSettingChanged("flashlight_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("flashlight_enabled", enabled) }
        }
        MainActivity.serviceStatic?.setFlashlightEnabled(enabled)
    }

    fun setFlashlightMode(mode: TorchMode) {
        _flashlightMode.value = mode
        analytics.logSettingChanged("flashlight_mode", mode.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("flashlight_mode", mode.name) }
        }
        MainActivity.serviceStatic?.setFlashlightMode(mode)
    }

    fun setFlashlightFreqRange(min: Float, max: Float) {
        _flashlightFreqMin.value = min
        _flashlightFreqMax.value = max
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit {
                    putInt("flashlight_freq_min", min.toInt())
                    putInt("flashlight_freq_max", max.toInt())
                }
        }
        MainActivity.serviceStatic?.setFlashlightFreqRange(min, max)
    }

    fun setFlashlightThreshold(value: Float) {
        _flashlightThreshold.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_threshold", value) }
        }
        MainActivity.serviceStatic?.setFlashlightThreshold(value)
    }

    fun setFlashlightSpeedMs(value: Float) {
        _flashlightSpeedMs.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_speed_ms", value) }
        }
        MainActivity.serviceStatic?.setFlashlightSpeedMs(value)
    }

    fun setFlashlightBeatSensitivity(value: Float) {
        _flashlightBeatSensitivity.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_beat_sensitivity", value) }
        }
        MainActivity.serviceStatic?.setFlashlightBeatSensitivity(value)
    }

    fun setFlashlightIntensityLevels(levels: Int) {
        _flashlightIntensityLevels.value = levels
        reloadFlashlightSpeedForLevels()
    }

    fun reloadFlashlightSpeedForLevels() {
        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        val defaultVal = if (_flashlightIntensityLevels.value > 1) 350f else 80f
        val saved = prefs.getFloat("flashlight_speed_ms", defaultVal)

        val min = if (_flashlightIntensityLevels.value > 1) 150f else 20f
        val max = if (_flashlightIntensityLevels.value > 1) 700f else 150f

        _flashlightSpeedMs.value = saved.coerceIn(min, max)
    }

    fun flashlightSpeedForUi(gamma: Float): Float {
        val min = if (_flashlightIntensityLevels.value > 1) 150f else 20f
        val max = if (_flashlightIntensityLevels.value > 1) 700f else 150f
        val normalized = (gamma - min) / (max - min)

        // For binary (1 level), lower = faster.
        // For multi (e.g. NP2), higher = longer fade out.
        if (_flashlightIntensityLevels.value <= 1) {
            return 150f - (normalized * 130f)
        }
        return gamma.coerceIn(20f, 150f)
    }

    // ── Logic ─────────────────────────────────────────────────────────────────
    val _visualizerState = MutableStateFlow(floatArrayOf())
    val visualizerState = _visualizerState.asStateFlow()

    fun setVisualizerState(state: FloatArray) {
        _visualizerState.value = state
    }

    val _hapticAmplitude = MutableStateFlow(0f)
    val hapticAmplitude = _hapticAmplitude.asStateFlow()

    val _uiAmplitude = MutableStateFlow(1f)
    val uiAmplitude = _uiAmplitude.asStateFlow()

    val _flashlightAmplitude = MutableStateFlow(0f)
    val flashlightAmplitude = _flashlightAmplitude.asStateFlow()

    val _isBeatDetected = MutableStateFlow(false)
    val isBeatDetected = _isBeatDetected.asStateFlow()

    val _isFlashlightBeatDetected = MutableStateFlow(false)
    val isFlashlightBeatDetected = _isFlashlightBeatDetected.asStateFlow()

    val hapticBeatDetector = BeatDetector()
    val flashlightBeatDetector = BeatDetector()

    var smoothedUiAmplitude = 1f
    var smoothedHapticAmplitude = 0f
    private var uiDynamicGain = 1.0f
    private var uiPeakValue = 0.1f

    val _fftState = MutableStateFlow(floatArrayOf())
    val fftState = _fftState.asStateFlow()

    fun setFftState(state: FloatArray) {
        _fftState.value = state
    }

        init {
        viewModelScope.launch(Dispatchers.Default) {
            fftState.collect { magnitude ->
                val service = MainActivity.serviceStatic
                
                val target = if (magnitude.isEmpty() || service == null) {
                    _hapticAmplitude.value = 0f
                    _flashlightAmplitude.value = 0f
                    _isBeatDetected.value = false
                    1.0f
                } else {
                    // Use pre-calculated peaks from the service to avoid re-scanning bins
                    val hapticPeak = service.latestHapticPeak
                    val uiPeak = service.latestUiPeak
                    val flashlightPeak = service.latestFlashlightPeak

                    val targetHaptic = (hapticPeak * _hapticAudioGain.value * 12f).coerceIn(0f, 1.0f).toDouble().pow(_hapticGamma.value.toDouble()).toFloat()

                    // Asymmetric smoothing for haptics
                    if (targetHaptic > smoothedHapticAmplitude) {
                        smoothedHapticAmplitude = smoothedHapticAmplitude * 0.15f + targetHaptic * 0.85f
                    } else {
                        smoothedHapticAmplitude = smoothedHapticAmplitude * 0.7f + targetHaptic * 0.3f
                    }

                    val finalValue = (smoothedHapticAmplitude * _hapticMultiplier.value)
                    _hapticAmplitude.value = finalValue.coerceIn(0f, 1.2f)

                    // Flashlight Amplitude using the pre-calculated flashlightPeak
                    val fTarget = (flashlightPeak * 16.0f).coerceIn(0f, 1.2f)
                    val fCur = Math.pow(fTarget.toDouble(), 2.2).toFloat()
                    val fDelta = (fCur - _flashlightAmplitude.value).coerceAtLeast(0f)
                    _flashlightAmplitude.value = (fCur + fDelta * 1.5f).coerceIn(0f, 1.2f)

                    // UI Amplitude using the pre-calculated uiPeak
                    uiPeakValue = uiPeakValue * 0.98f + uiPeak * 0.02f
                    if (uiPeak > uiPeakValue) uiPeakValue = uiPeak
                    
                    val targetGain = if (uiPeakValue > 0.01f) 0.15f / uiPeakValue else 12f
                    uiDynamicGain = uiDynamicGain * 0.9f + targetGain.coerceIn(5f, 25f) * 0.1f
                    
                    (1.0f + (uiPeak * uiDynamicGain - 0.2f)).coerceIn(0.8f, 1.2f)
                }

                if (target > smoothedUiAmplitude) {
                    smoothedUiAmplitude = smoothedUiAmplitude * 0.1f + target * 0.9f
                } else {
                    smoothedUiAmplitude = smoothedUiAmplitude * 0.85f + target * 0.15f
                }
                
                _uiAmplitude.value = if (_uiAmplitudeSyncEnabled.value) {
                    smoothedUiAmplitude
                } else {
                    1.0f
                }

                if (magnitude.isEmpty()) return@collect
                
                val hzPerBin = 44100f / 2048f
                val binLo = (_hapticFreqMin.value / hzPerBin).toInt().coerceIn(0, magnitude.lastIndex)
                val binHi = (_hapticFreqMax.value / hzPerBin).toInt().coerceIn(binLo, magnitude.lastIndex)

                // 2. Beat Detection
                if (_hapticMode.value == HapticMode.BEAT_DETECTION) {
                    hapticBeatDetector.sensitivity = _hapticBeatSensitivity.value
                    if (hapticBeatDetector.detect(magnitude, binLo, binHi)) {
                        _isBeatDetected.value = true
                        viewModelScope.launch {
                            delay(50)
                            _isBeatDetected.value = false
                        }
                    }
                } else {
                    _isBeatDetected.value = false
                }

                if (_flashlightMode.value == TorchMode.BEAT_DETECTION) {
                    val fBinLo = (_flashlightFreqMin.value / hzPerBin).toInt().coerceIn(0, magnitude.lastIndex)
                    val fBinHi = (_flashlightFreqMax.value / hzPerBin).toInt().coerceIn(fBinLo, magnitude.lastIndex)
                    flashlightBeatDetector.sensitivity = _flashlightBeatSensitivity.value
                    if (flashlightBeatDetector.detect(magnitude, fBinLo, fBinHi)) {
                        _isFlashlightBeatDetected.value = true
                        viewModelScope.launch {
                            delay(50)
                            _isFlashlightBeatDetected.value = false
                        }
                    }
                } else {
                    _isFlashlightBeatDetected.value = false
                }
            }
        }

        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        _developerModeEnabled.value = prefs.getBoolean("developer_mode_v2", false)
        _spoofedDevice.value = prefs.getInt("spoofed_device", DeviceProfile.DEVICE_NP1)
        _autoDeviceMemorize.value = prefs.getBoolean("auto_device_memorize", true)
        _m3eEnabled.value = prefs.getBoolean("m3e_enabled", true)
        _gammaValue.value = prefs.getFloat("gamma_value", 1.0f)
        _spectrumGain.value = prefs.getFloat("spectrum_gain", 1.0f)
        _maxBrightness.value = prefs.getInt("max_brightness", 4095)
        _fftReadMethod.value = AudioProcessor.ReadMethod.valueOf(prefs.getString("fft_read_method", AudioProcessor.ReadMethod.MAX.name)!!)
        _glyphsEnabled.value = prefs.getBoolean("glyphs_enabled", true)
        _selectedPreset.value = prefs.getString("selected_preset", "Default") ?: "Default"
        _selectedTheme.value = prefs.getString("selected_theme", "Default") ?: "Default"
        _selectedFont.value = prefs.getString("selected_font", "Default") ?: "Default"
        _flashlightMultiIntensityForced.value = prefs.getBoolean("flashlight_multi_intensity_forced", false)
        _notificationButtonSet.value = prefs.getString("notification_button_set", "presets") ?: "presets"

        _hapticMotorEnabled.value = prefs.getBoolean("haptic_motor_enabled", false)
        _hapticMode.value = HapticMode.valueOf(prefs.getString("haptic_mode", HapticMode.BASS_TO_AMPLITUDE.name)!!)
        _hapticFreqMin.value = prefs.getInt("haptic_freq_min", 20).toFloat()
        _hapticFreqMax.value = prefs.getInt("haptic_freq_max", 250).toFloat()
        _hapticMultiplier.value = prefs.getFloat("haptic_multiplier", 1.0f)
        _hapticAudioGain.value = prefs.getFloat("haptic_audio_gain", 1.0f)
        _hapticGamma.value = prefs.getFloat("haptic_gamma", 2.0f)
        _hapticBeatSensitivity.value = prefs.getFloat("haptic_beat_sensitivity", 1.5f)
        _hapticBeatGamma.value = prefs.getFloat("haptic_beat_gamma", 8.0f)

        _flashlightEnabled.value = prefs.getBoolean("flashlight_enabled", false)
        _flashlightMode.value = TorchMode.valueOf(prefs.getString("flashlight_mode", TorchMode.AMPLITUDE.name)!!)
        _flashlightFreqMin.value = prefs.getInt("flashlight_freq_min", 20).toFloat()
        _flashlightFreqMax.value = prefs.getInt("flashlight_freq_max", 250).toFloat()
        _flashlightThreshold.value = prefs.getFloat("flashlight_threshold", 0.15f)
        _flashlightBeatSensitivity.value = prefs.getFloat("flashlight_beat_sensitivity", 1.5f)

        _idleBreathingEnabled.value = prefs.getBoolean("idle_breathing_enabled", false)
        _idlePattern.value = prefs.getString("idle_pattern", "pulse") ?: "pulse"
        _strobeEnabled.value = prefs.getBoolean("strobe_enabled", false)
        _disableGlyphsWhenSilent.value = prefs.getBoolean("disable_glyphs_when_silent", false)
        _overlayEnabled.value = prefs.getBoolean("overlay_enabled", false)
        _overlayTopEnabled.value = prefs.getBoolean("overlay_top_enabled", true)
        _overlayBottomEnabled.value = prefs.getBoolean("overlay_bottom_enabled", false)
        _overlayWidth.value = prefs.getInt("overlay_width", 120)
        _overlayHeight.value = prefs.getInt("overlay_height", 12)
        _overlayHeightBottom.value = prefs.getInt("overlay_height_bottom", 12)
        _overlayYOffset.value = prefs.getInt("overlay_y_offset", 2)
        _overlaySensitivity.value = prefs.getFloat("overlay_sensitivity", 1.0f)
        _overlaySensitivityBottom.value = prefs.getFloat("overlay_sensitivity_bottom", 1.0f)
        _edgeVisualizerEnabled.value = prefs.getBoolean("edge_visualizer_enabled", false)
        _edgeThickness.value = prefs.getInt("edge_thickness", 12)
        _edgeSensitivity.value = prefs.getFloat("edge_sensitivity", 1.0f)
        _edgeBarCountHoriz.value = prefs.getInt("edge_bar_count_horiz", 20)
        _edgeBarCountVert.value = prefs.getInt("edge_bar_count_vert", 40)
        _edgeCornerRadius.value = prefs.getFloat("edge_corner_radius", 2f)
        _edgeTopEnabled.value = prefs.getBoolean("edge_top_enabled", true)
        _edgeBottomEnabled.value = prefs.getBoolean("edge_bottom_enabled", true)

        _lensVisualizerEnabled.value = prefs.getBoolean("lens_visualizer_enabled", false)
        _lensVisualizerRadius.value = prefs.getFloat("lens_visualizer_radius", 16f)
        _lensVisualizerX.value = prefs.getFloat("lens_visualizer_x", 0.50f)
        _lensVisualizerY.value = prefs.getFloat("lens_visualizer_y", 0.03f)
        _lensVisualizerBarWidth.value = prefs.getFloat("lens_visualizer_bar_width", 1f)
        _lensVisualizerMaxHeight.value = prefs.getFloat("lens_visualizer_max_height", 5f)
        _lensVisualizerBarCount.value = prefs.getInt("lens_visualizer_bar_count", 35)
        _lensVisualizerSensitivity.value = prefs.getFloat("lens_visualizer_sensitivity", 0.32f)

        reloadFlashlightSpeedForLevels()

        updateSelectedDevice()
        refreshPresets()
        initDatabase()
    }

    fun initDatabase() {
        // Mock
    }

    fun phoneModelForDevice(device: Int): String {
        return when (device) {
            DeviceProfile.DEVICE_NP1 -> "Nothing Phone (1)"
            DeviceProfile.DEVICE_NP2 -> "Nothing Phone (2)"
            DeviceProfile.DEVICE_NP2A -> "Nothing Phone (2a)"
            DeviceProfile.DEVICE_NP3A -> "Nothing Phone (3a)"
            DeviceProfile.DEVICE_NP4A -> "Nothing Phone (4a)"
            DeviceProfile.DEVICE_NP4APRO -> "Nothing Phone (4a) Pro"
            DeviceProfile.DEVICE_NP3 -> "Nothing Phone (3)"
            DeviceProfile.DEVICE_NP4B -> "Nothing Phone (4b)"
            else -> "Unknown Device"
        }
    }

    // ── Updates ───────────────────────────────────────────────────────────────
    val _configVersion = MutableStateFlow("Unknown")
    val configVersion = _configVersion.asStateFlow()

    val _remoteConfigVersion = MutableStateFlow<String?>(null)
    val remoteConfigVersion = _remoteConfigVersion.asStateFlow()

    sealed class ConfigUpdateStatus {
        object Idle : ConfigUpdateStatus()
        object Updating : ConfigUpdateStatus()
        data class Success(val message: String) : ConfigUpdateStatus()
        data class Error(val message: String) : ConfigUpdateStatus()
    }
    val _configUpdateStatus = MutableStateFlow<ConfigUpdateStatus>(ConfigUpdateStatus.Idle)
    val configUpdateStatus = _configUpdateStatus.asStateFlow()

    fun resetConfigUpdateStatus() { _configUpdateStatus.value = ConfigUpdateStatus.Idle }

    fun refreshPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshPresetsInternal()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Presets refresh failed, pulling from remote", e)
                // If we are already updating, don't trigger another one
                if (_configUpdateStatus.value !is ConfigUpdateStatus.Updating) {
                    withContext(Dispatchers.Main) {
                        updateZonesConfig()
                    }
                }
            }
        }
    }

    fun refreshPresetsInternal() {
        try {
            val json = AudioCaptureService.loadZonesConfigText(ctx)
            if (json != null) {
                try {
                    val root = JSONObject(json)
                    val version = root.optString("version", "Unknown")
                    _configVersion.value = version
                    
                    // If it's a "simple" fallback config, don't show any presets in the UI
                    // to encourage the user to update to the full version.
                    if (version.contains(".simple")) {
                        Log.d("MainViewModel", "Simple config detected (v$version), clearing preset list")
                        _presetInfos.value = emptyList()
                    } else {
                        val list = AudioCaptureService.loadPresetInfos(ctx, selectedDevice.value)
                        Log.d("MainViewModel", "Loaded ${list.size} presets from zones.config (v$version)")
                        _presetInfos.value = list
                    }
                } catch (e: JSONException) {
                    Log.e("MainViewModel", "Invalid JSON in zones.config", e)
                    _configVersion.value = "Invalid JSON"
                    _presetInfos.value = emptyList()
                }
            } else {
                Log.w("MainViewModel", "zones.config text is null")
                _configVersion.value = "Missing"
                _presetInfos.value = emptyList()
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to refresh presets internally", e)
            _presetInfos.value = emptyList()
        }
    }

    fun updateLatencyPresets(newPresets: List<Int>) {
        _latencyPresets.value = newPresets
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit {
                    putString("latency_presets", newPresets.joinToString(","))
                }
        }
    }

    fun persistGamma(gamma: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("gamma_value", gamma) }
        }
    }

    fun reloadLatencyForCurrentRoute(): Int {
        val key = activeLatencyRouteKey()
        if (key != null) {
            val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getInt("latency_$key", 0)
            _latencyMs.value = saved
            return saved
        }
        return 0
    }

    fun activeLatencyRouteKey(): String? {
        return MainActivity.serviceStatic?.getActiveAudioRouteKey()
    }

    fun signOut() {
        // Just clear the user. The init logic will re-create an anonymous
        // session (after a short delay) if no real sign-in happens.
        // Previously this called signInAnonymously() immediately, which made
        // it impossible to ever sign back in with the same provider right
        // after logging out because Firebase would re-issue the cached
        // anonymous account.
        FirebaseAuth.getInstance().signOut()
    }

    fun signInWithEmail(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Signing in with email: $email")
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Signed in!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Email sign in failed", e)
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Sign in failed") }
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Creating account for: $email")
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Account created!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Email sign up failed", e)
                withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Sign up failed") }
            }
        }
    }

    fun linkWithCredential(credential: AuthCredential) {
        val auth = FirebaseAuth.getInstance()
        // If there isn't an active user (e.g. logged out just now), fall back
        // to a plain credential sign in. Otherwise the credential is dropped
        // on the floor and the user can't log in again.
        if (auth.currentUser == null) {
            signInWithCredential(credential)
            return
        }
        val user = auth.currentUser!!
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Linking user with credential...")
                val result = user.linkWithCredential(credential).await()
                val firebaseUser = result.user
                
                // Update profile from firebase user info
                if (firebaseUser != null) {
                    val profile = _userProfile.value?.copy(
                        displayName = firebaseUser.displayName ?: _userNickname.value,
                        profilePictureUrl = firebaseUser.photoUrl?.toString() ?: _userProfile.value?.profilePictureUrl
                    ) ?: UserProfile(
                        userId = firebaseUser.uid,
                        displayName = firebaseUser.displayName ?: "Anonymous",
                        profilePictureUrl = firebaseUser.photoUrl?.toString(),
                        createdAt = System.currentTimeMillis()
                    )
                    userRepository.saveUserProfile(profile)
                    _userProfile.value = profile
                    _userNickname.value = profile.displayName ?: "Anonymous"
                }
                
                syncStats(firebaseUser?.uid)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Google account linked!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Linking failed", e)
                
                // If linking fails because provider is already linked, try signing in instead
                val msg = e.message ?: ""
                if (msg.contains("provider already linked", ignoreCase = true) || 
                    msg.contains("already in use", ignoreCase = true) ||
                    msg.contains("credential_already_associated", ignoreCase = true)) {
                    Log.d("MainViewModel", "Credential already linked, signing in instead...")
                    signInWithCredential(credential)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Linking failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun signInWithCredential(credential: AuthCredential) {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Signing in with credential...")
                val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val firebaseUser = result.user
                if (firebaseUser != null) {
                    // Try to fetch existing profile
                    var profile = userRepository.getUserProfile(firebaseUser.uid)
                    if (profile == null) {
                        val newProfile = UserProfile(
                            userId = firebaseUser.uid,
                            displayName = firebaseUser.displayName ?: "Anonymous",
                            profilePictureUrl = firebaseUser.photoUrl?.toString(),
                            createdAt = System.currentTimeMillis()
                        )
                        userRepository.saveUserProfile(newProfile)
                        profile = newProfile
                    } else if (profile.displayName == "Anonymous") {
                        // If existing profile has default name, try to use Google name
                        val googleName = firebaseUser.displayName
                        if (!googleName.isNullOrBlank()) {
                            val updatedProfile = profile.copy(displayName = googleName)
                            userRepository.saveUserProfile(updatedProfile)
                            profile = updatedProfile
                        }
                    }
                    _userProfile.value = profile
                    _userNickname.value = profile.displayName
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Welcome, ${profile.displayName}!", Toast.LENGTH_SHORT).show()
                    }
                }
                syncStats(firebaseUser?.uid)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Sign in failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Sign in failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onDevDepressed() {
        // Mock
    }

    override fun onCleared() {
        super.onCleared()
        saveStatsLocally()
    }
}
