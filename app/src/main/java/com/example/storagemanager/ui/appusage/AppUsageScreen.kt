package com.example.storagemanager.ui.appusage

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.storagemanager.data.repository.AppUsageItem
import com.example.storagemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    onBack: (() -> Unit)?,
    viewModel: AppUsageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredApps = viewModel.getFilteredApps()
    val context = LocalContext.current
    val pkgManager = context.packageManager

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Usage") },
                navigationIcon = {
                    onBack?.let { back ->
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.padding(innerPadding)) {
                    // Search bar
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search apps...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                    )
                    // Filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.filter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = { Text(filter.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName },
                        ) { app ->
                            AppUsageItemRow(
                                app = app,
                                icon = runCatching { pkgManager.getApplicationIcon(app.packageName) }
                                    .getOrNull(),
                            )
                        }
                        if (filteredApps.isEmpty() && uiState.apps.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "No apps match your filter",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageItemRow(
    app: AppUsageItem,
    icon: Drawable?,
) {
    ListItem(
        headlineContent = {
            Text(
                text = app.label,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = FormatUtils.formatBytes(app.totalSize),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Code: ${FormatUtils.formatBytes(app.codeSize)}", style = MaterialTheme.typography.labelSmall)
                    Text("Data: ${FormatUtils.formatBytes(app.dataSize)}", style = MaterialTheme.typography.labelSmall)
                    Text("Cache: ${FormatUtils.formatBytes(app.cacheSize)}", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        leadingContent = {
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap(40, 40).asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}