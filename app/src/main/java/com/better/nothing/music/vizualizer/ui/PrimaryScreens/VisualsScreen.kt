package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.ScreenTitle

@Composable
fun VisualsScreen(
    viewModel: MainViewModel,
    overlayEnabled: Boolean,
    onOverlayEnabledChanged: (Boolean) -> Unit,
    onOverlayPermissionRequest: () -> Unit,
    padding: PaddingValues = PaddingValues(),
) {
    val overlayWidth by viewModel.overlayWidth.collectAsStateWithLifecycle()
    val overlayHeight by viewModel.overlayHeight.collectAsStateWithLifecycle()
    val overlayHeightBottom by viewModel.overlayHeightBottom.collectAsStateWithLifecycle()
    val overlayYOffset by viewModel.overlayYOffset.collectAsStateWithLifecycle()
    val overlaySensitivity by viewModel.overlaySensitivity.collectAsStateWithLifecycle()
    val overlaySensitivityBottom by viewModel.overlaySensitivityBottom.collectAsStateWithLifecycle()
    val overlayTopEnabled by viewModel.overlayTopEnabled.collectAsStateWithLifecycle()
    val overlayBottomEnabled by viewModel.overlayBottomEnabled.collectAsStateWithLifecycle()
    
    val edgeVisualizerEnabled by viewModel.edgeVisualizerEnabled.collectAsStateWithLifecycle()
    val edgeThickness by viewModel.edgeThickness.collectAsStateWithLifecycle()
    val edgeSensitivity by viewModel.edgeSensitivity.collectAsStateWithLifecycle()
    val edgeBarCountHoriz by viewModel.edgeBarCountHoriz.collectAsStateWithLifecycle()
    val edgeBarCountVert by viewModel.edgeBarCountVert.collectAsStateWithLifecycle()
    val edgeCornerRadius by viewModel.edgeCornerRadius.collectAsStateWithLifecycle()
    val edgeTopEnabled by viewModel.edgeTopEnabled.collectAsStateWithLifecycle()
    val edgeBottomEnabled by viewModel.edgeBottomEnabled.collectAsStateWithLifecycle()
    
    val lensEnabled by viewModel.lensVisualizerEnabled.collectAsStateWithLifecycle()
    val lensRadius by viewModel.lensVisualizerRadius.collectAsStateWithLifecycle()
    val lensX by viewModel.lensVisualizerX.collectAsStateWithLifecycle()
    val lensY by viewModel.lensVisualizerY.collectAsStateWithLifecycle()
    val lensBarWidth by viewModel.lensVisualizerBarWidth.collectAsStateWithLifecycle()
    val lensMaxHeight by viewModel.lensVisualizerMaxHeight.collectAsStateWithLifecycle()
    val lensBarCount by viewModel.lensVisualizerBarCount.collectAsStateWithLifecycle()
    val lensSensitivity by viewModel.lensVisualizerSensitivity.collectAsStateWithLifecycle()

    val context = LocalContext.current
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

        ScreenTitle(text = stringResource(R.string.tab_visuals))

        // ── Overlay Visualizer ──────────────────────────────────────────────
        ExpressiveCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.nav_overlay),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = overlayEnabled) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Width Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overlay Width",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${overlayWidth}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = overlayWidth.toFloat(),
                        onValueChange = { viewModel.setOverlayWidth(it.toInt()) },
                        valueRange = 40f..600f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Y Offset Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vertical Position",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${overlayYOffset}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = overlayYOffset.toFloat(),
                        onValueChange = { viewModel.setOverlayYOffset(it.toInt()) },
                        valueRange = -300f..300f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Top Segment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = overlayTopEnabled,
                            onCheckedChange = { viewModel.setOverlayTopEnabled(it) }
                        )
                        Text(
                            text = "Top Edge Overlay",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (overlayTopEnabled) {
                        // Top Height Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bar Height",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${overlayHeight}dp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        ExpressiveSlider(
                            value = overlayHeight.toFloat(),
                            onValueChange = { viewModel.setOverlayHeight(it.toInt()) },
                            valueRange = 1f..128f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Top Sensitivity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sensitivity",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = String.format("%.2fx", overlaySensitivity),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        ExpressiveSlider(
                            value = overlaySensitivity,
                            onValueChange = { viewModel.setOverlaySensitivity(it) },
                            valueRange = 0.01f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Bottom Segment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = overlayBottomEnabled,
                            onCheckedChange = { viewModel.setOverlayBottomEnabled(it) }
                        )
                        Text(
                            text = "Bottom Edge Overlay",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (overlayBottomEnabled) {
                        // Bottom Height Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bar Height",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${overlayHeightBottom}dp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        ExpressiveSlider(
                            value = overlayHeightBottom.toFloat(),
                            onValueChange = { viewModel.setOverlayHeightBottom(it.toInt()) },
                            valueRange = 1f..128f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Bottom Sensitivity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sensitivity",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = String.format("%.2fx", overlaySensitivityBottom),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        ExpressiveSlider(
                            value = overlaySensitivityBottom,
                            onValueChange = { viewModel.setOverlaySensitivityBottom(it) },
                            valueRange = 0.01f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ── Edge Visualizer ──────────────────────────────────────────────
        ExpressiveCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Edge Visualizer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = edgeVisualizerEnabled) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Edge Bar Height",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${edgeThickness}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = edgeThickness.toFloat(),
                        onValueChange = { viewModel.setEdgeThickness(it.toInt()) },
                        valueRange = 1f..128f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = edgeTopEnabled,
                            onCheckedChange = { viewModel.setEdgeTopEnabled(it) }
                        )
                        Text(
                            text = "Top Edge Segment",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = edgeBottomEnabled,
                            onCheckedChange = { viewModel.setEdgeBottomEnabled(it) }
                        )
                        Text(
                            text = "Bottom Edge Segment",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Screen Corner Radius",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${edgeCornerRadius.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = edgeCornerRadius,
                        onValueChange = { viewModel.setEdgeCornerRadius(it) },
                        valueRange = 0f..60f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Horizontal Bar Count",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$edgeBarCountHoriz",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = edgeBarCountHoriz.toFloat(),
                        onValueChange = { viewModel.setEdgeBarCountHoriz(it.toInt()) },
                        valueRange = 4f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vertical Bar Count",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$edgeBarCountVert",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = edgeBarCountVert.toFloat(),
                        onValueChange = { viewModel.setEdgeBarCountVert(it.toInt()) },
                        valueRange = 4f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Edge Sensitivity",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format("%.2fx", edgeSensitivity),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = edgeSensitivity,
                        onValueChange = { viewModel.setEdgeSensitivity(it) },
                        valueRange = 0.01f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Lens Visualizer ──────────────────────────────────────────────
        ExpressiveCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Lens Visualizer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = lensEnabled) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lens Radius",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensRadius.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = lensRadius,
                        onValueChange = { viewModel.setLensVisualizerRadius(it) },
                        valueRange = 2f..20f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "X Position",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format("%.2f", lensX),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = lensX,
                        onValueChange = { viewModel.setLensVisualizerX(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Y Position",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format("%.2f", lensY),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = lensY,
                        onValueChange = { viewModel.setLensVisualizerY(it) },
                        valueRange = -0.1f..1.1f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bar Width",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensBarWidth.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = lensBarWidth,
                        onValueChange = { viewModel.setLensVisualizerBarWidth(it) },
                        valueRange = 1f..10f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Max Height",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensMaxHeight.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = lensMaxHeight,
                        onValueChange = { viewModel.setLensVisualizerMaxHeight(it) },
                        valueRange = 5f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bar Count",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensBarCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = lensBarCount.divideByToOne(),
                        onValueChange = { viewModel.setLensVisualizerBarCount(it.toInt()) },
                        valueRange = 8f..48f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sensitivity",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = String.format("%.2fx", lensSensitivity),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveSlider(
                        value = lensSensitivity,
                        onValueChange = { viewModel.setLensVisualizerSensitivity(it) },
                        valueRange = 0.01f..1.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(85.dp))
    }
}

private fun Int.divideByToOne(): Float = this.toFloat()
