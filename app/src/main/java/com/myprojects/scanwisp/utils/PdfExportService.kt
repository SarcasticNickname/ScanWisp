package com.myprojects.scanwisp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.WordBox
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

private const val HIGH_QUALITY_DPI = 300
private const val BALANCED_QUALITY_DPI = 200
private const val JPEG_COMPRESSION_QUALITY = 85

/**
 * DPI, используемый для конвертации пикселей в PDF-points
 * когда fitToA4 = false (размер страницы по изображению).
 * 72 points = 1 inch → imagePx / IMAGE_RENDER_DPI * 72 = points.
 */
private const val IMAGE_RENDER_DPI = 150f

@Singleton
class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressionService: ImageCompressionService,
    private val crashlytics: FirebaseCrashlytics
) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun exportToPdf(
        pages: List<PageEntity>,
        title: String,
        profile: PdfExportProfile,
        fitToA4: Boolean
    ): File? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null

        val policy = SafeNamePolicy(context)
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val safeTitle = policy.exportBaseNameFromTitle(title)
        val outputFile = policy.uniqueFile(dir, safeTitle, ".pdf")

        PDDocument().use { document ->
            try {
                // Загружаем Unicode-шрифт один раз на весь документ
                val unicodeFont = loadUnicodeFont(document)

                pages.forEach { page ->
                    val imageBytes =
                        getImageBytesForProfile(page.processedImagePath, profile, fitToA4)
                    val pdImage =
                        PDImageXObject.createFromByteArray(document, imageBytes, null)

                    val pdfPage: PDPage
                    val imgW: Float
                    val imgH: Float
                    var imageOffsetX = 0f
                    var imageOffsetY = 0f

                    if (fitToA4) {
                        pdfPage = PDPage(PDRectangle.A4)
                        document.addPage(pdfPage)
                        val pageBounds = pdfPage.mediaBox

                        // FIT-скейлинг (min): изображение целиком на странице,
                        // без обрезки отсканированного документа.
                        val scale = min(
                            pageBounds.width / pdImage.width,
                            pageBounds.height / pdImage.height
                        )
                        imgW = pdImage.width * scale
                        imgH = pdImage.height * scale

                        // Центрируем на странице
                        imageOffsetX = (pageBounds.width - imgW) / 2
                        imageOffsetY = (pageBounds.height - imgH) / 2

                        PDPageContentStream(document, pdfPage).use { cs ->
                            cs.drawImage(pdImage, imageOffsetX, imageOffsetY, imgW, imgH)
                        }
                    } else {
                        // Конвертируем пиксели в PDF points (72 pts/inch)
                        // чтобы страница имела разумный физический размер.
                        imgW = pdImage.width * 72f / IMAGE_RENDER_DPI
                        imgH = pdImage.height * 72f / IMAGE_RENDER_DPI
                        pdfPage = PDPage(PDRectangle(imgW, imgH))
                        document.addPage(pdfPage)
                        PDPageContentStream(document, pdfPage).use { cs ->
                            cs.drawImage(pdImage, 0f, 0f, imgW, imgH)
                        }
                    }

                    // --- Текстовый слой (невидимый) ---
                    val wordBoxes = page.wordBoxesJson?.let { json ->
                        runCatching { WordBox.fromJson(json) }.getOrNull()
                    }
                    if (!wordBoxes.isNullOrEmpty()) {
                        addInvisibleTextLayer(
                            document,
                            pdfPage,
                            wordBoxes,
                            page.processedImagePath,
                            imgW,
                            imgH,
                            imageOffsetX,
                            imageOffsetY,
                            unicodeFont
                        )
                    } else if (!page.extractedText.isNullOrBlank()) {
                        addPlainTextLayer(document, pdfPage, page.extractedText, unicodeFont)
                    }
                }
                document.save(outputFile)
                return@withContext outputFile
            } catch (e: Exception) {
                Timber.e(e, "PDF export failed")
                crashlytics.recordException(e)
                outputFile.delete()
                return@withContext null
            }
        }
    }

    /**
     * Загружает Unicode TTF-шрифт для невидимого текстового слоя.
     * Если шрифт недоступен — fallback на Helvetica (только Latin-1).
     *
     * Для работы необходим файл: assets/fonts/NotoSans-Regular.ttf
     * Скачать: https://fonts.google.com/noto/specimen/Noto+Sans
     */
    private fun loadUnicodeFont(document: PDDocument): PDFont {
        return try {
            val fontStream = context.assets.open("fonts/NotoSans-Regular.ttf")
            PDType0Font.load(document, fontStream)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load NotoSans, falling back to Helvetica (no Cyrillic support)")
            PDType1Font.HELVETICA
        }
    }

    /**
     * Кладёт невидимый текст поверх изображения с точным позиционированием по словам.
     *
     * Координаты Tesseract — origin верхний левый (в пикселях исходного изображения).
     * Координаты PDF — origin нижний левый (в points).
     *
     * @param pdfWidth  ширина изображения на PDF-странице (в points)
     * @param pdfHeight высота изображения на PDF-странице (в points)
     * @param offsetX   смещение изображения от левого края страницы (в points)
     * @param offsetY   смещение изображения от нижнего края страницы (в points)
     * @param font      шрифт для текстового слоя (PDType0Font для Unicode)
     */
    private fun addInvisibleTextLayer(
        document: PDDocument,
        page: PDPage,
        wordBoxes: List<WordBox>,
        imagePath: String,
        pdfWidth: Float,
        pdfHeight: Float,
        offsetX: Float,
        offsetY: Float,
        font: PDFont
    ) {
        // Получаем реальные размеры исходного изображения для масштабирования
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, opts)
        val imgOrigW = opts.outWidth.toFloat().takeIf { it > 0 } ?: return
        val imgOrigH = opts.outHeight.toFloat().takeIf { it > 0 } ?: return

        // Масштаб: пиксель исходного изображения → PDF points на странице
        val scaleX = pdfWidth / imgOrigW
        val scaleY = pdfHeight / imgOrigH

        PDPageContentStream(
            document, page,
            PDPageContentStream.AppendMode.APPEND,
            true   // compress
        ).use { cs ->
            cs.beginText()
            cs.setRenderingMode(RenderingMode.NEITHER)
            cs.setFont(font, 1f)

            wordBoxes.forEach { wb ->
                val wordW = (wb.right - wb.left) * scaleX
                val wordH = (wb.bottom - wb.top) * scaleY

                if (wordW <= 0 || wordH <= 0) return@forEach

                // Координата X: offset изображения + позиция слова в масштабе
                val pdfX = offsetX + wb.left * scaleX
                // Координата Y: offset + инвертированная Y (Tesseract top→PDF bottom)
                val pdfY = offsetY + (pdfHeight - wb.bottom * scaleY)

                try {
                    // Масштабируем шрифт так, чтобы слово занимало ровно ширину bbox
                    val fontSize = wordH.coerceAtLeast(1f)
                    val textWidth = font.getStringWidth(wb.text) / 1000f * fontSize
                    val xScale = if (textWidth > 0) wordW / textWidth else 1f

                    cs.setFont(font, fontSize)
                    cs.setTextMatrix(
                        Matrix(xScale, 0f, 0f, 1f, pdfX, pdfY)
                    )
                    cs.showText(wb.text)
                } catch (e: Exception) {
                    // Пропускаем слово при ошибке кодировки шрифта
                    Timber.w(e, "Skipping word in PDF text layer: '${wb.text}'")
                }
            }
            cs.endText()
        }
    }

    /**
     * Fallback текстовый слой: когда есть extractedText, но нет wordBoxes.
     * Текст размещается невидимым по центру страницы (для корректного выделения
     * при Ctrl+F в PDF-ридерах).
     */
    private fun addPlainTextLayer(
        document: PDDocument,
        page: PDPage,
        text: String,
        font: PDFont
    ) {
        PDPageContentStream(
            document, page,
            PDPageContentStream.AppendMode.APPEND,
            true
        ).use { cs ->
            cs.beginText()
            cs.setRenderingMode(RenderingMode.NEITHER)
            cs.setFont(font, 8f)
            // Размещаем текст чуть выше нижнего края — многие ридеры
            // показывают подсветку поиска в этой области
            val mediaBox = page.mediaBox
            cs.newLineAtOffset(10f, mediaBox.height - 20f)
            val sanitized = text.take(1000).replace("\n", " ")
            try {
                cs.showText(sanitized)
            } catch (e: Exception) {
                Timber.w(e, "Skipping plain text layer due to encoding error")
            }
            cs.endText()
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
                    downsampleImage(inputStream, BALANCED_QUALITY_DPI, 2400, fitToA4)
                }

                PdfExportProfile.HIGH -> {
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

        var bitmap =
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                ?: throw IOException("Failed to decode bitmap for PDF export")

        // inSampleSize даёт только кратные 2 уменьшения.
        // Если после decode изображение всё ещё больше целевого — делаем точный resize.
        val targetLongSide = if (fitToA4) maxOf(targetWidth, targetHeight) else maxSidePx
        val currentLongSide = maxOf(bitmap.width, bitmap.height)
        if (currentLongSide > targetLongSide) {
            val scale = targetLongSide.toFloat() / currentLongSide
            val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            JPEG_COMPRESSION_QUALITY,
            outputStream
        )
        bitmap.recycle()

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