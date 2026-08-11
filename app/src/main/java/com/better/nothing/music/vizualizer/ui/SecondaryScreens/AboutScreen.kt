package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.LinkCard
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.SectionHeader

@Composable
internal fun AboutScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()

    val credits = listOf(
        CreditEntry("Aleks Levet", stringResource(R.string.credit_alekslevet_role), "aleks-levet"),
        CreditEntry("rKyzen", stringResource(R.string.credit_rkyzen_role), "rKyzen"),
        CreditEntry("Oliver Lebaigue", stringResource(R.string.credit_oliver_role), "oliverlebaigue"),
        CreditEntry("nicouschulas", stringResource(R.string.credit_nicouschulas_role), "nicouschulas"),
        CreditEntry("Sebiai", stringResource(R.string.credit_sebiai_role), "Sebiai"),
        CreditEntry("EarnedEL", stringResource(R.string.credit_earnedel_role), "EarnedEL"),
        CreditEntry("Ake", stringResource(R.string.credit_ake_role), null),
        CreditEntry("Interlastic", stringResource(R.string.credit_interlastic_role), "Interlastic")
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            ScreenTitle(text = stringResource(R.string.about_title), modifier = Modifier.padding(bottom = 0.dp))

            // App Intro Card
            ExpressiveCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            alignment = Alignment.Center,
                            contentScale = ContentScale.Fit
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = stringResource(
                                R.string.version_info,
                                BuildConfig.VERSION_NAME
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }


            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GitHub Action
                LinkCard(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.github_repository),
                    subtitle = stringResource(R.string.view_source_contributions),
                    onClick = { uriHandler.openUri("https://github.com/Aleks-Levet/better-nothing-music-visualizer") }
                )

                // License Action
                LinkCard(
                    icon = Icons.Default.Gavel,
                    title = stringResource(R.string.license_agreement),
                    subtitle = stringResource(R.string.read_license),
                    onClick = { viewModel.showLicense() }
                )

                // Update Action
                val statusText = when (val status = appUpdateStatus) {
                    is MainViewModel.AppUpdateStatus.Checking -> stringResource(R.string.update_checking)
                    is MainViewModel.AppUpdateStatus.Available -> stringResource(R.string.update_available, status.version)
                    is MainViewModel.AppUpdateStatus.Downloading -> stringResource(R.string.update_downloading, (status.progress * 100).toInt())
                    is MainViewModel.AppUpdateStatus.UpToDate -> stringResource(R.string.update_uptodate)
                    is MainViewModel.AppUpdateStatus.Error -> stringResource(R.string.update_error, status.message)
                    else -> stringResource(R.string.check_software_updates)
                }

                LinkCard(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.software_update),
                    subtitle = statusText,
                    onClick = {
                        val status = appUpdateStatus
                        if (status is MainViewModel.AppUpdateStatus.Available) {
                            if (status.apkUrl != null) {
                                viewModel.downloadAndInstallUpdate(status.apkUrl, status.version)
                            } else {
                                uriHandler.openUri(status.url)
                            }
                        } else if (status !is MainViewModel.AppUpdateStatus.Downloading) {
                            viewModel.checkAppUpdate()
                        }
                    },
                    trailingContent = {
                        val status = appUpdateStatus
                        if (status is MainViewModel.AppUpdateStatus.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (status is MainViewModel.AppUpdateStatus.Downloading) {
                            CircularProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (status is MainViewModel.AppUpdateStatus.Available) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text(
                                    stringResource(R.string.update_label),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                )
            }

            SectionHeader(text = stringResource(R.string.credits))
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                credits.forEachIndexed { index, credit ->
                    val isFirst = index == 0
                    val isLast = index == credits.size - 1
                    val topRounding =
                        if (isFirst) MaterialTheme.shapes.large.topStart else CornerSize(6.dp)
                    val bottomRounding =
                        if (isLast) MaterialTheme.shapes.large.bottomStart else CornerSize(6.dp)

                    ExpressiveCard(
                        shape = RoundedCornerShape(
                            topStart = topRounding,
                            topEnd = topRounding,
                            bottomStart = bottomRounding,
                            bottomEnd = bottomRounding
                        ),
                        modifier = Modifier.let { m ->
                            if (credit.githubUsername != null) {
                                m.clickable { uriHandler.openUri("https://github.com/${credit.githubUsername}") }
                            } else m
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = credit.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = credit.role,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (credit.githubUsername != null) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = stringResource(R.string.open_github_profile),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

private data class CreditEntry(
    val name: String,
    val role: String,
    val githubUsername: String?
)
