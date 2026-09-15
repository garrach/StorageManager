package com.example.storagemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.storagemanager.data.model.CleanupSuggestion
import com.example.storagemanager.data.model.SuggestionType
import com.example.storagemanager.util.FormatUtils

@Composable
fun SuggestionCard(
    suggestion: CleanupSuggestion,
    onExpand: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = suggestionIcon(suggestion.type),
                    contentDescription = suggestion.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = suggestion.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (suggestion.reclaimableBytes > 0) {
                    SuggestionChip(
                        onClick = onExpand,
                        label = { Text(FormatUtils.formatBytes(suggestion.reclaimableBytes)) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                FilledTonalButton(
                    onClick = onDeleteAll,
                    enabled = suggestion.itemCount > 0,
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Clean (${suggestion.itemCount})")
                }
            }
        }
    }
}

private fun suggestionIcon(type: SuggestionType): ImageVector = when (type) {
    SuggestionType.LARGE_FILES -> Icons.Filled.Storage
    SuggestionType.DUPLICATES -> Icons.Filled.ContentCopy
    SuggestionType.JUNK_FILES -> Icons.Filled.CleaningServices
    SuggestionType.EMPTY_FOLDERS -> Icons.Filled.FolderOff
}