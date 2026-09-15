package com.example.storagemanager.ui.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.SortOrder
import com.example.storagemanager.data.model.StorageCategory
import com.example.storagemanager.data.repository.FileRepository
import com.example.storagemanager.data.repository.MediaStoreRepository
import com.example.storagemanager.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilesUiState(
    val category: StorageCategory = StorageCategory.OTHER,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val searchQuery: String = "",
    val isGrid: Boolean = false,
    val isDeleting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaStoreRepository: MediaStoreRepository,
    private val fileRepository: FileRepository,
) : ViewModel() {

    val category: StorageCategory = StorageCategory.fromRoute(
        savedStateHandle["category"] ?: ""
    ) ?: StorageCategory.OTHER

    private val _uiState = MutableStateFlow(FilesUiState(category = category))
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    val filesPagingData: Flow<PagingData<FileEntry>> = combine(
        _uiState.map { it.category },
        _uiState.map { it.sortOrder },
        _uiState.map { it.searchQuery },
    ) { cat, sort, query -> Triple(cat, sort, query) }
        .distinctUntilChanged()
        .flatMapLatest { (cat, sort, query) ->
            mediaStoreRepository.filesPager(
                category = cat,
                sortOrder = sort,
                searchQuery = query.ifBlank { null },
            )
        }
        .cachedIn(viewModelScope)

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query.trim()) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(isGrid = !it.isGrid) }
    }

    fun deleteFiles(entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val report = fileRepository.deleteFiles(entries)
            val msg = buildString {
                append("Deleted ${report.deletedCount} file${if (report.deletedCount != 1) "s" else ""}")
                if (report.bytesFreed > 0) append(" • freed ${FormatUtils.formatBytes(report.bytesFreed)}")
                if (report.failedCount > 0) append(" • ${report.failedCount} failed")
            }
            _uiState.update { it.copy(isDeleting = false, message = msg) }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}