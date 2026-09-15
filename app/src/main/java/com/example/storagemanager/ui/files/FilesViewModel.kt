package com.example.storagemanager.ui.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.StorageCategory
import com.example.storagemanager.data.repository.MediaStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilesUiState(
    val category: StorageCategory = StorageCategory.OTHER,
    val isDeleting: Boolean = false,
    val deletionResult: String? = null,
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaStoreRepository: MediaStoreRepository,
) : ViewModel() {

    val category: StorageCategory = StorageCategory.fromRoute(
        savedStateHandle["category"] ?: ""
    ) ?: StorageCategory.OTHER

    private val _uiState = MutableStateFlow(FilesUiState(category = category))
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    val filesPagingData: Flow<PagingData<FileEntry>> =
        mediaStoreRepository.filesPager(category)
            .cachedIn(viewModelScope)
}