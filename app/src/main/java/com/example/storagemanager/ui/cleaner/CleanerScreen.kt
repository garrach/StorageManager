package com.example.storagemanager.ui.cleaner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.storagemanager.ui.components.SuggestionCard
import com.example.storagemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
    onBack: (() -> Unit)?,
    viewModel: CleanerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.deletionResult) {
        uiState.deletionResult?.let { report ->
            val msg = buildString {
                append("Deleted ${report.deletedCount} file${if (report.deletedCount != 1) "s" else ""}")
                if (report.bytesFreed > 0) append(" • freed ${FormatUtils.formatBytes(report.bytesFreed)}")
                if (report.failedCount > 0) append(" • ${report.failedCount} failed")
            }
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissResult()
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = uiState.suggestions,
                        key = { it.type },
                    ) { suggestion ->
                        Column {
                            SuggestionCard(
                                suggestion = suggestion,
                                onExpand = { viewModel.toggleExpanded(suggestion.type) },
                                onDeleteAll = { viewModel.deleteSuggestionItems(suggestion) },
                            )
                            AnimatedVisibility(
                                visible = uiState.expandedType == suggestion.type,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    suggestion.items.take(30).forEach { entry ->
                                        val isSelected = entry.id in uiState.selectedForDeletion
                                        ListItem(
                                            headlineContent = { Text(entry.name) },
                                            supportingContent = {
                                                Text(
                                                    FormatUtils.formatBytes(entry.size),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            },
                                            leadingContent = {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        viewModel.toggleItemSelection(entry.id)
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    if (suggestion.items.size > 30) {
                                        Text(
                                            "... and ${suggestion.items.size - 30} more",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                                        )
                                    }
                                    HorizontalDivider(Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}