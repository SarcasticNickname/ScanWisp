package com.myprojects.scanwisp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Экспорт в PDF с 3 режимами:
 *  - HIGH: JPEG → без перекодирования и даунскейла (stream-to-PDF); не-JPEG → lossless (PNG)
 *  - BALANCED: умеренная компрессия (цвет)
 *  - SMALL: сильнее + grayscale
 *
 * Страница — A4, изображение вписывается с полями (масштаб матрицей, пиксели не «портим» сами по себе).
 * EXIF-ориентация корректируется матрицей, без повторной записи файла.
 */
@Singleton
class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressionService: ImageCompressionService,
    private val imageInfoReader: ImageInfoReader
) {

    private val marginPt = 12f // поля страницы

    init {
        // Инициализация PDFBox-Android (идемпотентно; можно звать многократно)
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Throwable) {
            // ignore
        }
    }

    /**
     * Создаёт PDF из набора путей (String — file://, content:// или обычный путь) с учётом профиля.
     * Возвращает файл в кэше либо null, если что-то пошло не так.
     */
    suspend fun exportToPdf(
        pageImagePaths: List<String>,
        title: String,
        profile: PdfExportProfile
    ): File? = withContext(Dispatchers.IO) {
        if (pageImagePaths.isEmpty()) return@withContext null

        var doc: PDDocument? = null
        try {
            doc = PDDocument()

            pageImagePaths.forEachIndexed { index, path ->
                val uri = toUri(path)
                val info = imageInfoReader.readInfo(uri)

                // Страница — A4
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)

                // Готовим PDImageXObject согласно профилю и типу
                val imgXObject = buildImageXObject(doc, uri, info, profile)

                // Рисуем с учётом ориентации
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true).use { cs ->
                    drawImageWithOrientation(
                        cs = cs,
                        img = imgXObject,
                        info = info,
                        pageRect = PDRectangle.A4,
                        margin = marginPt
                    )
                }
            }

            val file = savePdfToCache(doc, title)
            return@withContext file
        } catch (t: Throwable) {
            Log.e("PdfExportService", "exportToPdf failed", t)
            return@withContext null
        } finally {
            try { doc?.close() } catch (_: Throwable) {}
        }
    }

    // --- Helpers ---

    private fun toUri(path: String): Uri {
        return when {
            path.startsWith("content://") || path.startsWith("file://") || path.startsWith("android.resource://") ->
                Uri.parse(path)
            else -> Uri.fromFile(File(path))
        }
    }

    /**
     * Создаёт PDImageXObject:
     *  - HIGH/JPEG → JPEGFactory.createFromStream (без перекодирования)
     *  - HIGH/non-JPEG → LosslessFactory.createFromImage (PNG без потерь)
     *  - BALANCED/SMALL → компрессия в JPEG через ImageCompressionService
     */
    private fun buildImageXObject(
        doc: PDDocument,
        uri: Uri,
        info: ImageInfoReader.ImageInfo,
        profile: PdfExportProfile
    ): PDImageXObject {
        val resolver = context.contentResolver
        val mime = (info.mimeType ?: "").lowercase()

        return when (profile) {
            PdfExportProfile.HIGH -> {
                if (mime.contains("jpeg") || mime.contains("jpg")) {
                    resolver.openInputStream(uri)?.use { input ->
                        JPEGFactory.createFromStream(doc, input)
                    } ?: throw IllegalStateException("Null input stream for JPEG: $uri")
                } else {
                    // Не-JPEG: делаем lossless PNG (без потерь)
                    val bmp = decodeBitmapFull(uri)
                    try {
                        LosslessFactory.createFromImage(doc, bmp)
                    } finally {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }
            }

            PdfExportProfile.BALANCED,
            PdfExportProfile.SMALL -> {
                resolver.openInputStream(uri)?.use { input ->
                    val jpegBytes = imageCompressionService.processImageStream(input, profile)
                    ByteArrayInputStream(jpegBytes).use { bais ->
                        JPEGFactory.createFromStream(doc, bais)
                    }
                } ?: throw IllegalStateException("Null input stream for $uri")
            }
        }
    }

    /**
     * Декодировать bitmap полностью (без даунсэмплинга) — используется ТОЛЬКО
     * для HIGH/non-JPEG, когда нужен PNG без потерь.
     */
    private fun decodeBitmapFull(uri: Uri): Bitmap {
        val cr = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(cr, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            cr.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
                    ?: throw IllegalStateException("decodeStream failed for $uri")
            } ?: throw IllegalStateException("Cannot open input stream: $uri")
        }
    }

    private fun drawImageWithOrientation(
        cs: PDPageContentStream,
        img: PDImageXObject,
        info: ImageInfoReader.ImageInfo,
        pageRect: PDRectangle,
        margin: Float
    ) {
        val pageW = pageRect.width
        val pageH = pageRect.height

        val imgWpxEff =
            if (info.rotationDegrees == 90 || info.rotationDegrees == 270) info.heightPx else info.widthPx
        val imgHpxEff =
            if (info.rotationDegrees == 90 || info.rotationDegrees == 270) info.widthPx else info.heightPx

        // Вписываем в страницу с полями (масштаб сохраняет пропорции)
        val maxW = (pageW - 2 * margin).coerceAtLeast(1f)
        val maxH = (pageH - 2 * margin).coerceAtLeast(1f)
        val scale = min(maxW / imgWpxEff, maxH / imgHpxEff)
        val drawW = imgWpxEff * scale
        val drawH = imgHpxEff * scale

        val x = (pageW - drawW) / 2f
        val y = (pageH - drawH) / 2f

        cs.saveGraphicsState()
        when (info.rotationDegrees) {
            90 -> {
                val m = Matrix()
                m.translate(x + drawW, y)
                m.rotate(Math.toRadians(90.0))
                cs.transform(m)
                cs.drawImage(img, 0f, 0f, drawH, drawW)
            }
            180 -> {
                val m = Matrix()
                m.translate(x + drawW, y + drawH)
                m.rotate(Math.toRadians(180.0))
                cs.transform(m)
                cs.drawImage(img, 0f, 0f, drawW, drawH)
            }
            270 -> {
                val m = Matrix()
                m.translate(x, y + drawH)
                m.rotate(Math.toRadians(270.0))
                cs.transform(m)
                cs.drawImage(img, 0f, 0f, drawH, drawW)
            }
            else -> {
                cs.drawImage(img, x, y, drawW, drawH)
            }
        }
        cs.restoreGraphicsState()
    }

    private fun savePdfToCache(pdfDocument: PDDocument, title: String): File {
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val policy = SafeNamePolicy(context)
        val safe = policy.exportBaseNameFromTitle(title)
        val file = policy.uniqueFile(dir, safe, ".pdf")
        pdfDocument.save(file)
        return file
    }
}
