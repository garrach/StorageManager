package com.example.storagemanager.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageStats(
    val volumeLabel: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val path: String,
) {
    val usedFraction: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
}

class StorageUtils @Inject constructor(
    private val context: Context,
) {

    @SuppressLint("UsableSpace")
    suspend fun getPrimaryStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
        val volumes = manager.storageVolumes
        val primary = volumes.firstOrNull { it.isPrimary }
        val dir = primary?.directory ?: Environment.getExternalStorageDirectory()
        val statFs = StatFs(dir.path)
        val blockSize = statFs.blockSizeLong
        val total = statFs.blockCountLong * blockSize
        val free = statFs.availableBlocksLong * blockSize
        val label = primary?.getDescription(context) ?: dir.name
        StorageStats(
            volumeLabel = label,
            totalBytes = total,
            usedBytes = total - free,
            freeBytes = free,
            path = dir.path,
        )
    }

    fun getRootVolumes(): List<StorageVolumeInfo> {
        val manager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
        return manager.storageVolumes.mapNotNull { volume ->
            val dir = volume.directory ?: return@mapNotNull null
            val free = try {
                val statFs = StatFs(dir.path)
                statFs.availableBlocksLong * statFs.blockSizeLong
            } catch (_: Exception) {
                0L
            }
            StorageVolumeInfo(
                label = volume.getDescription(context),
                path = dir.path,
                isPrimary = volume.isPrimary,
                isRemovable = volume.isRemovable,
                freeBytes = free,
            )
        }
    }

    data class StorageVolumeInfo(
        val label: String,
        val path: String,
        val isPrimary: Boolean,
        val isRemovable: Boolean,
        val freeBytes: Long,
    )
}