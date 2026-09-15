package com.example.storagemanager.ui.cleaner

import android.content.Context
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storagemanager.data.model.CleanupSuggestion
import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.SuggestionType
import com.example.storagemanager.data.model.TrashEntry
import com.example.storagemanager.data.repository.FileRepository
import com.example.storagemanager.domain.usecase.GetCleanupSuggestionsUseCase
import com.example.storagemanager.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanerUiState(
    val isLoading: Boolean = true,
    val suggestions: List<CleanupSuggestion> = emptyList(),
    val selectedType: SuggestionType? = null,
    val selectedForDeletion: Set<Long> = emptySet(),
    val isDeleting: Boolean = false,
    val error: String? = null,
    val showRecycleBin: Boolean = false,
    val trashEntries: List<TrashEntry> = emptyList(),
    val isTrashLoading: Boolean = false,
    val trashCount: Int = 0,
)

data class CleanerSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
)

@HiltViewModel
class CleanerViewModel @Inject constructor(
    private val getSuggestions: GetCleanupSuggestionsUseCase,
    private val fileRepository: FileRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CleanerSnackbarEvent>()
    val events: SharedFlow<CleanerSnackbarEvent> = _events.asSharedFlow()

    private var lastTrashed: List<TrashEntry> = emptyList()

    init {
        load()
        refreshTrashCount()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val roots = storageManager.storageVolumes.mapNotNull { it.directory?.absolutePath }
                val suggestions = getSuggestions(roots)
                _uiState.update {
                    it.copy(isLoading = false, suggestions = suggestions, selectedType = null)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleExpanded(type: SuggestionType) {
        _uiState.update {
            it.copy(
                selectedType = if (it.selectedType == type) null else type,
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
            val report = fileRepository.trashFiles(entries)
            if (report.deletedCount > 0) {
                val deletedPaths = entries.mapNotNull { it.path }.toSet()
                lastTrashed = fileRepository.getTrashEntries()
                    .filter { it.originalPath in deletedPaths }
            }
            _uiState.update {
                val updatedSuggestions = it.suggestions.map { s ->
                    if (s.type == suggestion.type) s.copy(items = emptyList(), reclaimableBytes = 0L)
                    else s
                }
                it.copy(
                    isDeleting = false,
                    suggestions = updatedSuggestions.filter { s -> s.itemCount > 0 },
                    selectedType = null,
                    selectedForDeletion = emptySet(),
                    trashCount = it.trashCount + report.deletedCount,
                )
            }
            _events.emit(
                CleanerSnackbarEvent(
                    message = buildString {
                        append("Moved ${report.deletedCount} item${if (report.deletedCount != 1) "s" else ""} to Recycle Bin")
                        if (report.failedCount > 0) append(" • ${report.failedCount} failed")
                    },
                    actionLabel = "UNDO",
                )
            )
        }
    }

    fun trashItem(entry: FileEntry) {
        viewModelScope.launch {
            val report = fileRepository.trashFiles(listOf(entry))
            if (report.deletedCount > 0) {
                lastTrashed = fileRepository.getTrashEntries()
                    .filter { it.originalPath == entry.path }
                    .takeLast(1)
            }
            _uiState.update {
                val updatedSuggestions = it.suggestions.map { s ->
                    if (s.items.any { item -> item.id == entry.id }) {
                        val newItems = s.items.filter { item -> item.id != entry.id }
                        s.copy(
                            items = newItems,
                            reclaimableBytes = (s.reclaimableBytes - entry.size).coerceAtLeast(0L),
                        )
                    } else s
                }
                it.copy(
                    suggestions = updatedSuggestions.filter { s -> s.itemCount > 0 },
                    trashCount = it.trashCount + report.deletedCount,
                )
            }
            _events.emit(
                CleanerSnackbarEvent(
                    message = if (report.deletedCount > 0)
                        "\"${entry.name}\" moved to Recycle Bin"
                    else "Unable to move file",
                    actionLabel = if (report.deletedCount > 0) "UNDO" else null,
                )
            )
        }
    }

    fun undoLastTrash() {
        val entries = lastTrashed
        if (entries.isEmpty()) return
        lastTrashed = emptyList()
        viewModelScope.launch {
            var restored = 0
            for (entry in entries) {
                if (fileRepository.restoreTrashEntry(entry)) restored++
            }
            if (restored > 0) {
                load()
                _events.emit(
                    CleanerSnackbarEvent("Restored $restored item${if (restored != 1) "s" else ""}")
                )
            }
        }
    }

    fun openRecycleBin() {
        viewModelScope.launch {
            _uiState.update { it.copy(showRecycleBin = true, isTrashLoading = true) }
            val entries = fileRepository.getTrashEntries()
            _uiState.update {
                it.copy(isTrashLoading = false, trashEntries = entries, trashCount = entries.size)
            }
        }
    }

    fun closeRecycleBin() {
        _uiState.update { it.copy(showRecycleBin = false) }
    }

    fun restoreTrashEntry(entry: TrashEntry) {
        viewModelScope.launch {
            val ok = fileRepository.restoreTrashEntry(entry)
            refreshTrashEntries()
            if (ok) {
                _events.emit(CleanerSnackbarEvent("Restored \"${entry.name}\""))
            }
        }
    }

    fun permanentlyDeleteTrashEntry(entry: TrashEntry) {
        viewModelScope.launch {
            val ok = fileRepository.permanentlyDeleteTrash(entry)
            refreshTrashEntries()
            if (ok) {
                _events.emit(CleanerSnackbarEvent("Deleted \"${entry.name}\" permanently"))
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val deleted = fileRepository.emptyTrash()
            _uiState.update { it.copy(trashEntries = emptyList(), trashCount = 0) }
            _events.emit(
                CleanerSnackbarEvent(
                    "Recycle Bin emptied ($deleted file${if (deleted != 1) "s" else ""})"
                )
            )
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(selectedForDeletion = emptySet()) }
    }

    private fun refreshTrashCount() {
        viewModelScope.launch {
            val entries = fileRepository.getTrashEntries()
            _uiState.update { it.copy(trashCount = entries.size) }
        }
    }

    private fun refreshTrashEntries() {
        viewModelScope.launch {
            val entries = fileRepository.getTrashEntries()
            _uiState.update { it.copy(trashEntries = entries, trashCount = entries.size) }
        }
    }
}