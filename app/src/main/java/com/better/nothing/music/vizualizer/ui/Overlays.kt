package com.better.nothing.music.vizualizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.AboutScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.LicenseScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.StatsScreen
import android.app.Activity

@Composable
fun MainOverlays(viewModel: MainViewModel, selectedDevice: Int) {
    val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
    val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
    val isShowingStats by viewModel.isShowingStats.collectAsStateWithLifecycle()
    
    if (isShowingAbout) {
        AboutScreen(viewModel = viewModel, onDismiss = { viewModel.hideAbout() })
    }

    if (isShowingLicense) {
        LicenseScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.hideLicense() }
        )
    }

    if (isShowingStats) {
        StatsScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.hideStats() }
        )
    }
}
