package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.SectionHeader
import java.util.concurrent.TimeUnit

@Composable
internal fun StatsScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val totalTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val idleTime by viewModel.totalIdleTime.collectAsStateWithLifecycle()
    val activeTime by viewModel.totalActiveTime.collectAsStateWithLifecycle()
    val glyphTime by viewModel.totalGlyphTime.collectAsStateWithLifecycle()
    val hapticTime by viewModel.totalHapticTime.collectAsStateWithLifecycle()
    val flashlightTime by viewModel.totalFlashlightTime.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                ScreenTitle(text = "Usage Stats", modifier = Modifier.padding(bottom = 0.dp))
            }

            // Hero Card
            HeroStatCard(
                label = "Total Visualization Time",
                value = formatTime(totalTime),
                icon = Icons.Default.Timer,
                color = MaterialTheme.colorScheme.primary
            )

            // Engagement Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(text = "Engagement")
                
                val total = (activeTime + idleTime).coerceAtLeast(1L)
                val activePercent = (activeTime * 100 / total).toInt()
                val idlePercent = 100 - activePercent

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EngagementCard(
                        label = "Active Music",
                        percentage = activePercent,
                        time = formatTime(activeTime),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    EngagementCard(
                        label = "Idle Pulse",
                        percentage = idlePercent,
                        time = formatTime(idleTime),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Feature Breakdown
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(text = "Feature Breakdown")
                
                ExpressiveCard {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        DetailedFeatureRow(
                            icon = ImageVector.vectorResource(id = R.drawable.ic_nav_glyphs),
                            label = "Glyph Interface",
                            value = formatTime(glyphTime),
                            color = MaterialTheme.colorScheme.primary
                        )
                        DetailedFeatureRow(
                            icon = Icons.Default.Vibration,
                            label = "Haptic Feedback",
                            value = formatTime(hapticTime),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        DetailedFeatureRow(
                            icon = Icons.Default.FlashOn,
                            label = "Flashlight Sync",
                            value = formatTime(flashlightTime),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
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
    icon: ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = color,
        contentColor = Color.Black,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black.copy(alpha = 0.7f)
                )
                Icon(icon, null, modifier = Modifier.size(28.dp), tint = Color.Black.copy(alpha = 0.7f))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
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
    ExpressiveCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
private fun DetailedFeatureRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = color)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Total Active Use",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

private fun formatTime(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" 
           else if (minutes > 0) "${minutes}m ${seconds}s"
           else "${seconds}s"
}
