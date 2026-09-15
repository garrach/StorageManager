package com.example.storagemanager.domain.usecase

import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.SuggestionType
import com.example.storagemanager.data.model.CleanupSuggestion
import com.example.storagemanager.data.repository.FileRepository
import com.example.storagemanager.util.FileSystemUtils
import java.io.File
import java.util.Arrays
import java.util.HashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetCleanupSuggestionsUseCase @Inject constructor(
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(roots: List<String>): List<CleanupSuggestion> =
        withContext(Dispatchers.Default) {
            val suggestions = mutableListOf<CleanupSuggestion>()

            val allFiles = mutableListOf<FileEntry>()

            for (rootPath in roots) {
                val root = File(rootPath)
                if (!root.exists() || !root.isDirectory) continue
                val (result, _) = collectAllFiles(root, maxFiles = 20000)
                allFiles.addAll(result)
            }

            // Large files > 100MB
            val largeFiles = allFiles
                .filter { !it.isDirectory && it.size > 100L * 1024 * 1024 }
                .sortedByDescending { it.size }
            if (largeFiles.isNotEmpty()) {
                suggestions.add(
                    CleanupSuggestion(
                        type = SuggestionType.LARGE_FILES,
                        title = "Large files (> 100 MB)",
                        description = "${largeFiles.size} file${if (largeFiles.size != 1) "s" else ""} taking ${FileSystemUtils.formatBytes(largeFiles.sumOf { it.size })}",
                        reclaimableBytes = largeFiles.sumOf { it.size },
                        items = largeFiles.take(100),
                    )
                )
            }

            // Junk files (tmp, cache, etc.)
            val junk = allFiles.filter {
                !it.isDirectory && FileSystemUtils.isJunkFile(File(it.path ?: ""))
            }
            if (junk.isNotEmpty()) {
                suggestions.add(
                    CleanupSuggestion(
                        type = SuggestionType.JUNK_FILES,
                        title = "Cache & temporary files",
                        description = "${junk.size} file${if (junk.size != 1) "s" else ""} (${FileSystemUtils.formatBytes(junk.sumOf { it.size })})",
                        reclaimableBytes = junk.sumOf { it.size },
                        items = junk.take(100),
                    )
                )
            }

            // Empty directories
            val visitedPaths = allFiles.filter { it.isDirectory }.map { it.path }.toSet()
            val emptyDirs = allFiles.filter { it.isDirectory }
                .filter { entry ->
                    val file = File(entry.path!!)
                    val children = FileSystemUtils.safeListFiles(file)
                    children.isEmpty()
                }
            if (emptyDirs.isNotEmpty()) {
                suggestions.add(
                    CleanupSuggestion(
                        type = SuggestionType.EMPTY_FOLDERS,
                        title = "Empty folders",
                        description = "${emptyDirs.size} empty folder${if (emptyDirs.size != 1) "s" else ""} taking 0 B",
                        reclaimableBytes = 0L,
                        items = emptyDirs.take(100),
                    )
                )
            }

            // Duplicates by file size then first 64KB
            val sizeGroups = HashMap<Long, MutableList<FileEntry>>()
            for (file in allFiles.filter { !it.isDirectory && it.size > 0 }) {
                sizeGroups.getOrPut(file.size) { mutableListOf() }.add(file)
            }
            val duplicates = sizeGroups.values
                .filter { it.size >= 2 }
                .mapNotNull { group ->
                    val byPrefix = HashMap<String, MutableList<FileEntry>>()
                    for (entry in group) {
                        val file = File(entry.path ?: return@mapNotNull null)
                        val prefix = runCatching {
                            file.inputStream().use { stream ->
                                val buf = ByteArray(65536)
                                val read = stream.read(buf)
                                if (read > 0) Arrays.copyOf(buf, read).hashCode() else 0
                            }.toString()
                        }.getOrElse { entry.name }
                        byPrefix.getOrPut(prefix) { mutableListOf() }.add(entry)
                    }
                    val dupes = byPrefix.values.filter { it.size >= 2 }.flatten()
                    if (dupes.size >= 2) dupes else null
                }
            if (duplicates.isNotEmpty()) {
                val total = duplicates.sumOf { fileList -> fileList.sumOf { it.size } }
                suggestions.add(
                    CleanupSuggestion(
                        type = SuggestionType.DUPLICATES,
                        title = "Duplicate files",
                        description = "${duplicates.size} file${if (duplicates.size != 1) "s" else ""} (${FileSystemUtils.formatBytes(total)})",
                        reclaimableBytes = total,
                        items = duplicates.take(100),
                    )
                )
            }

            suggestions
        }

    private fun collectAllFiles(
        root: File,
        maxFiles: Int,
    ): Pair<List<FileEntry>, Boolean> {
        val entries = mutableListOf<FileEntry>()
        var id = 0L
        val visited = HashSet<String>()
        val dirStack = ArrayDeque<Triple<File, Int, String?>>()
        val canonRoot = runCatching { root.canonicalPath }.getOrElse { root.absolutePath }
        visited.add(canonRoot)
        dirStack.add(Triple(root, 0, null))

        while (dirStack.isNotEmpty() && entries.size < maxFiles) {
            val (dir, depth, parent) = dirStack.removeFirst()
            for (child in FileSystemUtils.safeListFiles(dir)) {
                if (entries.size >= maxFiles) break
                if (FileSystemUtils.isSymbolicLink(child)) continue
                id++
                val isDir = child.isDirectory
                entries.add(
                    FileEntry(
                        id = id,
                        uri = android.net.Uri.fromFile(child),
                        name = child.name,
                        path = child.absolutePath,
                        mimeType = FileSystemUtils.getMimeFromExtension(child.name),
                        size = if (isDir) 0L else child.length(),
                        dateModified = child.lastModified(),
                        isDirectory = isDir,
                        category = com.example.storagemanager.data.model.StorageCategory.fromExtension(child.name),
                        depth = depth,
                        parentPath = parent,
                    )
                )
                if (isDir) {
                    val canon = runCatching { child.canonicalPath }.getOrElse { child.absolutePath }
                    if (visited.add(canon)) {
                        dirStack.add(Triple(child, depth + 1, child.absolutePath))
                    }
                }
            }
        }
        return entries to (entries.size >= maxFiles)
    }
}