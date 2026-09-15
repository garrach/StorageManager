package com.example.storagemanager.data.model

data class ScanProgress(
    val isScanning: Boolean = true,
    val filesScanned: Long = 0,
    val totalBytes: Long = 0,
    val currentPath: String? = null,
    val truncated: Boolean = false,
    val error: String? = null,
    val entries: List<FileEntry> = emptyList(),
) {
    val isComplete: Boolean get() = !isScanning
    val isEmpty: Boolean get() = entries.isEmpty()
}

data class ScanResult(
    val entries: List<FileEntry>,
    val filesScanned: Long,
    val totalBytes: Long,
    val truncated: Boolean,
)