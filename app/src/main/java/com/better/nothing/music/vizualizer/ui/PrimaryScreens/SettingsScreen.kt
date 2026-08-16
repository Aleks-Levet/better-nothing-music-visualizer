package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.logic.AudioProcessor
import com.better.nothing.music.vizualizer.model.DeviceProfile
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.ExpandableExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.ExpressiveSwitch
import com.better.nothing.music.vizualizer.ui.LinkCard
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
    onOverlayPermissionRequest: () -> Unit,
    padding: PaddingValues = PaddingValues(),
    isTablet: Boolean = false,
) {
    val uiAmplitudeSyncEnabled by viewModel.uiAmplitudeSyncEnabled.collectAsStateWithLifecycle()
    val idleBrightness by viewModel.idleBrightness.collectAsStateWithLifecycle()
    val idleBackgroundBrightness by viewModel.idleBackgroundBrightness.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val restrictedLocales = listOf("hi", "ar", "ja", "ru", "zh")
    val currentLocale = configuration.locales[0].language
    val isRestrictedLocale = restrictedLocales.contains(currentLocale)
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val devModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()

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
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                viewModel.setDeveloperModeEnabled(!devModeEnabled)
            }
        )

        var expandedCardId by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LinkCard(
                title = stringResource(R.string.about_title),
                icon = Icons.Default.Info,
                onClick = { viewModel.showAbout() }
            )
            LinkCard(
                title = stringResource(R.string.vizualizer_stats),
                icon = Icons.Default.BarChart,
                onClick = { viewModel.showStats() }
            )
        }

        // ── App Theme ───────────────────────────────────────────────────────
        ExpandableExpressiveCard(
            title = stringResource(R.string.app_theme),
            icon = Icons.Default.Palette,
            expanded = expandedCardId == "theme",
            onExpandedChange = { expandedCardId = if (it) "theme" else null }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    // Typography
                    if (!isRestrictedLocale) {
                        ExpressiveSplitButton(
                            items = listOf("NDot", "NType", "Google Sans Flex"),
                            selectedItem = selectedFont,
                            onItemSelection = { viewModel.setSelectedFont(it) },
                            labelProvider = {
                                when (it) {
                                    "NDot" -> stringResource(R.string.font_ndot)
                                    "NType" -> stringResource(R.string.font_ntype)
                                    "Google Sans Flex" -> stringResource(R.string.font_google_sans)
                                    else -> it
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

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

                    // Tablet Mode Settings
                    if (isTablet) {
                        val tabletTabWidth by viewModel.tabletTabWidth.collectAsStateWithLifecycle()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.tablet_width_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ExpressiveSplitButton(
                            items = listOf(400, 500, 600, 700),
                            selectedItem = tabletTabWidth,
                            onItemSelection = { viewModel.setTabletTabWidth(it) },
                            labelProvider = {
                                "${it}dp"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxButtonsPerRow = 4
                        )
                    }
            }
        }

        // ── Idle Breathing ──────────────────────────────────────────────────
        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
            ExpandableExpressiveCard(
                title = stringResource(R.string.idle_breathing_title),
                icon = Icons.Default.Air,
                expanded = expandedCardId == "idle",
                onExpandedChange = { expandedCardId = if (it) "idle" else null },
                trailingContent = {
                    ExpressiveSwitch(
                        checked = idleBreathingEnabled,
                        onCheckedChange = onIdleBreathingEnabledChanged
                    )
                }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.idle_breathing_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

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

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.idle_pulse_brightness, (idleBrightness * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveSlider(
                        value = idleBrightness,
                        onValueChange = { viewModel.setIdleBrightness(it) },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.idle_background_brightness, (idleBackgroundBrightness * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveSlider(
                        value = idleBackgroundBrightness,
                        onValueChange = { viewModel.setIdleBackgroundBrightness(it) },
                        valueRange = 0.0f..0.15f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Developer Mode ──────────────────────────────────────────────────
        AnimatedVisibility(devModeEnabled) {
            ExpandableExpressiveCard(
                title = stringResource(R.string.developer_mode),
                icon = Icons.Default.Code,
                expanded = expandedCardId == "dev",
                onExpandedChange = { expandedCardId = if (it) "dev" else null },
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 3. Device Spoofing
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                        }

                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val spoofedDevice by viewModel.spoofedDevice.collectAsStateWithLifecycle()
                            val devices = listOf(
                                DeviceProfile.DEVICE_UNKNOWN,
                                DeviceProfile.DEVICE_NP1,
                                DeviceProfile.DEVICE_NP2,
                                DeviceProfile.DEVICE_NP2A,
                                DeviceProfile.DEVICE_NP3A,
                                DeviceProfile.DEVICE_NP4A,
                                DeviceProfile.DEVICE_NP4B,
                                DeviceProfile.DEVICE_NP4APRO,
                                DeviceProfile.DEVICE_NP3
                            )

                            ExpressiveSplitButton(
                                items = devices,
                                selectedItem = spoofedDevice,
                                onItemSelection = { dev -> viewModel.setSpoofedDevice(dev) },
                                labelProvider = { dev ->
                                    DeviceProfile.shortdeviceName(dev)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                maxButtonsPerRow = 4
                            )
                            BodyText(
                                text = stringResource(R.string.spoof_device_description),
                                size = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Language Selection ──────────────────────────────────────────────
        val currentSpoofLocale by viewModel.spoofLocale.collectAsStateWithLifecycle()
        ExpandableExpressiveCard(
            title = stringResource(R.string.select_language),
            icon = Icons.Default.Language,
            expanded = expandedCardId == "locale",
            onExpandedChange = { expandedCardId = if (it) "locale" else null },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val locales = listOf(
                    "en" to "🇺🇸 English",
                    "fr" to "🇫🇷 Français",
                    "it" to "🇮🇹 Italiano",
                    "de" to "🇩🇪 Deutsch",
                    "es" to "🇪🇸 Español",
                    "ru" to "🇷🇺 Русский",
                    "tr" to "🇹🇷 Türkçe",
                    "pt-BR" to "🇧🇷 Português",
                    "zh-CN" to "🇨🇳 中文",
                    "ja" to "🇯🇵 日本語",
                    "hi" to "🇮🇳 हिन्दी",
                    "cy" to "🏴󠁧󠁢󠁷󠁬󠁳󠁿 Cymraeg",
                    "ar" to "🇸🇦 العربية",
                    null to "🌐 " + stringResource(R.string.system_language)
                )

                ExpressiveSplitButton(
                    items = locales.map { it.first },
                    selectedItem = currentSpoofLocale,
                    onItemSelection = { tag -> viewModel.setSpoofLocale(tag) },
                    labelProvider = { tag ->
                        locales.firstOrNull { it.first == tag }?.second.orEmpty()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxButtonsPerRow = 3
                )
                BodyText(
                    text = stringResource(R.string.spoof_locale_description),
                    size = 11.sp
                )
            }
        }



        // ── Experimental Features ───────────────────────────────────────────
        ExpandableExpressiveCard(
            title = stringResource(R.string.experimental_features),
            icon = Icons.Default.Tune,
            expanded = expandedCardId == "experimental",
            onExpandedChange = { expandedCardId = if (it) "experimental" else null }
        ) {
            val alternateGlyphVizEnabled by viewModel.alternateGlyphVizEnabled.collectAsStateWithLifecycle()
            val highQualityAnalysis by viewModel.highQualityAnalysis.collectAsStateWithLifecycle()
            val onScreenVisualizersEnabled by viewModel.onScreenVisualizersEnabled.collectAsStateWithLifecycle()

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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

                OptionTile(
                    label = stringResource(R.string.higher_quality_audio_analysis),
                    icon = Icons.Default.HighQuality,
                    isSelected = highQualityAnalysis,
                    onClick = { viewModel.setHighQualityAnalysis(!highQualityAnalysis) }
                )

                if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
                    OptionTile(
                        label = stringResource(R.string.alternate_glyph_viz),
                        icon = Icons.Default.Bolt,
                        isSelected = alternateGlyphVizEnabled,
                        onClick = { viewModel.setAlternateGlyphVizEnabled(!alternateGlyphVizEnabled) }
                    )
                }

                OptionTile(
                    label = stringResource(R.string.enable_on_screen_visualizers),
                    icon = Icons.Default.Layers,
                    isSelected = onScreenVisualizersEnabled,
                    onClick = {
                        viewModel.setOnScreenVisualizersEnabled(!onScreenVisualizersEnabled, context, onOverlayPermissionRequest)
                    }
                )
            }
            val fftReadMethod by viewModel.fftReadMethod.collectAsStateWithLifecycle()

            ExpandableExpressiveCard(
                title = stringResource(R.string.audio_pipeline_title),
                icon = Icons.Default.GraphicEq,
                expanded = expandedCardId == "processing",
                onExpandedChange = { expandedCardId = if (it) "processing" else null }
            ) {
                Column(
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

        // ── Links & Info ────────────────────────────────────────────────────

        LinkCard(
            title = stringResource(R.string.discord_server),
            icon = ImageVector.vectorResource(id = R.drawable.ic_discord),
            onClick = { uriHandler.openUri("https://discord.gg/h7DYNttc8K") }
        )


        Spacer(modifier = Modifier.height(85.dp))
    }
}

