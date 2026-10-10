package com.better.nothing.music.vizualizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.BeatEngineMode

@Composable
fun <M> ActuatorSettingsLayout(
    title: String,
    onTitleClick: (() -> Unit)? = null,
    onTitleLongPress: (() -> Unit)? = null,

    // Card 1: Intensity / Multiplier + Frequency Range
    intensityControl: (@Composable ColumnScope.() -> Unit)? = null,
    freqTitle: String,
    freqMin: Float,
    freqMax: Float,
    freqMinLimit: Float = 20f,
    freqMaxLimit: Float = 2500f,
    onFreqRangeChanged: (Float, Float) -> Unit,
    freqDescription: String? = null,

    // Card 2: Mode Selection & Beat Sensitivity
    modeTitle: String,
    modes: List<M>,
    selectedMode: M,
    onModeSelected: (M) -> Unit,
    modeLabelProvider: @Composable (M) -> String,
    isBeatDetectionMode: Boolean,
    beatSensitivityTitle: String,
    beatSensitivity: Float,
    onBeatSensitivityChanged: (Float) -> Unit,
    beatSensitivityRange: ClosedFloatingPointRange<Float> = 0.3f..6.0f,
    beatSensitivityDescription: String? = null,

    // Card 3: Amplitude Mode Specific Controls
    amplitudeModeContent: (@Composable ColumnScope.() -> Unit)? = null,

    // Card 4: Beat Engine Controls
    hasMultipleIntensities: Boolean,
    beatEngineMode: BeatEngineMode,
    onBeatEngineModeChanged: (BeatEngineMode) -> Unit,
    beatGammaTitle: String,
    beatGamma: Float,
    onBeatGammaChanged: (Float) -> Unit,
    beatGammaRange: ClosedFloatingPointRange<Float> = 4.0f..15.0f,
    pulseDurationTitle: String,
    pulseDurationMs: Int,
    onPulseDurationMsChanged: (Int) -> Unit,
    pulseDurationRange: ClosedFloatingPointRange<Float> = 5f..200f,
    beatDescription: String? = null,

    // Card 5: Monitor Card
    monitorTitle: String,
    isBeatDetected: Boolean,
    amplitude: Float,
    monitorVisualizer: @Composable BoxScope.() -> Unit,
    monitorStatsOverlay: (@Composable ColumnScope.() -> Unit)? = null,

    padding: PaddingValues = PaddingValues(),
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

        ScreenTitle(
            text = title,
            onClick = onTitleClick,
            onLongPress = onTitleLongPress
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CARD 1: Intensity / Multiplier & Frequency Range
            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                if (intensityControl != null) {
                    intensityControl()
                    Spacer(modifier = Modifier.height(15.dp))
                }

                CardHeader(title = freqTitle)

                val currentRange = invLerpLog(freqMin, freqMinLimit, freqMaxLimit)..invLerpLog(freqMax, freqMinLimit, freqMaxLimit)

                ExpressiveRangeSlider(
                    value = currentRange,
                    onValueChange = { newRange ->
                        val newMin = lerpLog(newRange.start, freqMinLimit, freqMaxLimit)
                        val newMax = lerpLog(newRange.endInclusive, freqMinLimit, freqMaxLimit)

                        if (newMax - newMin >= 10f) {
                            onFreqRangeChanged(newMin, newMax)
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                if (freqDescription != null) {
                    BodyText(
                        text = freqDescription,
                        size = 12.sp
                    )
                }
            }

            // CARD 2: Mode Selection & Sensitivity
            ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(title = modeTitle)
                ExpressiveSplitButton(
                    items = modes,
                    selectedItem = selectedMode,
                    onItemSelection = onModeSelected,
                    labelProvider = modeLabelProvider,
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(isBeatDetectionMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Spacer(modifier = Modifier.height(15.dp))

                        CardHeader(title = beatSensitivityTitle)
                        ExpressiveSlider(
                            value = beatSensitivity,
                            onValueChange = onBeatSensitivityChanged,
                            valueRange = beatSensitivityRange,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (beatSensitivityDescription != null) {
                            BodyText(
                                text = beatSensitivityDescription,
                                size = 12.sp
                            )
                        }
                    }
                }
            }

            // CARD 3: Amplitude Mode Settings
            AnimatedVisibility(!isBeatDetectionMode) {
                if (amplitudeModeContent != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                            amplitudeModeContent()
                        }
                    }
                }
            }

            // CARD 4: Beat Engine Controls
            AnimatedVisibility(isBeatDetectionMode) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(title = stringResource(R.string.beat_engine_mode_label))
                        if (hasMultipleIntensities) {
                            ExpressiveSplitButton(
                                items = BeatEngineMode.entries,
                                selectedItem = beatEngineMode,
                                onItemSelection = onBeatEngineModeChanged,
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

                        AnimatedVisibility(beatEngineMode == BeatEngineMode.SMOOTH && hasMultipleIntensities) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Spacer(modifier = Modifier.height(15.dp))
                                CardHeader(title = beatGammaTitle)
                                ExpressiveSlider(
                                    value = beatGamma,
                                    onValueChange = onBeatGammaChanged,
                                    valueRange = beatGammaRange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        AnimatedVisibility(!(beatEngineMode == BeatEngineMode.SMOOTH && hasMultipleIntensities)) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Spacer(modifier = Modifier.height(15.dp))
                                CardHeader(title = pulseDurationTitle)
                                ExpressiveSlider(
                                    value = pulseDurationMs.toFloat(),
                                    onValueChange = { onPulseDurationMsChanged(it.toInt()) },
                                    valueRange = pulseDurationRange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (beatDescription != null) {
                        BodyText(
                            text = beatDescription,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // CARD 5: Monitor Card
            ExpressiveCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CardHeader(title = monitorTitle)

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
                        .height(220.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        MorphingPolygon(
                            isBeatDetected = isBeatDetected,
                            amplitude = amplitude,
                            color = flashColor,
                            modifier = Modifier.size(110.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                        content = monitorVisualizer
                    )
                }

                if (monitorStatsOverlay != null) {
                    monitorStatsOverlay()
                }
            }
        }

        Spacer(modifier = Modifier.height(85.dp))
    }
}
