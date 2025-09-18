package com.myprojects.scanwisp.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressionService @Inject constructor() {


    /**
     * ИЗМЕНЕНИЕ: Метод теперь не принимает профиль. Его единственная задача -
     * применить агрессивное сжатие (уменьшение, ч/б, низкое качество) для
     * создания "маленького" файла.
     */
    suspend fun processImageStream(inputStream: InputStream): ByteArray =
        withContext(Dispatchers.IO) {
            val imageBytes = inputStream.readBytes() // 1. Чтение с диска - это IO операция.

            // 2. Все вычисления с Bitmap переносим на вычислительный диспетчер.
            withContext(Dispatchers.Default) {
                val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(ByteArrayInputStream(imageBytes), null, optsBounds)
                val srcW = optsBounds.outWidth
                val srcH = optsBounds.outHeight
                if (srcW <= 0 || srcH <= 0) {
                    throw IOException("Failed to read image bounds")
                }

                // Агрессивные настройки для профиля "Малый размер"
                val jpegQuality = 75
                val maxSidePx = 1600 // Уменьшаем разрешение еще сильнее
                val toGray = true

                val scale = if (srcW >= srcH) {
                    (maxSidePx.toFloat() / srcW).coerceAtMost(1f)
                } else {
                    (maxSidePx.toFloat() / srcH).coerceAtMost(1f)
                }
                val reqW = (srcW * scale).toInt().coerceAtLeast(1)
                val reqH = (srcH * scale).toInt().coerceAtLeast(1)

                val inSample = calculateInSampleSize(srcW, srcH, reqW, reqH)

                val optsDecode = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = inSample
                }
                val decoded =
                    BitmapFactory.decodeStream(ByteArrayInputStream(imageBytes), null, optsDecode)
                        ?: throw IOException("Failed to decode bitmap")

                val bitmapToWrite = if (toGray) {
                    val gray =
                        Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(gray)
                    val paint = Paint()
                    val cm = ColorMatrix().apply { setSaturation(0f) }
                    paint.colorFilter = ColorMatrixColorFilter(cm)
                    canvas.drawBitmap(decoded, 0f, 0f, paint)
                    decoded.recycle()
                    gray
                } else {
                    decoded
                }

                val out = ByteArrayOutputStream()
                bitmapToWrite.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                bitmapToWrite.recycle()
                out.toByteArray() // Возвращаем результат из блока Dispatchers.Default
            }
        }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}