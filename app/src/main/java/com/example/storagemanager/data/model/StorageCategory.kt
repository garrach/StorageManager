package com.example.storagemanager.data.model

import com.example.storagemanager.util.FileSystemUtils
import java.util.Locale

enum class StorageCategory(val label: String, val route: String) {
    IMAGES("Images", "images"),
    VIDEOS("Videos", "videos"),
    AUDIO("Audio", "audio"),
    DOWNLOADS("Downloads", "downloads"),
    DOCUMENTS("Documents", "documents"),
    APPS("Apps", "apps"),
    OTHER("Other", "other");

    companion object {
        fun fromRoute(route: String): StorageCategory? =
            entries.firstOrNull { it.route == route }

        fun fromMimeType(mimeType: String?): StorageCategory {
            if (mimeType == null) return OTHER
            return when {
                mimeType.startsWith("image/") -> IMAGES
                mimeType.startsWith("video/") -> VIDEOS
                mimeType.startsWith("audio/") -> AUDIO
                mimeType == "application/pdf" ||
                    mimeType.startsWith("text/") ||
                    mimeType.contains("document") ||
                    mimeType.contains("msword") ||
                    mimeType.contains("spreadsheet") -> DOCUMENTS
                mimeType.startsWith("application/") &&
                    (mimeType.contains("zip") || mimeType.contains("compressed") ||
                        mimeType.contains("tar") || mimeType.contains("rar")) -> DOWNLOADS
                else -> OTHER
            }
        }

        fun fromExtension(name: String): StorageCategory {
            val ext = FileSystemUtils.getExtension(name).lowercase(Locale.US)
            return when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg", "avif" -> IMAGES
                "mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "m4v" -> VIDEOS
                "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma", "mid" -> AUDIO
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk" -> DOWNLOADS
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv" -> DOCUMENTS
                else -> OTHER
            }
        }
    }
}