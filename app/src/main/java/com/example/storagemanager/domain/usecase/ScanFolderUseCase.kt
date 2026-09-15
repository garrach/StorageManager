package com.example.storagemanager.domain.usecase

import com.example.storagemanager.data.model.FileEntry
import com.example.storagemanager.data.model.ScanProgress
import com.example.storagemanager.data.repository.FileRepository
import java.io.File
import javax.inject.Inject

class ScanFolderUseCase @Inject constructor(
    private val fileRepository: FileRepository,
) {
    operator fun invoke(root: File): Flow<ScanProgress> = fileRepository.scanFolder(root)
}