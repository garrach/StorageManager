package com.example.storagemanager.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.navigation.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ScannerScreen(
    onBack: (() -> Unit)?,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<FileEntry?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
            )
            if (event.actionLabel != null && result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastDeletion()
            }
        }
    }

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
                            Icon(Icons.Filled.ClearAll, contentDescription = "Deselect all")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                ScanningContent(
                    currentPath = uiState.scanProgress?.currentPath,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                val navigator = rememberListDetailPaneScaffoldNavigator<FileEntry>()
                val scope = rememberCoroutineScope()
                NavigableListDetailPaneScaffold(
                    navigator = navigator,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    listPane = {
                        AnimatedPane {
                            ScanResultContent(
                                uiState = uiState,
                                onToggleSelection = { viewModel.toggleSelection(it) },
                                onToggleFolder = { viewModel.toggleFolder(it) },
                                onSelectAll = { viewModel.selectAllVisible() },
                                onBackToPicker = { viewModel.resetToPicker() },
                                onFileClick = { entry ->
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry)
                                    }
                                },
                                onCancelScan = { viewModel.cancelScan() },
                            )
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            val selected = navigator.currentDestination?.contentKey
                            if (selected == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Select a file for details",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                ScannerDetailPane(
                                    entry = selected,
                                    onDelete = { pendingDelete = selected },
                                    onNavigateBack = { scope.launch { navigator.navigateBack() } },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    pendingDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Move to Recycle Bin?") },
            text = {
                Text(
                    "\"${toDelete.name}\" (${FormatUtils.formatBytes(toDelete.size)}) will be " +
                        "moved to the Recycle Bin and can be restored later."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(toDelete)
                        pendingDelete = null
                    },
                ) {
                    Text("Move to Recycle Bin")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
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
private fun ScanningContent(
    currentPath: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
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
                text = currentPath ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
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
    onFileClick: (FileEntry) -> Unit,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = uiState.scanProgress?.entries ?: emptyList()
    val hasSelection = uiState.selectedEntries.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.isScanning && uiState.scanProgress != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "${FormatUtils.formatCount(uiState.scanProgress.filesScanned)} files • ${FormatUtils.formatBytes(uiState.scanProgress.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

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
                        .clickable {
                            if (entry.isDirectory) {
                                onToggleFolder(entry.path ?: return@clickable)
                            } else {
                                onFileClick(entry)
                            }
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

        if (uiState.isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                SmallFloatingActionButton(
                    onClick = onCancelScan,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop scan")
                }
            }
        }
    }
}

@Composable
private fun ScannerDetailPane(
    entry: FileEntry,
    onDelete: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = scannerFileIcon(entry),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ScannerDetailRow("Size", FormatUtils.formatBytes(entry.size))
                ScannerDetailRow("Path", entry.name)
                entry.parentPath?.let { parent ->
                    ScannerDetailRow("Folder", parent, maxLines = 3)
                }
                ScannerDetailRow("Category", entry.category.label)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Trash")
            }
        }
    }
}

@Composable
private fun ScannerDetailRow(
    label: String,
    value: String,
    maxLines: Int = 2,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun scannerFileIcon(entry: FileEntry): ImageVector = when {
    entry.isDirectory -> Icons.Filled.Folder
    entry.mimeType?.startsWith("image/") == true -> Icons.Filled.Image
    entry.mimeType?.startsWith("video/") == true -> Icons.Filled.Videocam
    entry.mimeType?.startsWith("audio/") == true -> Icons.Filled.MusicNote
    entry.extension in setOf("zip", "rar", "7z", "tar", "gz", "apk") -> Icons.Filled.Archive
    entry.extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "md") ->
        Icons.Filled.Description
    else -> Icons.Filled.InsertDriveFile
}