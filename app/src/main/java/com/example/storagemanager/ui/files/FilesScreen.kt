package com.example.storagemanager.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.SortOrder
import com.example.storagemanager.ui.components.FileListItem
import com.example.storagemanager.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FilesScreen(
    categoryRoute: String,
    onBack: () -> Unit,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagingItems = viewModel.filesPagingData.collectAsLazyPagingItems()

    val navigator = rememberListDetailPaneScaffoldNavigator<FileEntry>()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<FileEntry?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.category.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            listPane = {
                AnimatedPane {
                    FilesListPane(
                        uiState = uiState,
                        pagingItems = pagingItems,
                        onFileClick = { entry ->
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry)
                            }
                        },
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onSortSelected = { viewModel.setSortOrder(it) },
                        onToggleGrid = { viewModel.toggleGrid() },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val selected = navigator.currentDestination?.contentKey
                    if (selected == null) {
                        EmptyDetailPane()
                    } else {
                        FileDetailPane(
                            entry = selected,
                            isDeleting = uiState.isDeleting,
                            onDelete = { pendingDelete = selected },
                            onNavigateBack = {
                                scope.launch { navigator.navigateBack() }
                            },
                        )
                    }
                }
            },
        )
    }

    pendingDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "Deleting \"${toDelete.name}\" (${FormatUtils.formatBytes(toDelete.size)}) " +
                        "cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFiles(listOf(toDelete))
                        pendingDelete = null
                        scope.launch {
                            navigator.currentDestination?.let { navigator.navigateBack() }
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FilesListPane(
    uiState: FilesUiState,
    pagingItems: LazyPagingItems<FileEntry>,
    onFileClick: (FileEntry) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onToggleGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search files...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort files")
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            leadingIcon = {
                                if (uiState.sortOrder == order) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onSortSelected(order)
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onToggleGrid) {
                Icon(
                    imageVector = if (uiState.isGrid) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (uiState.isGrid) "List view" else "Grid view",
                )
            }
        }
        Text(
            text = "${uiState.sortOrder.label} • ${pagingItems.itemCount} items",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))

        when (pagingItems.loadState.refresh) {
            is LoadState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LoadState.Error -> {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Failed to load files",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { pagingItems.retry() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            is LoadState.NotLoading -> {
                if (pagingItems.itemCount == 0) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (uiState.searchQuery.isBlank()) "No files in this category"
                            else "No results for \"${uiState.searchQuery}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (uiState.isGrid) {
                    FilesGrid(
                        pagingItems = pagingItems,
                        onFileClick = onFileClick,
                    )
                } else {
                    FilesList(
                        pagingItems = pagingItems,
                        onFileClick = onFileClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesList(
    pagingItems: LazyPagingItems<FileEntry>,
    onFileClick: (FileEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { "${it.id}_${it.uri}" },
        ) { index ->
            val entry = pagingItems[index] ?: return@items
            FileListItem(entry = entry, onClick = { onFileClick(entry) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
        if (pagingItems.loadState.append is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FilesGrid(
    pagingItems: LazyPagingItems<FileEntry>,
    onFileClick: (FileEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { "${it.id}_${it.uri}" },
        ) { index ->
            val entry = pagingItems[index] ?: return@items
            FileGridCard(entry = entry, onClick = { onFileClick(entry) })
        }
        if (pagingItems.loadState.append is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FileGridCard(
    entry: FileEntry,
    onClick: () -> Unit,
) {
    val showThumb = entry.mimeType?.startsWith("image/") == true ||
        entry.mimeType?.startsWith("video/") == true
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showThumb) {
                AsyncImage(
                    model = entry.uri,
                    contentDescription = entry.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = gridFileIcon(entry),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = FormatUtils.formatBytes(entry.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Select a file for details",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileDetailPane(
    entry: FileEntry,
    isDeleting: Boolean,
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
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = gridFileIcon(entry),
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
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow("Size", FormatUtils.formatBytes(entry.size))
                DetailRow("Modified", FormatUtils.formatMillisAgo(entry.dateModified))
                DetailRow("Type", entry.extension.uppercase().ifBlank { "File" })
                entry.mimeType?.let { DetailRow("MIME", it) }
                entry.category.label.let { DetailRow("Category", it) }
                entry.path?.let { DetailRow("Path", it) }
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
                enabled = !isDeleting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

private fun gridFileIcon(entry: FileEntry): ImageVector = when {
    entry.isDirectory -> Icons.Filled.Folder
    entry.mimeType?.startsWith("image/") == true -> Icons.Filled.Image
    entry.mimeType?.startsWith("video/") == true -> Icons.Filled.Videocam
    entry.mimeType?.startsWith("audio/") == true -> Icons.Filled.MusicNote
    entry.mimeType?.startsWith("text/") == true ||
        entry.extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "md")
        -> Icons.Filled.Description
    entry.extension in setOf("zip", "rar", "7z", "tar", "gz", "apk") -> Icons.Filled.Archive
    else -> Icons.Filled.InsertDriveFile
}