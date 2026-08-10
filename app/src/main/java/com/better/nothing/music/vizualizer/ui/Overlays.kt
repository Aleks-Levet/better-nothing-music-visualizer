package com.better.nothing.music.vizualizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.AboutScreen as AboutScreenSecondary
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.LicenseScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.StatsScreen

@Composable
fun MainOverlays(viewModel: MainViewModel, selectedDevice: Int) {
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

    AnimatedVisibility(
        visible = isShowingAbout,
        enter = enterTransition,
        exit = exitTransition
    ) {
        AboutScreenSecondary(
            viewModel = viewModel,
            onDismiss = { viewModel.hideAbout() }
        )
    }

    AnimatedVisibility(
        visible = isShowingLicense,
        enter = enterTransition,
        exit = exitTransition
    ) {
        LicenseScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.hideLicense() }
        )
    }

    AnimatedVisibility(
        visible = isShowingStats,
        enter = enterTransition,
        exit = exitTransition
    ) {
        StatsScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.hideStats() }
        )
    }
}
