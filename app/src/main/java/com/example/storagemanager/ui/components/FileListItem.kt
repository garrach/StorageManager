package com.example.storagemanager.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.util.FormatUtils

@Composable
fun FileListItem(
    entry: FileEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailDrawable: Drawable? = null,
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
                text = "${FormatUtils.formatBytes(entry.size)} • ${FormatUtils.formatMillisAgo(entry.dateModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            if (thumbnailDrawable != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailDrawable)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = fileIcon(entry),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (entry.isDirectory) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

private fun fileIcon(entry: FileEntry): ImageVector = when {
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