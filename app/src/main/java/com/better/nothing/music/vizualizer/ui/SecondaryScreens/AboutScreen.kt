package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
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
                ScreenTitle(text = stringResource(R.string.about_title), modifier = Modifier.padding(bottom = 0.dp))
            }

            // App Intro Card
            ExpressiveCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.GraphicEq, null, tint = Color.Black)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.version_info, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 4.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                )

                // GitHub Action
                InfoRow(
                    icon = Icons.Default.Code,
                    title = "GitHub Repository",
                    subtitle = "View source and contributions",
                    onClick = { uriHandler.openUri("https://github.com/Aleks-Levet/better-nothing-music-visualizer") }
                )

                // License Action
                InfoRow(
                    icon = Icons.Default.Gavel,
                    title = stringResource(R.string.license_agreement),
                    subtitle = stringResource(R.string.read_license),
                    onClick = { viewModel.showLicense() }
                )

                // Update Action
                val statusText = when (val status = appUpdateStatus) {
                    is MainViewModel.AppUpdateStatus.Checking -> "Checking for updates..."
                    is MainViewModel.AppUpdateStatus.Available -> "Update available: ${status.version}"
                    is MainViewModel.AppUpdateStatus.Downloading -> "Downloading: ${(status.progress * 100).toInt()}%"
                    is MainViewModel.AppUpdateStatus.UpToDate -> "Latest version installed"
                    is MainViewModel.AppUpdateStatus.Error -> "Error: ${status.message}"
                    else -> "Check for software updates"
                }

                InfoRow(
                    icon = Icons.Default.Sync,
                    title = "Software Update",
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
                                    "UPDATE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
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

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

private data class CreditEntry(
    val name: String,
    val role: String,
    val githubUsername: String?
)
