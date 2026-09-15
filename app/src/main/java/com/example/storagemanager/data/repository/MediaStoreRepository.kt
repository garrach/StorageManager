package com.example.storagemanager.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.storagemanager.data.model.CategoryUsage
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.SortOrder
import com.example.storagemanager.data.model.StorageCategory
import com.example.storagemanager.data.source.MediaStorePagingSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaStoreRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun filesPager(
        category: StorageCategory,
        sortOrder: SortOrder = SortOrder.DATE_DESC,
        searchQuery: String? = null,
    ): Flow<PagingData<FileEntry>> = Pager(
        config = PagingConfig(
            pageSize = 40,
            prefetchDistance = 8,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            MediaStorePagingSource.forCategory(contentResolver, category, sortOrder, searchQuery)
        },
    ).flow

    suspend fun getCategoryUsage(): Map<StorageCategory, CategoryUsage> = withContext(ioDispatcher) {
        val result = mutableMapOf<StorageCategory, CategoryUsage>()

        result[StorageCategory.IMAGES] = sumCollection(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        result[StorageCategory.VIDEOS] = sumCollection(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        result[StorageCategory.AUDIO] = sumCollection(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            result[StorageCategory.DOWNLOADS] = sumCollection(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            )
        } else {
            result[StorageCategory.DOWNLOADS] = CategoryUsage(0, 0)
        }

        val documents = sumCollection(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            selection = "${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.MediaColumns.MIME_TYPE} = ?",
            selectionArgs = arrayOf("text/%", "application/pdf"),
        )
        result[StorageCategory.DOCUMENTS] = documents

        val other = sumCollection(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            selection = "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ? AND " +
                "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ? AND " +
                "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ? AND " +
                "(${MediaStore.MediaColumns.MIME_TYPE} IS NULL OR " +
                "${MediaStore.MediaColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ?)",
            selectionArgs = arrayOf(
                "image/%", "video/%", "audio/%",
                "text/%", "application/pdf",
            ),
        )
        result[StorageCategory.OTHER] = other

        result
    }

    private fun sumCollection(
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
    ): CategoryUsage {
        var size = 0L
        var count = 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use { c ->
                val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    if (sizeCol >= 0 && !c.isNull(sizeCol)) {
                        val s = c.getLong(sizeCol)
                        if (s > 0) {
                            size += s
                            count++
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return CategoryUsage(size = size, count = count)
    }
}
