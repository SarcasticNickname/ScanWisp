package com.myprojects.scanwisp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

private const val CACHE_MAX_AGE_DAYS = 7L // Удалять файлы старше 7 дней

@HiltWorker
class CacheCleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting cache cleanup work.")
            val cacheDir = context.cacheDir
            if (!cacheDir.exists()) {
                Timber.d("Cache directory does not exist. Nothing to clean.")
                return@withContext Result.success()
            }

            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(CACHE_MAX_AGE_DAYS)
            var filesDeleted = 0

            // Папки, которые мы хотим чистить
            val exportDirs = listOf(
                File(cacheDir, "pdfs"),
                File(cacheDir, "zips"),
                File(cacheDir, "jpeg_exports")
            )

            exportDirs.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.lastModified() < cutoffTime) {
                            if (file.delete()) {
                                filesDeleted++
                            } else {
                                Timber.w("Failed to delete file: ${file.path}")
                            }
                        }
                    }
                }
            }

            Timber.d("Cache cleanup finished. Deleted $filesDeleted files.")
            return@withContext Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Error during cache cleanup work.")
            // Здесь можно добавить запись в Crashlytics
            return@withContext Result.failure()
        }
    }
}