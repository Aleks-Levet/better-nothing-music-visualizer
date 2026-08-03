package com.better.nothing.music.vizualizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.AboutScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.LeaderboardScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.LicenseScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.StatsScreen

@Composable
fun MainOverlays(viewModel: MainViewModel, selectedDevice: Int) {
    val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
    val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
    val isShowingStats by viewModel.isShowingStats.collectAsStateWithLifecycle()
    val isShowingLeaderboard by viewModel.isShowingLeaderboard.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    val leaderboardEntries by viewModel.leaderboardEntries.collectAsStateWithLifecycle()

    if (isShowingAbout) {
        AboutScreen(onDismiss = { viewModel.hideAbout() })
    }

    if (isShowingLicense) {
        val licenseStatus by viewModel.licenseStatus.collectAsStateWithLifecycle()
        LicenseScreen(
            status = licenseStatus,
            onDismiss = { viewModel.hideLicense() }
        )
    }

    if (isShowingStats) {
        StatsScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.hideStats() }
        )
    }

    if (isShowingLeaderboard) {
        LeaderboardScreen(
            entries = leaderboardEntries,
            isAnonymous = isAnonymous,
            onSignIn = { viewModel.signInPlayGames() },
            onDismiss = { viewModel.hideLeaderboard() }
        )
    }
}
