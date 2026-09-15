package com.example.storagemanager.data.model

enum class SuggestionType { LARGE_FILES, DUPLICATES, JUNK_FILES, EMPTY_FOLDERS }

data class CleanupSuggestion(
    val type: SuggestionType,
    val title: String,
    val description: String,
    val reclaimableBytes: Long,
    val items: List<FileEntry>,
) {
    val itemCount: Int get() = items.size
}