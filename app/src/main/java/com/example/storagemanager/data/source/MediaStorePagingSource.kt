package com.example.storagemanager.data.source

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.storagemanager.data.mapper.MediaStoreMapper
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.SortOrder
import com.example.storagemanager.data.model.StorageCategory

class MediaStorePagingSource(
    private val contentResolver: ContentResolver,
    private val collectionUri: Uri,
    private val category: StorageCategory,
    private val selection: String?,
    private val selectionArgs: Array<String>?,
    private val sortOrder: SortOrder,
    private val searchQuery: String?,
) : PagingSource<Int, FileEntry>() {

    override fun getRefreshKey(state: PagingState<Int, FileEntry>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileEntry> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        val orderSql = "${sortOrder.sql} LIMIT $limit OFFSET $offset"

        val (finalSelection, finalArgs) = if (!searchQuery.isNullOrBlank()) {
            val searchSelection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val searchArg = "%$searchQuery%"
            if (selection != null) {
                "$selection AND $searchSelection" to (selectionArgs.orEmpty() + searchArg)
            } else {
                searchSelection to arrayOf(searchArg)
            }
        } else {
            selection to selectionArgs
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
        )

        val entries = mutableListOf<FileEntry>()
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                collectionUri,
                projection,
                finalSelection,
                finalArgs,
                orderSql,
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    entries.add(MediaStoreMapper.toFileEntry(c, collectionUri))
                }
            }
            LoadResult.Page(
                data = entries,
                prevKey = if (offset > 0) offset - limit else null,
                nextKey = if (entries.size == limit) offset + entries.size else null,
            )
        } catch (e: SecurityException) {
            LoadResult.Error(e)
        } catch (e: Exception) {
            LoadResult.Error(e)
        } finally {
            cursor?.close()
        }
    }

    companion object {
        private fun baseSelection(category: StorageCategory): Pair<String?, Array<String>?> = when (category) {
            StorageCategory.IMAGES -> "${MediaStore.MediaColumns.SIZE} > ?" to arrayOf("0")
            StorageCategory.VIDEOS -> "${MediaStore.MediaColumns.SIZE} > ?" to arrayOf("0")
            StorageCategory.AUDIO -> "${MediaStore.MediaColumns.SIZE} > ?" to arrayOf("0")
            StorageCategory.DOWNLOADS -> "${MediaStore.MediaColumns.SIZE} > ?" to arrayOf("0")
            StorageCategory.DOCUMENTS -> (
                "${MediaStore.MediaColumns.SIZE} > ? AND " +
                    "(${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR " +
                    "(${MediaStore.MediaColumns.MIME_TYPE} LIKE ? AND " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} NOT LIKE ?))"
                ) to arrayOf("0", "text/%", "application/pdf", "%.pdf")
            StorageCategory.OTHER -> (
                "${MediaStore.MediaColumns.SIZE} > ? AND " +
                    "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ? AND " +
                    "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ? AND " +
                    "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ?"
                ) to arrayOf("0", "image/%", "video/%", "audio/%")
            StorageCategory.APPS -> "${MediaStore.MediaColumns.SIZE} < 0" to null
        }

        private fun collectionUri(category: StorageCategory): Uri = when (category) {
            StorageCategory.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            StorageCategory.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            StorageCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            StorageCategory.DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            StorageCategory.DOCUMENTS,
            StorageCategory.OTHER,
            StorageCategory.APPS -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        fun forCategory(
            contentResolver: ContentResolver,
            category: StorageCategory,
            sortOrder: SortOrder = SortOrder.DATE_DESC,
            searchQuery: String? = null,
        ): MediaStorePagingSource {
            val (selection, args) = baseSelection(category)
            return MediaStorePagingSource(
                contentResolver = contentResolver,
                collectionUri = collectionUri(category),
                category = category,
                selection = selection,
                selectionArgs = args,
                sortOrder = sortOrder,
                searchQuery = searchQuery,
            )
        }
    }
}
