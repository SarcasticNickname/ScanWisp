package com.myprojects.scanwisp.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Лёгкое чтение информации об изображении.
 * ИЗМЕНЕНИЕ: Теперь корректно работает как с content:// URI, так и с file-path.
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

        // Обертка для получения InputStream, которая работает и для content:// и для file://
        val inputStreamProvider = { openInputStream(uri) }

        val (w, h) = decodeBounds(inputStreamProvider)
        val (orientation, rotation) = readExif(inputStreamProvider, mime)

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

    /**
     * НОВЫЙ МЕТОД-ОБЕРТКА: Открывает InputStream как для Content URI, так и для File URI.
     */
    private fun openInputStream(uri: Uri): InputStream? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.openInputStream(uri)
        } else {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) FileInputStream(file) else null
            } else {
                null
            }
        }
    }

    private fun probeMeta(
        resolver: ContentResolver,
        uri: Uri
    ): Triple<String?, String?, Long?> {
        // ... (этот метод остается без изменений)
        var mime: String? = resolver.getType(uri)
        var name: String? = null
        var size: Long? = null

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } else {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    name = file.name
                    size = file.length()
                }
            }
        }


        if (mime == null) {
            val ext = MimeTypeMap.getFileExtensionFromUrl(name ?: "")
            if (!ext.isNullOrBlank()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            }
        }

        return Triple(mime, name, size)
    }

    private fun decodeBounds(inputStreamProvider: () -> InputStream?): Pair<Int, Int> {
        inputStreamProvider()?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(ShieldedInputStream(input), null, opts)
            return (opts.outWidth to opts.outHeight)
        }
        return 0 to 0
    }

    private fun readExif(
        inputStreamProvider: () -> InputStream?,
        mime: String?
    ): Pair<Int, Int> {
        val supportsExif = when {
            mime.isNullOrBlank() -> true
            mime.contains("jpeg") || mime.contains("jpg") -> true
            mime.contains("heic") || mime.contains("heif") -> true
            else -> false
        }

        if (!supportsExif) return 0 to 0

        return try {
            inputStreamProvider()?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    ExifInterface.ORIENTATION_TRANSPOSE -> 90
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

private class ShieldedInputStream(private val delegate: InputStream) : InputStream() {
    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun close() = delegate.close()
    override fun available(): Int = delegate.available()
    override fun markSupported(): Boolean = false
}