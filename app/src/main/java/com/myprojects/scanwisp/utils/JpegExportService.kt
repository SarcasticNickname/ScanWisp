package com.myprojects.scanwisp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
     * Это необходимо для получения content:// Uri через FileProvider для безопасной передачи.
     *
     * @param sourcePath Абсолютный путь к исходному файлу изображения.
     * @param newFileName Имя для файла в кэше (без расширения .jpg).
     * @return [File] для скопированного файла или null в случае ошибки.
     */
    suspend fun copyToCache(sourcePath: String, newFileName: String): File? =
        withContext(Dispatchers.IO) {
            val policy = SafeNamePolicy(context)
            val jpegDir = File(context.cacheDir, "jpeg_exports").apply { mkdirs() }
            val destFile = policy.uniqueFile(jpegDir, policy.sanitizeBase(newFileName), ".jpg")

            try {
// START: AI_MODIFIED_BLOCK
                val uri = Uri.parse(sourcePath)
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    destFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: throw Exception("ContentResolver returned null InputStream for $uri")
                return@withContext destFile
// END: AI_MODIFIED_BLOCK
            } catch (e: Exception) {
                Log.e("JpegExportService", "Failed to copy JPEG to cache", e)
                destFile.delete()
                return@withContext null
            }
        }
}