package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.LocalUIAmplitude
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.SectionHeader
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

@Composable
internal fun StatsScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val isRunning by viewModel.runningState.collectAsStateWithLifecycle()

    val totalTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val idleTime by viewModel.totalIdleTime.collectAsStateWithLifecycle()
    val activeTime by viewModel.totalActiveTime.collectAsStateWithLifecycle()
    val glyphTime by viewModel.totalGlyphTime.collectAsStateWithLifecycle()
    val hapticTime by viewModel.totalHapticTime.collectAsStateWithLifecycle()
    val flashlightTime by viewModel.totalFlashlightTime.collectAsStateWithLifecycle()
    val broadcastTime by viewModel.totalBroadcastingTime.collectAsStateWithLifecycle()
    val overlayTime by viewModel.totalOverlayTime.collectAsStateWithLifecycle()

    val glyphsEnabled by viewModel.glyphsEnabled.collectAsStateWithLifecycle()
    val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticMotorEnabled.collectAsStateWithLifecycle()
    val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
    val broadcastEnabled by viewModel.broadcastEnabled.collectAsStateWithLifecycle()
    val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
    val onScreenVisualizersEnabled by viewModel.onScreenVisualizersEnabled.collectAsStateWithLifecycle()

    val isGlyphActive = !isRunning or  glyphsEnabled && maxBrightness > 0
    val isHapticActive = !isRunning or hapticEnabled
    val isFlashlightActive = !isRunning or  flashlightEnabled
    val isBroadcastActive = !isRunning or  broadcastEnabled
    val isOverlayActive = !isRunning or (overlayEnabled && onScreenVisualizersEnabled)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val base64 = viewModel.getStatsJsonBase64()
                        if (base64 != null) {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                OutputStreamWriter(outputStream).use { writer ->
                                    writer.write(base64)
                                }
                            }
                        } else {
                            Toast.makeText(context, R.string.stats_export_error, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.stats_export_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            InputStreamReader(inputStream).use { reader ->
                                val base64 = reader.readText()
                                if (viewModel.importStatsFromBase64(base64)) {
                                    Toast.makeText(context, R.string.stats_import_success, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, R.string.stats_import_error, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.stats_import_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            ScreenTitle(text = stringResource(R.string.usage_stats), modifier = Modifier.padding(bottom = 0.dp))

            // Hero Card
            HeroStatCard(
                label = stringResource(R.string.total_visualization_time),
                value = formatTime(totalTime),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )

            // Engagement Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                val total = (activeTime + idleTime).coerceAtLeast(1L)
                val activePercent = (activeTime * 100 / total).toInt()
                val idlePercent = 100 - activePercent

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EngagementCard(
                        label = stringResource(R.string.active_music),
                        percentage = activePercent,
                        time = formatTime(activeTime),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    EngagementCard(
                        label = stringResource(R.string.idle_pulse),
                        percentage = idlePercent,
                        time = formatTime(idleTime),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Feature Breakdown
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(text = stringResource(R.string.feature_breakdown))
                
                ExpressiveCard {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        DetailedFeatureRow(
                            icon = ImageVector.vectorResource(id = R.drawable.ic_nav_glyphs),
                            label = stringResource(R.string.glyph_interface),
                            value = formatTime(glyphTime),
                            color = MaterialTheme.colorScheme.primary,
                            isActive = isGlyphActive,
                            isRunning = isRunning // Pass the running state
                        )
                        DetailedFeatureRow(
                            icon = Icons.Default.Vibration,
                            label = stringResource(R.string.haptic_feedback),
                            value = formatTime(hapticTime),
                            color = MaterialTheme.colorScheme.primary,
                            isActive = isHapticActive,
                            isRunning = isRunning // Pass the running state
                        )
                        DetailedFeatureRow(
                            icon = Icons.Default.FlashOn,
                            label = stringResource(R.string.flashlight_sync_stat),
                            value = formatTime(flashlightTime),
                            color = MaterialTheme.colorScheme.primary,
                            isActive = isFlashlightActive,
                            isRunning = isRunning // Pass the running state
                        )
                        DetailedFeatureRow(
                            icon = Icons.Default.Wifi,
                            label = stringResource(R.string.broadcasting_stat),
                            value = formatTime(broadcastTime),
                            color = MaterialTheme.colorScheme.primary,
                            isActive = isBroadcastActive,
                            isRunning = isRunning // Pass the running state
                        )
                        DetailedFeatureRow(
                            icon = Icons.Default.Layers,
                            label = stringResource(R.string.overlay_stat),
                            value = formatTime(overlayTime),
                            color = MaterialTheme.colorScheme.primary,
                            isActive = isOverlayActive,
                            isRunning = isRunning // Pass the running state
                        )
                    }
                }
            }

            // Export/Import Buttons
            val uiAmp = LocalUIAmplitude.current
            val buttonBorderWidth = (1.dp + 2.dp * (uiAmp - 1.0f)).coerceAtLeast(1.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("stats_export.json") },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        buttonBorderWidth,
                        MaterialTheme.colorScheme.outline.copy(
                            alpha = (0.5f + (uiAmp - 1.0f) * 0.5f).coerceIn(0.3f, 1.0f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_stats))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        buttonBorderWidth,
                        MaterialTheme.colorScheme.outline.copy(
                            alpha = (0.5f + (uiAmp - 1.0f) * 0.5f).coerceIn(0.3f, 1.0f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_stats))
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun HeroStatCard(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color
) {
    val uiAmp = LocalUIAmplitude.current
    val beatScale = 1.0f + (uiAmp - 1.0f) * 0.08f

    // Pill shape (fully rounded corners) when uiAmp is 1.0, diminishing radius as uiAmp increases
    val heroShapeRadius = (90.dp - (45.dp * (uiAmp - 1.0f))).coerceIn(12.dp, 120.dp)
    val valueWeight = FontWeight((900 + (uiAmp - 1.0f) * 350).toInt().coerceIn(500, 1000))

    Surface(
        shape = RoundedCornerShape(heroShapeRadius),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = beatScale
                scaleY = beatScale
            }
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = contentColor.copy(alpha = (0.7f + (uiAmp - 1.0f) * 0.3f).coerceIn(0.5f, 1.0f))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFeatureSettings = "tnum" // Enables tabular (equal width) figures
                ),
                fontSize = 68.sp,
                lineHeight = 74.sp, // Controls the line spacing/height for the large text
                fontWeight = valueWeight,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun EngagementCard(
    label: String,
    percentage: Int,
    time: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val uiAmp = LocalUIAmplitude.current
    val cardScale = 1.0f + (uiAmp - 1.0f) * 0.05f
    val percentageWeight = FontWeight((800 + (uiAmp - 1.0f) * 450).toInt().coerceIn(300, 1000))
    val timeWeight = FontWeight((400 + (uiAmp - 1.0f) * 300).toInt().coerceIn(200, 1000))
    val dynamicBorderWidth = (1.dp + 4.dp * (uiAmp - 1.0f)).coerceAtLeast(1.dp)
    val dynamicBorderAlpha = (0.2f + (uiAmp - 1.0f) * 0.8f).coerceIn(0.1f, 1.0f)

    ExpressiveCard(
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = percentageWeight,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = "tnum" // Enables tabular (equal width) figures
                ),
                fontWeight = timeWeight,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = (0.6f + (uiAmp - 1.0f) * 0.4f).coerceIn(0.4f, 1.0f)
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            WigglyProgressIndicator(
                progress = percentage / 100f,
                uiAmp = uiAmp,
                color = color,
                trackColor = color.copy(alpha = (0.15f + (uiAmp - 1.0f) * 0.2f).coerceIn(0.1f, 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
            )
        }
    }
}

@Composable
private fun WigglyProgressIndicator(
    progress: Float,
    uiAmp: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wiggleTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseAnimation"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokeWidth = 5.dp.toPx()
        val clampedProgress = progress.coerceIn(0f, 1f)
        val activeWidth = width * clampedProgress

        // Background track
        if (width > 0) {
            drawLine(
                color = trackColor,
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Active wiggly track
        if (activeWidth > 0f) {
            val baseAmplitude = 3.5.dp.toPx()
            val ampMultiplier = (1.0f + (uiAmp - 1.0f) * 3.0f)
            val amplitude = baseAmplitude * ampMultiplier
            val waveLength = 28.dp.toPx()

            val steps = (activeWidth / 2f).toInt().coerceAtLeast(12)
            val path = Path()

            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * activeWidth
                val envelope = Math.sin(Math.PI * (x / activeWidth).toDouble()).toFloat().coerceIn(0f, 1f)
                val y = centerY + amplitude * envelope * Math.sin((2.0 * Math.PI * x / waveLength) + phase).toFloat()

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun DetailedFeatureRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.primary,
    isActive: Boolean = true,
    isRunning: Boolean = true
) {
    val uiAmp = if (isActive) LocalUIAmplitude.current else 1.0f
    val avatarScale = 1.0f + (uiAmp - 1.0f) * 0.18f
    val iconScale = 1.0f + (uiAmp - 1.0f) * 0.25f
    val valueWeight = FontWeight((800 + (uiAmp - 1.0f) * 400).toInt().coerceIn(400, 1000))
    val labelWeight = FontWeight((700 + (uiAmp - 1.0f) * 250).toInt().coerceIn(400, 1000))
    val bgAlpha = if (isActive) (0.1f + (uiAmp - 1.0f) * 0.35f).coerceIn(0.05f, 0.6f) else 0.05f
    val activeAlpha = if (isActive) 1.0f else 0.4f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = bgAlpha),
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    tint = color.copy(alpha = activeAlpha)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = labelWeight,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = activeAlpha)
            )
            Text(
                text = stringResource(R.string.total_active_use),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = (if (isActive) (0.5f + (uiAmp - 1.0f) * 0.3f) else 0.3f).coerceIn(0.2f, 0.9f)
                )
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = "tnum" // Enables tabular (equal width) figures
            ),
            fontWeight = valueWeight,
            color = color.copy(alpha = activeAlpha)
        )
    }
}

private fun formatTime(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    val tenths = (ms / 100) % 10
    
    return if (hours > 0) "${hours}h ${minutes}m ${seconds}.${tenths}s"
           else if (minutes > 0) "${minutes}m ${seconds}.${tenths}s"
           else "${seconds}.${tenths}s"
}
