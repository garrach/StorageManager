package com.example.storagemanager.data.source

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.storagemanager.data.mapper.MediaStoreMapper
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.StorageCategory

class MediaStorePagingSource(
    private val contentResolver: ContentResolver,
    private val collectionUri: Uri,
    private val category: StorageCategory,
    private val selection: String?,
    private val selectionArgs: Array<String>?,
) : PagingSource<Int, FileEntry>() {

    override fun getRefreshKey(state: PagingState<Int, FileEntry>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileEntry> {
        val offset = params.key ?: 0
        val limit = params.loadSize
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC LIMIT $limit OFFSET $offset"

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
                selection,
                selectionArgs,
                sortOrder,
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
        fun forCategory(
            contentResolver: ContentResolver,
            category: StorageCategory,
        ): MediaStorePagingSource {
            val (uri, selection, args) = when (category) {
                StorageCategory.IMAGES -> Triple(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.SIZE} > ?",
                    arrayOf("0")
                )
                StorageCategory.VIDEOS -> Triple(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.SIZE} > ?",
                    arrayOf("0")
                )
                StorageCategory.AUDIO -> Triple(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.SIZE} > ?",
                    arrayOf("0")
                )
                StorageCategory.DOWNLOADS -> Triple(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.SIZE} > ?",
                    arrayOf("0")
                )
                StorageCategory.DOCUMENTS -> Triple(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${MediaStore.MediaColumns.SIZE} > ? AND " +
                        "(${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR " +
                        "(${MediaStore.MediaColumns.MIME_TYPE} LIKE ? AND " +
                        "${MediaStore.MediaColumns.DISPLAY_NAME} NOT LIKE ?))",
                    arrayOf("0", "text/%", "application/pdf")
                )
                StorageCategory.OTHER -> Triple(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${MediaStore.MediaColumns.SIZE} > ? AND " +
                        "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ? AND " +
                        "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ? AND " +
                        "${MediaStore.MediaColumns.MIME_TYPE} NOT LIKE ?",
                    arrayOf("0", "image/%", "video/%", "audio/%")
                )
                StorageCategory.APPS -> Triple(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${MediaStore.MediaColumns.SIZE} < 0",
                    null
                )
            }
            return MediaStorePagingSource(contentResolver, uri, category, selection, args)
        }
    }
}