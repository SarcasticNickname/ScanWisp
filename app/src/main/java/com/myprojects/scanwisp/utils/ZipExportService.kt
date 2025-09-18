package com.myprojects.scanwisp.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Создаёт ZIP из набора путей (строки могут быть file-path или content://-URI).
 * Дубли имён внутри архива устраняются (name (2).ext и т.д.).
 */
@Singleton
class ZipExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * @param filePaths Список путей: "content://..." | "file://..." | "/storage/..." | "/data/..."
     * @param zipFileName Имя архива БЕЗ расширения (сырой title/название документа).
     * @return Файл ZIP в cache/zips или null при ошибке.
     */
    suspend fun createZipArchive(
        filePaths: List<String>,
        zipFileName: String
    ): File? = withContext(Dispatchers.IO) {
        if (filePaths.isEmpty()) return@withContext null

        val policy = SafeNamePolicy(context)
        val dir = File(context.cacheDir, "zips").apply { mkdirs() }
        val base = policy.exportBaseNameFromTitle(zipFileName)
        val zipFile = policy.uniqueFile(dir, base, ".zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val usedNames = HashSet<String>() // для уникальности имён внутри архива

                for (path in filePaths) {
                    try {
                        val uri = toUri(path)
                        val entryName = buildUniqueEntryName(uri, usedNames, policy)
                        addEntry(zos, uri, entryName)
                    } catch (e: Throwable) {
                        Timber.e(e, "Skip entry due to error: $path")
                        // Продолжаем со следующими файлами
                    }
                }
            }
            return@withContext zipFile
        } catch (e: Throwable) {
            Timber.e(e, "Failed to create ZIP")
            zipFile.delete()
            null
        }
    }

    // --- Helpers ---

    private fun toUri(path: String): Uri {
        return when {
            path.startsWith("content://") ||
                    path.startsWith("file://") ||
                    path.startsWith("android.resource://") -> Uri.parse(path)

            else -> Uri.fromFile(File(path))
        }
    }

    private fun addEntry(zos: ZipOutputStream, uri: Uri, entryName: String) {
        val input = openForRead(uri) ?: throw IllegalStateException("Cannot open stream for $uri")
        BufferedInputStream(input).use { bis ->
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            bis.copyTo(zos, 8 * 1024)
            zos.closeEntry()
        }
    }

    private fun openForRead(uri: Uri) =
        if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            context.contentResolver.openInputStream(uri)
        } else {
            val f = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(uri.path.orEmpty())
            if (f.exists()) FileInputStream(f) else null
        }

    private fun buildUniqueEntryName(
        uri: Uri,
        used: MutableSet<String>,
        policy: SafeNamePolicy
    ): String {
        val (rawBase, rawExt) = resolveDisplayNameAndExt(uri)
        val cleanBase = policy.sanitizeBase(rawBase)
        val cleanExt = policy.sanitizeExt(rawExt)

        var candidate = "$cleanBase$cleanExt"
        var idx = 2
        // сравниваем case-insensitive — чаще всего так конфликтуют
        while (!used.add(candidate.lowercase(Locale.ROOT))) {
            candidate = "$cleanBase ($idx)$cleanExt"
            idx++
        }
        return candidate
    }

    private fun resolveDisplayNameAndExt(uri: Uri): Pair<String, String> {
        // Пытаемся взять DISPLAY_NAME из ContentResolver
        if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0) ?: ""
                        if (name.isNotBlank()) {
                            val (b, e) = splitBaseExt(name)
                            return b to e
                        }
                    }
                }
        }
        // Иначе — из самого пути
        val name =
            (uri.lastPathSegment ?: "item_${System.currentTimeMillis()}").substringAfterLast('/')
        val (b, e) = splitBaseExt(name)
        return b to e
    }

    private fun splitBaseExt(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) {
            name.substring(0, dot) to name.substring(dot) // ext с точкой
        } else {
            name to "" // без расширения
        }
    }
}
