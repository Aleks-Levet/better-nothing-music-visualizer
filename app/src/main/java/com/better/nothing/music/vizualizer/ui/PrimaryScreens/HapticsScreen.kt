package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.HapticMode
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
fun HapticsScreen(
    hapticMotorEnabled: Boolean,
    onHapticMotorEnabledChanged: (Boolean) -> Unit,
    hapticMode: HapticMode,
    onHapticModeChanged: (HapticMode) -> Unit,
    hapticFreqMin: Float,
    hapticFreqMax: Float,
    onHapticFreqRangeChanged: (Float, Float) -> Unit,
    hapticMultiplier: Float,
    onHapticMultiplierChanged: (Float) -> Unit,
    hapticAudioGain: Float,
    onHapticAudioGainChanged: (Float) -> Unit,
    hapticGamma: Float,
    onHapticGammaChanged: (Float) -> Unit,
    hapticBeatSensitivity: Float,
    onHapticBeatSensitivityChanged: (Float) -> Unit,
    hapticBeatGamma: Float,
    onHapticBeatGammaChanged: (Float) -> Unit,
    hapticAmplitudeProvider: () -> Float,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        ScreenTitle(text = stringResource(R.string.haptics_header))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(
                    title = stringResource(
                        R.string.haptics_amplitude_label,
                        hapticMultiplier
                    )
                )
                ExpressiveSlider(
                    value = hapticMultiplier,
                    onValueChange = onHapticMultiplierChanged,
                    valueRange = 0.3f..1.5f,
                    modifier = Modifier.fillMaxWidth()
                )
                BodyText(
                    text = stringResource(R.string.haptics_motor_multiplier_desc),
                    size = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(
                    title = stringResource(
                        R.string.haptics_frequency_label,
                        hapticFreqMin.toInt(),
                        hapticFreqMax.toInt()
                    )
                )

                val currentRange =
                    invLerpLog(hapticFreqMin, 20f, 1000f)..invLerpLog(hapticFreqMax, 20f, 1000f)

                ExpressiveRangeSlider(
                    value = currentRange,
                    onValueChange = { newRange ->
                        val newMin = lerpLog(newRange.start, 20f, 1000f)
                        val newMax = lerpLog(newRange.endInclusive, 20f, 1000f)

                        if (newMax - newMin >= 10f) {
                            onHapticFreqRangeChanged(newMin, newMax)
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                BodyText(
                    text = stringResource(R.string.haptics_frequency_desc),
                    size = 12.sp
                )
            }

            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(title = stringResource(R.string.haptics_mode_label))
                ExpressiveSplitButton(
                    items = HapticMode.entries,
                    selectedItem = hapticMode,
                    onItemSelection = onHapticModeChanged,
                    labelProvider = { mode ->
                        stringResource(
                            when (mode) {
                                HapticMode.BASS_TO_AMPLITUDE -> R.string.haptics_mode_bass
                                HapticMode.BEAT_DETECTION -> R.string.haptics_mode_beat
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (hapticMode == HapticMode.BASS_TO_AMPLITUDE) {
                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.haptics_audio_gain_label,
                            hapticAudioGain
                        )
                    )
                    ExpressiveSlider(
                        value = hapticAudioGain,
                        onValueChange = onHapticAudioGainChanged,
                        valueRange = 0.5f..4.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.haptics_gamma_label,
                            hapticGamma
                        )
                    )
                    ExpressiveSlider(
                        value = hapticGamma,
                        onValueChange = onHapticGammaChanged,
                        valueRange = 1.0f..3.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (hapticMode == HapticMode.BEAT_DETECTION) {
                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.haptics_sensitivity_label,
                            hapticBeatSensitivity
                        )
                    )
                    ExpressiveSlider(
                        value = hapticBeatSensitivity,
                        onValueChange = onHapticBeatSensitivityChanged,
                        valueRange = 0.3f..6.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.haptics_speed_label,
                            hapticBeatGamma
                        )
                    )
                    ExpressiveSlider(
                        value = hapticBeatGamma,
                        onValueChange = onHapticBeatGammaChanged,
                        valueRange = 4.0f..15.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                BodyText(
                    text = stringResource(R.string.haptics_beat_detection_desc),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                CardHeader(title = "Haptic Monitor")

                val isBeatDetected = isBeatDetectedProvider()
                val hapticAmplitude = hapticAmplitudeProvider()

                val flashColor by animateColorAsState(
                    targetValue = if (isBeatDetected) Color.White else MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.8f
                    ),
                    animationSpec = if (isBeatDetected) snap() else spring(stiffness = Spring.StiffnessVeryLow),
                    label = "flashColor"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    flashColor.copy(alpha = 0.1f * hapticAmplitude),
                                    Color.Transparent
                                ),
                                radius = 300f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MorphingPolygon(
                        isBeatDetected = isBeatDetected,
                        amplitude = hapticAmplitude,
                        color = flashColor,
                        modifier = Modifier.size(110.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(85.dp))
    }
}
