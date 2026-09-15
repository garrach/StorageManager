package com.example.storagemanager.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.storage.StorageManager
import android.os.storage.StorageStatsManager
import com.example.storagemanager.data.model.CategoryUsage
import com.example.storagemanager.data.model.StorageOverview
import com.example.storagemanager.util.StorageUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppUsageItem(
    val packageName: String,
    val label: String,
    val codeSize: Long,
    val dataSize: Long,
    val cacheSize: Long,
    val totalSize: Long,
)

class StorageRepository @Inject constructor(
    private val context: Context,
    private val storageUtils: StorageUtils,
    private val mediaStoreRepository: MediaStoreRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun getOverview(): StorageOverview = withContext(ioDispatcher) {
        val stats = storageUtils.getPrimaryStorageStats()
        val categoryUsage = mediaStoreRepository.getCategoryUsage()
        val appUsage = getAppUsage()

        val appSize = appUsage.sumOf { it.totalSize }
        val merged = categoryUsage.toMutableMap()
        merged[com.example.storagemanager.data.model.StorageCategory.APPS] =
            CategoryUsage(size = appSize, count = appUsage.size.toLong())

        StorageOverview(
            totalBytes = stats.totalBytes,
            usedBytes = stats.usedBytes,
            freeBytes = stats.freeBytes,
            volumeLabel = stats.volumeLabel,
            categoryUsage = merged.mapValues { it.value.size },
            categoryFileCounts = merged.mapValues { it.value.count },
        )
    }

    suspend fun getAppUsage(): List<AppUsageItem> = withContext(ioDispatcher) {
        val packageManager = context.packageManager
        val storageStatsManager = context.getSystemService(Context.STORAGE_SERVICE)
            .let { it as? StorageStatsManager }
            ?: return@withContext emptyList()

        val volume = (context.getSystemService(Context.STORAGE_SERVICE) as StorageManager)
            .storageVolumes
            .firstOrNull { it.isPrimary }
        val volumeUuid = volume?.uuid

        val packages = runCatching {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        }.getOrElse { emptyList() }

        packages.mapNotNull { app ->
            val stats = runCatching {
                storageStatsManager.queryStatsForPackage(
                    volumeUuid,
                    app.packageName,
                    android.os.UserHandle.myUserId(),
                )
            }.getOrNull() ?: return@mapNotNull null

            AppUsageItem(
                packageName = app.packageName,
                label = runCatching { packageManager.getApplicationLabel(app)?.toString() }
                    .getOrElse { app.packageName },
                codeSize = stats.codeBytes,
                dataSize = stats.dataBytes,
                cacheSize = stats.cacheBytes,
                totalSize = stats.codeBytes + stats.dataBytes + stats.cacheBytes,
            )
        }.sortedWith(
            compareByDescending<AppUsageItem> { it.totalSize }.thenBy { it.label }
        )
    }
}