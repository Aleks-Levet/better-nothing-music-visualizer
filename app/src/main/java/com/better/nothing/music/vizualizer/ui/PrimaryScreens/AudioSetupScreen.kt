package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.logic.LatencyWizard
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.OptionTile
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AudioScreen(
    isRunning: Boolean,
    sessionDuration: Long = 0L,
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
    autoDeviceEnabled: Boolean,
    onAutoDeviceToggle: (Boolean) -> Unit,
    connectedDeviceName: String? = null,
    fftData: FloatArray = floatArrayOf(),
    captureSource: AudioCaptureService.CaptureSource = AudioCaptureService.CaptureSource.INTERNAL,
    onCaptureSourceChanged: (AudioCaptureService.CaptureSource) -> Unit = {},
    shizukuUnlocked: Boolean = false,
    latencyWizardState: LatencyWizard.State = LatencyWizard.State.Idle,
    onRunLatencyWizard: () -> Unit = {},
    onResetLatencyWizard: () -> Unit = {},
    glyphsEnabled: Boolean = true,
    onGlyphsEnabledChanged: (Boolean) -> Unit = {},
    hapticsEnabled: Boolean = false,
    onHapticsEnabledChanged: (Boolean) -> Unit = {},
    visualsEnabled: Boolean = false,
    onVisualsEnabledChanged: (Boolean) -> Unit = {},
    flashlightEnabled: Boolean = false,
    onFlashlightEnabledChanged: (Boolean) -> Unit = {},
    developerModeEnabled: Boolean = false,
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

    val wizardPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onRunLatencyWizard()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = LocalAppSpacing.current.edge)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        ScreenTitle(
            text = stringResource(
                R.string.audio_screen_title
            )
        )

        val headerSpacerHeight by animateDpAsState(
            targetValue = if (isRunning) 0.dp else 100.dp,
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
                } else {
                    onCaptureSourceChanged(source)
                }
            },
            shizukuUnlocked = shizukuUnlocked,
            developerModeEnabled = developerModeEnabled
        )

        OutputSelectionCard(
            glyphsEnabled = glyphsEnabled,
            onGlyphsToggle = onGlyphsEnabledChanged,
            hapticsEnabled = hapticsEnabled,
            onHapticsToggle = onHapticsEnabledChanged,
            visualsEnabled = visualsEnabled,
            onVisualsToggle = onVisualsEnabledChanged,
            flashlightEnabled = flashlightEnabled,
            onFlashlightToggle = onFlashlightEnabledChanged
        )
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
                containerColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = 0.5f
                )
            ) {
                BodyText(
                    text = descriptionText,
                    size = 14.sp
                )
            }
        }

        AnimatedVisibility(visible = isRunning) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                if (captureSource != AudioCaptureService.CaptureSource.MIC) {
                    LatencyCard(
                        latencyMs = latencyMs,
                        onLatencyChanged = onLatencyChanged,
                        latencyPresets = latencyPresets,
                        onLatencyPresetsChanged = onLatencyPresetsChanged,
                        wizardState = latencyWizardState,
                        onRunWizard = {
                            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            if (status == PackageManager.PERMISSION_GRANTED) {
                                onRunLatencyWizard()
                            } else {
                                wizardPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onResetWizard = onResetLatencyWizard,
                        autoDeviceEnabled = autoDeviceEnabled,
                        onAutoDeviceToggle = handleAutoToggle,
                        connectedDeviceName = connectedDeviceName
                    )
                }

                FFTSpectrumCard(fftData = fftData)

                ExpressiveCard(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                ) {
                    BodyText(
                        text = stringResource(R.string.latency_compensation_description),
                        size = 12.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(86.dp)) //no one will notice
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun OutputSelectionCard(
    glyphsEnabled: Boolean,
    onGlyphsToggle: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsToggle: (Boolean) -> Unit,
    visualsEnabled: Boolean,
    onVisualsToggle: (Boolean) -> Unit,
    flashlightEnabled: Boolean,
    onFlashlightToggle: (Boolean) -> Unit
) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(title = "Output Selection")
        val outputs = listOf(
            Triple("Glyphs", Icons.Default.Flare, glyphsEnabled to onGlyphsToggle),
            Triple("Haptics", Icons.Default.Vibration, hapticsEnabled to onHapticsToggle),
            Triple("Visuals", Icons.Default.Visibility, visualsEnabled to onVisualsToggle),
            Triple("Flashlight", Icons.Default.FlashlightOn, flashlightEnabled to onFlashlightToggle)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            outputs.forEach { (label, icon, statePair) ->
                val (isEnabled, onToggle) = statePair
                OptionTile(
                    label = label,
                    icon = icon,
                    isSelected = isEnabled,
                    onClick = { onToggle(!isEnabled) },
                    modifier = Modifier.height(64.dp).weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CaptureSourceCard(
    selectedSource: AudioCaptureService.CaptureSource,
    onSourceSelected: (AudioCaptureService.CaptureSource) -> Unit,
    shizukuUnlocked: Boolean,
    developerModeEnabled: Boolean
) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(title = "Capture Source")
        val mainSources = listOf(
            Triple(
                AudioCaptureService.CaptureSource.INTERNAL,
                stringResource(R.string.capture_media_projection),
                Icons.Default.PhoneAndroid
            ),
            Triple(
                AudioCaptureService.CaptureSource.MIC,
                stringResource(R.string.capture_microphone),
                Icons.Default.Mic
            ),
            Triple(
                AudioCaptureService.CaptureSource.VIZUALIZER,
                stringResource(R.string.capture_vizualizer),
                Icons.Default.GraphicEq
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
                val isEnabled = !isInternal || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

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
            
            // Placeholder button
            OptionTile(
                label = "Coming Soon...",
                icon = Icons.Default.Add,
                isSelected = false,
                enabled = false,
                onClick = { },
                modifier = Modifier.height(64.dp),
                maxLines = 2
            )

            if (developerModeEnabled) {
                val isSelected = selectedSource == AudioCaptureService.CaptureSource.SHIZUKU
                val isEnabled = shizukuUnlocked
                
                OptionTile(
                    label = if (!shizukuUnlocked) stringResource(R.string.capture_shizuku) + " (Locked)" 
                            else stringResource(R.string.capture_shizuku),
                    icon = Icons.Default.Terminal,
                    isSelected = isSelected,
                    enabled = isEnabled,
                    onClick = { onSourceSelected(AudioCaptureService.CaptureSource.SHIZUKU) },
                    modifier = Modifier.height(64.dp),
                    maxLines = 2
                )
            }
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
    wizardState: LatencyWizard.State,
    onRunWizard: () -> Unit,
    onResetWizard: () -> Unit,
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

        // Latency Wizard Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Latency Wizard",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (wizardState) {
                            is LatencyWizard.State.Idle -> "Sync lights using your microphone"
                            is LatencyWizard.State.Preparing -> "Preparing..."
                            is LatencyWizard.State.Recording -> "Recording pulse... Please stay quiet."
                            is LatencyWizard.State.Analyzing -> "Analyzing..."
                            is LatencyWizard.State.Success -> "Success! Detected ${wizardState.latencyMs}ms delay."
                            is LatencyWizard.State.Error -> "Error: ${wizardState.message}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = {
                        if (wizardState is LatencyWizard.State.Success || wizardState is LatencyWizard.State.Error) {
                            onResetWizard()
                        } else if (wizardState is LatencyWizard.State.Idle) {
                            onRunWizard()
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = if (wizardState is LatencyWizard.State.Idle)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    enabled = wizardState !is LatencyWizard.State.Preparing && 
                              wizardState !is LatencyWizard.State.Recording &&
                              wizardState !is LatencyWizard.State.Analyzing
                ) {
                    Text(
                        text = when (wizardState) {
                            is LatencyWizard.State.Idle -> "Start"
                            is LatencyWizard.State.Success, is LatencyWizard.State.Error -> "Reset"
                            else -> "Wait"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (wizardState is LatencyWizard.State.Idle)
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CardHeader(title = "Auto-Memorize Device")
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
fun FFTSpectrumCard(fftData: FloatArray) {
    val haptics = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }
    var touchX by remember { mutableStateOf<Float?>(null) }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    isExpanded = !isExpanded
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.live_spectrum),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            val decayedData = remember { mutableStateOf(floatArrayOf()) }
            LaunchedEffect(fftData) {
                if (fftData.isEmpty()) return@LaunchedEffect

                val current = decayedData.value
                if (current.size != fftData.size) {
                    decayedData.value = fftData.copyOf()
                    return@LaunchedEffect
                }

                val decay = 0.75f
                val next = FloatArray(fftData.size)
                for (i in fftData.indices) {
                    val newVal = fftData[i]
                    val prevVal = current[i]
                    if (newVal > prevVal) {
                        next[i] = newVal
                    } else {
                        next[i] = (decay * prevVal) + ((1f - decay) * newVal)
                    }
                }
                decayedData.value = next
            }

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
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
                        val data = decayedData.value
                        if (data.isEmpty()) return@Canvas

                        val w = size.width
                        val h = size.height

                        // Draw Grid
                        val gridColor = Color.White.copy(alpha = 0.03f)
                        drawLine(gridColor, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), 1f)
                        drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1f)
                        drawLine(gridColor, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), 1f)

                        val barPath = Path()
                        var first = true

                        // Dynamic gradient based on amplitude
                        val gradient = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.4f),
                                primaryColor.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = h
                        )

                        val points = data.size - 1
                        for (i in 0..points) {
                            val fraction = i.toFloat() / points
                            val mag = data[i]

                            // Nonlinear scaling for better visuals
                            val scaledMag = (mag * 1.2f).coerceIn(0f, 1.2f)
                            val y = h - (scaledMag * (h - 40f)) - 20f
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

                        // Outer glow
                        drawPath(
                            path = barPath,
                            color = primaryColor.copy(alpha = 0.3f),
                            style = Stroke(
                                width = 6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Main line
                        drawPath(
                            path = barPath,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
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
                        val logMin = log10(20f)
                        val logMax = log10(20000f)
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

                // Frequency labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val freqLabels = listOf("20Hz", "100Hz", "1kHz", "10kHz", "20kHz")
                    freqLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

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
