package com.example.storagemanager.util

import android.content.Context
import android.content.SharedPreferences
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.TrashEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecycleBin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("recycle_bin_index", Context.MODE_PRIVATE)
    private val binDir: File =
        File(context.filesDir, "recycle_bin").also { it.mkdirs() }
    private val mutex = Mutex()

    suspend fun trashFiles(entries: List<FileEntry>): Int = mutex.withLock {
        withContext(ioDispatcher) {
            var trashed = 0
            for (entry in entries) {
                if (trashSingle(entry)) trashed++
            }
            trashed
        }
    }

    suspend fun getTrashEntries(): List<TrashEntry> = mutex.withLock {
        withContext(ioDispatcher) { loadIndex() }
    }

    suspend fun restore(entry: TrashEntry): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val src = File(binDir, entry.trashName)
            if (!src.exists()) return@withContext false
            val dest = File(entry.originalPath)
            dest.parentFile?.mkdirs()
            val ok = src.renameTo(dest)
            if (ok) removeEntry(entry.trashName)
            ok
        }
    }

    suspend fun permanentlyDelete(entry: TrashEntry): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val src = File(binDir, entry.trashName)
            val ok = !src.exists() || src.delete()
            removeEntry(entry.trashName)
            ok
        }
    }

    suspend fun emptyTrash(): Int = mutex.withLock {
        withContext(ioDispatcher) {
            val entries = loadIndex()
            var deleted = 0
            for (entry in entries) {
                val file = File(binDir, entry.trashName)
                if (file.exists() && file.delete()) deleted++
            }
            prefs.edit().clear().apply()
            deleted
        }
    }

    private suspend fun trashSingle(entry: FileEntry): Boolean {
        val srcPath = entry.path ?: return false
        val src = File(srcPath)
        if (!src.exists() || !src.isFile) return false
        val safeName = buildString {
            append("trash_")
            append(System.currentTimeMillis())
            append("_")
            append(entry.name.replace("/", "_").replace("\u0000", ""))
        }
        val dest = File(binDir, safeName)
        val ok = src.renameTo(dest)
        if (ok) {
            val index = loadIndex().toMutableList()
            index.add(
                TrashEntry(
                    trashName = safeName,
                    originalPath = srcPath,
                    name = entry.name,
                    trashedAt = System.currentTimeMillis(),
                    size = entry.size,
                )
            )
            saveIndex(index)
        }
        return ok
    }

    private fun loadIndex(): List<TrashEntry> {
        val raw = prefs.getStringSet(KEY_ENTRIES, null) ?: return emptyList()
        return raw.mapNotNull { decodeEntry(it) }
    }

    private fun saveIndex(entries: List<TrashEntry>) {
        val encoded = entries.map { encodeEntry(it) }.toSet()
        prefs.edit().putStringSet(KEY_ENTRIES, encoded).apply()
    }

    private fun removeEntry(trashName: String) {
        val index = loadIndex().toMutableList()
        index.removeAll { it.trashName == trashName }
        saveIndex(index)
    }

    private fun encodeEntry(e: TrashEntry): String =
        "${e.trashName}|${e.originalPath}|${e.name}|${e.trashedAt}|${e.size}"

    private fun decodeEntry(raw: String): TrashEntry? {
        val parts = raw.split("|", limit = 5)
        return if (parts.size == 5) {
            TrashEntry(
                trashName = parts[0],
                originalPath = parts[1],
                name = parts[2],
                trashedAt = parts[3].toLongOrNull() ?: 0L,
                size = parts[4].toLongOrNull() ?: 0L,
            )
        } else null
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
    }
}
