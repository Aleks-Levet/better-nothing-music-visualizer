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
import androidx.compose.runtime.getValue
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
            exit = if (backProgress > 0f) fadeOut(animationSpec = tween(500)) else exitTransition,
            modifier = overlayModifier.graphicsLayer {
                if (backProgress > 0f) {
                    translationX = size.width * backProgress
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
            exit = if (backProgress > 0f) fadeOut(animationSpec = tween(500)) else exitTransition,
            modifier = overlayModifier.graphicsLayer {
                if (backProgress > 0f) {
                    translationX = size.width * backProgress
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
            exit = if (backProgress > 0f) fadeOut(animationSpec = tween(500)) else exitTransition,
            modifier = overlayModifier.graphicsLayer {
                if (backProgress > 0f) {
                    translationX = size.width * backProgress
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
