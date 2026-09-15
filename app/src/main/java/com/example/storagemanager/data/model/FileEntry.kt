package com.example.storagemanager.data.model

import android.net.Uri

data class FileEntry(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String?,
    val mimeType: String?,
    val size: Long,
    val dateModified: Long,
    val isDirectory: Boolean,
    val category: StorageCategory,
    val depth: Int = 0,
    val parentPath: String? = null,
) {
    val extension: String
        get() = com.example.storagemanager.util.FileSystemUtils.getExtension(name)
}