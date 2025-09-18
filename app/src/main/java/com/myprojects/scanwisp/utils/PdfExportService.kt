package com.myprojects.scanwisp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private const val HIGH_QUALITY_DPI = 300
private const val BALANCED_QUALITY_DPI = 200
private const val JPEG_COMPRESSION_QUALITY = 85

@Singleton
class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressionService: ImageCompressionService,
    private val crashlytics: FirebaseCrashlytics
) {

    suspend fun exportToPdf(
        pageImagePaths: List<String>,
        title: String,
        profile: PdfExportProfile,
        fitToA4: Boolean
    ): File? = withContext(Dispatchers.IO) {
        if (pageImagePaths.isEmpty()) return@withContext null

        val policy = SafeNamePolicy(context)
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val safeTitle = policy.exportBaseNameFromTitle(title)
        val outputFile = policy.uniqueFile(dir, safeTitle, ".pdf")

        PDDocument().use { document ->
            try {
                pageImagePaths.forEach { path ->
                    // ИЗМЕНЕНИЕ: Теперь передаем fitToA4 для умного сжатия
                    val imageBytes = getImageBytesForProfile(path, profile, fitToA4)
                    val pdImage = PDImageXObject.createFromByteArray(document, imageBytes, null)

                    if (fitToA4) {
                        // Логика для заполнения страницы A4
                        val page = PDPage(PDRectangle.A4)
                        document.addPage(page)
                        PDPageContentStream(document, page).use { contentStream ->
                            val pageBounds = page.mediaBox
                            val scale = max(
                                pageBounds.width / pdImage.width,
                                pageBounds.height / pdImage.height
                            )
                            val scaledWidth = pdImage.width * scale
                            val scaledHeight = pdImage.height * scale
                            val startX = (pageBounds.width - scaledWidth) / 2
                            val startY = (pageBounds.height - scaledHeight) / 2
                            contentStream.saveGraphicsState()
                            contentStream.addRect(0f, 0f, pageBounds.width, pageBounds.height)
                            contentStream.clip()
                            contentStream.drawImage(
                                pdImage,
                                startX,
                                startY,
                                scaledWidth,
                                scaledHeight
                            )
                            contentStream.restoreGraphicsState()
                        }
                    } else {
                        // Логика для страницы по размеру контента
                        val page =
                            PDPage(PDRectangle(pdImage.width.toFloat(), pdImage.height.toFloat()))
                        document.addPage(page)
                        PDPageContentStream(document, page).use { contentStream ->
                            contentStream.drawImage(
                                pdImage,
                                0f,
                                0f,
                                pdImage.width.toFloat(),
                                pdImage.height.toFloat()
                            )
                        }
                    }
                }
                document.save(outputFile)
                return@withContext outputFile
            } catch (e: Exception) {
                Timber.e(e, "Failed to create PDF using PDFBox")
                crashlytics.recordException(e)
                outputFile.delete()
                return@withContext null
            }
        }
    }

    private suspend fun getImageBytesForProfile(
        path: String,
        profile: PdfExportProfile,
        fitToA4: Boolean
    ): ByteArray {
        FileInputStream(File(path)).use { inputStream ->
            return when (profile) {
                PdfExportProfile.SMALL -> {
                    imageCompressionService.processImageStream(inputStream)
                }

                PdfExportProfile.BALANCED -> {
                    // Уменьшаем до 200 DPI для А4 или до макс. стороны 2400px
                    downsampleImage(inputStream, BALANCED_QUALITY_DPI, 2400, fitToA4)
                }

                PdfExportProfile.HIGH -> {
                    // Уменьшаем до 300 DPI для А4 или до макс. стороны 3200px
                    downsampleImage(inputStream, HIGH_QUALITY_DPI, 3200, fitToA4)
                }
            }
        }
    }

    private fun downsampleImage(
        inputStream: InputStream,
        targetDpi: Int,
        maxSidePx: Int,
        fitToA4: Boolean
    ): ByteArray {
        val imageBytes = inputStream.readBytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        val (targetWidth, targetHeight) = if (fitToA4) {
            // A4 is 8.27 x 11.69 inches
            ((8.27 * targetDpi).toInt() to (11.69 * targetDpi).toInt())
        } else {
            (maxSidePx to maxSidePx)
        }

        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inJustDecodeBounds = false

        val downsampledBitmap =
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        val outputStream = ByteArrayOutputStream()
        downsampledBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            JPEG_COMPRESSION_QUALITY,
            outputStream
        )
        downsampledBitmap.recycle()

        return outputStream.toByteArray()
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}