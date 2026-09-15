package com.example.storagemanager.ui.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.storagemanager.data.model.CleanupSuggestion
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.SuggestionType
import com.example.storagemanager.data.model.TrashEntry
import com.example.storagemanager.ui.components.SuggestionCard
import com.example.storagemanager.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CleanerScreen(
    onBack: (() -> Unit)?,
    viewModel: CleanerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
            )
            if (event.actionLabel != null && result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastTrash()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean Up") },
                navigationIcon = {
                    onBack?.let { back ->
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    BadgedBox(
                        badge = {
                            if (uiState.trashCount > 0) {
                                Badge { Text(if (uiState.trashCount > 99) "99+" else uiState.trashCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { viewModel.openRecycleBin() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Recycle Bin")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Text("Error", color = MaterialTheme.colorScheme.error)
                        Text(uiState.error ?: "")
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }
            }
            uiState.suggestions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "All clean!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No cleanup suggestions at this time",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                val navigator = rememberListDetailPaneScaffoldNavigator<SuggestionType>()
                val scope = rememberCoroutineScope()
                NavigableListDetailPaneScaffold(
                    navigator = navigator,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    listPane = {
                        AnimatedPane {
                            CleanerListPane(
                                suggestions = uiState.suggestions,
                                selectedType = uiState.selectedType,
                                onSuggestionClick = { type ->
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, type)
                                    }
                                    viewModel.toggleExpanded(type)
                                },
                                onCleanAll = { viewModel.deleteSuggestionItems(it) },
                            )
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            val type = navigator.currentDestination?.contentKey
                            val suggestion = uiState.suggestions.firstOrNull { it.type == type }
                            if (suggestion == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Select a suggestion to review items",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                SuggestionItemsPane(
                                    suggestion = suggestion,
                                    selectedForDeletion = uiState.selectedForDeletion,
                                    onToggleItem = { viewModel.toggleItemSelection(it) },
                                    onTrashItem = { viewModel.trashItem(it) },
                                    onCleanAll = { viewModel.deleteSuggestionItems(suggestion) },
                                    onNavigateBack = { scope.launch { navigator.navigateBack() } },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (uiState.showRecycleBin) {
        RecycleBinDialog(
            entries = uiState.trashEntries,
            isLoading = uiState.isTrashLoading,
            onDismiss = { viewModel.closeRecycleBin() },
            onRestore = { viewModel.restoreTrashEntry(it) },
            onPermanentDelete = { viewModel.permanentlyDeleteTrashEntry(it) },
            onEmpty = { viewModel.emptyTrash() },
        )
    }
}

@Composable
private fun CleanerListPane(
    suggestions: List<CleanupSuggestion>,
    selectedType: SuggestionType?,
    onSuggestionClick: (SuggestionType) -> Unit,
    onCleanAll: (CleanupSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "${suggestions.size} category${if (suggestions.size != 1) "ies" else "y"} found",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(
            items = suggestions,
            key = { it.type },
        ) { suggestion ->
            SuggestionCard(
                suggestion = suggestion,
                onExpand = { onSuggestionClick(suggestion.type) },
                onDeleteAll = { onCleanAll(suggestion) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionItemsPane(
    suggestion: CleanupSuggestion,
    selectedForDeletion: Set<Long>,
    onToggleItem: (Long) -> Unit,
    onTrashItem: (FileEntry) -> Unit,
    onCleanAll: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${suggestion.itemCount} item${if (suggestion.itemCount != 1) "s" else ""} • " +
                        FormatUtils.formatBytes(suggestion.reclaimableBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onCleanAll,
                enabled = suggestion.itemCount > 0,
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clean All")
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(
                items = suggestion.items,
                key = { it.id },
            ) { entry ->
                SwipeToDismissItem(
                    entry = entry,
                    isSelected = entry.id in selectedForDeletion,
                    onToggleSelection = { onToggleItem(entry.id) },
                    onTrash = { onTrashItem(entry) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissItem(
    entry: FileEntry,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onTrash: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onTrash()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Move to Recycle Bin",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
            },
            supportingContent = {
                Text(
                    text = FormatUtils.formatBytes(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                )
            },
        )
    }
}

@Composable
private fun RecycleBinDialog(
    entries: List<TrashEntry>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRestore: (TrashEntry) -> Unit,
    onPermanentDelete: (TrashEntry) -> Unit,
    onEmpty: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recycle Bin", modifier = Modifier.weight(1f))
                IconButton(onClick = onEmpty, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = "Empty Recycle Bin")
                }
            }
        },
        text = {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Recycle Bin is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                    ) {
                        items(
                            items = entries,
                            key = { it.trashName },
                        ) { entry ->
                            TrashEntryRow(
                                entry = entry,
                                onRestore = { onRestore(entry) },
                                onPermanentDelete = { onPermanentDelete(entry) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun TrashEntryRow(
    entry: TrashEntry,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = "${FormatUtils.formatBytes(entry.size)} • ${FormatUtils.formatMillisAgo(entry.trashedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Filled.Restore,
                        contentDescription = "Restore",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onPermanentDelete) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = "Delete forever",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}