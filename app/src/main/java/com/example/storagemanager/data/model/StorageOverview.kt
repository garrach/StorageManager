package com.example.storagemanager.data.model

data class StorageOverview(
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val volumeLabel: String = "",
    val categoryUsage: Map<StorageCategory, Long> = emptyMap(),
    val categoryFileCounts: Map<StorageCategory, Long> = emptyMap(),
) {
    val usedFraction: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
}