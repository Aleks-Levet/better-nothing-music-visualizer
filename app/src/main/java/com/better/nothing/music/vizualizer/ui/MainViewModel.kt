package com.better.nothing.music.vizualizer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.logic.*
import com.better.nothing.music.vizualizer.model.*
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.math.pow

enum class Tab(val label: String, val labelRes: Int) {
    Audio("Audio", R.string.tab_audio), 
    Glyphs("Glyphs", R.string.tab_glyphs), 
    Haptics("Haptics", R.string.tab_haptics), 
    Flashlight("Flashlight", R.string.tab_flashlight), 
    Visuals("Visuals", R.string.tab_visuals),
    Settings("Settings", R.string.tab_settings);
}

data class AudioRoute(
    val storageKey: String,
    val displayName: String,
)

inline fun <reified T : Enum<T>> safeValueOf(value: String?, default: T): T {
    return try {
        if (value == null) default else enumValueOf<T>(value)
    } catch (e: Exception) {
        default
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        var instance: MainViewModel? = null
            private set
    }

    init {
        instance = this
    }

    val ctx = application

    val hasHapticMotor: Boolean by lazy {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.hasVibrator() == true
    }

    val hasAmplitudeControl: Boolean by lazy {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.hasAmplitudeControl() == true
    }

    val hasFlashlight: Boolean by lazy {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    val _flashlightIntensityLevels = MutableStateFlow(1)
    val flashlightIntensityLevels = _flashlightIntensityLevels.asStateFlow()

    val _flashlightLevel = MutableStateFlow(0)
    val flashlightLevel = _flashlightLevel.asStateFlow()

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

    private val _isFirstTime = MutableStateFlow(false)
    val isFirstTime = _isFirstTime.asStateFlow()
    fun dismissFirstTime() {
        _isFirstTime.value = false
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("first_time_v2", false) }
        }
    }

    fun fetchLicense() {
        viewModelScope.launch(Dispatchers.IO) {
            _licenseStatus.value = LicenseStatus.Loading
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://raw.githubusercontent.com/aleks-levet/better-nothing-music-visualizer/main/LICENSE.md")
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    _licenseStatus.value = LicenseStatus.Success(content)
                } else {
                    _licenseStatus.value = LicenseStatus.Error(ctx.getString(R.string.license_load_error, connection.responseCode))
                }
            } catch (e: Exception) {
                _licenseStatus.value = LicenseStatus.Error(e.message ?: ctx.getString(R.string.unknown_error))
            } finally {
                connection?.disconnect()
            }
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

    private val _broadcastEnabled = MutableStateFlow(false)
    val broadcastEnabled = _broadcastEnabled.asStateFlow()

    private val _connectedClients = MutableStateFlow<Map<InetAddress, Int?>>(emptyMap())
    val connectedClients = _connectedClients.asStateFlow()

    fun setBroadcastEnabled(enabled: Boolean, fromService: Boolean = false) {
        if (_broadcastEnabled.value == enabled) return
        _broadcastEnabled.value = enabled
        if (!fromService) {
            MainActivity.serviceStatic?.setBroadcastEnabled(enabled)
            viewModelScope.launch(Dispatchers.IO) {
                ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    .edit { putBoolean("broadcast_enabled", enabled) }
            }
        }
        checkAnyOutputSelected()
    }

    private val _discoveredHosts = MutableStateFlow<List<UdpNetworkSync.HostInfo>>(emptyList())
    val discoveredHosts = _discoveredHosts.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    fun startDiscovery() {
        _isDiscovering.value = true
        _discoveredHosts.value = emptyList()
        MainActivity.serviceStatic?.discoverHosts { host ->
            val current = _discoveredHosts.value.toMutableList()
            if (!current.any { it.ip == host.ip }) {
                current.add(host)
                _discoveredHosts.value = current
            }
            return@discoverHosts kotlin.Unit
        }
        viewModelScope.launch {
            delay(3000)
            _isDiscovering.value = false
        }
    }

    private val _selectedHost = MutableStateFlow<UdpNetworkSync.HostInfo?>(null)
    val selectedHost = _selectedHost.asStateFlow()

    fun connectToHost(host: UdpNetworkSync.HostInfo) {
        _selectedHost.value = host
        setCaptureSource(AudioCaptureService.CaptureSource.NETWORK)
    }

    private val _onScreenVisualizersEnabled = MutableStateFlow(false)
    val onScreenVisualizersEnabled = _onScreenVisualizersEnabled.asStateFlow()

    fun setOnScreenVisualizersEnabled(enabled: Boolean, context: Context, onPermissionRequired: () -> Unit) {
        if (enabled && !android.provider.Settings.canDrawOverlays(context)) {
            onPermissionRequired()
            return
        }
        _onScreenVisualizersEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("on_screen_visualizers_enabled", enabled) }
        }
        // If master switch is turned off, we should probably tell the service to hide everything
        if (!enabled) {
            MainActivity.serviceStatic?.setOverlayEnabled(false)
            MainActivity.serviceStatic?.setEdgeVisualizerEnabled(false)
            MainActivity.serviceStatic?.setLensVisualizerEnabled(false)
        } else {
            // Restore individual states
            MainActivity.serviceStatic?.setOverlayEnabled(_overlayEnabled.value)
            MainActivity.serviceStatic?.setEdgeVisualizerEnabled(_edgeVisualizerEnabled.value)
            MainActivity.serviceStatic?.setLensVisualizerEnabled(_lensVisualizerEnabled.value)
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

    private val _tabletTabWidth = MutableStateFlow(0)
    val tabletTabWidth = _tabletTabWidth.asStateFlow()
    fun setTabletTabWidth(width: Int) {
        _tabletTabWidth.value = width
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("tablet_tab_width", width) }
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

    private val _overlayColor = MutableStateFlow(Color.White)
    val overlayColor = _overlayColor.asStateFlow()
    fun setOverlayColor(color: Color) {
        _overlayColor.value = color
        MainActivity.serviceStatic?.setOverlayColor(color.toArgb())
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_color", color.toArgb()) }
        }
    }

    private val _edgeVisualizerEnabled = MutableStateFlow(false)
    val edgeVisualizerEnabled = _edgeVisualizerEnabled.asStateFlow()
    fun setEdgeVisualizerEnabled(enabled: Boolean, fromService: Boolean = false) {
        if (_edgeVisualizerEnabled.value == enabled) return
        _edgeVisualizerEnabled.value = enabled
        if (_onScreenVisualizersEnabled.value && !fromService) {
            MainActivity.serviceStatic?.setEdgeVisualizerEnabled(enabled)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("edge_visualizer_enabled", enabled) }
        }
        checkAnyOutputSelected()
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

    fun setEdgeBarCount(count: Int) {
        _edgeBarCountHoriz.value = count
        _edgeBarCountVert.value = count
        MainActivity.serviceStatic?.setEdgeBarCounts(count, count)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit {
                    putInt("edge_bar_count_horiz", count)
                    putInt("edge_bar_count_vert", count)
                }
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

    private val _edgeColor = MutableStateFlow(Color.White)
    val edgeColor = _edgeColor.asStateFlow()
    fun setEdgeColor(color: Color) {
        _edgeColor.value = color
        MainActivity.serviceStatic?.setEdgeColor(color.toArgb())
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("edge_color", color.toArgb()) }
        }
    }

    private val _lensVisualizerEnabled = MutableStateFlow(false)
    val lensVisualizerEnabled = _lensVisualizerEnabled.asStateFlow()
    fun setLensVisualizerEnabled(enabled: Boolean, fromService: Boolean = false) {
        if (_lensVisualizerEnabled.value == enabled) return
        _lensVisualizerEnabled.value = enabled
        if (_onScreenVisualizersEnabled.value && !fromService) {
            MainActivity.serviceStatic?.setLensVisualizerEnabled(enabled)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("lens_visualizer_enabled", enabled) }
        }
        checkAnyOutputSelected()
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

    private val _lensColor = MutableStateFlow(Color.White)
    val lensColor = _lensColor.asStateFlow()
    fun setLensColor(color: Color) {
        _lensColor.value = color
        MainActivity.serviceStatic?.setLensColor(color.toArgb())
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("lens_color", color.toArgb()) }
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

    private val _selectedFont = MutableStateFlow("NDot")
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
                        Toast.makeText(ctx, ctx.getString(R.string.download_failed_http, responseCode), Toast.LENGTH_SHORT).show()
                        _appUpdateStatus.value = AppUpdateStatus.Error(ctx.getString(R.string.download_failed_http, responseCode))
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Download failed with error", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = e.message ?: ctx.getString(R.string.unknown_error)
                    Toast.makeText(ctx, ctx.getString(R.string.download_error, errorMsg), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(ctx, ctx.getString(R.string.installation_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private val _isShowingStats = MutableStateFlow(false)
    val isShowingStats = _isShowingStats.asStateFlow()
    fun showStats() { _isShowingStats.value = true }
    fun hideStats() { _isShowingStats.value = false }

    private val _isShowingHostPicker = MutableStateFlow(false)
    val isShowingHostPicker = _isShowingHostPicker.asStateFlow()
    fun showHostPicker() {
        startDiscovery()
        _isShowingHostPicker.value = true
    }
    fun hideHostPicker() { _isShowingHostPicker.value = false }

    fun checkRemoteConfigVersion() {
        if (selectedDevice.value == DeviceProfile.DEVICE_UNKNOWN || !_glyphsEnabled.value) return
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
        if (selectedDevice.value == DeviceProfile.DEVICE_UNKNOWN || !_glyphsEnabled.value) return
        _configUpdateStatus.value = ConfigUpdateStatus.Updating

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
        if (selectedDevice.value == DeviceProfile.DEVICE_UNKNOWN || !_glyphsEnabled.value) return
        // 1. Set loading state immediately on Main Thread
        _configUpdateStatus.value = ConfigUpdateStatus.Updating

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

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled = _overlayEnabled.asStateFlow()

    fun setOverlayEnabled(enabled: Boolean, fromService: Boolean = false) {
        if (_overlayEnabled.value == enabled) return
        _overlayEnabled.value = enabled
        if (_onScreenVisualizersEnabled.value && !fromService) {
            MainActivity.serviceStatic?.setOverlayEnabled(enabled)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("overlay_enabled", enabled) }
        }
        checkAnyOutputSelected()
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

    val _idleBrightness = MutableStateFlow(0.4f)
    val idleBrightness = _idleBrightness.asStateFlow()

    fun setIdleBrightness(value: Float) {
        _idleBrightness.value = value
        MainActivity.serviceStatic?.setIdleBrightness(value)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("idle_brightness", value) }
        }
    }

    val _idleBackgroundBrightness = MutableStateFlow(0.02f)
    val idleBackgroundBrightness = _idleBackgroundBrightness.asStateFlow()

    fun setIdleBackgroundBrightness(value: Float) {
        _idleBackgroundBrightness.value = value
        MainActivity.serviceStatic?.setIdleBackgroundBrightness(value)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("idle_background_brightness", value) }
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

    val _favoritePresets = MutableStateFlow<Set<String>>(emptySet())
    val favoritePresets = _favoritePresets.asStateFlow()

    val _captureSource = MutableStateFlow(AudioCaptureService.CaptureSource.INTERNAL)
    val captureSource = _captureSource.asStateFlow()

    val _showSpoofingSettings = MutableStateFlow(false)
    val showSpoofingSettings = _showSpoofingSettings.asStateFlow()

    fun setShowSpoofingSettings(show: Boolean) {
        _showSpoofingSettings.value = show
    }

    val _spoofLocale = MutableStateFlow<String?>(null)
    val spoofLocale = _spoofLocale.asStateFlow()

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
    }

    fun navigateBack(): Boolean {
        if (_isShowingStats.value) { hideStats(); return true }
        if (_isShowingLicense.value) { hideLicense(); return true }
        if (_isShowingAbout.value) { hideAbout(); return true }
        if (_isShowingHostPicker.value) { hideHostPicker(); return true }

        if (_selectedTab.value != Tab.Audio) {
            selectTab(Tab.Audio)
            return true
        }
        return false
    }

    fun setCaptureSource(source: AudioCaptureService.CaptureSource) {
        _captureSource.value = source
        MainActivity.serviceStatic?.setCaptureSource(source)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("capture_source", source.name) }
        }
    }

    // ── Device ────────────────────────────────────────────────────────────────
    val selectedDevice = MutableStateFlow(DeviceProfile.DEVICE_NP2)

    val _developerModeEnabled = MutableStateFlow(false)
    val developerModeEnabled = _developerModeEnabled.asStateFlow()

    val _spoofedDevice = MutableStateFlow(DeviceProfile.DEVICE_NP1)
    val spoofedDevice = _spoofedDevice.asStateFlow()

    fun setDeveloperModeEnabled(enabled: Boolean) {
        _developerModeEnabled.value = enabled
        updateSelectedDevice()
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("developer_mode_v2", enabled) }
        }
    }

    fun setSpoofedDevice(device: Int) {
        _spoofedDevice.value = device
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
        if (targetDevice == DeviceProfile.DEVICE_UNKNOWN) {
            _glyphsEnabled.value = false
            MainActivity.serviceStatic?.setMaxBrightness(0)
            if (_selectedTab.value == Tab.Glyphs) {
                _selectedTab.value = Tab.Audio
            }
        }
        refreshPresets()
        reloadLatencyForCurrentRoute()
        MainActivity.serviceStatic?.setDevice(targetDevice)
    }

    // ── Latency ───────────────────────────────────────────────────────────────
    val _latencyMs = MutableStateFlow(0)
    val latencyMs = _latencyMs.asStateFlow()

    val _latencyPresets = MutableStateFlow(listOf(0, 150, 300, 500))
    val latencyPresets = _latencyPresets.asStateFlow()

    fun setLatencyMs(value: Int) {
        _latencyMs.value = value
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
    val _gammaValue = MutableStateFlow(2.2f)
    val gammaValue = _gammaValue.asStateFlow()

    fun setGammaValue(value: Float) {
        _gammaValue.value = value
        MainActivity.serviceStatic?.setGamma(value)
    }

    val _spectrumGain = MutableStateFlow(4.0f)
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

    val _maxBrightness = MutableStateFlow(10000)
    val maxBrightness = _maxBrightness.asStateFlow()

    val _glyphsEnabled = MutableStateFlow(true)
    val glyphsEnabled = _glyphsEnabled.asStateFlow()

    fun setGlyphsEnabled(enabled: Boolean, fromService: Boolean = false) {
        if (selectedDevice.value == DeviceProfile.DEVICE_UNKNOWN && enabled) return
        if (_glyphsEnabled.value == enabled) return
        _glyphsEnabled.value = enabled
        if (!fromService) {
            viewModelScope.launch(Dispatchers.IO) {
                ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    .edit { putBoolean("glyphs_enabled", enabled) }
            }
            MainActivity.serviceStatic?.setMaxBrightness(if (enabled) _maxBrightness.value else 0)
        }
        AudioCaptureService.requestWidgetRefresh(ctx)
        refreshPresets()
        checkAnyOutputSelected()
    }

    private fun checkAnyOutputSelected() {
        val hasAnyOutput = _glyphsEnabled.value || _hapticMotorEnabled.value || _flashlightEnabled.value || 
                          _broadcastEnabled.value || _overlayEnabled.value || _edgeVisualizerEnabled.value || 
                          _lensVisualizerEnabled.value
        if (!hasAnyOutput) {
            android.widget.Toast.makeText(ctx, ctx.getString(R.string.toast_no_output), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun setMaxBrightness(value: Int) {
        val clamped = value.coerceIn(50, 5000)
        _maxBrightness.value = clamped
        MainActivity.serviceStatic?.setMaxBrightness(clamped)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("max_brightness", clamped) }
        }
    }

    private val _alternateGlyphVizEnabled = MutableStateFlow(false)
    val alternateGlyphVizEnabled = _alternateGlyphVizEnabled.asStateFlow()

    private val _highQualityAnalysis = MutableStateFlow(false)
    val highQualityAnalysis = _highQualityAnalysis.asStateFlow()

    fun setHighQualityAnalysis(enabled: Boolean) {
        _highQualityAnalysis.value = enabled
        MainActivity.serviceStatic?.setHighQualityAnalysis(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("high_quality_analysis", enabled) }
        }
    }

    fun setAlternateGlyphVizEnabled(enabled: Boolean) {
        _alternateGlyphVizEnabled.value = enabled
        MainActivity.serviceStatic?.setAlternateGlyphVizEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("alternate_glyph_viz_enabled", enabled) }
        }
    }

    val _runningState = MutableStateFlow(false)
    val runningState = _runningState.asStateFlow()

    fun setRunning(running: Boolean) {
        if (running) {
            val hasAnyOutput = _glyphsEnabled.value || _hapticMotorEnabled.value || _flashlightEnabled.value || 
                              _broadcastEnabled.value || _overlayEnabled.value || _edgeVisualizerEnabled.value || 
                              _lensVisualizerEnabled.value
            
            if (!hasAnyOutput) {
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.toast_no_output), android.widget.Toast.LENGTH_SHORT).show()
            }
            
            _runningState.value = running
            viewModelScope.launch {
                MainActivity.serviceStatic?.getConnectedClientsFlow()?.collect {
                    _connectedClients.value = it
                }
            }
        } else {
            _runningState.value = running
            _connectedClients.value = emptyMap()
            saveStatsLocally()
        }
        AudioCaptureService.requestTileRefresh(ctx)
    }

    val _selectedPreset = MutableStateFlow("Default")
    val selectedPreset = _selectedPreset.asStateFlow()

    fun currentPreset() = _selectedPreset.value

    fun setSelectedPreset(preset: String) {
        _selectedPreset.value = preset
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
    
    val _hapticBeatEngineMode = MutableStateFlow(BeatEngineMode.SMOOTH)
    val hapticBeatEngineMode = _hapticBeatEngineMode.asStateFlow()
    
    val _hapticPulseDurationMs = MutableStateFlow(40)
    val hapticPulseDurationMs = _hapticPulseDurationMs.asStateFlow()

    fun setHapticMotorEnabled(enabled: Boolean, fromService: Boolean = false) {
        if (_hapticMotorEnabled.value == enabled) return
        _hapticMotorEnabled.value = enabled
        if (!fromService) {
            viewModelScope.launch(Dispatchers.IO) {
                ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    .edit { putBoolean("haptic_motor_enabled", enabled) }
            }
            MainActivity.serviceStatic?.setHapticMotorEnabled(enabled)
        }
        checkAnyOutputSelected()
    }

    fun setHapticMode(mode: HapticMode) {
        _hapticMode.value = mode
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

    fun setHapticBeatEngineMode(mode: BeatEngineMode) {
        _hapticBeatEngineMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("haptic_beat_engine_mode", mode.name) }
        }
        MainActivity.serviceStatic?.setHapticBeatEngineMode(mode)
    }

    fun setHapticPulseDurationMs(ms: Int) {
        _hapticPulseDurationMs.value = ms
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("haptic_pulse_duration_ms", ms) }
        }
        MainActivity.serviceStatic?.setHapticPulseDurationMs(ms)
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

    val _flashlightBeatEngineMode = MutableStateFlow(BeatEngineMode.SMOOTH)
    val flashlightBeatEngineMode = _flashlightBeatEngineMode.asStateFlow()

    val _flashlightPulseDurationMs = MutableStateFlow(40)
    val flashlightPulseDurationMs = _flashlightPulseDurationMs.asStateFlow()

    fun setFlashlightEnabled(enabled: Boolean, fromService: Boolean = false) {
        if (_flashlightEnabled.value == enabled) return
        _flashlightEnabled.value = enabled
        if (!fromService) {
            viewModelScope.launch(Dispatchers.IO) {
                ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    .edit { putBoolean("flashlight_enabled", enabled) }
            }
            MainActivity.serviceStatic?.setFlashlightEnabled(enabled)
        }
        checkAnyOutputSelected()
    }

    fun setFlashlightMode(mode: TorchMode) {
        _flashlightMode.value = mode
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

    fun setFlashlightBeatEngineMode(mode: BeatEngineMode) {
        _flashlightBeatEngineMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("flashlight_beat_engine_mode", mode.name) }
        }
        MainActivity.serviceStatic?.setFlashlightBeatEngineMode(mode)
    }

    fun setFlashlightPulseDurationMs(ms: Int) {
        _flashlightPulseDurationMs.value = ms
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("flashlight_pulse_duration_ms", ms) }
        }
        MainActivity.serviceStatic?.setFlashlightPulseDurationMs(ms)
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

    val _hapticMotorIntensity = MutableStateFlow(0f)
    val hapticMotorIntensity = _hapticMotorIntensity.asStateFlow()

    val _uiAmplitude = MutableStateFlow(1f)
    val uiAmplitude = _uiAmplitude.asStateFlow()

    val _flashlightAmplitude = MutableStateFlow(0f)
    val flashlightAmplitude = _flashlightAmplitude.asStateFlow()

    val _flashlightMotorIntensity = MutableStateFlow(0f)
    val flashlightMotorIntensity = _flashlightMotorIntensity.asStateFlow()

    val _isBeatDetected = MutableStateFlow(false)
    val isBeatDetected = _isBeatDetected.asStateFlow()

    val _isFlashlightBeatDetected = MutableStateFlow(false)
    val isFlashlightBeatDetected = _isFlashlightBeatDetected.asStateFlow()

    val hapticBeatDetector = BeatDetector()
    val flashlightBeatDetector = BeatDetector()

    var smoothedHapticAmplitude = 0f

    val _fftState = MutableStateFlow(floatArrayOf())
    val fftState = _fftState.asStateFlow()

    private val manualDecayFft = FloatArray(512)

    fun setFftData(raw: IntArray) {
        if (raw.size == 512) {
            for (i in 0 until 512) {
                val fVal = raw[i] / 4095f
                if (fVal > manualDecayFft[i]) {
                    manualDecayFft[i] = fVal
                } else {
                    // Faster manual decay for UI (0.04f per frame)
                    manualDecayFft[i] = (manualDecayFft[i] - 0.04f).coerceAtLeast(fVal)
                }
            }
            _fftState.value = manualDecayFft.copyOf()
        }
    }

    fun syncIntensities(hPeak: Float, hIntensity: Float, fPeak: Float, fIntensity: Float, hBeat: Boolean, fBeat: Boolean) {
        _hapticAmplitude.value = hPeak
        _hapticMotorIntensity.value = hIntensity
        _flashlightAmplitude.value = fPeak
        _flashlightMotorIntensity.value = fIntensity

        if (hBeat) {
            _isBeatDetected.value = true
            viewModelScope.launch { delay(50); _isBeatDetected.value = false }
        }

        if (fBeat) {
            _isFlashlightBeatDetected.value = true
            viewModelScope.launch { delay(50); _isFlashlightBeatDetected.value = false }
        }
        
        // Handle UI amplitude - Direct mapping of 0..1 (from 0..4095) to 1.0..1.25
        val service = MainActivity.serviceStatic
        if (service != null) {
            val uiPeak = service.latestUiPeak
            val target = 0.8f + (uiPeak * 0.45f)
            
            val current = _uiAmplitude.value
            val next = if (_uiAmplitudeSyncEnabled.value) target.coerceIn(0.8f, 1.25f) else 1.0f
            
            if (next > current) {
                // Instant rise
                _uiAmplitude.value = next
            } else {
                // Downward smoothing
                _uiAmplitude.value = current * 0.88f + next * 0.12f
            }
        }
    }



    fun setFftStateEmpty() {
        manualDecayFft.fill(0f)
        _fftState.value = floatArrayOf()
        
        _hapticAmplitude.value = 0f
        _hapticMotorIntensity.value = 0f
        _flashlightAmplitude.value = 0f
        _flashlightMotorIntensity.value = 0f
        _uiAmplitude.value = 1.0f
        smoothedHapticAmplitude = 0f
    }

    fun phoneModelForDevice(device: Int): String {
        return when (device) {
            DeviceProfile.DEVICE_NP1 -> ctx.getString(R.string.device_np1)
            DeviceProfile.DEVICE_NP2 -> ctx.getString(R.string.device_np2)
            DeviceProfile.DEVICE_NP2A -> ctx.getString(R.string.device_np2a)
            DeviceProfile.DEVICE_NP3A -> ctx.getString(R.string.device_np3a)
            DeviceProfile.DEVICE_NP4A -> ctx.getString(R.string.device_np4a)
            DeviceProfile.DEVICE_NP4APRO -> ctx.getString(R.string.device_np4apro)
            DeviceProfile.DEVICE_NP3 -> ctx.getString(R.string.device_np3)
            DeviceProfile.DEVICE_NP4B -> ctx.getString(R.string.device_np4b)
            else -> ctx.getString(R.string.device_unknown)
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
                if (_configUpdateStatus.value !is ConfigUpdateStatus.Updating) {
                    withContext(Dispatchers.Main) {
                        updateZonesConfig()
                    }
                }
            }
        }
    }

    fun refreshPresetsInternal() {
        if (selectedDevice.value == DeviceProfile.DEVICE_UNKNOWN || !_glyphsEnabled.value) {
            _configVersion.value = "Disabled"
            _presetInfos.value = emptyList()
            return
        }
        try {
            val json = AudioCaptureService.loadZonesConfigText(ctx)
            if (json != null) {
                try {
                    val root = JSONObject(json)
                    val version = root.optString("version", "Unknown")
                    _configVersion.value = version
                    
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

    init {
        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        
        // Stats and Basic settings
        _favoritePresets.value = prefs.getStringSet("favorite_presets", emptySet()) ?: emptySet()
        val savedSource = prefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
        _captureSource.value = safeValueOf(savedSource, AudioCaptureService.CaptureSource.INTERNAL)
        _uiAmplitudeSyncEnabled.value = prefs.getBoolean("ui_amplitude_sync_enabled", true)
        _totalVisualizedTime.value = prefs.getLong("total_visualized_time", 0L)
        _totalIdleTime.value = prefs.getLong("total_idle_time", 0L)
        _totalActiveTime.value = prefs.getLong("total_active_time", 0L)
        _totalGlyphTime.value = prefs.getLong("total_glyph_time", 0L)
        _totalHapticTime.value = prefs.getLong("total_haptic_time", 0L)
        _totalFlashlightTime.value = prefs.getLong("total_flashlight_time", 0L)
        _spoofLocale.value = prefs.getString("spoof_locale", null)
        val initialLocales = if (_spoofLocale.value == null) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(_spoofLocale.value)
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(initialLocales)

        // General settings
        _developerModeEnabled.value = prefs.getBoolean("developer_mode_v2", false)
        _spoofedDevice.value = prefs.getInt("spoofed_device", DeviceProfile.DEVICE_NP1)
        _autoDeviceMemorize.value = prefs.getBoolean("auto_device_memorize", true)
        _m3eEnabled.value = prefs.getBoolean("m3e_enabled", true)
        _gammaValue.value = prefs.getFloat("gamma_value", 2.2f)
        _spectrumGain.value = prefs.getFloat("spectrum_gain", 4.0f)
        _maxBrightness.value = prefs.getInt("max_brightness", 4095).coerceIn(50, 5000)
        _fftReadMethod.value = safeValueOf(prefs.getString("fft_read_method", null), AudioProcessor.ReadMethod.MAX)
        _glyphsEnabled.value = prefs.getBoolean("glyphs_enabled", true)
        _selectedPreset.value = prefs.getString("selected_preset", "Default") ?: "Default"
        _selectedTheme.value = prefs.getString("selected_theme", "Default") ?: "Default"
        _selectedFont.value = prefs.getString("selected_font", "NDot") ?: "NDot"
        _broadcastEnabled.value = prefs.getBoolean("broadcast_enabled", false)

        // Haptics settings
        _hapticMotorEnabled.value = prefs.getBoolean("haptic_motor_enabled", false)
        _hapticMode.value = safeValueOf(prefs.getString("haptic_mode", null), HapticMode.BASS_TO_AMPLITUDE)
        _hapticFreqMin.value = prefs.getInt("haptic_freq_min", 20).toFloat()
        _hapticFreqMax.value = prefs.getInt("haptic_freq_max", 250).toFloat()
        _hapticMultiplier.value = prefs.getFloat("haptic_multiplier", 1.0f)
        _hapticAudioGain.value = prefs.getFloat("haptic_audio_gain", 1.0f)
        _hapticGamma.value = prefs.getFloat("haptic_gamma", 2.0f)
        _hapticBeatSensitivity.value = prefs.getFloat("haptic_beat_sensitivity", 1.5f)
        _hapticBeatGamma.value = prefs.getFloat("haptic_beat_gamma", 8.0f)

        val defaultHapticEngineMode = if (hasAmplitudeControl) BeatEngineMode.SMOOTH else BeatEngineMode.SHORT_PULSE
        _hapticBeatEngineMode.value = safeValueOf(prefs.getString("haptic_beat_engine_mode", null), defaultHapticEngineMode)
        _hapticPulseDurationMs.value = prefs.getInt("haptic_pulse_duration_ms", 40)

        // Flashlight settings
        _flashlightEnabled.value = prefs.getBoolean("flashlight_enabled", false)
        _flashlightMode.value = safeValueOf(prefs.getString("flashlight_mode", null), TorchMode.AMPLITUDE)
        _flashlightFreqMin.value = prefs.getInt("flashlight_freq_min", 20).toFloat()
        _flashlightFreqMax.value = prefs.getInt("flashlight_freq_max", 250).toFloat()
        _flashlightThreshold.value = prefs.getFloat("flashlight_threshold", 0.15f)
        _flashlightBeatSensitivity.value = prefs.getFloat("flashlight_beat_sensitivity", 1.5f)
        
        val defaultFlashlightEngineMode = if (flashlightIntensityLevels.value > 1) BeatEngineMode.SMOOTH else BeatEngineMode.SHORT_PULSE
        _flashlightBeatEngineMode.value = safeValueOf(prefs.getString("flashlight_beat_engine_mode", null), defaultFlashlightEngineMode)
        _flashlightPulseDurationMs.value = prefs.getInt("flashlight_pulse_duration_ms", 40)

        // Overlay and other visual settings
        _idleBreathingEnabled.value = prefs.getBoolean("idle_breathing_enabled", false)
        _idlePattern.value = prefs.getString("idle_pattern", "pulse") ?: "pulse"
        _idleBrightness.value = prefs.getFloat("idle_brightness", 0.4f)
        _idleBackgroundBrightness.value = prefs.getFloat("idle_background_brightness", 0.02f)
        _overlayEnabled.value = prefs.getBoolean("overlay_enabled", false)
        _overlayTopEnabled.value = prefs.getBoolean("overlay_top_enabled", true)
        _overlayBottomEnabled.value = prefs.getBoolean("overlay_bottom_enabled", false)
        _overlayWidth.value = prefs.getInt("overlay_width", 120)
        _overlayHeight.value = prefs.getInt("overlay_height", 12)
        _overlayHeightBottom.value = prefs.getInt("overlay_height_bottom", 12)
        _tabletTabWidth.value = prefs.getInt("tablet_tab_width", 0)
        _overlayYOffset.value = prefs.getInt("overlay_y_offset", 2)
        _overlaySensitivity.value = prefs.getFloat("overlay_sensitivity", 1.0f)
        _overlaySensitivityBottom.value = prefs.getFloat("overlay_sensitivity_bottom", 1.0f)
        _overlayColor.value = Color(prefs.getInt("overlay_color", Color.White.toArgb()))
        _edgeVisualizerEnabled.value = prefs.getBoolean("edge_visualizer_enabled", false)
        _edgeThickness.value = prefs.getInt("edge_thickness", 12)
        _edgeSensitivity.value = prefs.getFloat("edge_sensitivity", 1.0f)
        _edgeBarCountHoriz.value = prefs.getInt("edge_bar_count_horiz", 20)
        _edgeBarCountVert.value = prefs.getInt("edge_bar_count_vert", 40)
        _edgeCornerRadius.value = prefs.getFloat("edge_corner_radius", 2f)
        _edgeTopEnabled.value = prefs.getBoolean("edge_top_enabled", true)
        _edgeBottomEnabled.value = prefs.getBoolean("edge_bottom_enabled", true)
        _edgeColor.value = Color(prefs.getInt("edge_color", Color.White.toArgb()))

        _lensVisualizerEnabled.value = prefs.getBoolean("lens_visualizer_enabled", false)
        _lensVisualizerRadius.value = prefs.getFloat("lens_visualizer_radius", 16f)
        _lensVisualizerX.value = prefs.getFloat("lens_visualizer_x", 0.50f)
        _lensVisualizerY.value = prefs.getFloat("lens_visualizer_y", 0.03f)
        _lensVisualizerBarWidth.value = prefs.getFloat("lens_visualizer_bar_width", 1f)
        _lensVisualizerMaxHeight.value = prefs.getFloat("lens_visualizer_max_height", 5f)
        _lensVisualizerBarCount.value = prefs.getInt("lens_visualizer_bar_count", 35)
        _lensVisualizerSensitivity.value = prefs.getFloat("lens_visualizer_sensitivity", 0.32f)
        _lensColor.value = Color(prefs.getInt("lens_color", Color.White.toArgb()))

        _alternateGlyphVizEnabled.value = prefs.getBoolean("alternate_glyph_viz_enabled", false)
        _highQualityAnalysis.value = prefs.getBoolean("high_quality_analysis", false)
        _onScreenVisualizersEnabled.value = prefs.getBoolean("on_screen_visualizers_enabled", false)

        _isFirstTime.value = prefs.getBoolean("first_time_v2", true)

        // Launch background tasks
        viewModelScope.launch {
            var lastUpdate = SystemClock.elapsedRealtime()
            while (true) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastUpdate
                lastUpdate = now

                if (_runningState.value) {
                    _totalVisualizedTime.value += delta
                    val service = MainActivity.serviceStatic
                    val hasActivity = if (service != null) {
                        service.latestMagnitudes.any { it > 4 }
                    } else {
                        _fftState.value.any { it > 0.001f }
                    }
                    if (hasActivity) {
                        _totalActiveTime.value += delta
                        if (_hapticMotorEnabled.value) _totalHapticTime.value += delta
                        if (_flashlightEnabled.value) _totalFlashlightTime.value += delta
                        if (_glyphsEnabled.value && _maxBrightness.value > 0) _totalGlyphTime.value += delta
                    } else {
                        _totalIdleTime.value += delta
                    }
                    if (SystemClock.elapsedRealtime() % 5000 < 1100) saveStatsLocally()
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

        // Final initialization calls
        reloadFlashlightSpeedForLevels()
        updateSelectedDevice()
        refreshPresets()
    }

    override fun onCleared() {
        super.onCleared()
        saveStatsLocally()
    }
}
