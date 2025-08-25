package com.myprojects.scanwisp.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Лёгкое чтение информации об изображении:
 * - ширина/высота в пикселях (без декодирования в Bitmap)
 * - mime type
 * - EXIF-ориентация и вращение в градусах
 *
 * Важно: никуда не «readBytes()» — работаем потоками и вторично открываем их при необходимости.
 */
@Singleton
class ImageInfoReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class ImageInfo(
        val widthPx: Int,
        val heightPx: Int,
        val mimeType: String?,
        val rotationDegrees: Int,
        val exifOrientation: Int,
        val displayName: String? = null,
        val sizeBytes: Long? = null
    )

    fun readInfo(uri: Uri): ImageInfo {
        val resolver = context.contentResolver

        val (mime, name, size) = probeMeta(resolver, uri)

        // Быстрый проход для получения размеров без декодирования bitmap
        val (w, h) = decodeBounds(resolver, uri)

        // Читаем EXIF-ориентацию отдельным проходом (если формат поддерживает EXIF)
        val (orientation, rotation) = readExif(resolver, uri, mime)

        return ImageInfo(
            widthPx = w,
            heightPx = h,
            mimeType = mime,
            rotationDegrees = rotation,
            exifOrientation = orientation,
            displayName = name,
            sizeBytes = size
        )
    }

    private fun probeMeta(
        resolver: ContentResolver,
        uri: Uri
    ): Triple<String?, String?, Long?> {
        var mime: String? = resolver.getType(uri)
        var name: String? = null
        var size: Long? = null

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx)
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }

        if (mime == null) {
            // Попытка определить по расширению
            val ext = MimeTypeMap.getFileExtensionFromUrl(name ?: "")
            if (!ext.isNullOrBlank()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            }
        }

        return Triple(mime, name, size)
    }

    private fun decodeBounds(resolver: ContentResolver, uri: Uri): Pair<Int, Int> {
        resolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(ShieldedInputStream(input), null, opts)
            return (opts.outWidth to opts.outHeight)
        }
        // Если не получилось — возвращаем нули (верхний код обработает это)
        return 0 to 0
    }

    private fun readExif(
        resolver: ContentResolver,
        uri: Uri,
        mime: String?
    ): Pair<Int, Int> {
        // EXIF актуален прежде всего для JPEG/Heif; для PNG не обязателен.
        val supportsExif = when {
            mime.isNullOrBlank() -> true // попробуем — если не получится, вернём 0
            mime.contains("jpeg") || mime.contains("jpg") -> true
            mime.contains("heic") || mime.contains("heif") -> true
            else -> false
        }

        if (!supportsExif) return 0 to 0

        return try {
            resolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    ExifInterface.ORIENTATION_TRANSPOSE -> 90   // зеркальные варианты мапим на ближайший поворот
                    ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
                orientation to rotation
            } ?: (0 to 0)
        } catch (_: Throwable) {
            0 to 0
        }
    }
}

/**
 * Некоторые потоки (особенно content://) не поддерживают mark/reset, а PDFBox/BitmapFactory
 * могут читать «чуть дальше». Оборачиваем в безопасный стек.
 */
private class ShieldedInputStream(private val delegate: InputStream) : InputStream() {
    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun close() = delegate.close()
    override fun available(): Int = delegate.available()
    override fun markSupported(): Boolean = false
}
