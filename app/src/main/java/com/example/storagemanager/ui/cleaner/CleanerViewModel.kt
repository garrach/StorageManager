package com.example.storagemanager.ui.cleaner

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storagemanager.data.model.CleanupSuggestion
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.repository.DeletionReport
import com.example.storagemanager.data.repository.FileRepository
import com.example.storagemanager.domain.usecase.GetCleanupSuggestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanerUiState(
    val isLoading: Boolean = true,
    val suggestions: List<CleanupSuggestion> = emptyList(),
    val expandedType: com.example.storagemanager.data.model.SuggestionType? = null,
    val selectedForDeletion: Set<Long> = emptySet(),
    val isDeleting: Boolean = false,
    val deletionResult: DeletionReport? = null,
    val error: String? = null,
)

@HiltViewModel
class CleanerViewModel @Inject constructor(
    private val getSuggestions: GetCleanupSuggestionsUseCase,
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val roots = storageManager.storageVolumes.mapNotNull { it.directory?.absolutePath }
                val suggestions = getSuggestions(roots)
                _uiState.update { it.copy(isLoading = false, suggestions = suggestions) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleExpanded(type: com.example.storagemanager.data.model.SuggestionType) {
        _uiState.update {
            it.copy(
                expandedType = if (it.expandedType == type) null else type,
                selectedForDeletion = emptySet(),
            )
        }
    }

    fun toggleItemSelection(entryId: Long) {
        _uiState.update {
            val new = if (entryId in it.selectedForDeletion) it.selectedForDeletion - entryId
            else it.selectedForDeletion + entryId
            it.copy(selectedForDeletion = new)
        }
    }

    fun deleteSuggestionItems(suggestion: CleanupSuggestion) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val entries = suggestion.items
            val report = fileRepository.deleteFiles(entries)
            _uiState.update {
                val updatedSuggestions = it.suggestions.map { s ->
                    if (s.type == suggestion.type) s.copy(items = emptyList(), reclaimableBytes = 0L)
                    else s
                }
                it.copy(
                    isDeleting = false,
                    deletionResult = report,
                    suggestions = updatedSuggestions.filter { s -> s.itemCount > 0 },
                    expandedType = null,
                )
            }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(deletionResult = null) }
    }
}