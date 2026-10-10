package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.BeatEngineMode
import com.better.nothing.music.vizualizer.model.HapticMode
import com.better.nothing.music.vizualizer.ui.ActuatorSettingsLayout
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.MainViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HapticsScreen(
    viewModel: MainViewModel,
    hapticMotorEnabled: Boolean,
    onHapticMotorEnabledChanged: (Boolean) -> Unit,
    hapticMode: HapticMode,
    onHapticModeChanged: (HapticMode) -> Unit,
    hapticBeatEngineMode: BeatEngineMode,
    onHapticBeatEngineModeChanged: (BeatEngineMode) -> Unit,
    hapticPulseDurationMs: Int,
    onHapticPulseDurationMsChanged: (Int) -> Unit,
    hasAmplitudeControl: Boolean,
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
    hapticMotorIntensityFlow: StateFlow<Float>,
    isBeatDetectedFlow: StateFlow<Boolean>,
    padding: PaddingValues = PaddingValues(),
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    val isBeatDetected by isBeatDetectedFlow.collectAsStateWithLifecycle()
    val hapticAmplitude by hapticAmplitudeFlow.collectAsStateWithLifecycle()
    val motorIntensity by hapticMotorIntensityFlow.collectAsStateWithLifecycle()

    ActuatorSettingsLayout(
        title = stringResource(R.string.haptics_header),
        onTitleClick = {
            viewModel.logEasterEggEvent("easter_egg_haptics")
            android.widget.Toast.makeText(context, context.getString(R.string.toast_haptics_title_tap), android.widget.Toast.LENGTH_SHORT).show()
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                null
            }

            if (Build.VERSION.SDK_INT >= 33) {
                val attr = android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_MEDIA)
                    .build()
                if (effect != null) {
                    vibrator.vibrate(effect, attr)
                }
            } else {
                val audioAttr = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (effect != null) {
                    vibrator.vibrate(effect, audioAttr)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(1000)
                }
            }
        },
        onTitleLongPress = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        },

        // Card 1: Multiplier & Frequency Range
        intensityControl = {
            CardHeader(
                title = stringResource(
                    R.string.haptics_amplitude_label1,
                    hapticMultiplier
                )
            )
            ExpressiveSlider(
                value = hapticMultiplier,
                onValueChange = onHapticMultiplierChanged,
                valueRange = 0.3f..1.5f,
                modifier = Modifier.fillMaxWidth()
            )
        },
        freqTitle = stringResource(
            R.string.haptics_frequency_label_b,
            hapticFreqMin.toInt(),
            hapticFreqMax.toInt()
        ),
        freqMin = hapticFreqMin,
        freqMax = hapticFreqMax,
        freqMinLimit = 20f,
        freqMaxLimit = 2500f,
        onFreqRangeChanged = onHapticFreqRangeChanged,

        // Card 2: Mode Selection & Sensitivity
        modeTitle = stringResource(R.string.haptics_mode_label),
        modes = HapticMode.entries,
        selectedMode = hapticMode,
        onModeSelected = onHapticModeChanged,
        modeLabelProvider = { mode ->
            stringResource(
                when (mode) {
                    HapticMode.BASS_TO_AMPLITUDE -> R.string.haptics_mode_bass
                    HapticMode.BEAT_DETECTION -> R.string.haptics_mode_beat
                }
            )
        },
        isBeatDetectionMode = (hapticMode == HapticMode.BEAT_DETECTION),
        beatSensitivityTitle = stringResource(
            R.string.haptics_sensitivity_label,
            hapticBeatSensitivity
        ),
        beatSensitivity = hapticBeatSensitivity,
        onBeatSensitivityChanged = onHapticBeatSensitivityChanged,

        // Card 3: Amplitude Mode Controls (Audio Gain & Gamma)
        amplitudeModeContent = {
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

            Spacer(modifier = Modifier.height(3.dp))

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
        },

        // Card 4: Beat Engine Controls
        hasMultipleIntensities = hasAmplitudeControl,
        beatEngineMode = hapticBeatEngineMode,
        onBeatEngineModeChanged = onHapticBeatEngineModeChanged,
        beatGammaTitle = stringResource(
            R.string.haptics_speed_label,
            hapticBeatGamma
        ),
        beatGamma = hapticBeatGamma,
        onBeatGammaChanged = onHapticBeatGammaChanged,
        pulseDurationTitle = stringResource(
            R.string.haptics_duration_label,
            hapticPulseDurationMs
        ),
        pulseDurationMs = hapticPulseDurationMs,
        onPulseDurationMsChanged = onHapticPulseDurationMsChanged,
        beatDescription = stringResource(R.string.haptics_beat_detection_1desc),

        // Card 5: Monitor Card
        monitorTitle = stringResource(R.string.haptic_monito),
        isBeatDetected = isBeatDetected,
        amplitude = hapticAmplitude,
        monitorVisualizer = {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                HapticSquigglyLine(
                    amplitude = motorIntensity,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },

        padding = padding
    )
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
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height * (1f - (amplitude * 0.25f))
        val yOffset = (size.height - height) / 2f
        val points = 45
        val path = androidx.compose.ui.graphics.Path()

        val maxSquiggleWidth = width * 0.8f
        val currentSquiggleWidth = maxSquiggleWidth * amplitude

        for (i in 0..points) {
            val progress = i.toFloat() / points
            val y = yOffset + progress * height
            val x = width / 2 + Math.sin(progress * 4 * Math.PI + phase).toFloat() * currentSquiggleWidth / 2

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = (5 + (amplitude * 4)).dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}
