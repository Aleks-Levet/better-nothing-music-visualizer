package com.better.nothing.music.vizualizer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.AudioScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.FlashlightScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.GlyphsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.HapticsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.HostSelectionSheet
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.SettingsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.VisualsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    private var service: AudioCaptureService? = null
    private var bound = false
    private var pendingResultCode = 0
    private var pendingData: Intent? = null
    private var pendingVisualizerStart = false

    companion object {
        const val EXTRA_REQUEST_START = "request_start"
        var serviceStatic: AudioCaptureService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshConnectedAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshConnectedAudioRoute()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as AudioCaptureService.LocalBinder
            service = localBinder.service
            serviceStatic = service
            bound = true

            applyServiceSettings()

            lifecycleScope.launch {
                service?.isRunningFlow()?.collect { running ->
                    viewModel.setRunning(running)
                }
            }

            if (pendingVisualizerStart) {
                val isTrampoline = intent.getBooleanExtra(EXTRA_REQUEST_START, false)
                if (pendingData != null) {
                    service?.startCapture(pendingResultCode, pendingData!!)
                    pendingData = null
                    pendingResultCode = 0
                    if (isTrampoline) finish()
                } else {
                    if (isTrampoline) {
                        toggleVisualizer()
                    } else {
                        service?.startVisualizer()
                    }
                }
                pendingVisualizerStart = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            serviceStatic = null
            bound = false
        }
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            deliverProjectionToken(result.resultCode, result.data!!)
        } else {
            if (intent.getBooleanExtra(EXTRA_REQUEST_START, false)) {
                AudioCaptureService.requestTileRefresh(this)
                finish()
            }
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            toggleVisualizer()
        } else {
            Toast.makeText(this, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
            if (intent.getBooleanExtra(EXTRA_REQUEST_START, false)) {
                AudioCaptureService.requestTileRefresh(this)
                finish()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Proceed regardless of whether permission was granted or denied
        toggleVisualizer(skipNotificationCheck = true)
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            // We don't know which one was requested, so we enable based on intent if possible, 
            // but for simplicity let's just assume overlay enabled if they granted it.
            viewModel.setOverlayEnabled(true)
        } else {
            Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTrampoline = intent.getBooleanExtra(EXTRA_REQUEST_START, false)
        if (isTrampoline) {
            setTheme(R.style.Theme_Transparent)
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
        super.onCreate(savedInstanceState)
        
        if (isTrampoline) {
            pendingVisualizerStart = true
            // If we are already running and requested to start, it's a toggle to stop
            if (AudioCaptureService.isRunning()) {
                val intentStop = AudioCaptureService.createStopIntent(this)
                startService(intentStop)
                finish()
                return
            }
        }


        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        val intentService = Intent(this, AudioCaptureService::class.java)
        bindService(intentService, serviceConnection, BIND_AUTO_CREATE)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        setContent {
            if (isTrampoline) return@setContent

            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
            val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
            val m3eEnabled by viewModel.m3eEnabled.collectAsStateWithLifecycle()
            val musicThemeColor by viewModel.musicThemeColor.collectAsStateWithLifecycle()
            val isRunning by viewModel.runningState.collectAsStateWithLifecycle()

            LaunchedEffect(isRunning) {
                if (isRunning) {
                    while (true) {
                        val s = service ?: serviceStatic
                        if (s != null) {
                            val lightState = s.currentLightState
                            if (lightState != null && lightState.isNotEmpty()) {
                                viewModel.setVisualizerState(lightState.copyOf())
                            }
                            val raw = s.latestRawFFT
                            if (raw != null) {
                                viewModel.setFftData(raw)
                            }
                        }
                        delay(16.milliseconds)
                    }
                } else {
                    viewModel.setFftStateEmpty()
                    viewModel.setVisualizerState(floatArrayOf())
                }
            }

            BetterVizTheme(
                themeName = selectedTheme,
                fontName = selectedFont,
                m3eEnabled = m3eEnabled,
                musicPrimaryColor = musicThemeColor,
            ) {
                val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
                val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
                val isShowingStats by viewModel.isShowingStats.collectAsStateWithLifecycle()
                val isShowingHostPicker by viewModel.isShowingHostPicker.collectAsStateWithLifecycle()
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

                val shouldIntercept = isShowingAbout || isShowingLicense || isShowingStats || isShowingHostPicker || selectedTab != Tab.Audio

                var backProgress by remember { mutableStateOf(0f) }

                PredictiveBackHandler(enabled = shouldIntercept) { progress ->
                    try {
                        progress.collect { backEvent ->
                            backProgress = backEvent.progress
                        }
                        // If we get here, the gesture was completed
                        viewModel.navigateBack()
                    } catch (e: Exception) {
                        // Gesture cancelled
                    } finally {
                        backProgress = 0f
                    }
                }

                val isOverlayVisible = isShowingAbout || isShowingLicense || isShowingStats || isShowingHostPicker

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (backProgress > 0f && !isOverlayVisible) {
                                val scale = 1f - (backProgress * 0.05f)
                                scaleX = scale
                                scaleY = scale
                                clip = true
                                shape = RoundedCornerShape((backProgress * 24).dp)
                            }
                        }
                ) {
                    BetterVizApp(
                        viewModel = viewModel,
                        onToggleVisualizer = { toggleVisualizer() },
                        onOverlayPermissionRequest = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                            overlayPermissionLauncher.launch(intent)
                        },
                        backProgress = if (isOverlayVisible) backProgress else 0f
                    )
                }

                if (isShowingHostPicker) {
                    HostSelectionSheet(
                        viewModel = viewModel,
                        onDismiss = { viewModel.hideHostPicker() },
                        onHostSelected = { host ->
                            viewModel.connectToHost(host)
                            viewModel.hideHostPicker()
                            // Also start the visualizer after selection
                            toggleVisualizer(forceStart = true)
                        }
                    )
                }

            }
        }
    }

    private fun toggleVisualizer(forceStart: Boolean = false, skipNotificationCheck: Boolean = false) {
        val s = service ?: return
        val isTrampoline = intent.getBooleanExtra(EXTRA_REQUEST_START, false)
        if (s.isVisualizerRunning && !forceStart) {
            s.stopVisualizer()
            if (isTrampoline) finish()
        } else {
            // Check for notification permission on Android 13+ if not skipped
            if (!skipNotificationCheck && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }

            val source = if (isTrampoline) {
                val prefs = getSharedPreferences("viz_prefs", MODE_PRIVATE)
                val saved = prefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
                try {
                    AudioCaptureService.CaptureSource.valueOf(saved ?: AudioCaptureService.CaptureSource.INTERNAL.name)
                } catch (e: Exception) {
                    AudioCaptureService.CaptureSource.INTERNAL
                }
            } else {
                viewModel.captureSource.value
            }

            when (source) {
                AudioCaptureService.CaptureSource.INTERNAL -> {
                    startForegroundService(Intent(this, AudioCaptureService::class.java))
                    launchProjection()
                }

                AudioCaptureService.CaptureSource.MIC -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startForegroundService(Intent(this, AudioCaptureService::class.java))
                        s.startVisualizer()
                        if (isTrampoline) finish()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                AudioCaptureService.CaptureSource.VIZUALIZER -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startForegroundService(Intent(this, AudioCaptureService::class.java))
                        s.startVisualizer()
                        if (isTrampoline) finish()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                AudioCaptureService.CaptureSource.NETWORK -> {
                    if (!isTrampoline && !forceStart) {
                        viewModel.showHostPicker()
                    } else {
                        startForegroundService(Intent(this, AudioCaptureService::class.java))
                        s.startVisualizer()
                        if (isTrampoline) finish()
                    }
                }
            }
        }
    }

    private fun launchProjection() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun deliverProjectionToken(resultCode: Int, data: Intent) {
        val s = service
        if (s != null) {
            s.startCapture(resultCode, data)
            if (intent.getBooleanExtra(EXTRA_REQUEST_START, false)) {
                finish()
            }
        } else {
            pendingResultCode = resultCode
            pendingData = data
            pendingVisualizerStart = true

            val intent = Intent(this, AudioCaptureService::class.java)
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun applyServiceSettings() {
        service?.let {
            it.setDevice(viewModel.selectedDevice.value)
            it.setCaptureSource(viewModel.captureSource.value)
            it.setLatencyMs(viewModel.latencyMs.value)
            it.setGamma(viewModel.gammaValue.value)
            it.setSpectrumGain(viewModel.spectrumGain.value)
            it.setMaxBrightness(if (viewModel.glyphsEnabled.value) viewModel.maxBrightness.value else 0)
            it.setSelectedPreset(viewModel.selectedPreset.value)
            it.setHapticMotorEnabled(viewModel.hapticMotorEnabled.value)
            it.setHapticMode(viewModel.hapticMode.value)
            it.setHapticBeatEngineMode(viewModel.hapticBeatEngineMode.value)
            it.setHapticPulseDurationMs(viewModel.hapticPulseDurationMs.value)
            it.setFlashlightEnabled(viewModel.flashlightEnabled.value)
            it.setFlashlightMode(viewModel.flashlightMode.value)
            it.setFlashlightBeatEngineMode(viewModel.flashlightBeatEngineMode.value)
            it.setFlashlightPulseDurationMs(viewModel.flashlightPulseDurationMs.value)
            viewModel.setFlashlightIntensityLevels(it.flashlightIntensityLevels)
            it.setIdleBreathingEnabled(viewModel.idleBreathingEnabled.value)
            it.setIdlePattern(viewModel.idlePattern.value)
            it.setDisableGlyphsWhenSilent(viewModel.disableGlyphsWhenSilent.value)
            it.setLensVisualizerEnabled(viewModel.lensVisualizerEnabled.value)
            it.setLensVisualizerRadius(viewModel.lensVisualizerRadius.value)
            it.setLensVisualizerX(viewModel.lensVisualizerX.value)
            it.setLensVisualizerY(viewModel.lensVisualizerY.value)
            it.setLensVisualizerBarWidth(viewModel.lensVisualizerBarWidth.value)
            it.setLensVisualizerMaxHeight(viewModel.lensVisualizerMaxHeight.value)
            it.setLensVisualizerBarCount(viewModel.lensVisualizerBarCount.value)
            it.setLensVisualizerSensitivity(viewModel.lensVisualizerSensitivity.value)
            
            it.setOverlayEnabled(viewModel.overlayEnabled.value)
            it.setOverlayTopEnabled(viewModel.overlayTopEnabled.value)
            it.setOverlayBottomEnabled(viewModel.overlayBottomEnabled.value)
            it.setOverlayWidth(viewModel.overlayWidth.value)
            it.setOverlayHeight(viewModel.overlayHeight.value)
            it.setOverlayHeightBottom(viewModel.overlayHeightBottom.value)
            it.setOverlayYOffset(viewModel.overlayYOffset.value)
            it.setOverlaySensitivity(viewModel.overlaySensitivity.value)
            it.setOverlaySensitivityBottom(viewModel.overlaySensitivityBottom.value)
            
            it.setEdgeVisualizerEnabled(viewModel.edgeVisualizerEnabled.value)
            it.setEdgeThickness(viewModel.edgeThickness.value)
            it.setEdgeSensitivity(viewModel.edgeSensitivity.value)
            it.setEdgeBarCounts(viewModel.edgeBarCountHoriz.value, viewModel.edgeBarCountVert.value)
            it.setEdgeCornerRadius(viewModel.edgeCornerRadius.value)
            it.setEdgeTopEnabled(viewModel.edgeTopEnabled.value)
            it.setEdgeBottomEnabled(viewModel.edgeBottomEnabled.value)
        }
    }

    private fun refreshConnectedAudioRoute() {
        val route = resolvePreferredAudioRoute()
        if (route != null) {
            serviceStatic?.setAudioRoute(route)
            if (viewModel.autoDeviceMemorize.value) {
                viewModel.reloadLatencyForCurrentRoute()
            }
        }
    }

    private fun resolvePreferredAudioRoute(): AudioRoute? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var preferred: AudioDeviceInfo? = null
        for (device in outputs) {
            if (device.isBluetoothOutput()) {
                preferred = device
                break
            }
        }
        if (preferred == null) {
            for (device in outputs) {
                if (device.isWiredOutput()) {
                    preferred = device
                    break
                }
            }
        }
        if (preferred == null) {
            for (device in outputs) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    preferred = device
                    break
                }
            }
        }
        return preferred?.toAudioRoute()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_START, false)) {
            if (bound) {
                toggleVisualizer()
            } else {
                pendingVisualizerStart = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    override fun finish() {
        super.finish()
        if (intent.getBooleanExtra(EXTRA_REQUEST_START, false)) {
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }
}

fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER || type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        } else {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    } else {
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
    }
}

fun AudioDeviceInfo.isWiredOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET
}

fun AudioDeviceInfo.toAudioRoute(): AudioRoute {
    val name = if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) MainViewModel.instance?.ctx?.getString(R.string.internal_speaker) ?: "Internal Speaker" else productName.toString()
    return AudioRoute(type.toString() + "_" + name, name)
}

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BetterVizApp(
    viewModel: MainViewModel,
    onToggleVisualizer: () -> Unit,
    onOverlayPermissionRequest: () -> Unit,
    backProgress: Float = 0f
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isRunning by viewModel.runningState.collectAsStateWithLifecycle()
    val totalVisualizedTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val developerModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()

    val glyphsEnabled by viewModel.glyphsEnabled.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticMotorEnabled.collectAsStateWithLifecycle()
    val visualsEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
    val edgeEnabled by viewModel.edgeVisualizerEnabled.collectAsStateWithLifecycle()
    val lensEnabled by viewModel.lensVisualizerEnabled.collectAsStateWithLifecycle()
    val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()

    val config = LocalConfiguration.current
    val isTablet = config.smallestScreenWidthDp >= 600 && config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    val visibleTabs = remember(selectedDevice, glyphsEnabled, hapticsEnabled, visualsEnabled, edgeEnabled, lensEnabled, flashlightEnabled, isTablet) {
        var tabs = Tab.entries.toList()

        if (!isTablet) {
            if (selectedDevice == com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_UNKNOWN || !glyphsEnabled) {
                tabs = tabs.filter { it != Tab.Glyphs }
            }
            if (!viewModel.hasHapticMotor || !hapticsEnabled) {
                tabs = tabs.filter { it != Tab.Haptics }
            }
            if (!viewModel.hasFlashlight || !flashlightEnabled) {
                tabs = tabs.filter { it != Tab.Flashlight }
            }
        } else {
            // On tablet, we show supported features even if disabled (placeholders)
            if (selectedDevice == com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_UNKNOWN) {
                tabs = tabs.filter { it != Tab.Glyphs }
            }
            if (!viewModel.hasHapticMotor) {
                tabs = tabs.filter { it != Tab.Haptics }
            }
            if (!viewModel.hasFlashlight) {
                tabs = tabs.filter { it != Tab.Flashlight }
            }
        }

        if (!visualsEnabled && !edgeEnabled && !lensEnabled) {
            tabs = tabs.filter { it != Tab.Visuals }
        }
        tabs
    }

    val pagerState = rememberPagerState(initialPage = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)) { visibleTabs.size }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // Sync pager when selectedTab changes (e.g. from bottom bar)
    LaunchedEffect(selectedTab) {
        val target = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            isProgrammaticScroll = true
            try {
                pagerState.scrollToPage(target)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    // CRITICAL: Handle dynamic tab visibility changes to prevent jumping
    LaunchedEffect(visibleTabs) {
        val targetIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
        if (pagerState.currentPage != targetIndex) {
            isProgrammaticScroll = true
            try {
                pagerState.scrollToPage(targetIndex)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val tabletTabWidth by viewModel.tabletTabWidth.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (!isTablet) {
                NativeBottomBar(
                    selectedTab = selectedTab,
                    visibleTabs = visibleTabs,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }
        },
        floatingActionButton = {
            StartStopButton(running = isRunning, onClick = onToggleVisualizer)
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        if (isTablet) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .horizontalScroll(scrollState)
            ) {
                visibleTabs.forEachIndexed { index, tab ->
                    key(tab) {
                        val isTabEnabled = when (tab) {
                            Tab.Glyphs -> glyphsEnabled
                            Tab.Haptics -> hapticsEnabled
                            Tab.Flashlight -> flashlightEnabled
                            else -> true
                        }

                        val tabModifier = if (tabletTabWidth > 0) {
                            Modifier.width(tabletTabWidth.dp)
                        } else {
                            Modifier.weight(1f).widthIn(min = 500.dp)
                        }

                        Box(
                            modifier = tabModifier
                                .fillMaxHeight()
                        ) {
                            if (isTabEnabled) {
                                TabContent(
                                    tab,
                                    viewModel,
                                    isRunning,
                                    totalVisualizedTime,
                                    developerModeEnabled,
                                    glyphsEnabled,
                                    hapticsEnabled,
                                    flashlightEnabled,
                                    visualsEnabled,
                                    onOverlayPermissionRequest,
                                    PaddingValues(0.dp),
                                    isTablet = true
                                )
                            } else {
                                DisabledFeaturePlaceholder(tab)
                            }
                        }
                    }
                    if (index < visibleTabs.size - 1) {
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 6,
                userScrollEnabled = true,
                key = { page -> if (page < visibleTabs.size) visibleTabs[page].name else page }
            ) { page ->
                if (page >= visibleTabs.size) return@HorizontalPager
                val tab = visibleTabs[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            val absOffset = pageOffset.coerceIn(-1f, 1f).let { kotlin.math.abs(it) }
                            val fraction = 1f - absOffset

                            val scale = 0.85f + (1f - 0.85f) * fraction
                            scaleX = scale
                            scaleY = scale
                            alpha = fraction

                            val maxRotation = 8f
                            val rotationAmount = maxRotation * (1f - fraction)

                            rotationZ = if (pageOffset > 0) -rotationAmount else rotationAmount
                        }
                ) {
                    TabContent(tab, viewModel, isRunning, totalVisualizedTime, developerModeEnabled, glyphsEnabled, hapticsEnabled, flashlightEnabled, visualsEnabled, onOverlayPermissionRequest, padding, isTablet = false)
                }
            }
        }
        MainOverlays(
            viewModel = viewModel,
            selectedDevice = selectedDevice,
            isTablet = isTablet,
            visibleTabCount = visibleTabs.size,
            padding = padding,
            backProgress = backProgress
        )
    }
}

@Composable
private fun TabContent(
    tab: Tab,
    viewModel: MainViewModel,
    isRunning: Boolean,
    totalVisualizedTime: Long,
    developerModeEnabled: Boolean,
    glyphsEnabled: Boolean,
    hapticsEnabled: Boolean,
    flashlightEnabled: Boolean,
    visualsEnabled: Boolean,
    onOverlayPermissionRequest: () -> Unit,
    padding: PaddingValues,
    isTablet: Boolean = false
) {
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    when (tab) {
        Tab.Audio -> {
            val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
            val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
            val autoDeviceEnabled by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
            val fftRaw by viewModel.fftState.collectAsStateWithLifecycle()
            val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()

            val connectedClients by viewModel.connectedClients.collectAsStateWithLifecycle()

            AudioScreen(
                isRunning = isRunning,
                sessionDuration = totalVisualizedTime,
                latencyMs = latencyMs,
                onLatencyChanged = { viewModel.setLatencyMs(it) },
                latencyPresets = latencyPresets,
                onLatencyPresetsChanged = { viewModel.updateLatencyPresets(it) },
                autoDeviceEnabled = autoDeviceEnabled,
                onAutoDeviceToggle = { viewModel.setAutoDeviceMemorize(it) },
                connectedDeviceName = MainActivity.serviceStatic?.getActiveAudioRouteName()
                    ?: "Unknown",
                viewModel = viewModel,
                fftRaw = fftRaw,
                captureSource = captureSource,
                onCaptureSourceChanged = { viewModel.setCaptureSource(it) },
                glyphsEnabled = glyphsEnabled,
                onGlyphsEnabledChanged = { viewModel.setGlyphsEnabled(it) },
                hapticsEnabled = hapticsEnabled,
                onHapticsEnabledChanged = { viewModel.setHapticMotorEnabled(it) },
                flashlightEnabled = flashlightEnabled,
                onFlashlightEnabledChanged = { viewModel.setFlashlightEnabled(it) },
                broadcastEnabled = viewModel.broadcastEnabled.collectAsStateWithLifecycle().value,
                onBroadcastEnabledChanged = { viewModel.setBroadcastEnabled(it) },
                connectedClients = connectedClients,
                developerModeEnabled = developerModeEnabled,
                isGlyphAvailable = selectedDevice != com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_UNKNOWN,
                hasHapticMotor = viewModel.hasHapticMotor,
                hasFlashlight = viewModel.hasFlashlight,
                padding = padding
            )
        }
        Tab.Glyphs -> {
            val gammaValue by viewModel.gammaValue.collectAsStateWithLifecycle()
            val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
            val presets by viewModel.presetInfos.collectAsStateWithLifecycle()
            val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()

            GlyphsScreen(
                gammaValue = gammaValue,
                onGammaChanged = {
                    viewModel.setGammaValue(it); viewModel.persistGamma(
                    it
                )
                },
                maxBrightness = maxBrightness,
                onMaxBrightnessChanged = { viewModel.setMaxBrightness(it) },
                presets = presets,
                selectedPreset = selectedPreset,
                onPresetSelected = { viewModel.setSelectedPreset(it) },
                isRunning = isRunning,
                selectedDevice = selectedDevice,
                viewModel = viewModel,
                padding = padding
            )
        }
        Tab.Visuals -> {
            VisualsScreen(
                viewModel = viewModel,
                overlayEnabled = visualsEnabled,
                onOverlayEnabledChanged = { viewModel.setOverlayEnabled(it) },
                onOverlayPermissionRequest = { onOverlayPermissionRequest() },
                padding = padding
            )
        }
        Tab.Haptics -> {
            val hapticMotorEnabled by viewModel.hapticMotorEnabled.collectAsStateWithLifecycle()
            val hapticMode by viewModel.hapticMode.collectAsStateWithLifecycle()
            val hapticFreqMin by viewModel.hapticFreqMin.collectAsStateWithLifecycle()
            val hapticFreqMax by viewModel.hapticFreqMax.collectAsStateWithLifecycle()
            val hapticMultiplier by viewModel.hapticMultiplier.collectAsStateWithLifecycle()
            val hapticAudioGain by viewModel.hapticAudioGain.collectAsStateWithLifecycle()
            val hapticGamma by viewModel.hapticGamma.collectAsStateWithLifecycle()
            val hapticBeatSensitivity by viewModel.hapticBeatSensitivity.collectAsStateWithLifecycle()
            val hapticBeatGamma by viewModel.hapticBeatGamma.collectAsStateWithLifecycle()
            val isBeatDetected by viewModel.isBeatDetected.collectAsStateWithLifecycle()
            val hapticAmplitude by viewModel.hapticAmplitude.collectAsStateWithLifecycle()

            HapticsScreen(
                hapticMotorEnabled = hapticMotorEnabled,
                onHapticMotorEnabledChanged = {
                    viewModel.setHapticMotorEnabled(
                        it
                    )
                },
                hapticMode = hapticMode,
                onHapticModeChanged = { viewModel.setHapticMode(it) },
                hapticBeatEngineMode = viewModel.hapticBeatEngineMode.collectAsStateWithLifecycle().value,
                onHapticBeatEngineModeChanged = { viewModel.setHapticBeatEngineMode(it) },
                hapticPulseDurationMs = viewModel.hapticPulseDurationMs.collectAsStateWithLifecycle().value,
                onHapticPulseDurationMsChanged = { viewModel.setHapticPulseDurationMs(it) },
                hasAmplitudeControl = viewModel.hasAmplitudeControl,
                hapticFreqMin = hapticFreqMin,
                hapticFreqMax = hapticFreqMax,
                onHapticFreqRangeChanged = { min, max ->
                    viewModel.setHapticFreqRange(
                        min,
                        max
                    )
                },
                hapticMultiplier = hapticMultiplier,
                onHapticMultiplierChanged = { viewModel.setHapticMultiplier(it) },
                hapticAudioGain = hapticAudioGain,
                onHapticAudioGainChanged = { viewModel.setHapticAudioGain(it) },
                hapticGamma = hapticGamma,
                onHapticGammaChanged = { viewModel.setHapticGamma(it) },
                hapticBeatSensitivity = hapticBeatSensitivity,
                onHapticBeatSensitivityChanged = {
                    viewModel.setHapticBeatSensitivity(
                        it
                    )
                },
                hapticBeatGamma = hapticBeatGamma,
                onHapticBeatGammaChanged = { viewModel.setHapticBeatGamma(it) },
                hapticAmplitudeFlow = viewModel.hapticAmplitude,
                isBeatDetectedFlow = viewModel.isBeatDetected,
                padding = padding
            )
        }
        Tab.Flashlight -> {
            val flashlightEnabledInternal by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
            val flashlightMode by viewModel.flashlightMode.collectAsStateWithLifecycle()
            val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
            val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
            val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
            val flashlightSpeedMs by viewModel.flashlightSpeedMs.collectAsStateWithLifecycle()
            val flashlightBeatSensitivity by viewModel.flashlightBeatSensitivity.collectAsStateWithLifecycle()
            val flashlightIntensityLevels by viewModel.flashlightIntensityLevels.collectAsStateWithLifecycle()
            val flashlightLevel by viewModel.flashlightLevel.collectAsStateWithLifecycle()
            val isFlashlightBeatDetected by viewModel.isFlashlightBeatDetected.collectAsStateWithLifecycle()
            val flashlightAmplitude by viewModel.flashlightAmplitude.collectAsStateWithLifecycle()

            FlashlightScreen(
                flashlightEnabled = flashlightEnabledInternal,
                onFlashlightEnabledChanged = { viewModel.setFlashlightEnabled(it) },
                flashlightMode = flashlightMode,
                onFlashlightModeChanged = { viewModel.setFlashlightMode(it) },
                flashlightBeatEngineMode = viewModel.flashlightBeatEngineMode.collectAsStateWithLifecycle().value,
                onFlashlightBeatEngineModeChanged = { viewModel.setFlashlightBeatEngineMode(it) },
                flashlightPulseDurationMs = viewModel.flashlightPulseDurationMs.collectAsStateWithLifecycle().value,
                onFlashlightPulseDurationMsChanged = { viewModel.setFlashlightPulseDurationMs(it) },
                flashlightFreqMin = flashlightFreqMin,
                flashlightFreqMax = flashlightFreqMax,
                onFlashlightFreqRangeChanged = { min, max ->
                    viewModel.setFlashlightFreqRange(
                        min,
                        max
                    )
                },
                flashlightThreshold = flashlightThreshold,
                onFlashlightThresholdChanged = {
                    viewModel.setFlashlightThreshold(
                        it
                    )
                },
                flashlightSpeedMs = flashlightSpeedMs,
                onFlashlightSpeedMsChanged = { viewModel.setFlashlightSpeedMs(it) },
                flashlightBeatSensitivity = flashlightBeatSensitivity,
                onFlashlightBeatSensitivityChanged = {
                    viewModel.setFlashlightBeatSensitivity(
                        it
                    )
                },
                flashlightIntensityLevels = flashlightIntensityLevels,
                flashlightCurrentLevel = flashlightLevel,
                flashlightAmplitudeFlow = viewModel.flashlightAmplitude,
                isBeatDetectedFlow = viewModel.isFlashlightBeatDetected,
                padding = padding
            )
        }
        Tab.Settings -> {
            val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
            val idlePattern by viewModel.idlePattern.collectAsStateWithLifecycle()
            val disableGlyphsWhenSilent by viewModel.disableGlyphsWhenSilent.collectAsStateWithLifecycle()

            SettingsScreen(
                viewModel = viewModel,
                idleBreathingEnabled = idleBreathingEnabled,
                onIdleBreathingEnabledChanged = {
                    viewModel.setIdleBreathingEnabled(
                        it
                    )
                },
                idlePattern = idlePattern,
                onIdlePatternChanged = { viewModel.setIdlePattern(it) },
                disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                onDisableGlyphsWhenSilentChanged = {
                    viewModel.setDisableGlyphsWhenSilent(
                        it
                    )
                },
                padding = padding,
                isTablet = isTablet
            )
        }
    }
}
