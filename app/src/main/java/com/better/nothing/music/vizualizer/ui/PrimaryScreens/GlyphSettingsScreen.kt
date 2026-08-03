package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.GlyphPreview
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import kotlin.math.pow


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlyphsScreen(
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
    maxBrightness: Int,
    onMaxBrightnessChanged: (Int) -> Unit,
    presets: List<AudioCaptureService.PresetInfo>,
    selectedPreset: String,
    onPresetSelected: (String) -> Unit,
    isRunning: Boolean,
    selectedDevice: Int,
    viewModel: com.better.nothing.music.vizualizer.ui.MainViewModel,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val mainScrollState = rememberScrollState()
    val context = LocalContext.current
    val configStatus by viewModel.configUpdateStatus.collectAsStateWithLifecycle()
    val configVersion by viewModel.configVersion.collectAsStateWithLifecycle()
    val remoteVersion by viewModel.remoteConfigVersion.collectAsStateWithLifecycle()

    val selectedInfo = remember(selectedPreset, presets) {
        presets.firstOrNull { it.key == selectedPreset } ?: presets.firstOrNull()
    }

    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Preset?") },
            text = { Text("Are you sure you want to delete the local preset '${showDeleteConfirm}'?") },
            confirmButton = {
                TextButton(onClick = { 
                    // viewModel.deleteCustomPreset(it) was removed as part of Community Removal
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = LocalAppSpacing.current.edge)
            .verticalScroll(mainScrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        ScreenTitle(
            text = stringResource(
                R.string.glyph_controls
            )
        )

        val DEFAULT_BR = 4095
        val lastNonZero = remember { mutableIntStateOf(if (maxBrightness > 0) maxBrightness else DEFAULT_BR) }
        androidx.compose.runtime.LaunchedEffect(maxBrightness) {
            if (maxBrightness > 0) lastNonZero.intValue = maxBrightness
        }

        BrightnessCard(
            maxBrightness = maxBrightness,
            enabled = true,
            lastNonZero = lastNonZero.intValue,
            onLastNonZeroChanged = { v -> lastNonZero.intValue = v },
            onMaxBrightnessChanged = onMaxBrightnessChanged,
            gammaValue = gammaValue,
            onGammaChanged = onGammaChanged
        )

        ExpressiveCard(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardHeader(
                    title = stringResource(
                        R.string.visualizer_presets
                    )
                )
            }

            val favorites by viewModel.favoritePresets.collectAsStateWithLifecycle()
            val sortedPresets = remember(presets, favorites) {
                presets.sortedByDescending { favorites.contains(it.key) }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sortedPresets.isNotEmpty()) {
                    ExpressiveSplitButton(
                        items = sortedPresets,
                        selectedItem = sortedPresets.firstOrNull { it.key == selectedPreset }
                            ?: sortedPresets.first(),
                        onItemSelection = { preset -> onPresetSelected(preset.key) },
                        labelProvider = { preset -> preset.key },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Crossfade(
                            targetState = selectedInfo?.description,
                            label = "desc_fade",
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            modifier = Modifier.weight(1f)
                        ) { description ->
                            Text(
                                text = description ?: stringResource(R.string.glyph_no_config),
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (selectedInfo?.description?.startsWith("Custom:") == true) {
                            IconButton(
                                onClick = { showDeleteConfirm = selectedInfo.key },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Local Preset",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (configVersion.contains(".simple")) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Update Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Download full config to see presets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (isRunning) {
            val vizStateState = viewModel.visualizerState.collectAsStateWithLifecycle()
            val previewHeight = when (selectedDevice) {
                com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP2 -> 530.dp
                com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP1,
                com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP3,
                com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP4A,
                com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP4B,
                com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP4APRO -> 560.dp
                else -> 400.dp
            }
            GlyphPreview(
                vizStateProvider = { vizStateState.value },
                device = selectedDevice,
                modifier = Modifier
                    .width(380.dp)
                    .height(previewHeight)
                    .align(Alignment.CenterHorizontally)
            )
        }

        LaunchedEffect(Unit) {
            viewModel.checkRemoteConfigVersion()
        }

        LaunchedEffect(configStatus) {
            when (val status = configStatus) {
                is com.better.nothing.music.vizualizer.ui.MainViewModel.ConfigUpdateStatus.Success -> {
                    Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                    viewModel.resetConfigUpdateStatus()
                }
                is com.better.nothing.music.vizualizer.ui.MainViewModel.ConfigUpdateStatus.Error -> {
                    Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                    viewModel.resetConfigUpdateStatus()
                }
                else -> {}
            }
        }

        ExpressiveCard {
            CardHeader(title = "Visualizer Configuration")

            BodyText(
                text = "The zones.config file defines how frequencies map to Glyph LEDs. Updating from GitHub ensures support for new devices and presets.",
                size = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Version: $configVersion",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (remoteVersion != null && remoteVersion != "Unknown") {
                            val isUpdateAvailable = remoteVersion != configVersion
                            Text(
                                text = if (isUpdateAvailable) "Latest: $remoteVersion" else "Up to date",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUpdateAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (remoteVersion != null && remoteVersion != "Unknown" && remoteVersion != configVersion) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "UPDATE AVAILABLE",
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val filePickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let { viewModel.importZonesConfig(uri) }
            }

            val isUpdateAvailable =
                remoteVersion != null && remoteVersion != "Unknown" && remoteVersion != configVersion

            ExpressiveSplitButton(
                primaryText = if (isUpdateAvailable) "Update Now" else "Check GitHub",
                primaryIcon = if (configStatus is com.better.nothing.music.vizualizer.ui.MainViewModel.ConfigUpdateStatus.Updating) Icons.Default.Sync else Icons.Default.CloudDownload,
                onPrimaryClick = { viewModel.updateZonesConfig() },
                secondaryText = "Local",
                secondaryIcon = Icons.Default.FolderOpen,
                onSecondaryClick = { filePickerLauncher.launch("*/*") },
                enabled = configStatus is com.better.nothing.music.vizualizer.ui.MainViewModel.ConfigUpdateStatus.Idle,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(85.dp))
    }
}


@Composable
fun BrightnessCard(
    maxBrightness: Int,
    enabled: Boolean,
    lastNonZero: Int,
    onLastNonZeroChanged: (Int) -> Unit,
    onMaxBrightnessChanged: (Int) -> Unit,
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
) {
    androidx.compose.ui.platform.LocalHapticFeedback.current

    val MIN_BRIGHTNESS = 50
    val MAX_BRIGHTNESS = 4500

    var isGammaExpanded by remember { mutableStateOf(false) }

    // Quadratic mapping: slider position (0..1) -> value = min + (max-min) * pos^2
    fun linearToPos(linear: Int): Float {
        val clamped = linear.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        val ratio = (clamped - MIN_BRIGHTNESS).toFloat() / (MAX_BRIGHTNESS - MIN_BRIGHTNESS).toFloat()
        return kotlin.math.sqrt(ratio.coerceIn(0f, 1f))
    }

    fun posToLinear(pos: Float): Int {
        val p = pos.coerceIn(0f, 1f)
        val valf = MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * (p * p)
        return kotlin.math.round(valf).toInt()
    }

    val posValue = remember(maxBrightness, lastNonZero) { linearToPos(if (maxBrightness > 0) maxBrightness else lastNonZero) }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        CardHeader(
            title = stringResource(R.string.glyph_brightness),
            trailingContent = {
                Text(
                    text = "${if (maxBrightness > 0) maxBrightness else lastNonZero}/${MAX_BRIGHTNESS}" + (if (maxBrightness == 4095) " (default)" else ""),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            })

        ExpressiveSlider(
            value = posValue,
            onValueChange = { newPos ->
                val newLinearValue = posToLinear(newPos)
                onLastNonZeroChanged(newLinearValue)
                if (enabled) onMaxBrightnessChanged(newLinearValue)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        GammaSlider(
            gammaValue = gammaValue,
            onGammaChanged = onGammaChanged,
            isExpanded = isGammaExpanded,
            onToggleExpand = { isGammaExpanded = !isGammaExpanded }
        )

        AnimatedVisibility(
            visible = isGammaExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GammaPreviewCard(gammaValue = gammaValue)
                    BodyText(
                        text = stringResource(R.string.gamma_description),
                        modifier = Modifier.weight(1f),
                        size = 14.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun GammaSlider(
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    CardHeader(
        title = stringResource(
            R.string.light_gamma
        ), trailingContent = {
            Text(
                text = String.format("%.1f", gammaValue),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        })

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveSlider(
            value = gammaValue,
            onValueChange = onGammaChanged,
            valueRange = 0.4f..4.5f,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onToggleExpand) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Info,
                contentDescription = "Show Gamma Info",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun GammaPreviewCard(gammaValue: Float) {
    val animatedGamma by animateFloatAsState(
        targetValue  = gammaValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow,
        ),
        label = "gamma_curve",
    )

    val curvePath = remember { Path() }

    val gridColor = MaterialTheme.colorScheme.outline
    val accent    = MaterialTheme.colorScheme.primary

    Card(
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.size(130.dp, 130.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            val pad       = 8f
            val right  = size.width - pad
            val bottom = size.height - pad
            val w = right - pad
            val h = bottom - pad

            drawLine(gridColor, Offset(pad, bottom), Offset(right, bottom), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(gridColor, Offset(pad, bottom), Offset(pad, pad),    strokeWidth = 4f, cap = StrokeCap.Round)

            val hStep = h / 4f
            val vStep = w / 4f
            repeat(3) { i ->
                drawLine(gridColor, Offset(pad,         bottom - hStep * (i + 1)), Offset(right, bottom - hStep * (i + 1)), strokeWidth = 1f)
                drawLine(gridColor, Offset(pad + vStep * (i + 1), bottom),         Offset(pad + vStep * (i + 1),
                    pad
                ),     strokeWidth = 1f)
            }

            curvePath.reset()
            curvePath.moveTo(pad, bottom)
            val steps = 50
            for (step in 1..steps) {
                val x = step / steps.toFloat()
                val y = x.pow(animatedGamma)
                curvePath.lineTo(pad + x * w, bottom - y * h)
            }
            drawPath(curvePath, accent, style = Stroke(width = 8f, cap = StrokeCap.Round))
        }
    }
}
