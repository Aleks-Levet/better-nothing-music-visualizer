package com.better.nothing.music.vizualizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.AboutScreen as AboutScreenSecondary
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.LicenseScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.StatsScreen

@Composable
fun MainOverlays(
    viewModel: MainViewModel,
    selectedDevice: Int,
    isTablet: Boolean = false,
    visibleTabCount: Int = 1,
    backProgress: Float = 0f
) {
    val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
    val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
    val isShowingStats by viewModel.isShowingStats.collectAsStateWithLifecycle()

    var backTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(backProgress > 0f) {
        if (backProgress > 0f && backTarget == null) {
            backTarget = when {
                isShowingStats -> "stats"
                isShowingLicense -> "license"
                isShowingAbout -> "about"
                else -> null
            }
        } else if (backProgress <= 0f) {
            backTarget = null
        }
    }

    val enterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(durationMillis = 500, easing = EaseOutCubic)
    )
    val exitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(durationMillis = 500, easing = EaseOutCubic)
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        val overlayModifier = if (isTablet) {
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f / visibleTabCount.coerceAtLeast(1))
        } else {
            Modifier.fillMaxSize()
        }

        AnimatedVisibility(
            visible = isShowingAbout,
            enter = enterTransition,
            exit = if (backProgress > 0f && backTarget == "about") fadeOut(animationSpec = tween(500)) else exitTransition,
            modifier = overlayModifier.graphicsLayer {
                if (backProgress > 0f && backTarget == "about") {
                    translationX = size.width * backProgress
                    val scale = 1f - (backProgress * 0.05f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (backProgress * 0.2f)
                }
            }
        ) {
            AboutScreenSecondary(
                viewModel = viewModel,
                onDismiss = { viewModel.hideAbout() }
            )
        }

        AnimatedVisibility(
            visible = isShowingLicense,
            enter = enterTransition,
            exit = if (backProgress > 0f && backTarget == "license") fadeOut(animationSpec = tween(500)) else exitTransition,
            modifier = overlayModifier.graphicsLayer {
                if (backProgress > 0f && backTarget == "license") {
                    translationX = size.width * backProgress
                    val scale = 1f - (backProgress * 0.05f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (backProgress * 0.2f)
                }
            }
        ) {
            LicenseScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.hideLicense() }
            )
        }

        AnimatedVisibility(
            visible = isShowingStats,
            enter = enterTransition,
            exit = if (backProgress > 0f && backTarget == "stats") fadeOut(animationSpec = tween(500)) else exitTransition,
            modifier = overlayModifier.graphicsLayer {
                if (backProgress > 0f && backTarget == "stats") {
                    translationX = size.width * backProgress
                    val scale = 1f - (backProgress * 0.05f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (backProgress * 0.2f)
                }
            }
        ) {
            StatsScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.hideStats() }
            )
        }
    }
}
