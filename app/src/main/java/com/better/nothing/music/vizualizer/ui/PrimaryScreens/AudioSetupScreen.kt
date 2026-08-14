package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.Manifest
import android.R.attr.width
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontVariation.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.logic.AudioProcessor
import com.better.nothing.music.vizualizer.logic.UdpNetworkSync
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.OptionTile
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpandableExpressiveCard
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import java.net.InetAddress
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    viewModel: MainViewModel,
    isRunning: Boolean,
    sessionDuration: Long = 0L,
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
    autoDeviceEnabled: Boolean,
    onAutoDeviceToggle: (Boolean) -> Unit,
    connectedDeviceName: String? = null,
    fftRaw: FloatArray = floatArrayOf(),
    captureSource: AudioCaptureService.CaptureSource = AudioCaptureService.CaptureSource.INTERNAL,
    onCaptureSourceChanged: (AudioCaptureService.CaptureSource) -> Unit = {},
    glyphsEnabled: Boolean = true,
    onGlyphsEnabledChanged: (Boolean) -> Unit = {},
    hapticsEnabled: Boolean = false,
    onHapticsEnabledChanged: (Boolean) -> Unit = {},
    flashlightEnabled: Boolean = false,
    onFlashlightEnabledChanged: (Boolean) -> Unit = {},
    broadcastEnabled: Boolean = false,
    onBroadcastEnabledChanged: (Boolean) -> Unit = {},
    connectedClients: Map<InetAddress, Int?> = emptyMap(),
    developerModeEnabled: Boolean = false,
    isGlyphAvailable: Boolean = true,
    hasHapticMotor: Boolean = true,
    hasFlashlight: Boolean = true,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onAutoDeviceToggle(true)
        }
    }

    var pendingCaptureSource by remember { mutableStateOf<AudioCaptureService.CaptureSource?>(null) }
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingCaptureSource?.let {
            if (isGranted) {
                onCaptureSourceChanged(it)
            }
            pendingCaptureSource = null
        }
    }

    val handleAutoToggle: (Boolean) -> Unit = { setEnabled ->
        if (setEnabled) {
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                PackageManager.PERMISSION_GRANTED
            }
            if (status == PackageManager.PERMISSION_GRANTED) {
                onAutoDeviceToggle(true)
            } else {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            onAutoDeviceToggle(false)
        }
    }

    var isTitleToggled by remember { mutableStateOf(false) }
    val fullTitle = stringResource(R.string.audio_screen_title)
    val shortTitle = stringResource(R.string.app_name)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = LocalAppSpacing.current.edge)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        ScreenTitle(
            text = if (isTitleToggled) shortTitle else fullTitle,
            onClick = {
                android.widget.Toast.makeText(context, context.getString(R.string.toast_audio_title_tap), android.widget.Toast.LENGTH_SHORT).show()
            },
            onLongPress = {
                isTitleToggled = !isTitleToggled
            }
        )

        val headerSpacerHeight by animateDpAsState(
            targetValue = if (isRunning) 1.dp else 32.dp,
            animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
            label = "headerSpacerHeight"
        )

        if (headerSpacerHeight > 0.dp) {
            Spacer(Modifier.height(headerSpacerHeight))
        }

        CaptureSourceCard(
            selectedSource = captureSource,
            onSourceSelected = { source ->
                if (source == AudioCaptureService.CaptureSource.MIC || source == AudioCaptureService.CaptureSource.VIZUALIZER) {
                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    if (status == PackageManager.PERMISSION_GRANTED) {
                        onCaptureSourceChanged(source)
                    } else {
                        pendingCaptureSource = source
                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else if (source == AudioCaptureService.CaptureSource.NETWORK) {
                    onCaptureSourceChanged(source)
                } else {
                    onCaptureSourceChanged(source)
                }
            },
            developerModeEnabled = developerModeEnabled
        )

        val header2SpacerHeight by animateDpAsState(
            targetValue = if (isRunning) 1.dp else 32.dp,
            animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
            label = "headerSpacerHeight"
        )

        if (header2SpacerHeight > 0.dp) {
            Spacer(Modifier.height(header2SpacerHeight))
        }

        AnimatedVisibility(visible = isRunning) {
            FFTSpectrumCard(
                fftRaw = fftRaw
            )
        }
        Spacer(Modifier.height(1.dp))

        OutputSelectionCard(
            glyphsEnabled = glyphsEnabled,
            onGlyphsToggle = onGlyphsEnabledChanged,
            hapticsEnabled = hapticsEnabled,
            onHapticsToggle = onHapticsEnabledChanged,
            flashlightEnabled = flashlightEnabled,
            onFlashlightToggle = onFlashlightEnabledChanged,
            broadcastEnabled = broadcastEnabled,
            onBroadcastToggle = onBroadcastEnabledChanged,
            isGlyphAvailable = isGlyphAvailable,
            hasHapticMotor = hasHapticMotor,
            hasFlashlight = hasFlashlight,
            connectedClients = connectedClients,
            isRunning = isRunning
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isRunning) {
            val seconds = (sessionDuration / 1000) % 60
            val minutes = (sessionDuration / (1000 * 60)) % 60
            val hours = (sessionDuration / (1000 * 60 * 60))
            val timeStr = if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
            val descriptionText = stringResource(R.string.audio_description_running) + "\n\nActive Time: $timeStr"

            ExpressiveCard(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                BodyText(
                    text = descriptionText,
                    size = 14.sp
                )
            }
        }

        AnimatedVisibility(visible = isRunning) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                if (captureSource != AudioCaptureService.CaptureSource.MIC) {
                    LatencyCard(
                        latencyMs = latencyMs,
                        onLatencyChanged = onLatencyChanged,
                        latencyPresets = latencyPresets,
                        onLatencyPresetsChanged = onLatencyPresetsChanged,
                        autoDeviceEnabled = autoDeviceEnabled,
                        onAutoDeviceToggle = handleAutoToggle,
                        connectedDeviceName = connectedDeviceName
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(86.dp))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun HostSelectionSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onHostSelected: (UdpNetworkSync.HostInfo) -> Unit
) {
    val hosts by viewModel.discoveredHosts.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(modifier = Modifier.width(50.dp)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.connect_to_device),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isDiscovering && hosts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (hosts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.no_devices_found))
                    Button(onClick = { viewModel.startDiscovery() }) {
                        Text(stringResource(R.string.search_again))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hosts.forEach { host ->
                        ExpressiveCard(
                            modifier = Modifier.fillMaxWidth().clickable { onHostSelected(host) },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(host.name, fontWeight = FontWeight.Bold)
                                    Text("${host.model} • ${host.ip}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                if (isDiscovering) {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun OutputSelectionCard(
    glyphsEnabled: Boolean,
    onGlyphsToggle: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsToggle: (Boolean) -> Unit,
    flashlightEnabled: Boolean,
    onFlashlightToggle: (Boolean) -> Unit,
    broadcastEnabled: Boolean,
    onBroadcastToggle: (Boolean) -> Unit,
    isGlyphAvailable: Boolean = true,
    hasHapticMotor: Boolean = true,
    hasFlashlight: Boolean = true,
    connectedClients: Map<InetAddress, Int?> = emptyMap(),
    isRunning: Boolean = false
) {
    var isHelpExpanded by remember { mutableStateOf(false) }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(
            title = stringResource(R.string.output_selection),
            trailingContent = {
                IconButton(onClick = { isHelpExpanded = !isHelpExpanded }) {
                    Icon(
                        imageVector = if (isHelpExpanded) Icons.Default.ExpandLess else Icons.Default.Info,
                        contentDescription = "Show help",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        )

        AnimatedVisibility(
            visible = isHelpExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                BodyText(
                    text = stringResource(R.string.help_output_intro),
                    size = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                HelpItem(stringResource(R.string.help_output_glyphs_title), stringResource(R.string.help_output_glyphs_desc))
                HelpItem(stringResource(R.string.help_output_haptics_title), stringResource(R.string.help_output_haptics_desc))
                HelpItem(stringResource(R.string.help_output_flash_title), stringResource(R.string.help_output_flash_desc))
                HelpItem(stringResource(R.string.help_output_broadcast_title), stringResource(R.string.help_output_broadcast_desc))

                Spacer(modifier = Modifier.height(12.dp))
                BodyText(
                    text = stringResource(R.string.help_sync_title),
                    size = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                BodyText(
                    text = "${stringResource(R.string.help_sync_step1)}\n${stringResource(R.string.help_sync_step2)}",
                    size = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        val outputs = listOf(
            Triple(stringResource(R.string.tab_glyphs), ImageVector.vectorResource(R.drawable.ic_nav_glyphs), Triple(glyphsEnabled, onGlyphsToggle, isGlyphAvailable)),
            Triple(stringResource(R.string.tab_haptics), Icons.Default.Vibration, Triple(hapticsEnabled, onHapticsToggle, hasHapticMotor)),
            Triple(stringResource(R.string.tab_flashlight), Icons.Default.FlashlightOn, Triple(flashlightEnabled, onFlashlightToggle, hasFlashlight)),
            Triple(stringResource(R.string.broadcast), Icons.Default.Wifi, Triple(broadcastEnabled, onBroadcastToggle, true))
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            outputs.forEach { (label, icon, stateTriple) ->
                val (isEnabled, onToggle, isHardwareAvailable) = stateTriple
                OptionTile(
                    label = label,
                    icon = icon,
                    isSelected = isEnabled && isHardwareAvailable,
                    enabled = isHardwareAvailable,
                    onClick = { if (isHardwareAvailable) onToggle(!isEnabled) },
                    modifier = Modifier.height(64.dp),
                    maxLines = 2
                )
            }
        }

        AnimatedVisibility(visible = broadcastEnabled && isRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Broadcast Clients",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${connectedClients.size} Connected",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (connectedClients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        connectedClients.forEach { (client, latency) ->
                            val latencyText = if (latency != null) " (Latency: ${latency}ms)" else ""
                            Text(
                                text = "• ${client.hostAddress}$latencyText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Waiting for devices...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CaptureSourceCard(
    selectedSource: AudioCaptureService.CaptureSource,
    onSourceSelected: (AudioCaptureService.CaptureSource) -> Unit,
    developerModeEnabled: Boolean
) {
    var isHelpExpanded by remember { mutableStateOf(false) }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(
            title = stringResource(R.string.select_capture_source),
            trailingContent = {
                IconButton(onClick = { isHelpExpanded = !isHelpExpanded }) {
                    Icon(
                        imageVector = if (isHelpExpanded) Icons.Default.ExpandLess else Icons.Default.Info,
                        contentDescription = "Show help",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        )

        AnimatedVisibility(
            visible = isHelpExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                BodyText(
                    text = stringResource(R.string.help_source_intro),
                    size = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                HelpItem(stringResource(R.string.help_source_internal_title), stringResource(R.string.help_source_internal_desc))
                HelpItem(stringResource(R.string.help_source_mic_title), stringResource(R.string.help_source_mic_desc))
                HelpItem(stringResource(R.string.help_source_network_title), stringResource(R.string.help_source_network_desc))
                HelpItem(stringResource(R.string.help_source_viz_title), stringResource(R.string.help_source_viz_desc))
                
                Spacer(modifier = Modifier.height(8.dp))
                BodyText(
                    text = stringResource(R.string.help_source_privacy),
                    size = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        val mainSources = listOf(
            Triple(
                AudioCaptureService.CaptureSource.INTERNAL,
                stringResource(R.string.capture_media_projection),
                Icons.Default.Cast
            ),
            Triple(
                AudioCaptureService.CaptureSource.MIC,
                stringResource(R.string.capture_microphone),
                Icons.Default.Mic
            ),
            Triple(
                AudioCaptureService.CaptureSource.VIZUALIZER,
                stringResource(R.string.capture_vizualizer),
                Icons.Default.Android
            )
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mainSources.forEach { (source, label, icon) ->
                val isSelected = selectedSource == source
                val isInternal = source == AudioCaptureService.CaptureSource.INTERNAL
                val isEnabled = (source != AudioCaptureService.CaptureSource.INTERNAL || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

                OptionTile(
                    label = if (isInternal && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "$label (API 29+)"
                    else label,
                    icon = icon,
                    isSelected = isSelected,
                    enabled = isEnabled,
                    onClick = { onSourceSelected(source) },
                    modifier = Modifier.height(64.dp),
                    maxLines = 2
                )
            }

            OptionTile(
                label = "Another device or app...",
                icon = Icons.Default.Wifi,
                isSelected = selectedSource == AudioCaptureService.CaptureSource.NETWORK,
                enabled = true,
                onClick = { onSourceSelected(AudioCaptureService.CaptureSource.NETWORK) },
                modifier = Modifier.height(64.dp),
                maxLines = 2
            )
        }
        if (selectedSource == AudioCaptureService.CaptureSource.VIZUALIZER) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.audio_warning_vizualizer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LatencyCard(
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
    autoDeviceEnabled: Boolean,
    onAutoDeviceToggle: (Boolean) -> Unit,
    connectedDeviceName: String?
) {
    val haptics = LocalHapticFeedback.current
    var draggingIndex by remember { mutableIntStateOf(-1) }

    val visualOrder = remember(latencyPresets) {
        latencyPresets.mapIndexed { i, v -> i to v }
            .sortedBy { it.second }
            .map { it.first }
    }

    var isFirstOrderChange by remember { mutableStateOf(true) }
    LaunchedEffect(visualOrder) {
        if (isFirstOrderChange) {
            isFirstOrderChange = false
            return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val activeIndex = if (draggingIndex != -1) draggingIndex else latencyPresets.indexOf(latencyMs)

    val updateLatency = { newValue: Int ->
        val clampedValue = newValue.coerceIn(0, 500)
        if (draggingIndex == -1) draggingIndex = latencyPresets.indexOf(latencyMs)

        onLatencyChanged(clampedValue)

        if (draggingIndex != -1) {
            val currentList = latencyPresets.toMutableList()
            val isColliding = currentList.mapIndexed { i, v -> i to v }
                .any { (i, v) -> i != draggingIndex && v == clampedValue }

            if (!isColliding) {
                currentList[draggingIndex] = clampedValue
                onLatencyPresetsChanged(currentList)
            }
        }
    }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(
            title = stringResource(
                R.string.latency_compensation
            ), trailingContent = {
                Text(
                    text = "${latencyMs}ms",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            })
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    MaterialTheme.shapes.large
                )
                .padding(4.dp)
        ) {
            val spacing = 4.dp
            val itemWidth = (maxWidth - (spacing * (latencyPresets.size - 1))) / latencyPresets.size

            latencyPresets.forEachIndexed { index, preset ->
                val isSelected = index == activeIndex
                val visualIndex = visualOrder.indexOf(index)
                val targetOffset = (itemWidth + spacing) * visualIndex

                val animatedX by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
                    label = "swap"
                )

                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .offset(x = animatedX)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingIndex = index
                            onLatencyChanged(preset)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${preset}ms",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ExpressiveSlider(
            value = latencyMs.toFloat(),
            onValueChange = { updateLatency(it.toInt()) },
            valueRange = 0f..500f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(-10, -1, 1, 10).forEach { amount ->
                FineTuneButton(
                    amount = amount,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        updateLatency(latencyMs + amount)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-Memorize Device Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardHeader(title = stringResource(R.string.auto_memorize_device))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (autoDeviceEnabled)
                            stringResource(
                                R.string.saving_latency_for,
                                connectedDeviceName
                                    ?: stringResource(R.string.internal_speaker)
                            )
                        else stringResource(R.string.manual_mode_global_latency),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = autoDeviceEnabled,
                    onCheckedChange = onAutoDeviceToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
fun FFTSpectrumCard(
    fftRaw: FloatArray
) {
    val haptics = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var touchX by remember { mutableStateOf<Float?>(null) }

    val data = fftRaw
    ExpandableExpressiveCard(
        title = stringResource(R.string.live_spectrum),
        icon = Icons.Default.Leaderboard,
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { touchX = it.x },
                            onDrag = { change, _ ->
                                change.consume()
                                touchX = change.position.x
                            },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                touchX = it.x
                                tryAwaitRelease()
                                touchX = null
                            }
                        )
                    }
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val width = maxWidth
                val density = LocalDensity.current

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (data.isEmpty()) return@Canvas

                    val w = size.width
                    val h = size.height

                    val barPath = Path()
                    var first = true

                    val gradient = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.6f),
                            primaryColor.copy(alpha = 0.02f)
                        ),
                        startY = 0f,
                        endY = h
                    )

                    val points = data.size - 1
                    for (i in 5..points) {
                        val fraction = i.divideBy(points)
                        val mag = data[i]

                        // data is already normalized 0..1 from MainActivity/MainViewModel
                        val y = h - (mag * (h - 40f)) - 20f
                        val x = fraction * w

                        if (first) {
                            barPath.moveTo(x, y)
                            first = false
                        } else {
                            barPath.lineTo(x, y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(barPath)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }

                    drawPath(path = fillPath, brush = gradient)


                    drawPath(
                        path = barPath,
                        color = primaryColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    touchX?.let { tx ->
                        val x = tx.coerceIn(0f, w)
                        drawLine(
                            color = primaryColor.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))
                        )
                    }
                }

                touchX?.let { tx ->
                    val fraction = (tx / constraints.maxWidth.toFloat()).coerceIn(0f, 1f)
                    val logMin = log10(30f)
                    val logMax = log10(16000f)
                    val logFreq = logMin + fraction * (logMax - logMin)
                    val freq = 10f.pow(logFreq)

                    val text = if (freq >= 1000) String.format(
                        Locale.US,
                        "%.1fkHz",
                        freq / 1000f
                    ) else String.format(Locale.US, "%dHz", freq.toInt())
                    val txDp = with(density) { tx.toDp() }

                    Surface(
                        modifier = Modifier
                            .offset(
                                x = (txDp - 30.dp).coerceIn(4.dp, width - 64.dp),
                                y = 12.dp
                            ),
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 4.dp
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

private fun Int.divideBy(divisor: Int): Float = this.toFloat() / divisor.toFloat()

@Composable
fun RowScope.FineTuneButton(
    amount: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isAnimating = true
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    delay(100.milliseconds)
                    isAnimating = false
                }
            }
        }
    }

    val animatedWeight by animateFloatAsState(
        targetValue = if (isAnimating) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "weight"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isAnimating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = Modifier
            .weight(animatedWeight)
            .fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (amount > 0) "+$amount" else "$amount",
                style = MaterialTheme.typography.labelMedium,
                color = if (isAnimating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
