package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.TorchMode
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveRangeSlider
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MorphingPolygon
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.invLerpLog
import com.better.nothing.music.vizualizer.ui.lerpLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen(
    flashlightEnabled: Boolean,
    onFlashlightEnabledChanged: (Boolean) -> Unit,
    flashlightMode: TorchMode,
    onFlashlightModeChanged: (TorchMode) -> Unit,
    flashlightFreqMin: Float,
    flashlightFreqMax: Float,
    onFlashlightFreqRangeChanged: (Float, Float) -> Unit,
    flashlightThreshold: Float,
    onFlashlightThresholdChanged: (Float) -> Unit,
    flashlightSpeedMs: Float,
    onFlashlightSpeedMsChanged: (Float) -> Unit,
    flashlightBeatSensitivity: Float,
    onFlashlightBeatSensitivityChanged: (Float) -> Unit,
    flashlightIntensityLevels: Int,
    flashlightCurrentLevel: Int,
    flashlightAmplitudeProvider: () -> Float,
    isBeatDetectedProvider: () -> Boolean,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = LocalAppSpacing.current.edge)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        ScreenTitle(text = stringResource(R.string.flashlight_header))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(
                    title = stringResource(
                        R.string.flashlight_frequency_label,
                        flashlightFreqMin.toInt(),
                        flashlightFreqMax.toInt()
                    )
                )

                val currentRange =
                    invLerpLog(flashlightFreqMin, 20f, 1000f)..invLerpLog(flashlightFreqMax, 20f, 1000f)

                ExpressiveRangeSlider(
                    value = currentRange,
                    onValueChange = { newRange ->
                        val newMin = lerpLog(newRange.start, 20f, 1000f)
                        val newMax = lerpLog(newRange.endInclusive, 20f, 1000f)

                        if (newMax - newMin >= 10f) {
                            onFlashlightFreqRangeChanged(newMin, newMax)
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                BodyText(
                    text = stringResource(R.string.flashlight_frequency_desc),
                    size = 12.sp
                )
            }

            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(title = stringResource(R.string.flashlight_mode_label))
                ExpressiveSplitButton(
                    items = TorchMode.entries,
                    selectedItem = flashlightMode,
                    onItemSelection = onFlashlightModeChanged,
                    labelProvider = { mode ->
                        stringResource(
                            when (mode) {
                                TorchMode.AMPLITUDE -> R.string.flashlight_mode_amplitude
                                TorchMode.BEAT_DETECTION -> R.string.flashlight_mode_beat
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (flashlightMode == TorchMode.AMPLITUDE) {
                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.flashlight_threshold_label,
                            flashlightThreshold
                        )
                    )
                    ExpressiveSlider(
                        value = flashlightThreshold,
                        onValueChange = onFlashlightThresholdChanged,
                        valueRange = 0.05f..0.8f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BodyText(
                        text = stringResource(R.string.flashlight_threshold_desc),
                        size = 12.sp
                    )
                }
            }

            if (flashlightMode == TorchMode.BEAT_DETECTION) {
                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.flashlight_beat_sensitivity_label,
                            flashlightBeatSensitivity
                        )
                    )
                    ExpressiveSlider(
                        value = flashlightBeatSensitivity,
                        onValueChange = onFlashlightBeatSensitivityChanged,
                        valueRange = 0.3f..6.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BodyText(
                        text = stringResource(R.string.flashlight_beat_sensitivity_desc),
                        size = 12.sp
                    )
                }
            }

            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(
                    title = stringResource(
                        R.string.flashlight_speed_label,
                        flashlightSpeedMs
                    )
                )
                ExpressiveSlider(
                    value = flashlightSpeedMs,
                    onValueChange = onFlashlightSpeedMsChanged,
                    valueRange = 40f..150f,
                    modifier = Modifier.fillMaxWidth()
                )
                BodyText(
                    text = stringResource(R.string.flashlight_speed_desc),
                    size = 12.sp
                )
            }

            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)
            ) {
                CardHeader(title = stringResource(R.string.flashlight_monitor_label))

                val isBeatDetected = isBeatDetectedProvider()
                val flashlightAmplitude = flashlightAmplitudeProvider()

                val flashColor by animateColorAsState(
                    targetValue = if (flashlightCurrentLevel > 0) Color.White else MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.8f
                    ),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "flashColor"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    flashColor.copy(alpha = 0.1f * flashlightAmplitude),
                                    Color.Transparent
                                ),
                                radius = 300f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MorphingPolygon(
                        isBeatDetected = isBeatDetected,
                        amplitude = flashlightAmplitude,
                        color = flashColor,
                        modifier = Modifier.size(110.dp)
                    )

                    // Stats Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "LEVEL: $flashlightCurrentLevel / $flashlightIntensityLevels",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(85.dp))
    }
}
