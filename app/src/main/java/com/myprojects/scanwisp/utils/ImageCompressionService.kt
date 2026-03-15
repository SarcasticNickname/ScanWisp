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
     * Применяет агрессивное сжатие для профиля SMALL:
     * уменьшение до maxSidePx, конвертация в grayscale, низкое JPEG quality.
     */
    suspend fun processImageStream(inputStream: InputStream): ByteArray =
        withContext(Dispatchers.IO) {
            val imageBytes = inputStream.readBytes()

            withContext(Dispatchers.Default) {
                val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(ByteArrayInputStream(imageBytes), null, optsBounds)
                val srcW = optsBounds.outWidth
                val srcH = optsBounds.outHeight
                if (srcW <= 0 || srcH <= 0) {
                    throw IOException("Failed to read image bounds")
                }

                val jpegQuality = 75
                val maxSidePx = 1600
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
                var decoded =
                    BitmapFactory.decodeStream(ByteArrayInputStream(imageBytes), null, optsDecode)
                        ?: throw IOException("Failed to decode bitmap")

                // inSampleSize даёт только кратные 2 уменьшения (1, 2, 4, 8...).
                // Если после decode изображение всё ещё больше целевого — делаем точный resize.
                val currentLongSide = maxOf(decoded.width, decoded.height)
                if (currentLongSide > maxSidePx) {
                    val resizeScale = maxSidePx.toFloat() / currentLongSide
                    val newW = (decoded.width * resizeScale).toInt().coerceAtLeast(1)
                    val newH = (decoded.height * resizeScale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(decoded, newW, newH, true)
                    if (scaled !== decoded) decoded.recycle()
                    decoded = scaled
                }

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
                out.toByteArray()
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