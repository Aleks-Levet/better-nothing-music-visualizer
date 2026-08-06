package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.DeviceProfile
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.better.nothing.music.vizualizer.logic.AudioProcessor
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.OptionTile
import com.better.nothing.music.vizualizer.ui.ScreenTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    viewModel: MainViewModel,
    idleBreathingEnabled: Boolean,
    onIdleBreathingEnabledChanged: (Boolean) -> Unit,
    idlePattern: String,
    onIdlePatternChanged: (String) -> Unit,
    disableGlyphsWhenSilent: Boolean,
    onDisableGlyphsWhenSilentChanged: (Boolean) -> Unit,
    padding: PaddingValues = PaddingValues(),
) {
    val uiAmplitudeSyncEnabled by viewModel.uiAmplitudeSyncEnabled.collectAsStateWithLifecycle()
    val flashlightMultiIntensityForced by viewModel.flashlightMultiIntensityForced.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    var showDevModePanel by remember { mutableStateOf(false) }

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
            text = stringResource(R.string.settings_title),
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                showDevModePanel = !showDevModePanel
            }
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinkCard(
                title = stringResource(R.string.about_title),
                icon = Icons.Default.Info,
                onClick = { viewModel.showAbout() },
                modifier = Modifier.weight(1f)
            )
            LinkCard(
                title = "Vizualizer Stats",
                icon = Icons.Default.BarChart,
                onClick = { viewModel.showStats() },
                modifier = Modifier.weight(1f)
            )
        }

        // ── App Theme ───────────────────────────────────────────────────────
        var themeExpanded by remember { mutableStateOf(false) }

        ExpressiveCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        themeExpanded = !themeExpanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.app_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (themeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(visible = themeExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Typography
                    Text(
                        text = stringResource(R.string.typography),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ExpressiveSplitButton(
                        items = listOf("NDot", "NType"),
                        selectedItem = selectedFont,
                        onItemSelection = { viewModel.setSelectedFont(it) },
                        labelProvider = {
                            if (it == "NDot") stringResource(R.string.font_ndot) else stringResource(
                                R.string.font_ntype
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    BodyText(
                        text = stringResource(R.string.typography_help_text),
                        size = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Theme Options
                    val themeOptions = listOf(
                        Triple(
                            "Default",
                            stringResource(R.string.theme_normal),
                            ImageVector.vectorResource(R.drawable.ic_notif_monochrome)
                        ),
                        Triple(
                            "Liquorice Black",
                            stringResource(R.string.theme_liquorice),
                            Icons.Default.DarkMode
                        ),
                        Triple(
                            "Nothing",
                            stringResource(R.string.theme_nothing),
                            ImageVector.vectorResource(R.drawable.ic_nav_glyphs)
                        ),
                        Triple(
                            "Material You",
                            stringResource(R.string.theme_material_you),
                            Icons.Default.Android
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themeOptions.forEach { (key, label, icon) ->
                            val isSelected = selectedTheme == key
                            OptionTile(
                                label = label,
                                icon = icon,
                                isSelected = isSelected,
                                onClick = {
                                    viewModel.setSelectedTheme(key)
                                },
                                modifier = Modifier.height(64.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ── Idle Breathing ──────────────────────────────────────────────────
        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
            ExpressiveCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Air,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.idle_breathing_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.idle_breathing_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Switch(
                        checked = idleBreathingEnabled,
                        onCheckedChange = onIdleBreathingEnabledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(height = 24.dp, width = 48.dp)
                    )
                }

                AnimatedVisibility(visible = idleBreathingEnabled) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.idle_pattern),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val patternOptions = listOf(
                            "pulse" to stringResource(R.string.idle_pattern_pulse),
                            "wave" to stringResource(R.string.idle_pattern_wave),
                            "rain" to stringResource(R.string.idle_pattern_rain),
                            "zebra" to stringResource(R.string.idle_pattern_zebra),
                            "orbit" to stringResource(R.string.idle_pattern_orbit),
                            "heartbeat" to stringResource(R.string.idle_pattern_heartbeat),
                            "scanner" to stringResource(R.string.idle_pattern_cylon)
                        )

                        ExpressiveSplitButton(
                            items = patternOptions.map { it.first },
                            selectedItem = idlePattern,
                            onItemSelection = onIdlePatternChanged,
                            labelProvider = { key ->
                                patternOptions.find { it.first == key }?.second ?: key
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ── Developer Mode ──────────────────────────────────────────────────
        if (showDevModePanel) {
            val devModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()

            ExpressiveCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.developer_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.developer_mode_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Switch(
                        checked = devModeEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setDeveloperModeEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(height = 24.dp, width = 48.dp)
                    )
                }

                AnimatedVisibility(visible = devModeEnabled) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 3. Device Spoofing Toggle
                        val showSpoofing by viewModel.showSpoofingSettings.collectAsStateWithLifecycle()
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setShowSpoofingSettings(!showSpoofing) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Dns,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        stringResource(R.string.spoof_device),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    if (showSpoofing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }

                            AnimatedVisibility(visible = showSpoofing) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val spoofedDevice by viewModel.spoofedDevice.collectAsStateWithLifecycle()
                                    val devices = listOf(
                                        DeviceProfile.DEVICE_NP1,
                                        DeviceProfile.DEVICE_NP2,
                                        DeviceProfile.DEVICE_NP2A,
                                        DeviceProfile.DEVICE_NP3A,
                                        DeviceProfile.DEVICE_NP4A,
                                        DeviceProfile.DEVICE_NP4B,
                                        DeviceProfile.DEVICE_NP4APRO,
                                        DeviceProfile.DEVICE_NP3
                                    )

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExpressiveSplitButton(
                                            items = devices,
                                            selectedItem = spoofedDevice,
                                            onItemSelection = { dev -> viewModel.setSpoofedDevice(dev) },
                                            labelProvider = { dev ->
                                                DeviceProfile.deviceName(dev).replace("Nothing Phone ", "")
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    BodyText(
                                        text = stringResource(R.string.spoof_device_description),
                                        size = 11.sp
                                    )
                                }
                            }
                        }

                        // 4. Locale Spoofing
                        val currentSpoofLocale by viewModel.spoofLocale.collectAsStateWithLifecycle()
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        stringResource(R.string.spoof_locale),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val locales = listOf(
                                "en" to "English",
                                "fr" to "Français",
                                "it" to "Italiano",
                                "de" to "DE",
                                "es" to "Espanol",
                                "ru" to "RU",
                                "tr" to "TR",
                                "pt-BR" to "PT-BR",
                                "zh-CN" to "ZH-CN",
                                "ja" to "JA",
                                "hi" to "HI",
                                "cy" to "CY",
                                null to "System Language"
                            )

                            ExpressiveSplitButton(
                                items = locales.map { it.first },
                                selectedItem = currentSpoofLocale,
                                onItemSelection = { tag -> viewModel.setSpoofLocale(tag) },
                                labelProvider = { tag ->
                                    locales.firstOrNull { it.first == tag }?.second.orEmpty()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            BodyText(
                                text = stringResource(R.string.spoof_locale_description),
                                size = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Audio Processing ────────────────────────────────────────────────
        var processingExpanded by remember { mutableStateOf(false) }
        val fftReadMethod by viewModel.fftReadMethod.collectAsStateWithLifecycle()

        ExpressiveCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        processingExpanded = !processingExpanded
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
                        text = stringResource(R.string.audio_pipeline_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (processingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(visible = processingExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.freq_detection_method),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ExpressiveSplitButton(
                        items = AudioProcessor.ReadMethod.entries,
                        selectedItem = fftReadMethod,
                        onItemSelection = { viewModel.setFftReadMethod(it) },
                        labelProvider = { it.name },
                        modifier = Modifier.fillMaxWidth()
                    )

                    BodyText(
                        text = stringResource(R.string.freq_detection_desc),
                        size = 12.sp
                    )
                }
            }
        }

        // ── Experimental Features ───────────────────────────────────────────
        var experimentalExpanded by remember { mutableStateOf(false) }

        ExpressiveCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        experimentalExpanded = !experimentalExpanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.experimental_features),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (experimentalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(visible = experimentalExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionTile(
                            label = stringResource(R.string.sync_ui_to_beat),
                            icon = Icons.Default.SyncAlt,
                            isSelected = uiAmplitudeSyncEnabled,
                            onClick = { viewModel.setUiAmplitudeSyncEnabled(!uiAmplitudeSyncEnabled) }
                        )
                        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
                            OptionTile(
                                label = stringResource(R.string.disable_glyphs_when_silent_title),
                                icon = Icons.AutoMirrored.Filled.VolumeOff,
                                isSelected = disableGlyphsWhenSilent,
                                onClick = { onDisableGlyphsWhenSilentChanged(!disableGlyphsWhenSilent) }
                            )
                        }
                    }

                    // Notification Button Set
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.notification_controls_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val currentNotifSet by viewModel.notificationButtonSet.collectAsStateWithLifecycle()
                    ExpressiveSplitButton(
                        items = listOf("presets", "controls"),
                        selectedItem = currentNotifSet,
                        onItemSelection = { viewModel.setNotificationButtonSet(it) },
                        labelProvider = {
                            if (it == "presets") stringResource(R.string.notification_button_set_presets) 
                            else stringResource(R.string.notification_button_set_quick_controls)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    BodyText(
                        text = stringResource(R.string.notification_controls_desc),
                        size = 12.sp
                    )

                    // ── UI Amplitude Sync & Visual Toggles (Moved from AudioSetupScreen) ───────────
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "On screen vizualizers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
                    val edgeVisualizerEnabled by viewModel.edgeVisualizerEnabled.collectAsStateWithLifecycle()
                    val lensVisualizerEnabled by viewModel.lensVisualizerEnabled.collectAsStateWithLifecycle()

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionTile(
                            label = stringResource(R.string.nav_overlay),
                            icon = Icons.Default.Layers,
                            isSelected = overlayEnabled,
                            onClick = { viewModel.setOverlayEnabled(!overlayEnabled) }
                        )

                        OptionTile(
                            label = "Edge Visualizer",
                            icon = Icons.Default.BorderOuter,
                            isSelected = edgeVisualizerEnabled,
                            onClick = { viewModel.setEdgeVisualizerEnabled(!edgeVisualizerEnabled) }
                        )

                        OptionTile(
                            label = stringResource(R.string.lens_visualizer),
                            icon = Icons.Default.BlurCircular,
                            isSelected = lensVisualizerEnabled,
                            onClick = { viewModel.setLensVisualizerEnabled(!lensVisualizerEnabled) }
                        )
                        
                        if (viewModel.hasFlashlight) {
                            OptionTile(
                                label = stringResource(R.string.flashlight_multi_intensity_forced_title),
                                icon = Icons.Default.FlashlightOn,
                                isSelected = flashlightMultiIntensityForced,
                                onClick = { viewModel.setFlashlightMultiIntensityForced(!flashlightMultiIntensityForced) }
                            )
                        }
                    }
                }
            }
        }

        // ── Links & Info ────────────────────────────────────────────────────


        LinkCard(
            title = stringResource(R.string.discord_server),
            icon = Icons.Default.Public,
            onClick = { uriHandler.openUri("https://discord.gg/h7DYNttc8K") }
        )


        Spacer(modifier = Modifier.height(85.dp))
    }
}

@Composable
fun LinkCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current

    // 1. Stream raw touch events directly to trigger frame-perfect hardware haptics
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    // Tactile down-press simulation
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                is PressInteraction.Release -> {
                    // Tactile up-release simulation
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
                }
                is PressInteraction.Cancel -> {
                    // Mutes or triggers a light cleanup if the user drags their finger away
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                }
            }
        }
    }

    // 2. Purely visual spring-physics layout tracking
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (isPressed) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surface
        },
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceBright,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
