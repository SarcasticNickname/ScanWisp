package com.myprojects.scanwisp.core.storage

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val MB = 1024L * 1024L

private const val DEFAULT_HEADROOM = 10L * MB   // «запас прочности», мягче
private const val MIN_HEADROOM = 10L * MB
val PER_PAGE_ESTIMATE = (3.5 * MB).toLong()     // ≈3.5 МБ на страницу (обработка+превью)

/** Токен резерва (освобождается автоматически через use/close). */
class SpaceReservation internal constructor(
    private val counter: AtomicLong,
    val bytes: Long
) : AutoCloseable {
    private val released = AtomicBoolean(false)
    override fun close() {
        if (released.compareAndSet(false, true)) counter.addAndGet(-bytes)
    }
}

interface StorageService {
    fun getAvailableBytes(dir: File): Long

    fun tryReserve(
        requiredBytes: Long,
        headroomBytes: Long = DEFAULT_HEADROOM,
        dir: File
    ): SpaceReservation?

    fun clearExportCache(): Long
    fun clearThumbCache(): Long

    fun estimateForPages(count: Int): Long = count.coerceAtLeast(0) * PER_PAGE_ESTIMATE

    /** Размер объекта по строковому пути (file path или content://). */
    fun sizeOf(path: String?): Long

    fun appFilesDir(): File
    fun appCacheDir(): File
}

@Singleton
class StorageServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageService {

    private val inFlightReserved = AtomicLong(0L)

    override fun appFilesDir(): File = context.filesDir
    override fun appCacheDir(): File = context.cacheDir

    override fun getAvailableBytes(dir: File): Long {
        return try {
            // ВАЖНО: гарантируем существование директории перед StatFs,
            // иначе на подпапках StatFs может давать 0/Exception.
            if (!dir.exists()) dir.mkdirs()
            val stat = StatFs(dir.absolutePath)
            val raw = stat.availableBytes
            val reserved = inFlightReserved.get()
            (raw - reserved).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    override fun tryReserve(
        requiredBytes: Long,
        headroomBytes: Long,
        dir: File
    ): SpaceReservation? {
        if (requiredBytes <= 0L) {
            // Нулевая/отрицательная «резервация» — не блокируем поток
            return SpaceReservation(inFlightReserved, 0L)
        }

        val headroom = headroomBytes.coerceAtLeast(MIN_HEADROOM)

        // 1) Прямая попытка с headroom
        val avail1 = getAvailableBytes(dir)
        if (avail1 >= requiredBytes + headroom) {
            return acquire(requiredBytes)
        }

        // 2) Почистим экспортные кэши, затем ещё раз с headroom
        clearExportCache()
        // НЕ вызываем clearThumbCache() — thumbs содержат активные миниатюры страниц
        val avail2 = getAvailableBytes(dir)
        if (avail2 >= requiredBytes + headroom) {
            return acquire(requiredBytes)
        }

        // 3) Мягкий режим — если на сам файл хватает, игнорируем headroom.
        if (avail2 >= requiredBytes) {
            Timber.w("tryReserve: soft mode (no headroom). required=$requiredBytes, available=$avail2")
            return acquire(requiredBytes)
        }

        // Не смогли — действительно мало места
        return null
    }

    private fun acquire(requiredBytes: Long): SpaceReservation? {
        while (true) {
            val before = inFlightReserved.get()
            val after = before + requiredBytes
            if (inFlightReserved.compareAndSet(before, after)) {
                return SpaceReservation(inFlightReserved, requiredBytes)
            }
        }
    }

    override fun clearExportCache(): Long {
        val base = appCacheDir()
        val pdfs = clearDir(File(base, "pdfs"))
        val zips = clearDir(File(base, "zips"))
        val jpegs = clearDir(File(base, "jpeg_exports"))
        // Восстановим пустые папки на всякий
        File(base, "pdfs").mkdirs()
        File(base, "zips").mkdirs()
        File(base, "jpeg_exports").mkdirs()
        return pdfs + zips + jpegs
    }

    override fun clearThumbCache(): Long {
        val thumbsRoot = File(appFilesDir(), "thumbs")
        val freed = clearDir(thumbsRoot)
        thumbsRoot.mkdirs()
        return freed
    }

    private fun clearDir(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        try {
            dir.walkTopDown().forEach { f ->
                try {
                    val size = if (f.isFile) f.length() else 0L
                    if (f.delete()) freed += size
                } catch (_: Exception) { /* ignore */
                }
            }
        } catch (_: Exception) { /* ignore */
        }
        return freed
    }

    override fun sizeOf(path: String?): Long {
        if (path.isNullOrEmpty()) return 0L
        return try {
            val uri = runCatching { Uri.parse(path) }.getOrNull()
            if (uri != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                sizeOfContentUri(uri)
            } else {
                val f = File(path)
                if (f.exists() && f.isFile) f.length() else 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun sizeOfContentUri(uri: Uri): Long {
        // Сначала пробуем через OpenableColumns.SIZE
        var c: Cursor? = null
        try {
            c = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (c != null && c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) {
                    val v = c.getLong(idx)
                    if (v > 0) return v
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                c?.close()
            } catch (_: Throwable) {
            }
        }

        // Фолбэк: через ParcelFileDescriptor.statSize
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val len = pfd.statSize
                if (len > 0) len else 0L
            } ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }
}