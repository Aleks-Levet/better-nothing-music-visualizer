package com.better.nothing.music.vizualizer.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var trigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        trigger = true
        delay(900L)
        onFinished()
    }

    val contentColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val heights = listOf(59f, 45f, 66f, 42f, 29f, 51f, 10f)
            heights.forEachIndexed { index, h ->
                val delayMs = index * 40L
                var barTrigger by remember { mutableStateOf(false) }
                LaunchedEffect(trigger) {
                    if (trigger) {
                        delay(delayMs)
                        barTrigger = true
                    }
                }

                val scaleY by animateFloatAsState(
                    targetValue = if (barTrigger) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "barScale$index"
                )

                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height((h * 1.2f).dp)
                        .graphicsLayer(
                            scaleY = scaleY,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        )
                        .background(
                            color = if (index == 0) Color(0xFFD71921) else contentColor,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
        }
    }
}
