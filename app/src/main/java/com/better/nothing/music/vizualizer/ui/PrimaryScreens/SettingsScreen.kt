package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.content.Intent
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.model.DeviceProfile
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.better.nothing.music.vizualizer.logic.AudioProcessor
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.OptionTile
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.Tab

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    viewModel: MainViewModel,
    idleBreathingEnabled: Boolean,
    onIdleBreathingEnabledChanged: (Boolean) -> Unit,
    idlePattern: String,
    onIdlePatternChanged: (String) -> Unit,
    strobeEnabled: Boolean,
    onStrobeEnabledChanged: (Boolean) -> Unit,
    disableGlyphsWhenSilent: Boolean,
    onDisableGlyphsWhenSilentChanged: (Boolean) -> Unit,
    onGoogleSignIn: () -> Unit,
    padding: PaddingValues = PaddingValues(),
) {
    val uiAmplitudeSyncEnabled by viewModel.uiAmplitudeSyncEnabled.collectAsStateWithLifecycle()
    val flashlightMultiIntensityForced by viewModel.flashlightMultiIntensityForced.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showNicknameDialog by remember { mutableStateOf(false) }
    var newNickname by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { viewModel.uploadProfilePicture(it) }
        }
    )

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val localContext = LocalContext.current
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
                            Icons.Default.BrightnessAuto
                        ),
                        Triple(
                            "Liquorice Black",
                            stringResource(R.string.theme_liquorice),
                            Icons.Default.DarkMode
                        ),
                        Triple(
                            "Nothing",
                            stringResource(R.string.theme_nothing),
                            Icons.Default.Settings
                        ),
                        Triple(
                            "Material You",
                            stringResource(R.string.theme_material_you),
                            Icons.Default.Palette
                        ),
                        Triple(
                            "Music",
                            stringResource(R.string.theme_music),
                            Icons.Default.MusicNote
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
                                    if (key == "Music" && !viewModel.isNotificationAccessGranted()) {
                                        val message =
                                            localContext.getString(R.string.music_theme_notification_access)
                                        Toast.makeText(localContext, message, Toast.LENGTH_LONG)
                                            .show()
                                        localContext.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                    } else {
                                        viewModel.setSelectedTheme(key)
                                    }
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
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = false
                    passwordInput = ""
                },
                title = { Text(stringResource(R.string.developer_access)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.developer_access_desc))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text(stringResource(R.string.password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrect = false
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.verifyDeveloperPassword(passwordInput)) {
                                viewModel.showAnnouncementEditor()
                                showPasswordDialog = false
                                passwordInput = ""
                            } else {
                                val message = localContext.getString(R.string.incorrect_password)
                                Toast.makeText(localContext, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.unlock))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPasswordDialog = false
                        passwordInput = ""
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

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
                        // 1. Announcements
                        val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
                        ExpressiveCard(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Campaign,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.global_announcements),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        stringResource(R.string.global_announcements_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                if (isAdmin || BuildConfig.DEBUG || devModeEnabled) {
                                    Button(
                                        onClick = { 
                                            showPasswordDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        )
                                    ) {
                                        Text(
                                            stringResource(R.string.create),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Shizuku Source Toggle
                        val shizukuUnlocked by viewModel.shizukuSourceUnlocked.collectAsStateWithLifecycle()
                        ExpressiveCard(
                            containerColor = if (shizukuUnlocked) MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.1f
                            ) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (shizukuUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(
                                    alpha = 0.1f
                                )
                            )
                        ) {
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
                                        Icons.Default.Terminal,
                                        null,
                                        tint = if (shizukuUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.6f
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.unlock_shizuku_source),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            stringResource(R.string.unlock_shizuku_source_desc),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Switch(
                                    checked = shizukuUnlocked,
                                    onCheckedChange = { viewModel.setShizukuSourceUnlocked(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.size(height = 24.dp, width = 48.dp)
                                )
                            }
                        }

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
                                null to "System",
                                "en" to "EN",
                                "fr" to "FR",
                                "it" to "IT",
                                "de" to "DE",
                                "es" to "ES",
                                "ru" to "RU",
                                "tr" to "TR",
                                "pt-BR" to "PT-BR",
                                "zh-CN" to "ZH-CN",
                                "ja" to "JA",
                                "hi" to "HI",
                                "cy" to "CY"
                            )

                            ExpressiveSplitButton(
                                items = locales.map { it.first },
                                selectedItem = currentSpoofLocale,
                                onItemSelection = { tag -> viewModel.setSpoofLocale(tag) },
                                labelProvider = { tag ->
                                    // Find the matching visual label from our list of pairs
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
                        if (viewModel.hasFlashlight) {
                            OptionTile(
                                label = stringResource(R.string.flashlight_multi_intensity_forced_title),
                                icon = Icons.Default.FlashlightOn,
                                isSelected = flashlightMultiIntensityForced,
                                onClick = { viewModel.setFlashlightMultiIntensityForced(!flashlightMultiIntensityForced) }
                            )
                        }
                        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
                            OptionTile(
                                label = stringResource(R.string.strobe_mode),
                                icon = Icons.Default.Vibration,
                                isSelected = strobeEnabled,
                                onClick = { onStrobeEnabledChanged(!strobeEnabled) }
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
                }
            }
        }

        // ── Account ─────────────────────────────────────────────────────────
        ExpressiveCard {
            CardHeader(title = "Account")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable(!isAnonymous) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (userProfile?.profilePictureUrl != null) {
                            AsyncImage(
                                model = userProfile?.profilePictureUrl,
                                contentDescription = stringResource(R.string.profile_picture),
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                if (isAnonymous) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        if (!isAnonymous) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(!isAnonymous) {
                                newNickname = userProfile?.displayName ?: ""
                                showNicknameDialog = true
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAnonymous) stringResource(R.string.anonymous_user) else userProfile?.displayName ?: stringResource(R.string.authenticated_user),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!isAnonymous) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Text(
                            text = if (isAnonymous) stringResource(R.string.sync_account_desc) else "Your visualization data is synced.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (isAnonymous) {
                    Button(
                        onClick = { onGoogleSignIn() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_in_with_google))
                    }
                } else {
                    Button(
                        onClick = { viewModel.signOut() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
        }

        // ── Links & Info ────────────────────────────────────────────────────
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinkCard(
                title = stringResource(R.string.discord_server),
                icon = Icons.Default.Public,
                onClick = { uriHandler.openUri("https://discord.gg/h7DYNttc8K") },
                modifier = Modifier.weight(1f)
            )
            LinkCard(
                title = "Vizualizer Stats",
                icon = Icons.Default.BarChart,
                onClick = { viewModel.showStats() },
                modifier = Modifier.weight(1f)
            )
            LinkCard(
                title = stringResource(R.string.about_title),
                icon = Icons.Default.Info,
                onClick = { viewModel.showAbout() },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(85.dp))
    }

    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text(stringResource(R.string.leaderboard_nickname)) },
            text = {
                OutlinedTextField(
                    value = newNickname,
                    onValueChange = { if (it.length <= 20) newNickname = it },
                    label = { Text("Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(newNickname)
                        showNicknameDialog = false
                    },
                    enabled = newNickname.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
                }
            }
        }
    }

    // 2. Purely visual spring-physics layout tracking
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "m3e_link_card_scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(24.dp),
        color = if (isPressed) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surface
        },
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
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
                        modifier = Modifier.size(23.dp)
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