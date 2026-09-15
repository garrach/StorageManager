package com.example.storagemanager.data.mapper

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.StorageCategory

object MediaStoreMapper {

    fun toFileEntry(cursor: Cursor, collectionUri: Uri): FileEntry {
        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

        val id = if (idCol >= 0 && !cursor.isNull(idCol)) cursor.getLong(idCol) else 0L
        val name = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else ""
        val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
        val date = if (dateCol >= 0 && !cursor.isNull(dateCol)) cursor.getLong(dateCol) else 0L
        val mime = if (mimeCol >= 0 && !cursor.isNull(mimeCol)) cursor.getString(mimeCol) else null

        return FileEntry(
            id = id,
            uri = ContentUris.withAppendedId(collectionUri, id),
            name = name,
            path = null,
            mimeType = mime,
            size = size,
            dateModified = date * 1000,
            isDirectory = false,
            category = if (mime != null) StorageCategory.fromMimeType(mime)
            else StorageCategory.fromExtension(name),
        )
    }
}