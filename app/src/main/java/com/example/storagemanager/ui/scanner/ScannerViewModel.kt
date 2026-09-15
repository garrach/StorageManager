package com.example.storagemanager.ui.scanner

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.ScanProgress
import com.example.storagemanager.data.repository.DeletionReport
import com.example.storagemanager.data.repository.FileRepository
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
    val deletionReport: DeletionReport? = null,
    val expandedFolders: Set<String> = emptySet(),
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

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
                deletionReport = null,
                expandedFolders = emptySet(),
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

    fun deleteSelected() {
        val selectedIds = _uiState.value.selectedEntries
        val entries = _uiState.value.scanProgress?.entries?.filter { it.id in selectedIds } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val report = fileRepository.deleteFiles(entries)
            _uiState.update {
                val remaining = it.scanProgress?.entries?.filter { e -> e.id !in selectedIds } ?: emptyList()
                it.copy(
                    isScanning = false,
                    deletionReport = report,
                    selectedEntries = emptySet(),
                    scanProgress = it.scanProgress?.copy(
                        entries = remaining,
                        filesScanned = remaining.size.toLong(),
                    ),
                )
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
        _uiState.update {
            ScannerUiState()
        }
    }
}