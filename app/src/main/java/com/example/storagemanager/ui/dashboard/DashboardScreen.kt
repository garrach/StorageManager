package com.example.storagemanager.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.storagemanager.data.model.StorageCategory
import com.example.storagemanager.data.model.StorageOverview
import com.example.storagemanager.ui.components.CategoryCard
import com.example.storagemanager.ui.components.StorageRingChart
import com.example.storagemanager.ui.components.StorageTreemap
import com.example.storagemanager.ui.components.TreemapItem
import com.example.storagemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToCategory: (StorageCategory) -> Unit,
    onNavigateToApps: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Manager") },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.overview == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.overview != null -> {
                DashboardContent(
                    overview = uiState.overview!!,
                    onNavigateToCategory = onNavigateToCategory,
                    onNavigateToApps = onNavigateToApps,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.load() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    overview: StorageOverview,
    onNavigateToCategory: (StorageCategory) -> Unit,
    onNavigateToApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 170.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            StorageOverviewCard(overview)
        }

        items(StorageCategory.entries.filter { it != StorageCategory.APPS }) { category ->
            val size = overview.categoryUsage[category] ?: 0L
            val count = overview.categoryFileCounts[category] ?: 0L
            CategoryCard(
                category = category,
                sizeBytes = size,
                fileCount = count,
                totalSizeBytes = overview.usedBytes,
                onClick = { onNavigateToCategory(category) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Button(
                onClick = onNavigateToApps,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View Per-App Usage")
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    overview: StorageOverview,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            if (maxWidth >= 640.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StorageRingChart(
                            usedFraction = overview.usedFraction,
                            totalLabel = FormatUtils.formatBytes(overview.totalBytes),
                            usedLabel = "Used",
                            freeLabel = FormatUtils.formatBytes(overview.freeBytes),
                            size = 180.dp,
                            strokeWidth = 24.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "${FormatUtils.formatBytes(overview.usedBytes)} of ${FormatUtils.formatBytes(overview.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${FormatUtils.formatBytes(overview.freeBytes)} free",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    StorageTreemap(
                        items = treemapItems(overview),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StorageRingChart(
                        usedFraction = overview.usedFraction,
                        totalLabel = FormatUtils.formatBytes(overview.totalBytes),
                        usedLabel = "Used",
                        freeLabel = FormatUtils.formatBytes(overview.freeBytes),
                        size = 200.dp,
                        strokeWidth = 28.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "${FormatUtils.formatBytes(overview.usedBytes)} of ${FormatUtils.formatBytes(overview.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${FormatUtils.formatBytes(overview.freeBytes)} free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    StorageTreemap(
                        items = treemapItems(overview),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun treemapItems(overview: StorageOverview): List<TreemapItem> =
    StorageCategory.entries.mapNotNull { category ->
        val size = overview.categoryUsage[category] ?: 0L
        if (size <= 0) null
        else TreemapItem(
            label = category.label,
            value = size,
            color = categoryColor(category),
        )
    }

private fun categoryColor(category: StorageCategory): Color = when (category) {
    StorageCategory.IMAGES -> Color(0xFF00897B)
    StorageCategory.VIDEOS -> Color(0xFFE53935)
    StorageCategory.AUDIO -> Color(0xFF8E24AA)
    StorageCategory.DOWNLOADS -> Color(0xFF1E88E5)
    StorageCategory.DOCUMENTS -> Color(0xFF43A047)
    StorageCategory.APPS -> Color(0xFFFB8C00)
    StorageCategory.OTHER -> Color(0xFF757575)
}