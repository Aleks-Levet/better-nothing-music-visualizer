package com.better.nothing.music.vizualizer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.android.material.snackbar.Snackbar
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.projection.MediaProjectionManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.service.GlyphNotificationListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.math.absoluteValue
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.AudioScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.FlashlightScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.GlyphsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.HapticsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.SettingsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.VisualsScreen
import androidx.compose.runtime.collectAsState
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }


    private var service: AudioCaptureService? = null
    private var bound = false
    private var pendingResultCode = 0
    private var pendingData: Intent? = null
    private var hasPendingToken = false
    private var pendingVisualizerStart = false

    private val musicThemeHandler by lazy { MusicThemeHandler(this, viewModel) }

    companion object {
        const val EXTRA_REQUEST_START = "request_start"
        var serviceStatic: AudioCaptureService? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
            service?.startVisualizer()
        }
    }

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

            if (hasPendingToken) {
                deliverProjectionToken(pendingResultCode, pendingData!!)
                hasPendingToken = false
            }

            if (pendingVisualizerStart) {
                service?.startVisualizer()
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
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            toggleVisualizer()
        } else {
            Toast.makeText(this, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            viewModel.setOverlayEnabled(true)
        } else {
            Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                viewModel.linkWithCredential(credential)
            } catch (e: Exception) {
                Log.e("MainActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchGoogleSignIn() {
        try {
            val webClientId = getString(R.string.default_web_client_id)
            if (webClientId.isEmpty()) {
                Toast.makeText(this, "Web Client ID is missing!", Toast.LENGTH_LONG).show()
                return
            }
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to launch Google Sign In", e)
            Toast.makeText(this, "Launcher error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {}

        val intent = Intent(this, AudioCaptureService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        if (isNotificationServiceEnabled()) {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                musicThemeHandler.sessionsChangedListener,
                ComponentName(this, GlyphNotificationListener::class.java)
            )
            musicThemeHandler.updateActiveMediaController()
        }

        setContent {
            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
            val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
            val musicThemeColor by viewModel.musicThemeColor.collectAsStateWithLifecycle()
            val isRunning by viewModel.runningState.collectAsStateWithLifecycle()

            LaunchedEffect(isRunning) {
                if (isRunning) {
                    while (true) {
                        service?.currentLightState?.let {
                            viewModel.setVisualizerState(it)
                        }
                        service?.latestMagnitudes?.let {
                            viewModel.setFftState(it)
                        }
                        delay(33.milliseconds)
                    }
                } else {
                    viewModel.setFftState(floatArrayOf())
                    viewModel.setVisualizerState(floatArrayOf())
                }
            }

            BetterVizTheme(
                themeName = selectedTheme,
                fontName = selectedFont,
                musicPrimaryColor = musicThemeColor,
            ) {
                // Predictive Back Handling
                BackHandler(enabled = true) {
                    if (!viewModel.navigateBack()) {
                        finish()
                    }
                }

                BetterVizApp(
                    viewModel = viewModel,
                    onToggleVisualizer = { toggleVisualizer() },
                    onGoogleSignIn = { launchGoogleSignIn() },
                    onOverlayPermissionRequest = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                        overlayPermissionLauncher.launch(intent)
                    }
                )

                // Overlays
                MainOverlays(viewModel = viewModel, selectedDevice = viewModel.selectedDevice.collectAsState().value)
                CommunityOverlays(viewModel = viewModel)
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) return true
            }
        }
        return false
    }


    private fun toggleVisualizer() {
        val s = service ?: return
        if (s.isVisualizerRunning) {
            s.stopVisualizer()
        } else {
            val intent = Intent(this, AudioCaptureService::class.java)
            startForegroundService(intent)

            val source = viewModel.captureSource.value
            when (source) {
                AudioCaptureService.CaptureSource.INTERNAL -> launchProjection()

                AudioCaptureService.CaptureSource.MIC -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        s.startVisualizer()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                AudioCaptureService.CaptureSource.VIZUALIZER -> s.startVisualizer()
                AudioCaptureService.CaptureSource.SHIZUKU -> startShizukuVisualizer()
            }
        }
    }

    private fun startShizukuVisualizer() {
        val s = service ?: return
        try {
            if (Shizuku.isPreV11()) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                s.startVisualizer()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this, getString(R.string.shizuku_permission_required), Toast.LENGTH_LONG).show()
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show()
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
        } else {
            pendingResultCode = resultCode
            pendingData = data
            hasPendingToken = true
            pendingVisualizerStart = true
            try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {}

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
            it.setMaxBrightness(viewModel.maxBrightness.value)
            it.setSelectedPreset(viewModel.selectedPreset.value)
            it.setHapticMotorEnabled(viewModel.hapticMotorEnabled.value)
            it.setHapticMode(viewModel.hapticMode.value)
            it.setFlashlightEnabled(viewModel.flashlightEnabled.value)
            viewModel.setFlashlightIntensityLevels(it.flashlightIntensityLevels)
            it.setIdleBreathingEnabled(viewModel.idleBreathingEnabled.value)
            it.setIdlePattern(viewModel.idlePattern.value)
            it.setStrobeEnabled(viewModel.strobeEnabled.value)
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

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        musicThemeHandler.onDestroy()
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        if (isNotificationServiceEnabled()) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(musicThemeHandler.sessionsChangedListener)
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
    val name = if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "Internal Speaker" else productName.toString()
    return AudioRoute(type.toString() + "_" + name, name)
}

val HeavyEasingSpec = tween<Float>(durationMillis = 600)


@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BetterVizApp(
    viewModel: MainViewModel,
    onToggleVisualizer: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onOverlayPermissionRequest: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isRunning by viewModel.runningState.collectAsStateWithLifecycle()
    val totalVisualizedTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val visibleTabs = remember(selectedDevice) {
        var tabs = if (selectedDevice == com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_UNKNOWN) {
            Tab.entries.filter { it != Tab.Glyphs }
        } else {
            Tab.entries.toList()
        }
        if (!viewModel.hasHapticMotor) {
            tabs = tabs.filter { it != Tab.Haptics }
        }
        if (!viewModel.hasFlashlight) {
            tabs = tabs.filter { it != Tab.Flashlight }
        }
        tabs
    }

    val pagerState = rememberPagerState(initialPage = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)) { visibleTabs.size }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        val target = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            isProgrammaticScroll = true
            try {
                val steps = (target - pagerState.currentPage).absoluteValue
                // Duration scales with distance but stays within a snappy range
                val duration = (350 + (steps - 1) * 80).coerceAtMost(700)
                
                pagerState.animateScrollToPage(
                    page = target,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = EaseOutCubic
                    )
                )
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page < visibleTabs.size) {
                val tab = visibleTabs[page]
                if (!isProgrammaticScroll && viewModel.selectedTab.value != tab) {
                    viewModel.selectTab(tab)
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NativeBottomBar(
                selectedTab = selectedTab,
                visibleTabs = visibleTabs,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        floatingActionButton = {
            StartStopButton(running = isRunning, onClick = onToggleVisualizer)
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = visibleTabs.size,
            userScrollEnabled = true
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
                when (tab) {
                    Tab.Audio -> {
                        val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
                        val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
                        val autoDeviceEnabled by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
                        val fftData by viewModel.fftState.collectAsStateWithLifecycle()
                        val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
                        val shizukuUnlocked by viewModel.shizukuSourceUnlocked.collectAsStateWithLifecycle()
                        val latencyWizardState by viewModel.latencyWizardState.collectAsStateWithLifecycle()

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
                            fftData = fftData,
                            captureSource = captureSource,
                            onCaptureSourceChanged = { viewModel.setCaptureSource(it) },
                            shizukuUnlocked = shizukuUnlocked,
                            latencyWizardState = latencyWizardState,
                            onRunLatencyWizard = { viewModel.runLatencyWizard() },
                            onResetLatencyWizard = { viewModel.resetLatencyWizard() },
                            padding = padding
                        )
                    }
                    Tab.Glyphs -> {
                        val gammaValue by viewModel.gammaValue.collectAsStateWithLifecycle()
                        val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
                        val presets by viewModel.presetInfos.collectAsStateWithLifecycle()
                        val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
                        val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

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
                        val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
                        VisualsScreen(
                            viewModel = viewModel,
                            overlayEnabled = overlayEnabled,
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

                        HapticsScreen(
                            hapticMotorEnabled = hapticMotorEnabled,
                            onHapticMotorEnabledChanged = {
                                viewModel.setHapticMotorEnabled(
                                    it
                                )
                            },
                            hapticMode = hapticMode,
                            onHapticModeChanged = { viewModel.setHapticMode(it) },
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
                            hapticAmplitudeProvider = { viewModel.hapticAmplitude.value },
                            isBeatDetectedProvider = { isBeatDetected },
                            padding = padding
                        )
                    }
                    Tab.Flashlight -> {
                        val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
                        val flashlightMode by viewModel.flashlightMode.collectAsStateWithLifecycle()
                        val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
                        val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
                        val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
                        val flashlightSpeedMs by viewModel.flashlightSpeedMs.collectAsStateWithLifecycle()
                        val flashlightBeatSensitivity by viewModel.flashlightBeatSensitivity.collectAsStateWithLifecycle()
                        val flashlightIntensityLevels by viewModel.flashlightIntensityLevels.collectAsStateWithLifecycle()
                        val flashlightLevel by viewModel.flashlightLevel.collectAsStateWithLifecycle()
                        val isFlashlightBeatDetected by viewModel.isFlashlightBeatDetected.collectAsStateWithLifecycle()

                        FlashlightScreen(
                            flashlightEnabled = flashlightEnabled,
                            onFlashlightEnabledChanged = { viewModel.setFlashlightEnabled(it) },
                            flashlightMode = flashlightMode,
                            onFlashlightModeChanged = { viewModel.setFlashlightMode(it) },
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
                            flashlightAmplitudeProvider = { viewModel.flashlightAmplitude.value },
                            isBeatDetectedProvider = { isFlashlightBeatDetected },
                            padding = padding
                        )
                    }
                    Tab.Settings -> {
                        val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
                        val idlePattern by viewModel.idlePattern.collectAsStateWithLifecycle()
                        val strobeEnabled by viewModel.strobeEnabled.collectAsStateWithLifecycle()
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
                            strobeEnabled = strobeEnabled,
                            onStrobeEnabledChanged = { viewModel.setStrobeEnabled(it) },
                            disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                            onDisableGlyphsWhenSilentChanged = {
                                viewModel.setDisableGlyphsWhenSilent(
                                    it
                                )
                            },
                            onGoogleSignIn = onGoogleSignIn,
                            padding = padding
                        )
                    }
                }
            }
        }
    }
}
