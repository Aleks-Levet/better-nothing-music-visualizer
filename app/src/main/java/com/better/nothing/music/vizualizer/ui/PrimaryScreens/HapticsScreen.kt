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
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    hapticAmplitudeFlow: StateFlow<Float>,
    isBeatDetectedFlow: StateFlow<Boolean>,
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
                CardHeader(title = stringResource(R.string.haptic_monitor))

                val isBeatDetected by isBeatDetectedFlow.collectAsStateWithLifecycle()
                val hapticAmplitude by hapticAmplitudeFlow.collectAsStateWithLifecycle()

                val flashColor by animateColorAsState(
                    targetValue = if (isBeatDetected) Color.White else MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.8f
                    ),
                    animationSpec = if (isBeatDetected) snap() else spring(stiffness = Spring.StiffnessVeryLow),
                    label = "flashColor"
                )

                Row(
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        MorphingPolygon(
                            isBeatDetected = isBeatDetected,
                            amplitude = hapticAmplitude,
                            color = flashColor,
                            modifier = Modifier.size(110.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        HapticSquigglyLine(
                            amplitude = hapticAmplitude,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(85.dp))
    }
}

@Composable
fun HapticSquigglyLine(
    amplitude: Float,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggly")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val points = 50
        val path = androidx.compose.ui.graphics.Path()

        val maxSquiggleWidth = width * 0.8f
        val currentSquiggleWidth = maxSquiggleWidth * amplitude

        for (i in 0..points) {
            val progress = i.toFloat() / points
            val y = progress * height
            val x = width / 2 + Math.sin(progress * 4 * Math.PI + phase).toFloat() * currentSquiggleWidth / 2

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}
