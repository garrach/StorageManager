package com.example.storagemanager.ui.scanner

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.ui.components.FileListItem
import com.example.storagemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: (() -> Unit)?,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folder Scanner") },
                navigationIcon = {
                    onBack?.let { back ->
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (uiState.scanProgress != null && uiState.isScanning) {
                        IconButton(onClick = { viewModel.cancelScan() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel scan")
                        }
                    }
                    if (uiState.selectedEntries.isNotEmpty()) {
                        Text(
                            "${uiState.selectedEntries.size} selected",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Filled.Deselect, contentDescription = "Deselect all")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.showVolumePicker -> {
                VolumePickerContent(
                    volumes = viewModel.getVolumes(),
                    onVolumeSelected = { viewModel.startScan(it) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            uiState.isScanning && uiState.scanProgress?.entries.isNullOrEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Scanning...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = uiState.scanProgress?.currentPath ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }
            else -> {
                ScanResultContent(
                    uiState = uiState,
                    onToggleSelection = { viewModel.toggleSelection(it) },
                    onToggleFolder = { viewModel.toggleFolder(it) },
                    onSelectAll = { viewModel.selectAllVisible() },
                    onBackToPicker = { viewModel.resetToPicker() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun VolumePickerContent(
    volumes: List<String>,
    onVolumeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Select a storage volume to scan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        volumes.forEach { path ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onVolumeSelected(path) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.SdStorage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = path.substringAfterLast('/').ifEmpty { path },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanResultContent(
    uiState: ScannerUiState,
    onToggleSelection: (Long) -> Unit,
    onToggleFolder: (String) -> Unit,
    onSelectAll: () -> Unit,
    onBackToPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = uiState.scanProgress?.entries ?: emptyList()
    val hasSelection = uiState.selectedEntries.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        // Progress bar during scan
        if (uiState.isScanning && uiState.scanProgress != null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${FormatUtils.formatCount(uiState.scanProgress.filesScanned)} files • ${FormatUtils.formatBytes(uiState.scanProgress.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Stats bar
        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${entries.size} items • ${FormatUtils.formatBytes(entries.sumOf { it.size })}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row {
                    if (!hasSelection) {
                        TextButton(onClick = onSelectAll) { Text("Select All") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onBackToPicker) { Text("New Scan") }
                }
            }
        }

        // File tree
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            val visibleEntries = entries.filter { entry ->
                entry.parentPath == null || entry.parentPath in uiState.expandedFolders
            }

            items(
                items = visibleEntries,
                key = { it.id },
            ) { entry ->
                val isSelected = entry.id in uiState.selectedEntries
                val indent = entry.depth.dp * 12

                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (entry.isDirectory) {
                                Icon(
                                    imageVector = if (entry.parentPath in uiState.expandedFolders)
                                        Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = if (entry.isDirectory) "Folder"
                            else "${FormatUtils.formatBytes(entry.size)} • ${entry.extension.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        if (!entry.isDirectory) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection(entry.id) },
                            )
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                    },
                    trailingContent = {
                        if (entry.isDirectory) {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(start = indent)
                        .clickable(enabled = entry.isDirectory) {
                            onToggleFolder(entry.path ?: return@clickable)
                        }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface,
                        ),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = indent),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }

        // FAB for scan in progress
        if (uiState.isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                SmallFloatingActionButton(
                    onClick = { viewModel.cancelScan() },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop scan")
                }
            }
        }
    }
}