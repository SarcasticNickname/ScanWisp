package com.myprojects.scanwisp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class DeletePagesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val crashlytics: FirebaseCrashlytics
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PAGE_IDS = "key_page_ids"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pageIds = inputData.getStringArray(KEY_PAGE_IDS)?.toList()
            if (pageIds.isNullOrEmpty()) {
                Timber.e("DeletePagesWorker received no page IDs. Aborting.")
                return@withContext Result.failure()
            }

            Timber.d("DeletePagesWorker starting. Deleting ${pageIds.size} pages.")

            // Важно: Сначала удаляем файлы, потом записи в БД.
            // Мы не можем просто вызвать repository.deletePages, так как он не удаляет файлы.
            // Нам нужно сначала получить пути к файлам.
            val pagesToDelete = documentRepository.getPagesByIds(pageIds)

            pagesToDelete.forEach { page ->
                // Логика удаления файлов уже есть в репозитории, но для воркера
                // лучше ее продублировать, чтобы он был полностью автономен.
                // В будущем можно вынести в отдельный File Deleter UseCase.
                deleteFileFromPath(page.processedImagePath)
                deleteFileFromPath(page.thumbnailPath)
            }

            // Теперь удаляем сами записи из базы данных
            documentRepository.deletePages(pageIds)

            Timber.i("DeletePagesWorker successfully deleted ${pageIds.size} pages.")
            return@withContext Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Error in DeletePagesWorker.")
            crashlytics.recordException(e)
            // Возвращаем retry, чтобы WorkManager попробовал выполнить задачу позже
            return@withContext Result.retry()
        }
    }

    private fun deleteFileFromPath(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = java.io.File(path)
            if (file.exists()) {
                if (!file.delete()) {
                    Timber.w("Failed to delete file: $path")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete file at path: $path")
            crashlytics.recordException(e)
        }
    }
}