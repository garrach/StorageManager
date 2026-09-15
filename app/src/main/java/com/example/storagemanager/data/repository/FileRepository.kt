package com.example.storagemanager.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.ScanProgress
import com.example.storagemanager.data.model.StorageCategory
import com.example.storagemanager.data.model.TrashEntry
import com.example.storagemanager.util.FileSystemUtils
import com.example.storagemanager.util.RecycleBin
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val MAX_ENTRIES = 30_000

data class DeletionReport(
    val deletedCount: Int,
    val failedCount: Int,
    val bytesFreed: Long,
)

class FileRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
    private val recycleBin: RecycleBin,
) {

    fun scanFolder(root: File): Flow<ScanProgress> = flow {
        emit(ScanProgress(isScanning = true, currentPath = root.path))

        val entries = ArrayList<FileEntry>(1024)
        val visited = HashSet<String>()
        val deque = ArrayDeque<DirFrame>()
        var fileCount = 0L
        var bytes = 0L
        var id = 0L
        var truncated = false

        val canonRoot = runCatching { root.canonicalPath }.getOrElse { root.absolutePath }
        visited.add(canonRoot)
        deque.add(DirFrame(dir = root, depth = 0, parent = null))

        while (deque.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val frame = deque.removeFirst()
            for (child in FileSystemUtils.safeListFiles(frame.dir)) {
                currentCoroutineContext().ensureActive()
                fileCount++

                if (entries.size >= MAX_ENTRIES) {
                    truncated = true
                    continue
                }
                if (FileSystemUtils.isSymbolicLink(child)) continue

                id++
                val isDir = child.isDirectory
                val size = if (isDir) 0L else child.length()
                bytes += size

                val entry = FileEntry(
                    id = id,
                    uri = Uri.fromFile(child),
                    name = child.name,
                    path = child.absolutePath,
                    mimeType = FileSystemUtils.getMimeFromExtension(child.name),
                    size = size,
                    dateModified = child.lastModified(),
                    isDirectory = isDir,
                    category = StorageCategory.fromExtension(child.name),
                    depth = frame.depth,
                    parentPath = frame.parent,
                )
                entries.add(entry)

                if (isDir) {
                    val canon = runCatching { child.canonicalPath }.getOrElse { child.absolutePath }
                    if (visited.add(canon)) {
                        deque.add(DirFrame(dir = child, depth = frame.depth + 1, parent = child.absolutePath))
                    }
                }

                if (entries.size % 300 == 0) {
                    emit(
                        ScanProgress(
                            isScanning = true,
                            filesScanned = fileCount,
                            totalBytes = bytes,
                            currentPath = child.absolutePath,
                            entries = entries.toList(),
                        )
                    )
                }
            }
        }

        emit(
            ScanProgress(
                isScanning = false,
                filesScanned = fileCount,
                totalBytes = bytes,
                currentPath = root.path,
                truncated = truncated,
                entries = entries.toList(),
            )
        )
    }.flowOn(ioDispatcher)

    suspend fun deleteFiles(entries: List<FileEntry>): DeletionReport =
        kotlinx.coroutines.withContext(ioDispatcher) {
            var deleted = 0
            var failed = 0
            var freed = 0L
            for (entry in entries) {
                val ok = deleteSingle(entry)
                if (ok) {
                    deleted++
                    freed += entry.size
                } else {
                    failed++
                }
            }
            DeletionReport(deleted, failed, freed)
        }

    suspend fun trashFiles(entries: List<FileEntry>): DeletionReport =
        kotlinx.coroutines.withContext(ioDispatcher) {
            val fileEntries = entries.filter { it.uri.scheme == "file" && it.path != null }
            val contentEntries = entries.filter { it.uri.scheme == "content" }

            val trashed = recycleBin.trashFiles(fileEntries)
            var contentDeleted = 0
            var contentFailed = 0
            var contentFreed = 0L
            for (entry in contentEntries) {
                val ok = deleteSingle(entry)
                if (ok) {
                    contentDeleted++
                    contentFreed += entry.size
                } else {
                    contentFailed++
                }
            }
            val freed = fileEntries.filter { it.path?.let { p -> File(p).exists() != true } == true }
                .sumOf { it.size } + contentFreed
            DeletionReport(
                deletedCount = trashed + contentDeleted,
                failedCount = contentFailed,
                bytesFreed = freed,
            )
        }

    suspend fun getTrashEntries(): List<TrashEntry> = recycleBin.getTrashEntries()

    suspend fun restoreTrashEntry(entry: TrashEntry): Boolean = recycleBin.restore(entry)

    suspend fun permanentlyDeleteTrash(entry: TrashEntry): Boolean = recycleBin.permanentlyDelete(entry)

    suspend fun emptyTrash(): Int = recycleBin.emptyTrash()

    private fun deleteSingle(entry: FileEntry): Boolean {
        return try {
            when (entry.uri.scheme) {
                "file" -> {
                    val file = File(entry.uri.path ?: return false)
                    !file.exists() || file.delete()
                }
                "content" -> contentResolver.delete(entry.uri, null, null) > 0
                else -> false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private data class DirFrame(
        val dir: File,
        val depth: Int,
        val parent: String?,
    )
}
