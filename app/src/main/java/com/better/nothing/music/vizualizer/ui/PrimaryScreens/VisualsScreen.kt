package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.ExpandableExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveSwitch
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.ExpressiveColorPicker
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.FineTuneButton
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.VisualizerStyle
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
    val overlayGlowBlurRadius by viewModel.overlayGlowBlurRadius.collectAsStateWithLifecycle()
    val overlayTopEnabled by viewModel.overlayTopEnabled.collectAsStateWithLifecycle()
    val overlayBottomEnabled by viewModel.overlayBottomEnabled.collectAsStateWithLifecycle()
    val overlayColor by viewModel.overlayColor.collectAsStateWithLifecycle()
    val overlayStyle by viewModel.overlayStyle.collectAsStateWithLifecycle()
    val overlayOpacity by viewModel.overlayOpacity.collectAsStateWithLifecycle()
    
    val edgeVisualizerEnabled by viewModel.edgeVisualizerEnabled.collectAsStateWithLifecycle()
    val edgeThickness by viewModel.edgeThickness.collectAsStateWithLifecycle()
    val edgeSensitivity by viewModel.edgeSensitivity.collectAsStateWithLifecycle()
    val edgeGlowBlurRadius by viewModel.edgeGlowBlurRadius.collectAsStateWithLifecycle()
    val edgeBarCountHoriz by viewModel.edgeBarCountHoriz.collectAsStateWithLifecycle()
    val edgeCornerRadius by viewModel.edgeCornerRadius.collectAsStateWithLifecycle()
    val edgeTopEnabled by viewModel.edgeTopEnabled.collectAsStateWithLifecycle()
    val edgeBottomEnabled by viewModel.edgeBottomEnabled.collectAsStateWithLifecycle()
    val edgeColor by viewModel.edgeColor.collectAsStateWithLifecycle()
    val edgeStyle by viewModel.edgeStyle.collectAsStateWithLifecycle()
    val edgeOpacity by viewModel.edgeOpacity.collectAsStateWithLifecycle()
    
    val lensEnabled by viewModel.lensVisualizerEnabled.collectAsStateWithLifecycle()
    val lensRadius by viewModel.lensVisualizerRadius.collectAsStateWithLifecycle()
    val lensX by viewModel.lensVisualizerX.collectAsStateWithLifecycle()
    val lensY by viewModel.lensVisualizerY.collectAsStateWithLifecycle()
    val lensBarWidth by viewModel.lensVisualizerBarWidth.collectAsStateWithLifecycle()
    val lensMaxHeight by viewModel.lensVisualizerMaxHeight.collectAsStateWithLifecycle()
    val lensBarCount by viewModel.lensVisualizerBarCount.collectAsStateWithLifecycle()
    val lensSensitivity by viewModel.lensVisualizerSensitivity.collectAsStateWithLifecycle()
    val lensGlowBlurRadius by viewModel.lensGlowBlurRadius.collectAsStateWithLifecycle()
    val lensColor by viewModel.lensColor.collectAsStateWithLifecycle()
    val lensStyle by viewModel.lensStyle.collectAsStateWithLifecycle()
    val lensOpacity by viewModel.lensOpacity.collectAsStateWithLifecycle()
    val emulateHdrOpacity by viewModel.emulateHdrOpacity.collectAsStateWithLifecycle()

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

        var overlayExpanded by rememberSaveable { mutableStateOf(overlayEnabled) }
        var edgeExpanded by rememberSaveable { mutableStateOf(edgeVisualizerEnabled) }
        var lensExpanded by rememberSaveable { mutableStateOf(lensEnabled) }
        var hdrExpanded by rememberSaveable { mutableStateOf(false) }

        // ── Overlay Visualizer ──────────────────────────────────────────────
        ExpandableExpressiveCard(
            title = stringResource(R.string.nav_overlay),
            icon = Icons.Default.Layers,
            expanded = overlayExpanded,
            onExpandedChange = { overlayExpanded = it },
            trailingContent = {
                ExpressiveSwitch(
                    checked = overlayEnabled,
                    onCheckedChange = onOverlayEnabledChanged
                )
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    Text(
                        text = stringResource(R.string.visualizer_style),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveSplitButton(
                        items = VisualizerStyle.entries.toList(),
                        selectedItem = overlayStyle,
                        onItemSelection = { viewModel.setOverlayStyle(it) },
                        labelProvider = { stringResource(it.labelRes) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.visualizer_opacity),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(overlayOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
                        value = overlayOpacity,
                        onValueChange = { viewModel.setOverlayOpacity(it) },
                        valueRange = 0f..1f,
                        fineTuneStep = 0.05f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Width Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_width),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${overlayWidth}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
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
                            text = stringResource(R.string.vertical_position),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${overlayYOffset}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
                        value = overlayYOffset.toFloat(),
                        onValueChange = { viewModel.setOverlayYOffset(it.toInt()) },
                        valueRange = -300f..300f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.visualizer_color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveColorPicker(
                        selectedColor = overlayColor,
                        onColorSelected = { viewModel.setOverlayColor(it) }
                    )

                    if (overlayStyle == VisualizerStyle.GLOW) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Glow blur",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${overlayGlowBlurRadius.toInt()}px",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        VisualSlider(
                            value = overlayGlowBlurRadius,
                            onValueChange = { viewModel.setOverlayGlowBlurRadius(it) },
                            valueRange = 0f..60f,
                            fineTuneStep = 1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

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
                            text = stringResource(R.string.top_edge_overlay),
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
                                text = stringResource(R.string.bar_height),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${overlayHeight}dp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        VisualSlider(
                            value = overlayHeight.toFloat(),
                            onValueChange = { viewModel.setOverlayHeight(it.toInt()) },
                            valueRange = 1f..128f,
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
                            text = stringResource(R.string.bottom_edge_overlay),
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
                                text = stringResource(R.string.bar_height),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${overlayHeightBottom}dp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        VisualSlider(
                            value = overlayHeightBottom.toFloat(),
                            onValueChange = { viewModel.setOverlayHeightBottom(it.toInt()) },
                            valueRange = 1f..128f,
                            modifier = Modifier.fillMaxWidth()
                        )

                    }
                }
            }
        

        // ── Edge Visualizer ──────────────────────────────────────────────
        ExpandableExpressiveCard(
            title = stringResource(R.string.edge_visualizer),
            icon = Icons.Default.BorderOuter,
            expanded = edgeExpanded,
            onExpandedChange = { edgeExpanded = it },
            trailingContent = {
                ExpressiveSwitch(
                    checked = edgeVisualizerEnabled,
                    onCheckedChange = { viewModel.setEdgeVisualizerEnabled(it) }
                )
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    Text(
                        text = stringResource(R.string.visualizer_style),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveSplitButton(
                        items = VisualizerStyle.entries.toList(),
                        selectedItem = edgeStyle,
                        onItemSelection = { viewModel.setEdgeStyle(it) },
                        labelProvider = { stringResource(it.labelRes) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.visualizer_opacity),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(edgeOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
                        value = edgeOpacity,
                        onValueChange = { viewModel.setEdgeOpacity(it) },
                        valueRange = 0f..1f,
                        fineTuneStep = 0.05f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.edge_bar_height),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${edgeThickness}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
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
                            text = stringResource(R.string.top_edge_segment),
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
                            text = stringResource(R.string.bottom_edge_segment),
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
                            text = stringResource(R.string.screen_corner_radius),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${edgeCornerRadius.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
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
                            text = stringResource(R.string.lens_bar_count),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$edgeBarCountHoriz",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
                        value = edgeBarCountHoriz.toFloat(),
                        onValueChange = { viewModel.setEdgeBarCount(it.toInt()) },
                        valueRange = 4f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.visualizer_color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveColorPicker(
                        selectedColor = edgeColor,
                        onColorSelected = { viewModel.setEdgeColor(it) }
                    )

                    if (edgeStyle == VisualizerStyle.GLOW) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Glow blur",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${edgeGlowBlurRadius.toInt()}px",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        VisualSlider(
                            value = edgeGlowBlurRadius,
                            onValueChange = { viewModel.setEdgeGlowBlurRadius(it) },
                            valueRange = 0f..60f,
                            fineTuneStep = 1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        

        // ── Lens Visualizer ──────────────────────────────────────────────
        ExpandableExpressiveCard(
            title = stringResource(R.string.lens_visualizer),
            icon = Icons.Default.BlurCircular,
            expanded = lensExpanded,
            onExpandedChange = { lensExpanded = it },
            trailingContent = {
                ExpressiveSwitch(
                    checked = lensEnabled,
                    onCheckedChange = { viewModel.setLensVisualizerEnabled(it) }
                )
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                    Text(
                        text = stringResource(R.string.visualizer_style),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveSplitButton(
                        items = VisualizerStyle.entries.toList(),
                        selectedItem = lensStyle,
                        onItemSelection = { viewModel.setLensStyle(it) },
                        labelProvider = { stringResource(it.labelRes) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.visualizer_opacity),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(lensOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
                        value = lensOpacity,
                        onValueChange = { viewModel.setLensOpacity(it) },
                        valueRange = 0f..1f,
                        fineTuneStep = 0.05f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.lens_radius),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensRadius.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
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
                            text = stringResource(R.string.x_position),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensX.toInt()}px",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSliderWithFineTune(
                        value = lensX,
                        onValueChange = { viewModel.setLensVisualizerX(it) },
                        valueRange = 0f..2000f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.y_position),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensY.toInt()}px",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSliderWithFineTune(
                        value = lensY,
                        onValueChange = { viewModel.setLensVisualizerY(it) },
                        valueRange = -500f..3500f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.lens_bar_width),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensBarWidth.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
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
                            text = stringResource(R.string.lens_max_height),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensMaxHeight.toInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
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
                            text = stringResource(R.string.lens_bar_count),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${lensBarCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
                        value = lensBarCount.divideByToOne(),
                        onValueChange = { viewModel.setLensVisualizerBarCount(it.toInt()) },
                        valueRange = 8f..48f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.visualizer_color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveColorPicker(
                        selectedColor = lensColor,
                        onColorSelected = { viewModel.setLensColor(it) }
                    )

                    if (lensStyle == VisualizerStyle.GLOW) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Glow blur",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${lensGlowBlurRadius.toInt()}px",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        VisualSlider(
                            value = lensGlowBlurRadius,
                            onValueChange = { viewModel.setLensGlowBlurRadius(it) },
                            valueRange = 0f..60f,
                            fineTuneStep = 1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

        AnimatedVisibility(
            visible = overlayEnabled || edgeVisualizerEnabled || lensEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ExpandableExpressiveCard(
                title = "Emulate HDR (OLED)",
                icon = Icons.Default.Brightness4,
                expanded = hdrExpanded,
                onExpandedChange = { hdrExpanded = it },
                subtitle = "Deepens blacks for improved contrast"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HDR Intensity",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(emulateHdrOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    VisualSlider(
                        value = emulateHdrOpacity,
                        onValueChange = { viewModel.setEmulateHdrOpacity(it) },
                        valueRange = 0f..0.2f,
                        fineTuneStep = 0.01f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        
        Spacer(modifier = Modifier.height(85.dp))
    }
}

private fun Int.divideByToOne(): Float = this.toFloat()

@Composable
private fun VisualSliderWithFineTune(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ExpressiveSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(-10, -1, 1, 10).forEach { amount ->
                FineTuneButton(
                    label = if (amount > 0) "+$amount" else "$amount",
                    onClick = { onValueChange((value + amount).coerceIn(valueRange)) }
                )
            }
        }
    }
}

@Composable
private fun VisualSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    fineTuneStep: Float = 1f,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FineTuneButton(
            label = if (fineTuneStep < 0.1f) "-1%" else if (fineTuneStep < 1f) "-$fineTuneStep" else "-${fineTuneStep.toInt()}",
            onClick = { onValueChange((value - fineTuneStep).coerceIn(valueRange)) }
        )
        ExpressiveSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(3f)
        )
        FineTuneButton(
            label = if (fineTuneStep < 0.1f) "+1%" else if (fineTuneStep < 1f) "+$fineTuneStep" else "+${fineTuneStep.toInt()}",
            onClick = { onValueChange((value + fineTuneStep).coerceIn(valueRange)) }
        )
    }
}
