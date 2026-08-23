package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.BeatEngineMode
import com.better.nothing.music.vizualizer.model.TorchMode
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
fun FlashlightScreen(
    flashlightEnabled: Boolean,
    onFlashlightEnabledChanged: (Boolean) -> Unit,
    flashlightMode: TorchMode,
    onFlashlightModeChanged: (TorchMode) -> Unit,
    flashlightBeatEngineMode: BeatEngineMode,
    onFlashlightBeatEngineModeChanged: (BeatEngineMode) -> Unit,
    flashlightPulseDurationMs: Int,
    onFlashlightPulseDurationMsChanged: (Int) -> Unit,
    flashlightFreqMin: Float,
    flashlightFreqMax: Float,
    onFlashlightFreqRangeChanged: (Float, Float) -> Unit,
    flashlightThreshold: Float,
    onFlashlightThresholdChanged: (Float) -> Unit,
    flashlightBeatSensitivity: Float,
    onFlashlightBeatSensitivityChanged: (Float) -> Unit,
    flashlightBeatGamma: Float,
    onFlashlightBeatGammaChanged: (Float) -> Unit,
    flashlightIntensityLevels: Int,
    flashlightMaxIntensity: Int,
    onFlashlightMaxIntensityChanged: (Int) -> Unit,
    flashlightCurrentLevel: Int,
    flashlightAmplitudeFlow: StateFlow<Float>,
    flashlightMotorIntensityFlow: StateFlow<Float>,
    isBeatDetectedFlow: StateFlow<Boolean>,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    val scrollState = rememberScrollState()
    val view = androidx.compose.ui.platform.LocalView.current

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
            text = stringResource(R.string.flashlight_header),
            onLongPress = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        )

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

            AnimatedVisibility(flashlightMode == TorchMode.AMPLITUDE) {
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

            AnimatedVisibility(flashlightMode == TorchMode.BEAT_DETECTION) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (flashlightIntensityLevels > 1) {
                        ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                            CardHeader(title = stringResource(R.string.beat_engine_mode_label))
                            ExpressiveSplitButton(
                                items = BeatEngineMode.entries,
                                selectedItem = flashlightBeatEngineMode,
                                onItemSelection = onFlashlightBeatEngineModeChanged,
                                labelProvider = { mode ->
                                    stringResource(
                                        when (mode) {
                                            BeatEngineMode.SMOOTH -> R.string.beat_engine_smooth
                                            BeatEngineMode.SHORT_PULSE -> R.string.beat_engine_short
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

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
            }

            if (flashlightIntensityLevels > 1) {
                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    val displayIntensity = if (flashlightMaxIntensity > 0) flashlightMaxIntensity else flashlightIntensityLevels
                    CardHeader(
                        title = stringResource(
                            R.string.flashlight_intensity_label,
                            displayIntensity
                        )
                    )
                    ExpressiveSlider(
                        value = displayIntensity.toFloat(),
                        onValueChange = { onFlashlightMaxIntensityChanged(it.toInt()) },
                        valueRange = 1f..flashlightIntensityLevels.toFloat(),
                        steps = if (flashlightIntensityLevels > 2) flashlightIntensityLevels - 2 else 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AnimatedVisibility(flashlightMode == TorchMode.BEAT_DETECTION) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (flashlightIntensityLevels > 1 && flashlightBeatEngineMode == BeatEngineMode.SMOOTH) {
                        ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                            CardHeader(
                                title = stringResource(
                                    R.string.haptics_speed_label,
                                    flashlightBeatGamma
                                )
                            )
                            ExpressiveSlider(
                                value = flashlightBeatGamma,
                                onValueChange = onFlashlightBeatGammaChanged,
                                valueRange = 4.0f..15.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                            CardHeader(
                                title = stringResource(
                                    R.string.haptics_duration_label,
                                    flashlightPulseDurationMs
                                )
                            )
                            ExpressiveSlider(
                                value = flashlightPulseDurationMs.toFloat(),
                                onValueChange = { onFlashlightPulseDurationMsChanged(it.toInt()) },
                                valueRange = 5f..200f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            BodyText(
                                text = stringResource(R.string.flashlight_duration_desc),
                                size = 12.sp
                            )
                        }
                    }
                }
            }

            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CardHeader(title = stringResource(R.string.flashlight_monitor_label))

                val isBeatDetected by isBeatDetectedFlow.collectAsStateWithLifecycle()
                val flashlightAmplitude by flashlightAmplitudeFlow.collectAsStateWithLifecycle()
                val motorIntensity by flashlightMotorIntensityFlow.collectAsStateWithLifecycle()

                val flashColor by animateColorAsState(
                    targetValue = if (flashlightCurrentLevel > 0) Color.White else MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.8f
                    ),
                    animationSpec = if (isBeatDetected) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(stiffness = Spring.StiffnessMediumLow),
                    label = "flashColor"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        MorphingPolygon(
                            isBeatDetected = isBeatDetected,
                            amplitude = flashlightAmplitude,
                            color = flashColor,
                            modifier = Modifier.size(110.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glowing white dot
                        val dotScaleTarget = 0.4f + (motorIntensity * 1f)
                        val dotAlphaTarget = 0.2f + (motorIntensity * 0.8f)


                        val dotScale by animateFloatAsState(
                            targetValue = dotScaleTarget,
                            animationSpec = snap(),
                            label = "dotScale"
                        )
                        val dotAlpha by animateFloatAsState(
                            targetValue = dotAlphaTarget,
                            animationSpec = snap(),
                            label = "dotAlpha"
                        )

                        // 1. Massive canvas size (300.dp) so the huge blur doesn't get clipped
                        Canvas(modifier = Modifier.size(200.dp)) {
                            val center = Offset(size.width / 2, size.height / 2)

                            // Keep the actual dot small so the contrast makes the glow look huge
                            val baseRadius = 15.dp.toPx() * dotScale

                            // 2. Outer Layer: Massive, soft atmospheric scatter (The "blinding" aura)
                            drawIntoCanvas { canvas ->
                                val paint = Paint().apply {
                                    // Lower alpha, massive spread
                                    color = Color.White.copy(alpha = dotAlpha * 0.3f)
                                    isAntiAlias = true
                                }
                                paint.asFrameworkPaint().maskFilter = BlurMaskFilter(
                                    150f * dotScale, // Huge blur radius
                                    BlurMaskFilter.Blur.NORMAL
                                )
                                canvas.drawCircle(
                                    center = center,
                                    radius = baseRadius * 5f,
                                    paint = paint
                                )
                            }

                            // 3. Middle Layer: Intense, tight glow (The "corona")
                            drawIntoCanvas { canvas ->
                                val paint = Paint().apply {
                                    // High alpha, tighter spread
                                    color = Color.White.copy(alpha = dotAlpha * 0.7f)
                                    isAntiAlias = true
                                }
                                paint.asFrameworkPaint().maskFilter = BlurMaskFilter(
                                    40f * dotScale, // Medium blur radius
                                    BlurMaskFilter.Blur.NORMAL
                                )
                                canvas.drawCircle(
                                    center = center,
                                    radius = baseRadius * 2f,
                                    paint = paint
                                )
                            }

                            // 4. Core: The pure white, hot center of the bulb
                            drawCircle(
                                color = Color.White,
                                radius = baseRadius,
                                alpha = dotAlpha // Keep this close to 1f for maximum intensity
                            )
                        }
                    }
                }

                // Stats Overlay
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.flashlight_level_stats, flashlightCurrentLevel, flashlightIntensityLevels),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(85.dp))
    }
}
