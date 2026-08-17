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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
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
import com.better.nothing.music.vizualizer.ui.IndicatorPill
import kotlin.math.pow


import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.better.nothing.music.vizualizer.ui.NTypeFontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun GlyphsScreen(
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
    thresholdValue: Float,
    onThresholdChanged: (Float) -> Unit,
    speedValue: Float,
    onSpeedChanged: (Float) -> Unit,
    maxBrightness: Int,
    onMaxBrightnessChanged: (Int) -> Unit,
    presets: List<AudioCaptureService.PresetInfo>,
    selectedPreset: String,
    onPresetSelected: (String) -> Unit,
    isRunning: Boolean,
    selectedDevice: Int,
    viewModel: com.better.nothing.music.vizualizer.ui.MainViewModel,
    padding: PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val mainScrollState = rememberScrollState()
    val context = LocalContext.current
    val configStatus by viewModel.configUpdateStatus.collectAsStateWithLifecycle()
    val configVersion by viewModel.configVersion.collectAsStateWithLifecycle()
    val remoteVersion by viewModel.remoteConfigVersion.collectAsStateWithLifecycle()

    var showEasterEggPopup by remember { mutableStateOf(false) }

    if (showEasterEggPopup) {
        BasicAlertDialog(
            onDismissRequest = { showEasterEggPopup = false },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.easter_egg_glyph_popup_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    AsyncImage(
                        model = "https://github.com/Aleks-Levet/better-nothing-music-visualizer/blob/main/Docs/app-icon-by-burgerkingfootlettuce-novolume-dev.jpg?raw=true",
                        contentDescription = "Easter Egg Image",
                        modifier = Modifier
                            .sizeIn(maxWidth = 400.dp, maxHeight = 400.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Text(
                        text = stringResource(R.string.easter_egg_glyph_popup_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.easter_egg_glyph_popup_quote),
                        style = TextStyle(
                            fontFamily = NTypeFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val haptics = LocalHapticFeedback.current
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showEasterEggPopup = false
                            },
                            modifier = Modifier.weight(5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.i_dont_give_a_fuck),
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                showEasterEggPopup = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = LocalAppSpacing.current.edge)
            .verticalScroll(mainScrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        ScreenTitle(
            text = stringResource(R.string.glyph_controls),
            onClick = {
                viewModel.logEasterEggEvent("easter_egg_glyphs_go_brrr")
                showEasterEggPopup = true
            }
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
            onGammaChanged = onGammaChanged,
            thresholdValue = thresholdValue,
            onThresholdChanged = onThresholdChanged,
            speedValue = speedValue,
            onSpeedChanged = onSpeedChanged
        )

        val isSlimDevice = selectedDevice == com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP4A ||
                selectedDevice == com.better.nothing.music.vizualizer.model.DeviceProfile.DEVICE_NP4B

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            ExpressiveCard(
                modifier = Modifier.weight(if (isSlimDevice) 0.72f else 1f).fillMaxHeight()
            ) {
                CardHeader(
                    title = stringResource(
                        R.string.visualizer_presets
                    )
                )

                val favorites by viewModel.favoritePresets.collectAsStateWithLifecycle()
                val sortedPresets = remember(presets, favorites) {
                    presets.sortedByDescending { favorites.contains(it.key) }
                }

                if (sortedPresets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpressiveSplitButton(
                        items = sortedPresets,
                        selectedItem = sortedPresets.firstOrNull { it.key == selectedPreset }
                            ?: sortedPresets.first(),
                        onItemSelection = { preset -> onPresetSelected(preset.key) },
                        labelProvider = { preset -> preset.key },
                        modifier = Modifier.fillMaxWidth(),
                        maxButtonsPerRow = if (isSlimDevice) 2 else 3
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Crossfade(
                        targetState = selectedPreset,
                        label = "desc_fade",
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) { presetKey ->
                        val description = remember(presetKey, presets) {
                            presets.firstOrNull { it.key == presetKey }?.description
                                ?: presets.firstOrNull { it.key == selectedPreset }?.description
                                ?: presets.firstOrNull()?.description
                        }
                        Text(
                            text = description ?: stringResource(R.string.glyph_no_config),
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (configVersion.contains(".simple")) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.update_required),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    stringResource(R.string.download_full_config),
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

            AnimatedVisibility(isSlimDevice && isRunning) {
                val vizStateState = viewModel.visualizerState.collectAsStateWithLifecycle()
                GlyphPreview(
                    vizStateProvider = { vizStateState.value },
                    device = selectedDevice,
                    modifier = Modifier
                        .width(60.dp)
                        .height(300.dp)
                )
            }
        }

        if (!isSlimDevice && isRunning) {
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
            CardHeader(title = stringResource(R.string.visualizer_configuration))

            BodyText(
                text = stringResource(R.string.config_description),
                size = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                                text = if (isUpdateAvailable) "Latest: $remoteVersion" else stringResource(R.string.up_to_date),
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
                                text = stringResource(R.string.update_available_label),
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
                primaryText = if (isUpdateAvailable) stringResource(R.string.update_now) else stringResource(R.string.check_github),
                primaryIcon = if (configStatus is com.better.nothing.music.vizualizer.ui.MainViewModel.ConfigUpdateStatus.Updating) Icons.Default.Sync else Icons.Default.CloudDownload,
                onPrimaryClick = { viewModel.updateZonesConfig() },
                secondaryText = stringResource(R.string.local_config),
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
    thresholdValue: Float,
    onThresholdChanged: (Float) -> Unit,
    speedValue: Float,
    onSpeedChanged: (Float) -> Unit
) {

    val MIN_BRIGHTNESS = 50
    val MAX_BRIGHTNESS = 5000

    var isMoreSlidersExpanded by remember { mutableStateOf(false) }
    var isThresholdExpanded by remember { mutableStateOf(false) }
    var isGammaExpanded by remember { mutableStateOf(false) }
    var isSpeedExpanded by remember { mutableStateOf(false) }

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
                val currentVal = if (maxBrightness > 0) maxBrightness else lastNonZero
                // Scale percent relative to hardware max 4095, allowing it to go above 100%
                val percent = (currentVal * 100 / 4095)
                Text(
                    text = "$percent%",
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

        Spacer(modifier = Modifier.height(30.dp))

        CardHeader(
            title = stringResource(R.string.show_more_sliders),
            trailingContent = {
                IndicatorPill(
                    isExpanded = isMoreSlidersExpanded,
                    onClick = { isMoreSlidersExpanded = !isMoreSlidersExpanded }
                )
            }
        )

        AnimatedVisibility(
            visible = isMoreSlidersExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                ThresholdSlider(
                    thresholdValue = thresholdValue,
                    onThresholdChanged = onThresholdChanged,
                    isExpanded = isThresholdExpanded,
                    onToggleExpand = { isThresholdExpanded = !isThresholdExpanded }
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                SpeedSlider(
                    speedValue = speedValue,
                    onSpeedChanged = onSpeedChanged,
                    isExpanded = isSpeedExpanded,
                    onToggleExpand = { isSpeedExpanded = !isSpeedExpanded }
                )
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
    val view = LocalView.current
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

        IconButton(onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onToggleExpand()
        }) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Info,
                contentDescription = stringResource(R.string.show_gamma_info),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ThresholdSlider(
    thresholdValue: Float,
    onThresholdChanged: (Float) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val view = LocalView.current
    CardHeader(
        title = stringResource(R.string.threshold_label),
        trailingContent = {
            Text(
                text = if (thresholdValue < 0.001f) stringResource(R.string.disabled) else String.format("%d%%", (thresholdValue * 100).toInt()),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        })

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveSlider(
            value = thresholdValue,
            onValueChange = onThresholdChanged,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onToggleExpand()
        }) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            BodyText(
                text = stringResource(R.string.threshold_description),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                size = 14.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SpeedSlider(
    speedValue: Float,
    onSpeedChanged: (Float) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val view = LocalView.current
    // 1.2f is low speed (left), 0.4f is high speed (right)
    val sliderPos = (1.2f - speedValue) / 0.8f

    CardHeader(
        title = stringResource(R.string.speed_label),
        trailingContent = {
            Text(
                text = String.format("%.2fx", 0.75f / speedValue), // Relative to default
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        })

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveSlider(
            value = sliderPos.coerceIn(0f, 1f),
            onValueChange = { pos ->
                val newSpeed = 1.2f - pos * 0.8f
                onSpeedChanged(newSpeed)
            },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onToggleExpand()
        }) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            BodyText(
                text = stringResource(R.string.speed_description),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                size = 14.sp,
                lineHeight = 22.sp,
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
