package com.example.storagemanager.util

import java.io.File
import java.io.IOException
import java.util.Locale

object FileSystemUtils {

    private const val MAX_DEPTH = 20
    private const val MAX_SCAN_FILES = 25_000

    val JUNK_EXTENSIONS = setOf(
        "tmp", "temp", "cache", "log", "bak", "old", "part", "crdownload"
    )

    fun getExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot in 1 until name.length - 1) name.substring(dot + 1).lowercase(Locale.US) else ""
    }

    fun getMimeFromExtension(name: String): String = when (getExtension(name)) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg", "avif" -> "image/*"
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "flv" -> "video/*"
        "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma" -> "audio/*"
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> "application/*"
        "pdf" -> "application/pdf"
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv" -> "text/*"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }

    fun isSymbolicLink(file: File): Boolean {
        return try {
            file.canonicalPath != file.absolutePath
        } catch (_: IOException) {
            false
        }
    }

    fun isHidden(file: File): Boolean = file.name.startsWith(".")

    tailrec fun safeListFiles(dir: File): List<File> =
        try {
            dir.listFiles()?.filter { !isHidden(it) } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }

    fun isJunkFile(file: File): Boolean {
        val ext = getExtension(file.name)
        return ext in JUNK_EXTENSIONS || file.length() == 0L
    }

    /**
     * Walks a directory tree with symlink-cycle protection.
     *
     * @param visitedCanonicalPaths caller-provided set reused across traversal
     */
    inline fun walkTree(
        root: File,
        visitedCanonicalPaths: MutableSet<String>,
        maxFiles: Int = MAX_SCAN_FILES,
        maxDepth: Int = MAX_DEPTH,
        onFile: (File, Int) -> Boolean,
    ): FileWalkResult {
        val canonRoot = try {
            root.canonicalPath
        } catch (_: IOException) {
            root.absolutePath
        }
        if (!visitedCanonicalPaths.add(canonRoot)) return FileWalkResult(0, false)
        if (!root.isDirectory) {
            onFile(root, 0)
            return FileWalkResult(1, false)
        }

        var files = 0L
        var truncated = false

        fun recurse(dir: File, depth: Int) {
            if (depth > maxDepth || files >= maxFiles) {
                if (depth <= maxDepth) truncated = true
                return
            }
            for (child in safeListFiles(dir)) {
                if (files >= maxFiles) {
                    truncated = true
                    return
                }
                files++
                if (isSymbolicLink(child)) continue
                if (child.isDirectory) {
                    val canon = try {
                        child.canonicalPath
                    } catch (_: IOException) {
                        child.absolutePath
                    }
                    if (visitedCanonicalPaths.add(canon)) {
                        recurse(child, depth + 1)
                    }
                } else {
                    onFile(child, depth)
                }
            }
        }

        recurse(root, 0)
        return FileWalkResult(files, truncated)
    }

    fun calculateFolderSize(dir: File): Long {
        var total = 0L
        walkTree(dir, HashSet(), maxFiles = MAX_SCAN_FILES * 10, onFile = { file, _ ->
            total += file.length()
            true
        })
        return total
    }

    data class FileWalkResult(
        val filesVisited: Long,
        val truncated: Boolean,
    )
}