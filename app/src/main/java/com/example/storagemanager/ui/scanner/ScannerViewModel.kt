package com.example.storagemanager.ui.scanner

import android.content.Context
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.ScanProgress
import com.example.storagemanager.data.repository.FileRepository
import com.example.storagemanager.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ScannerUiState(
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val selectedEntries: Set<Long> = emptySet(),
    val showVolumePicker: Boolean = true,
    val volumePath: String? = null,
    val expandedFolders: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
)

data class ScannerSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScannerSnackbarEvent>()
    val events: SharedFlow<ScannerSnackbarEvent> = _events.asSharedFlow()

    private var scanJob: Job? = null
    private var lastDeleted: List<FileEntry> = emptyList()

    fun getVolumes(): List<String> {
        val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        return storageManager.storageVolumes.mapNotNull { it.directory?.absolutePath }
    }

    fun startScan(path: String) {
        scanJob?.cancel()
        _uiState.update {
            it.copy(
                isScanning = true,
                scanProgress = null,
                selectedEntries = emptySet(),
                showVolumePicker = false,
                volumePath = path,
                expandedFolders = emptySet(),
                isDeleting = false,
            )
        }
        scanJob = viewModelScope.launch {
            val root = File(path)
            fileRepository.scanFolder(root).collect { progress ->
                _uiState.update { it.copy(scanProgress = progress) }
                if (progress.isComplete) {
                    _uiState.update { it.copy(isScanning = false) }
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun toggleSelection(entryId: Long) {
        _uiState.update {
            val new = if (entryId in it.selectedEntries) it.selectedEntries - entryId
            else it.selectedEntries + entryId
            it.copy(selectedEntries = new)
        }
    }

    fun selectAllVisible() {
        val current = _uiState.value.scanProgress?.entries ?: return
        val allIds = current.filter { !it.isDirectory }.map { it.id }.toSet()
        _uiState.update { it.copy(selectedEntries = allIds) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedEntries = emptySet()) }
    }

    fun deleteEntry(entry: FileEntry) {
        deleteEntries(listOf(entry))
    }

    fun deleteSelected() {
        val selectedIds = _uiState.value.selectedEntries
        val entries = _uiState.value.scanProgress?.entries?.filter { it.id in selectedIds } ?: return
        deleteEntries(entries)
    }

    private fun deleteEntries(entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val report = fileRepository.trashFiles(entries)
            if (report.deletedCount > 0) {
                lastDeleted = entries
            }
            val deletedUris = entries.map { it.uri }
            _uiState.update {
                val remaining = it.scanProgress?.entries
                    ?.filter { e -> e.uri !in deletedUris }
                    ?: emptyList()
                it.copy(
                    isDeleting = false,
                    selectedEntries = emptySet(),
                    scanProgress = it.scanProgress?.copy(
                        entries = remaining,
                        filesScanned = remaining.size.toLong(),
                    ),
                )
            }
            val msg = buildString {
                append("Deleted ${report.deletedCount} item${if (report.deletedCount != 1) "s" else ""}")
                if (report.bytesFreed > 0) append(" • ${FormatUtils.formatBytes(report.bytesFreed)}")
                if (report.failedCount > 0) append(" • ${report.failedCount} failed")
            }
            _events.emit(ScannerSnackbarEvent(msg, actionLabel = "UNDO"))
        }
    }

    fun undoLastDeletion() {
        val entries = lastDeleted
        if (entries.isEmpty()) return
        lastDeleted = emptyList()
        viewModelScope.launch {
            val trash = fileRepository.getTrashEntries().associateBy { it.originalPath }
            var restored = 0
            val restoredEntries = mutableListOf<FileEntry>()
            for (entry in entries) {
                val trashEntry = trash[entry.path] ?: continue
                if (fileRepository.restoreTrashEntry(trashEntry)) {
                    restored++
                    restoredEntries.add(entry)
                }
            }
            if (restored > 0) {
                _uiState.update {
                    it.copy(
                        scanProgress = it.scanProgress?.copy(
                            entries = (it.scanProgress.entries + restoredEntries)
                                .sortedBy { entry -> entry.id },
                        ),
                    )
                }
                _events.emit(
                    ScannerSnackbarEvent("Restored $restored item${if (restored != 1) "s" else ""}")
                )
            } else {
                _events.emit(ScannerSnackbarEvent("Nothing to restore"))
            }
        }
    }

    fun toggleFolder(path: String) {
        _uiState.update {
            val new = if (path in it.expandedFolders) it.expandedFolders - path
            else it.expandedFolders + path
            it.copy(expandedFolders = new)
        }
    }

    fun resetToPicker() {
        scanJob?.cancel()
        _uiState.update { ScannerUiState() }
    }
}