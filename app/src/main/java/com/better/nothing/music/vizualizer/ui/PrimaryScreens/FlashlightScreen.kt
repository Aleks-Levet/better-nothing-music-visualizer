package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.graphics.BlurMaskFilter
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.BeatEngineMode
import com.better.nothing.music.vizualizer.model.TorchMode
import com.better.nothing.music.vizualizer.ui.ActuatorSettingsLayout
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import kotlinx.coroutines.flow.StateFlow

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
    padding: PaddingValues = PaddingValues(),
) {
    val view = LocalView.current

    val isBeatDetected by isBeatDetectedFlow.collectAsStateWithLifecycle()
    val flashlightAmplitude by flashlightAmplitudeFlow.collectAsStateWithLifecycle()
    val motorIntensity by flashlightMotorIntensityFlow.collectAsStateWithLifecycle()

    val hasMultipleIntensities = flashlightIntensityLevels > 1

    ActuatorSettingsLayout(
        title = stringResource(R.string.flashlight_header),
        onTitleLongPress = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        },

        // Card 1: Intensity / Max Intensity slider (if supported) + Frequency Range
        intensityControl = if (hasMultipleIntensities) {
            {
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
        } else null,
        freqTitle = stringResource(
            R.string.flashlight_frequency_label,
            flashlightFreqMin.toInt(),
            flashlightFreqMax.toInt()
        ),
        freqMin = flashlightFreqMin,
        freqMax = flashlightFreqMax,
        freqMinLimit = 20f,
        freqMaxLimit = 1000f,
        onFreqRangeChanged = onFlashlightFreqRangeChanged,
        freqDescription = stringResource(R.string.flashlight_frequency_desc),

        // Card 2: Mode Selection & Sensitivity
        modeTitle = stringResource(R.string.flashlight_mode_label),
        modes = TorchMode.entries,
        selectedMode = flashlightMode,
        onModeSelected = onFlashlightModeChanged,
        modeLabelProvider = { mode ->
            stringResource(
                when (mode) {
                    TorchMode.AMPLITUDE -> R.string.flashlight_mode_amplitude
                    TorchMode.BEAT_DETECTION -> R.string.flashlight_mode_beat
                }
            )
        },
        isBeatDetectionMode = (flashlightMode == TorchMode.BEAT_DETECTION),
        beatSensitivityTitle = stringResource(
            R.string.flashlight_beat_sensitivity_label,
            flashlightBeatSensitivity
        ),
        beatSensitivity = flashlightBeatSensitivity,
        onBeatSensitivityChanged = onFlashlightBeatSensitivityChanged,
        beatSensitivityDescription = stringResource(R.string.flashlight_beat_sensitivity_desc),

        // Card 3: Amplitude Mode Controls (Threshold)
        amplitudeModeContent = {
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
        },

        // Card 4: Beat Engine Controls
        hasMultipleIntensities = hasMultipleIntensities,
        beatEngineMode = flashlightBeatEngineMode,
        onBeatEngineModeChanged = onFlashlightBeatEngineModeChanged,
        beatGammaTitle = stringResource(
            R.string.haptics_speed_label,
            flashlightBeatGamma
        ),
        beatGamma = flashlightBeatGamma,
        onBeatGammaChanged = onFlashlightBeatGammaChanged,
        pulseDurationTitle = stringResource(
            R.string.haptics_duration_label,
            flashlightPulseDurationMs
        ),
        pulseDurationMs = flashlightPulseDurationMs,
        onPulseDurationMsChanged = onFlashlightPulseDurationMsChanged,
        beatDescription = stringResource(R.string.flashlight_duration_desc),

        // Card 5: Monitor Card
        monitorTitle = stringResource(R.string.flashlight_monitor_label),
        isBeatDetected = isBeatDetected,
        amplitude = flashlightAmplitude,
        monitorVisualizer = {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
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

                Canvas(modifier = Modifier.size(200.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val baseRadius = 15.dp.toPx() * dotScale

                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = Color.White.copy(alpha = dotAlpha * 0.3f)
                            isAntiAlias = true
                        }
                        paint.asFrameworkPaint().maskFilter = BlurMaskFilter(
                            150f * dotScale,
                            BlurMaskFilter.Blur.NORMAL
                        )
                        canvas.drawCircle(
                            center = center,
                            radius = baseRadius * 5f,
                            paint = paint
                        )
                    }

                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = Color.White.copy(alpha = dotAlpha * 0.7f)
                            isAntiAlias = true
                        }
                        paint.asFrameworkPaint().maskFilter = BlurMaskFilter(
                            40f * dotScale,
                            BlurMaskFilter.Blur.NORMAL
                        )
                        canvas.drawCircle(
                            center = center,
                            radius = baseRadius * 2f,
                            paint = paint
                        )
                    }

                    drawCircle(
                        color = Color.White,
                        radius = baseRadius,
                        alpha = dotAlpha
                    )
                }
            }
        },
        monitorStatsOverlay = {
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
        },

        padding = padding
    )
}
