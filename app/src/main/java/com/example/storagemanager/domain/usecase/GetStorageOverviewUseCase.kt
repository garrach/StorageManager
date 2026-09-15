package com.example.storagemanager.domain.usecase

import com.example.storagemanager.data.model.StorageOverview
import com.example.storagemanager.data.repository.StorageRepository
import javax.inject.Inject

class GetStorageOverviewUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
) {
    suspend operator fun invoke(): StorageOverview = storageRepository.getOverview()
}