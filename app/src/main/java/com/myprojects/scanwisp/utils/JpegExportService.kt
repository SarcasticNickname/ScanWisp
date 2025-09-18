package com.myprojects.scanwisp.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сервис для экспорта одиночных JPEG-файлов.
 */
@Singleton
class JpegExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Копирует исходный файл изображения в кэш приложения.
     */
    suspend fun copyToCache(sourcePath: String, newFileName: String): File? =
        withContext(Dispatchers.IO) {
            val policy = SafeNamePolicy(context)
            val jpegDir = File(context.cacheDir, "jpeg_exports").apply { mkdirs() }
            val destFile = policy.uniqueFile(jpegDir, policy.sanitizeBase(newFileName), ".jpg")

            try {
                // ИЗМЕНЕНИЕ: Мы больше не используем ContentResolver.
                // Мы работаем напрямую с файлом, так как у нас есть его путь.
                val sourceFile = File(sourcePath)
                FileInputStream(sourceFile).use { inStream ->
                    destFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                return@withContext destFile
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy JPEG to cache")
                destFile.delete()
                return@withContext null
            }
        }
}