package com.example.storagemanager.domain.usecase

import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.repository.DeletionReport
import com.example.storagemanager.data.repository.FileRepository
import javax.inject.Inject

class DeleteFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(entries: List<FileEntry>): DeletionReport =
        fileRepository.deleteFiles(entries)
}