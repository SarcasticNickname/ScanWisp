package com.myprojects.scanwisp.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressionService @Inject constructor() {

    fun processImageStream(inputStream: InputStream, profile: PdfExportProfile): ByteArray {
        val imageBytes = inputStream.readBytes()

        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(imageBytes), null, optsBounds)
        val srcW = optsBounds.outWidth
        val srcH = optsBounds.outHeight
        if (srcW <= 0 || srcH <= 0) {
            throw IOException("Failed to read image bounds")
        }

        // START: AI_MODIFIED_BLOCK
        val (jpegQuality, maxSidePx, toGray) = when (profile) {
            PdfExportProfile.SMALL -> Triple(78, 2000, true)
            PdfExportProfile.BALANCED -> Triple(90, 2400, false) // Уменьшено с 3000 до 2400
            PdfExportProfile.HIGH -> error("HIGH profile must bypass ImageCompressionService")
        }
        // END: AI_MODIFIED_BLOCK

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
        val decoded = BitmapFactory.decodeStream(ByteArrayInputStream(imageBytes), null, optsDecode)
            ?: throw IOException("Failed to decode bitmap")

        val bitmapToWrite = if (toGray) {
            val gray = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
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
        return out.toByteArray()
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