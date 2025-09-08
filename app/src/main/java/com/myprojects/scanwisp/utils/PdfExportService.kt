package com.myprojects.scanwisp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ==========================================================
 * РЕФАКТОРИНГ: Сервис переписан на нативный android.graphics.pdf.PdfDocument.
 * Это делает экспорт быстрее, легче по потреблению RAM и уменьшает размер APK,
 * так как больше не используется тяжелая библиотека PDFBox.
 * ==========================================================
 *
 * Логика умного выбора размера страницы сохранена:
 * - Если скан меньше А4, страница PDF создается точно по размеру контента.
 * - Если скан больше А4, он пропорционально вписывается в стандартную страницу А4.
 */
@Singleton
class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressionService: ImageCompressionService,
    private val imageInfoReader: ImageInfoReader,
    private val crashlytics: FirebaseCrashlytics
) {

    // Размеры А4 в пунктах (1/72 дюйма)
    private val a4WidthPoints = 595
    private val a4HeightPoints = 842
    private val marginPoints = 12

    // Плотность пикселей для перевода пикселей в физические размеры (точки).
    private val referenceDpi = 300f

    /**
     * Создаёт PDF из набора путей к изображениям с учётом профиля качества.
     * Возвращает файл в кэше либо null в случае ошибки.
     */
    suspend fun exportToPdf(
        pageImagePaths: List<String>,
        title: String,
        profile: PdfExportProfile
    ): File? = withContext(Dispatchers.IO) {
        if (pageImagePaths.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()
        val policy = SafeNamePolicy(context)
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val safeTitle = policy.exportBaseNameFromTitle(title)
        val outputFile = policy.uniqueFile(dir, safeTitle, ".pdf")

        try {
            pageImagePaths.forEachIndexed { index, path ->
                val uri = Uri.parse(path)
                val info = imageInfoReader.readInfo(uri)

                // 1. Определяем размер страницы
                val imgWidthPt = (info.widthPx * 72f / referenceDpi).roundToInt()
                val imgHeightPt = (info.heightPx * 72f / referenceDpi).roundToInt()

                val fitsInA4 = imgWidthPt <= a4WidthPoints && imgHeightPt <= a4HeightPoints
                val useCropToContent = fitsInA4

                val (pageWidth, pageHeight) = if (useCropToContent) {
                    imgWidthPt to imgHeightPt
                } else {
                    a4WidthPoints to a4HeightPoints
                }

                // 2. Создаем страницу
                val pageInfo =
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // 3. Загружаем и рисуем Bitmap
                drawBitmapOnCanvas(canvas, uri, profile, useCropToContent)

                pdfDocument.finishPage(page)
            }

            // 4. Сохраняем документ в файл
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }

            return@withContext outputFile
        } catch (e: Exception) {
            Log.e("PdfExportService", "Failed to create PDF", e)
            crashlytics.recordException(e)
            outputFile.delete() // Удаляем частично созданный файл в случае ошибки
            return@withContext null
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Загружает, при необходимости сжимает, и рисует Bitmap на Canvas.
     */
    private fun drawBitmapOnCanvas(
        canvas: Canvas,
        uri: Uri,
        profile: PdfExportProfile,
        isCroppedToContent: Boolean
    ) {
        var bitmap: Bitmap? = null
        try {
            // Загружаем Bitmap с учетом профиля
            bitmap = loadBitmapForProfile(uri, profile)

            val margin = if (isCroppedToContent) 0 else marginPoints
            val canvasWidth = canvas.width - 2 * margin
            val canvasHeight = canvas.height - 2 * margin

            // Масштабируем Bitmap, чтобы он вписался в доступную область
            val scale = min(
                canvasWidth.toFloat() / bitmap.width,
                canvasHeight.toFloat() / bitmap.height
            )
            val drawWidth = (bitmap.width * scale).roundToInt()
            val drawHeight = (bitmap.height * scale).roundToInt()

            // Центрируем изображение
            val left = margin + (canvasWidth - drawWidth) / 2
            val top = margin + (canvasHeight - drawHeight) / 2
            val destRect = Rect(left, top, left + drawWidth, top + drawHeight)

            canvas.drawBitmap(bitmap, null, destRect, null)
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Загружает Bitmap из URI, применяя сжатие в соответствии с профилем.
     */
    private fun loadBitmapForProfile(uri: Uri, profile: PdfExportProfile): Bitmap {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            // Для HIGH профиля и JPEG-файлов мы могли бы попытаться использовать оригинал,
            // но для простоты и консистентности будем всегда декодировать.
            // ImageCompressionService уже оптимизирован.
            if (profile == PdfExportProfile.HIGH) {
                return BitmapFactory.decodeStream(inputStream)
                    ?: throw IOException("BitmapFactory failed to decode stream for URI: $uri")
            }

            // Для SMALL и BALANCED используем наш сервис сжатия
            val compressedBytes = imageCompressionService.processImageStream(inputStream, profile)
            return BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                ?: throw IOException("BitmapFactory failed to decode compressed byte array.")
        } ?: throw IOException("ContentResolver returned null InputStream for URI: $uri")
    }

    // Класс IOException для полноты картины
    class IOException(message: String) : Exception(message)
}