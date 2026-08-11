@file:OptIn(ExperimentalMaterial3Api::class)

package com.better.nothing.music.vizualizer.ui

import android.R.attr.shape
import android.os.SystemClock
import android.view.MotionEvent
import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.better.nothing.music.vizualizer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Duration.Companion.milliseconds
import android.graphics.Path as AndroidPath

// Linear position (0..1) to Logarithmic Frequency (20..2000)
fun lerpLog(value: Float, min: Float, max: Float): Float {
    val logMin = ln(min)
    val logMax = ln(max)
    return exp(logMin + (logMax - logMin) * value)
}

// Logarithmic Frequency (20..2000) back to Linear position (0..1)
fun invLerpLog(freq: Float, min: Float, max: Float): Float {
    val logMin = ln(min)
    val logMax = ln(max)
    return (ln(freq) - logMin) / (logMax - logMin)
}

@Composable
fun MorphingPolygon(
    isBeatDetected: Boolean,
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "polygonRotation")
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "baseRotation"
    )

    val polygonBase = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.85f,
            rounding = CornerRounding(0.2f)
        )
    }

    var sourcePoly by remember { mutableStateOf(polygonBase) }
    var targetPoly by remember { mutableStateOf(polygonBase) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(isBeatDetected) {
        if (isBeatDetected) {
            sourcePoly = targetPoly
            targetPoly = RoundedPolygon.star(
                numVerticesPerRadius = (3..24).random(),
                innerRadius = (25..85).random() / 100f,
                rounding = CornerRounding((4..20).random() / 100f)
            )
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Smooth amplitude to avoid jitter, but kept responsive
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceAtMost(1.2f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "animatedAmplitude"
    )

    val morph = remember(sourcePoly, targetPoly) {
        Morph(sourcePoly, targetPoly)
    }

    val path = remember { AndroidPath() }
    val composePath = remember { AndroidPath().asComposePath() }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier) {
        val size = size.minDimension
        // Base scale 0.15 + up to 0.85 from amplitude
        val scale = size * (0.15f + (animatedAmplitude * 0.7f))
        
        path.reset()
        matrix.reset()
        matrix.scale(scale, scale)
        matrix.translate(size / (2 * scale), size / (2 * scale))
        
        morph.toPath(progress.value, path)
        // Note: asComposePath() usually wraps the same underlying object, 
        // but we need to ensure the transformation is applied correctly.
        val currentComposePath = path.asComposePath()
        currentComposePath.transform(matrix)

        rotate(baseRotation) {
            drawPath(
                path = currentComposePath,
                color = color,
                style = Fill
            )
        }
    }
}

@Composable
fun ExpressiveSplitButton(
    modifier: Modifier = Modifier,
    primaryText: String,
    primaryIcon: ImageVector,
    onPrimaryClick: () -> Unit,
    secondaryText: String,
    secondaryIcon: ImageVector,
    onSecondaryClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Primary Action
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                onPrimaryClick()
            },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(2f).fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(primaryIcon, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(primaryText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        // Secondary Action
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSecondaryClick()
            },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(secondaryIcon, null, modifier = Modifier.size(20.dp))
                if (secondaryText.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(secondaryText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowScope.OptionTile(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    // This state controls the weight expansion explicitly
    var isWeightExpanded by remember { mutableStateOf(false) }

    // Guaranteeing a minimum 120ms animation window
    LaunchedEffect(interactionSource) {
        var pressStartTime = 0L

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressStartTime = SystemClock.elapsedRealtime()
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    isWeightExpanded = true
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val elapsed = SystemClock.elapsedRealtime() - pressStartTime
                    val remainingFloorDelay = 150L - elapsed

                    // If the finger was released before 120ms, hold it open
                    if (remainingFloorDelay > 0) {
                        delay(remainingFloorDelay.milliseconds)
                    }
                    isWeightExpanded = false
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            }
        }
    }

    // Color States - Base them on selection OR active expansion animation
    val isEffectivelySelected = (isSelected || isWeightExpanded) && enabled
    val backgroundColor by animateColorAsState(
        if (!enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else if (isEffectivelySelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "backgroundColor"
    )
    val contentColor by animateColorAsState(
        if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else if (isEffectivelySelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )

    // Corner Radius Animation
    val m3eEnabled = LocalM3EEnabled.current
    val targetRadius = if (isSelected && enabled) 32.dp else 20.dp
    val animatedRadius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = if (m3eEnabled) {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(stiffness = Spring.StiffnessMedium)
        },
        label = "cornerRadius"
    )

    // Weight Animation using the managed isWeightExpanded state
    val targetWeight = if (isWeightExpanded && enabled) 1.2f else 1f
    val uiAmp = LocalUIAmplitude.current
    val animatedWeight by animateFloatAsState(
        targetValue = targetWeight * uiAmp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "weight"
    )

    Surface(
        onClick = if (enabled) {
            {
                onClick()
            }
        } else ({}),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(animatedRadius),
        color = backgroundColor,
        contentColor = contentColor,
        modifier = modifier
            .weight(animatedWeight)
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(25.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected && enabled) FontWeight.Bold else FontWeight.Medium,
                maxLines = maxLines
            )
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    val configuration = LocalConfiguration.current
    val isRestrictedLocale = remember(configuration) {
        val currentLocale = configuration.locales.get(0).language
        listOf("hi", "ar", "ja", "ru", "zh").contains(currentLocale)
    }

    Column(
        modifier = modifier
            .padding(bottom = 8.dp)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Text(
            text  = if (isRestrictedLocale) text.replace("\n", " ") else text,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = if (isRestrictedLocale) 0.sp else (-1).sp,
            fontWeight = FontWeight.Bold,
            maxLines = if (isRestrictedLocale) 1 else Int.MAX_VALUE,
            softWrap = !isRestrictedLocale,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .padding(vertical = 0.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun LinkCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (isPressed) {
            MaterialTheme.colorScheme.surfaceBright
        } else {
            MaterialTheme.colorScheme.surface
        },
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = if (trailingContent != null) 20.dp else 84.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceBright,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (trailingContent != null) {
                    trailingContent()
                }
            }

            if (trailingContent == null) {
                IndicatorPill(
                    isExpanded = false,
                    isLink = true,
                    onClick = onClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp)
                )
            }
        }
    }
}

@Composable
fun ExpandableExpressiveCard(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    val dpBouncySpec: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val intSizeBouncySpec: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    // Smoothly animate top padding between 20.dp (centered for 72.dp height) and 16.dp
    val pillTopPadding by animateDpAsState(
        targetValue = if (expanded) 16.dp else 20.dp,
        animationSpec = dpBouncySpec,
        label = "pillTopPadding"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.animateContentSize(animationSpec = intSizeBouncySpec)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandedChange(!expanded)
                        }
                        .padding(start = 20.dp, end = 96.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceBright,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
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
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (trailingContent != null) {
                        trailingContent()
                    }
                }

                IndicatorPill(
                    isExpanded = expanded,
                    onClick = {
                        onExpandedChange(!expanded)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd) // Anchor strictly to TopEnd for transition to work
                        .padding(
                            top = pillTopPadding, // Animates smoothly with dpBouncySpec
                            end = 20.dp
                        )
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = intSizeBouncySpec,
                    expandFrom = Alignment.Top
                ),
                exit = shrinkVertically(
                    animationSpec = intSizeBouncySpec,
                    shrinkTowards = Alignment.Top
                )
            ) {
                Column(
                    modifier = Modifier
                        .clipToBounds()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun IndicatorPill(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    // Trigger light haptic tick immediately when pressed down
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    val bouncySpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val baseWidth = when {
        isExpanded -> 48.dp
        isLink -> 48.dp
        else -> 66.dp
    }

    val targetWidth = if (isPressed) baseWidth - 10.dp else baseWidth

    val width by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = bouncySpec,
        label = "pillWidth"
    )
    val height by animateDpAsState(
        targetValue = if (isExpanded) 48.dp else 32.dp,
        animationSpec = bouncySpec,
        label = "pillHeight"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 14.dp else 16.dp,
        animationSpec = bouncySpec,
        label = "pillRadius"
    )

    Surface(
        onClick = {
            onClick?.invoke()
        },
        enabled = onClick != null,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.size(width = width, height = height)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isLink) Icons.Default.ChevronRight
                else if (isExpanded) Icons.Default.ExpandLess
                else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(7.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        if (trailingContent != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent()
            }
        }
    }
    Spacer(modifier = Modifier.height(LocalAppSpacing.current.between))
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    lineHeight: TextUnit = 24.sp,
) {
    Text(
        text  = text,
        // Hoist TextStyle out of every recomposition; only reallocated when
        // size or lineHeight actually changes.
        style = remember(size, lineHeight) {
            TextStyle(
                fontSize   = size,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Normal,
            )
        },
        color    = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
fun StartStopButton(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed         by interactionSource.collectIsPressedAsState()
    val haptics           = LocalHapticFeedback.current
    val uiAmp             = LocalUIAmplitude.current

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    val containerColor by animateColorAsState(
        targetValue   = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label         = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue   = if (running) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label         = "contentColor"
    )

    val outerPadding = (8.dp / uiAmp).coerceAtLeast(1.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(outerPadding),
        contentAlignment = Alignment.Center
    ) {
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                onClick()
            },
            interactionSource = interactionSource,
            shape = RoundedCornerShape((18 + (uiAmp - 1) * 50).dp),
            modifier = Modifier
                .height((60 + (uiAmp - 1) * 50).dp)
                .widthIn(min = (130 + (uiAmp - 1) * 50).dp),
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = running,
                    transitionSpec = {
                        (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
                    },
                    label = "iconTransition"
                ) { isRunning ->
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(20.dp))

                Text(
                    text = stringResource(
                        if (running) R.string.stop_visualizer
                        else R.string.start_visualizer
                    ).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}

@Composable
fun NativeBottomBar(
    selectedTab: Tab,
    visibleTabs: List<Tab>,
    onTabSelected: (Tab) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        val uiAmp = LocalUIAmplitude.current

        // Iterate over all possible tabs so hiding/showing tabs can animate smoothly
        Tab.entries.forEach { tab ->
            val isVisible = tab in visibleTabs
            val isSelected = tab == selectedTab

            // 1. Animate the weight: high bounce on entry, 400ms EaseOutCubic on exit
            val animatedWeight by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0.0001f,
                animationSpec = if (isVisible) {
                    spring(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                } else {
                    tween(
                        durationMillis = 400,
                        easing = EaseOutCubic
                    )
                },
                label = "nav_tab_weight"
            )

            // 2. Animate scale/alpha so the tab cleanly fades & shrinks as weight decreases
            val animatedAlpha by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 150),
                label = "nav_tab_alpha"
            )

            val selectionScale by animateFloatAsState(
                targetValue = if (isSelected) 1.1f else 1.0f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                label = "nav_selection_scale"
            )

            // Render tab only if it has a noticeable weight
            if (animatedWeight > 0.0005f) {
                NavigationBarItem(
                    modifier = Modifier
                        .weight(animatedWeight)
                        .graphicsLayer { alpha = animatedAlpha },
                    selected = isSelected,
                    onClick = {
                        if (!isSelected && isVisible) {
                            onTabSelected(tab)
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,                         // Forces maximum of 1 line
                            softWrap = false,                      // Disables text wrapping onto next lines
                            overflow = TextOverflow.Ellipsis      // Truncates with "..." if the label is too long (requires importing androidx.compose.ui.text.style.TextOverflow)
                        )
                    },
                    icon = {
                        Box(contentAlignment = Alignment.Center) {
                            val iconModifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    val iconScale = selectionScale + (if (isSelected) (uiAmp - 1.0f) * 0.5f else 0f)
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }

                            when (tab) {
                                Tab.Audio -> Icon(painter = painterResource(R.drawable.ic_notif_monochrome), contentDescription = stringResource(tab.labelRes), modifier = iconModifier)
                                Tab.Glyphs -> Icon(painter = painterResource(R.drawable.ic_nav_glyphs), contentDescription = stringResource(tab.labelRes), modifier = iconModifier)
                                Tab.Visuals -> Icon(Icons.Default.Layers, stringResource(tab.labelRes), modifier = iconModifier)
                                Tab.Haptics -> Icon(Icons.Filled.Vibration, stringResource(tab.labelRes), modifier = iconModifier)
                                Tab.Flashlight -> Icon(Icons.Filled.FlashlightOn, stringResource(tab.labelRes), modifier = iconModifier)
                                Tab.Settings -> Icon(Icons.Filled.Settings, stringResource(tab.labelRes), modifier = iconModifier)
                            }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExpressiveSplitButton(
    items: List<T>,
    selectedItem: T,
    onItemSelection: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    maxButtonsPerRow: Int? = null
) {
    val haptics = LocalHapticFeedback.current
    val uiAmp = LocalUIAmplitude.current

    // 1. Resolve Composable labels into plain strings safely in the Composable pipeline
    val resolvedLabels = items.associateWith { labelProvider(it) }

    // 2. Chunk items into rows using the resolved plain string map
    val chunkedRows = remember(items, resolvedLabels, maxButtonsPerRow) {
        if (maxButtonsPerRow != null) {
            if (items.size == 4 && maxButtonsPerRow == 3) {
                items.chunked(2)
            } else {
                items.chunked(maxButtonsPerRow)
            }
        } else if (items.size <= 3) {
            listOf(items)
        } else {
            val rows = mutableListOf<MutableList<T>>()
            var currentRow = mutableListOf<T>()
            var currentCharacterCount = 0

            // Threshold budget limit per row
            val maxCharactersPerRow = 26

            items.forEach { item ->
                val labelText = resolvedLabels[item].orEmpty()
                val textLength = labelText.length

                if (currentCharacterCount + textLength > maxCharactersPerRow && currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                    currentCharacterCount = 0
                }
                currentRow.add(item)
                currentCharacterCount += textLength
            }
            if (currentRow.isNotEmpty()) {
                rows.add(currentRow)
            }
            rows
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        chunkedRows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEachIndexed { itemIndex, item ->
                    val isSelected = item == selectedItem
                    var isPressed by remember { mutableStateOf(false) }

                    val bouncySpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    val dpBouncySpec = spring<androidx.compose.ui.unit.Dp>(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )

                    val baseWeight by animateFloatAsState(
                        targetValue = if (isPressed) 0.89f 
                                      else if (isSelected) 1.2f 
                                      else 1.0f,
                        animationSpec = bouncySpec,
                        label = "ExpressiveWeightAnimationBase"
                    )
                    
                    val animatedWeight = if (isSelected) {
                        baseWeight * uiAmp
                    } else {
                        baseWeight
                    }

                    // Color transitions
                    val targetContainerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }

                    val targetContentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val containerColor by animateColorAsState(
                        targetValue = targetContainerColor,
                        animationSpec = tween(durationMillis = 250),
                        label = "ContainerColorAnimation"
                    )

                    val contentColor by animateColorAsState(
                        targetValue = targetContentColor,
                        animationSpec = tween(durationMillis = 250),
                        label = "ContentColorAnimation"
                    )

                    // Edge rounding physics logic
                    val fullyRounded = 20.dp
                    val innerRounded = 8.dp // Unified - truly sharp inner edges for the box look

                    val isFirstRow = rowIndex == 0
                    val isLastRow = rowIndex == chunkedRows.size - 1
                    val isFirstInRow = itemIndex == 0
                    val isLastInRow = itemIndex == rowItems.size - 1

                    val targetTopStart = if (isSelected || (isFirstRow && isFirstInRow)) fullyRounded else innerRounded
                    val targetTopEnd = if (isSelected || (isFirstRow && isLastInRow)) fullyRounded else innerRounded
                    val targetBottomStart = if (isSelected || (isLastRow && isFirstInRow)) fullyRounded else innerRounded
                    val targetBottomEnd = if (isSelected || (isLastRow && isLastInRow)) fullyRounded else innerRounded

                    val topStart by animateDpAsState(targetValue = targetTopStart, animationSpec = dpBouncySpec, label = "TopStart")
                    val bottomStart by animateDpAsState(targetValue = targetBottomStart, animationSpec = dpBouncySpec, label = "BottomStart")
                    val topEnd by animateDpAsState(targetValue = targetTopEnd, animationSpec = dpBouncySpec, label = "TopEnd")
                    val bottomEnd by animateDpAsState(targetValue = targetBottomEnd, animationSpec = dpBouncySpec, label = "BottomEnd")

                    val dynamicButtonShape = RoundedCornerShape(
                        topStart = topStart.coerceAtLeast(0.dp),
                        bottomStart = bottomStart.coerceAtLeast(0.dp),
                        topEnd = topEnd.coerceAtLeast(0.dp),
                        bottomEnd = bottomEnd.coerceAtLeast(0.dp)
                    )

                    val interactionSource = remember { MutableInteractionSource() }

                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect { interaction ->
                            when (interaction) {
                                is PressInteraction.Press -> {
                                    isPressed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                                is PressInteraction.Release -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    delay(80)
                                    isPressed = false
                                }
                                is PressInteraction.Cancel -> {
                                    isPressed = false
                                }
                            }
                        }
                    }

                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Surface(
                            onClick = {
                                if (!isSelected) {
                                    onItemSelection(item)
                                }
                            },
                            color = containerColor,
                        contentColor = contentColor,
                        shape = dynamicButtonShape,
                        modifier = Modifier.weight(animatedWeight),
                        interactionSource = interactionSource
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = resolvedLabels[item] ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val uiAmp = LocalUIAmplitude.current

    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isActive = isPressed || isDragged

    val wasActive = remember { mutableStateOf(false) }

    // Trigger haptic on Press/Release (skip initial state)
    LaunchedEffect(isActive) {
        if (!wasActive.value && isActive) {
            // Trigger on press (transition from false to true)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (wasActive.value && !isActive) {
            // Trigger on release (transition from true to false)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        wasActive.value = isActive
    }

    // The "Expressive" factor (1.0 to 1.8)
    val animationFactor by animateFloatAsState(
        targetValue = if (isActive && LocalM3EEnabled.current) 2.1f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressive_bounce"
    )

    val view = LocalView.current
    Slider(
        value = value,
        onValueChange = { newValue ->
            onValueChange(newValue)
        },
        valueRange = valueRange,
        steps = steps,
        interactionSource = interactionSource,
        modifier = modifier
            .height(56.dp)
            .pointerInput(isActive) {
                if (isActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
            }
            .pointerInteropFilter { motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            },
        thumb = {
            // THUMB: Gets THINNER as animationFactor increases
            // Width: 4dp -> 2dp | Height: 44dp -> 48dp
            val thumbWidth = 4.dp / animationFactor

            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = 44.dp * (animationFactor * 0.8f).coerceAtLeast(1f))
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp) // Keeps same corner radius
                    )
            )
        },
        track = { sliderState ->
            // TRACK: Gets THICKER
            // Radius: We want it to look like a pill when thin, but less rounded when thick
            val trackHeight = 16.dp * animationFactor * uiAmp

            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier
                    .height(trackHeight),
                thumbTrackGapSize = 4.dp,
                trackInsideCornerSize = 2.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}

@Composable
fun ExpressiveRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val uiAmp = LocalUIAmplitude.current

    val startActive by startInteractionSource.collectIsPressedAsState()
    val startDragged by startInteractionSource.collectIsDraggedAsState()
    val endActive by endInteractionSource.collectIsPressedAsState()
    val endDragged by endInteractionSource.collectIsDraggedAsState()

    val isAnyActive = startActive || startDragged || endActive || endDragged
    val wasActive = remember { mutableStateOf(false) }

    // Trigger haptic on Press/Release (skip initial state)
    LaunchedEffect(isAnyActive) {
        if (!wasActive.value && isAnyActive) {
            // Trigger on press (transition from false to true)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (wasActive.value && !isAnyActive) {
            // Trigger on release (transition from true to false)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        wasActive.value = isAnyActive
    }

    // Animation and Haptic logic remains the same...
    val animationFactor by animateFloatAsState(
        targetValue = if (isAnyActive && LocalM3EEnabled.current) 2.1f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "track_bloom"
    )

    val startThumbFactor by animateFloatAsState(if ((startActive || startDragged) && LocalM3EEnabled.current) 2.1f else 1.0f)
    val endThumbFactor by animateFloatAsState(if ((endActive || endDragged) && LocalM3EEnabled.current) 2.1f else 1.0f)

    val view = LocalView.current
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        startInteractionSource = startInteractionSource,
        endInteractionSource = endInteractionSource,
        modifier = modifier
            .height(64.dp)
            .pointerInput(isAnyActive) {
                if (isAnyActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
            }
            .pointerInteropFilter { motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            },
        startThumb = { ExpressiveThumb(factor = startThumbFactor) },
        endThumb = { ExpressiveThumb(factor = endThumbFactor) },
        track = { rangeSliderState ->
            val trackHeight = 12.dp * animationFactor * uiAmp
            SliderDefaults.Track(
                rangeSliderState = rangeSliderState,
                modifier = Modifier.height(trackHeight),
                thumbTrackGapSize = 4.dp,
                drawStopIndicator = null,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}

@Composable
private fun ExpressiveThumb(factor: Float) {
    // The thumb gets thinner and taller when grabbed
    val thumbWidth = 4.dp / factor
    val thumbHeight = 40.dp * (factor * 0.8f).coerceAtLeast(1f)

    Box(
        modifier = Modifier
            .size(width = thumbWidth, height = thumbHeight)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(2.dp)
            )
    )
}

@Composable
fun DisabledFeaturePlaceholder(
    tab: Tab,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val icon = when (tab) {
            Tab.Glyphs -> painterResource(R.drawable.ic_nav_glyphs)
            Tab.Haptics -> rememberVectorPainter(Icons.Filled.Vibration)
            Tab.Flashlight -> rememberVectorPainter(Icons.Filled.FlashlightOn)
            Tab.Visuals -> rememberVectorPainter(Icons.Filled.Layers)
            else -> rememberVectorPainter(Icons.Default.Settings)
        }

        val text = when (tab) {
            Tab.Glyphs -> stringResource(R.string.glyphs_disabled)
            Tab.Haptics -> stringResource(R.string.haptics_disabled)
            Tab.Flashlight -> stringResource(R.string.flashlight_disabled)
            else -> stringResource(R.string.feature_disabled)
        }

        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .alpha(0.2f),
            tint = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}


