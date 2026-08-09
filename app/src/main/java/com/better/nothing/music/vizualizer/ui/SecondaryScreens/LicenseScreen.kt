package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.ScreenTitle

@Composable
internal fun LicenseScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val scrollState = rememberScrollState()
    val licenseStatus by viewModel.licenseStatus.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LocalAppSpacing.current.edge)
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))


            ScreenTitle(text = stringResource(R.string.license_title).uppercase())

            ExpressiveCard {
                when (val status = licenseStatus) {
                    is MainViewModel.LicenseStatus.Loading -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is MainViewModel.LicenseStatus.Success -> {
                        DynamicLicenseText(status.content)
                    }
                    is MainViewModel.LicenseStatus.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.failed_load_license), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.fetchLicense() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DynamicLicenseText(content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (index == 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("• ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        BodyText(text = trimmed.removePrefix("* "), size = 13.sp)
                    }
                }
                trimmed.contains(":") && isMetadataLine(trimmed) -> {
                    val parts = trimmed.split(":", limit = 2)
                    Column {
                        Text(parts[0].trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(parts.getOrElse(1) { "" }.trim(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    // Add a divider after the metadata section (usually ends with Organization)
                    if (trimmed.startsWith("Organization")) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
                trimmed == stringResource(R.string.end_license_agreement) -> {
                    Text(trimmed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    BodyText(text = trimmed, size = 14.sp)
                }
            }
        }
    }
}

private fun isMetadataLine(line: String): Boolean {
    val prefixes = listOf("Version", "Date", "Licensor", "Project Founder", "Organization")
    return prefixes.any { line.startsWith(it) }
}