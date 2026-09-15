package com.example.storagemanager.ui.appusage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.storagemanager.data.repository.AppUsageItem
import com.example.storagemanager.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUsageUiState(
    val isLoading: Boolean = true,
    val apps: List<AppUsageItem> = emptyList(),
    val error: String? = null,
    val filter: AppFilter = AppFilter.ALL,
    val searchQuery: String = "",
)

enum class AppFilter(val label: String) {
    ALL("All"),
    LARGE("Largest (>10 MB)"),
    HIGH_CACHE("High Cache (>1 MB)"),
}

@HiltViewModel
class AppUsageViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUsageUiState())
    val uiState: StateFlow<AppUsageUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val apps = storageRepository.getAppUsage()
                _uiState.update { it.copy(isLoading = false, apps = apps) }
            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Permission denied. Please grant storage access.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(filter: AppFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun getFilteredApps(): List<AppUsageItem> {
        val state = _uiState.value
        return state.apps.filter { app ->
            val matchesFilter = when (state.filter) {
                AppFilter.ALL -> true
                AppFilter.LARGE -> app.totalSize > 10L * 1024 * 1024
                AppFilter.HIGH_CACHE -> app.cacheSize > 1L * 1024 * 1024
            }
            val matchesQuery = state.searchQuery.isBlank() ||
                app.label.contains(state.searchQuery, ignoreCase = true) ||
                app.packageName.contains(state.searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }
}