package com.example.storagemanager.data.model

enum class SortOrder(val label: String, val sql: String) {
    DATE_DESC("Newest first", "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"),
    SIZE_DESC("Largest first", "${android.provider.MediaStore.MediaColumns.SIZE} DESC"),
    NAME_ASC("Name A–Z", "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"),
    SIZE_ASC("Smallest first", "${android.provider.MediaStore.MediaColumns.SIZE} ASC"),
    DATE_ASC("Oldest first", "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} ASC"),
    NAME_DESC("Name Z–A", "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE DESC"),
}
