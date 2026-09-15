package com.example.storagemanager.data.model

data class TrashEntry(
    val trashName: String,
    val originalPath: String,
    val name: String,
    val trashedAt: Long,
    val size: Long,
)
