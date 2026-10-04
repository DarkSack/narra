package app.narra.data.repository

import app.narra.data.files.BookStorage
import app.narra.domain.model.StorageUsage
import app.narra.domain.repository.StorageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepositoryImpl @Inject constructor(private val storage: BookStorage) : StorageRepository {
    override suspend fun usage(): StorageUsage = storage.usage()

    override suspend fun clearTemporaryFiles(): Long = storage.clearTemporaryFiles()
}
