package com.myprojects.scanwisp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class GarbageCollectorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentDao: DocumentDao,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting garbage collection work for soft-deleted documents.")

            val retentionDays = settingsRepository.trashRetentionDays.first()

            // -1 означает «никогда не удалять автоматически»
            if (retentionDays < 0) {
                Timber.d("Auto-deletion disabled (retention = never). Work skipped.")
                return@withContext Result.success()
            }

            val cutoffTimestamp =
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())

            val documentsToDelete = documentDao.getDocumentsPendingDeletion(cutoffTimestamp)
            if (documentsToDelete.isEmpty()) {
                Timber.d("No documents pending for physical deletion. Work finished.")
                return@withContext Result.success()
            }

            Timber.d("Found ${documentsToDelete.size} documents to delete permanently.")

            coroutineScope {
                val fullDocumentsDeferred = documentsToDelete.map { docEntity ->
                    async {
                        documentDao.getDocumentWithPagesByIdAny(docEntity.id).first()
                    }
                }

                val fullDocuments = fullDocumentsDeferred.awaitAll().filterNotNull()

                fullDocuments.forEach { docWithPages ->
                    docWithPages.pages.forEach { page ->
                        deleteFile(page.processedImagePath)
                        deleteFile(page.thumbnailPath)
                    }
                }
            }

            val idsToDelete = documentsToDelete.map { it.id }
            idsToDelete.forEach { docId ->
                documentDao.deleteFtsEntriesForDocument(docId)
            }
            documentDao.deleteDocumentsByIds(idsToDelete)

            Timber.d("Garbage collection finished. Permanently deleted ${idsToDelete.size} documents.")
            return@withContext Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Error during garbage collection work.")
            return@withContext Result.retry()
        }
    }

    private fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete file: $path")
        }
    }
}