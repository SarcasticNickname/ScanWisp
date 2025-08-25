package com.myprojects.scanwisp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FileManager"

/**
 * Централизованный сервис для управления файлами в приложении.
 * Предоставляет методы для копирования, создания и удаления файлов во внутреннем хранилище.
 */
@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Копирует файл из исходного URI в постоянное внутреннее хранилище приложения.
     *
     * @param sourceUri URI исходного файла (например, из кеша сканера).
     * @return URI файла во внутреннем хранилище приложения или null в случае ошибки.
     */
    fun copyUriToAppStorage(sourceUri: Uri): Uri? {
        return try {
            val destinationDir = File(context.filesDir, "scans").apply { mkdirs() }
            val destinationFile = File(destinationDir, "scan_${System.currentTimeMillis()}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Uri.fromFile(destinationFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file to app storage", e)
            null
        }
    }
}