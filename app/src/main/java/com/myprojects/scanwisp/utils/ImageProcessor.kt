package com.myprojects.scanwisp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

// Константы для качества и размеров изображений
private const val FULL_SIZE_LONG_SIDE_PX = 2400
private const val THUMBNAIL_LONG_SIDE_PX = 800
private const val JPEG_QUALITY = 85

/**
 * DTO для возврата путей к обработанным изображениям.
 * @param processedImagePath Путь к полноразмерному, правильно ориентированному изображению.
 * @param thumbnailPath Путь к уменьшенному превью для списков.
 */
data class ProcessedImagePaths(
    val processedImagePath: String,
    val thumbnailPath: String
)

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safeNamePolicy: SafeNamePolicy
) {

    suspend fun processImageForStorage(sourceUri: Uri): ProcessedImagePaths? =
        withContext(Dispatchers.IO) {
            // НОВЫЙ ЛОГ: Начало работы функции
            Timber.d("-> processImageForStorage НАЧАЛО. URI: $sourceUri")
            try {
                // 1. Читаем EXIF-ориентацию
                Timber.d("   Шаг 1: Чтение EXIF-ориентации...")
                val rotationDegrees = getRotationDegrees(sourceUri)
                Timber.d("   Шаг 1: Ориентация определена: $rotationDegrees градусов.")

                // 2. Декодируем полноразмерный битмап
                Timber.d("   Шаг 2: Декодирование полноразмерного изображения...")
                val fullBitmap = decodeSampledBitmap(sourceUri, FULL_SIZE_LONG_SIDE_PX)
                Timber.d("   Шаг 2: Полноразмерное изображение декодировано. Поворот...")
                val rotatedFullBitmap = fullBitmap.rotate(rotationDegrees.toFloat())
                Timber.d("   Шаг 2: Полноразмерное изображение повернуто.")

                // 3. Декодируем битмап для превью
                Timber.d("   Шаг 3: Декодирование превью...")
                val thumbBitmap = decodeSampledBitmap(sourceUri, THUMBNAIL_LONG_SIDE_PX)
                Timber.d("   Шаг 3: Превью декодировано. Поворот...")
                val rotatedThumbBitmap = thumbBitmap.rotate(rotationDegrees.toFloat())
                Timber.d("   Шаг 3: Превью повернуто.")

                // 4. Сохраняем оба изображения.
                Timber.d("   Шаг 4: Подготовка к сохранению файлов...")
                val originalDir = File(context.filesDir, "originals").apply { mkdirs() }
                val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

                val baseName = "scan_${System.currentTimeMillis()}"
                val originalFile = safeNamePolicy.uniqueFile(originalDir, baseName, ".jpg")
                val thumbFile = File(thumbDir, originalFile.name)
                Timber.d("   Шаг 4: Пути для сохранения: \n      - ${originalFile.absolutePath}\n      - ${thumbFile.absolutePath}")

                saveBitmapToFile(rotatedFullBitmap, originalFile)
                Timber.d("   Шаг 4: Полноразмерное изображение сохранено.")
                saveBitmapToFile(rotatedThumbBitmap, thumbFile)
                Timber.d("   Шаг 4: Превью сохранено.")

                // 5. Освобождаем память и возвращаем пути
                Timber.d("   Шаг 5: Освобождение памяти...")
                rotatedFullBitmap.recycle()
                rotatedThumbBitmap.recycle()
                Timber.d("   Шаг 5: Память освобождена.")

                // НОВЫЙ ЛОГ: Успешное завершение
                Timber.i("<- processImageForStorage УСПЕХ. URI: $sourceUri")

                return@withContext ProcessedImagePaths(
                    processedImagePath = originalFile.absolutePath,
                    thumbnailPath = thumbFile.absolutePath
                )
            } catch (e: Exception) {
                // НОВЫЙ ЛОГ: Очень подробный лог ошибки
                Timber.e("----------------- ОШИБКА ОБРАБОТКИ ИЗОБРАЖЕНИЯ -----------------")
                Timber.e("URI, на котором произошла ошибка: $sourceUri")
                Timber.e("Тип исключения: ${e.javaClass.simpleName}")
                Timber.e("Сообщение: ${e.message}")
                Timber.e(e, "Полный stack trace:") // Этот метод выведет полный стектрейс
                Timber.e("-----------------------------------------------------------------")
                // В случае ошибки логируем и возвращаем null
                // Здесь можно добавить запись в Crashlytics, если ее нет выше по стеку
                return@withContext null
            }
        }

    private fun getRotationDegrees(sourceUri: Uri): Int {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Не могу открыть InputStream для чтения EXIF: $sourceUri")

        return inputStream.use {
            val exifInterface = ExifInterface(it)
            exifInterface.rotationDegrees
        }
    }

    private fun decodeSampledBitmap(sourceUri: Uri, targetLongSide: Int): Bitmap {
        var inputStream: InputStream? = null
        try {
            // Первый проход: получаем размеры
            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Не могу открыть InputStream для проверки размеров: $sourceUri")

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Рассчитываем inSampleSize
            val longSide = max(options.outWidth, options.outHeight)
            options.inSampleSize = calculateInSampleSize(longSide, targetLongSide)

            // Второй проход: декодируем
            options.inJustDecodeBounds = false
            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Не могу открыть InputStream для декодирования: $sourceUri")

            return BitmapFactory.decodeStream(inputStream, null, options)
                ?: throw IllegalStateException("BitmapFactory не смог декодировать поток из URI.")
        } finally {
            inputStream?.close()
        }
    }

    private fun calculateInSampleSize(longSide: Int, targetLongSide: Int): Int {
        var inSampleSize = 1
        if (longSide > targetLongSide) {
            val halfLongSide = longSide / 2
            while ((halfLongSide / inSampleSize) >= targetLongSide) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        if (degrees == 0f) return this
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotatedBitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotatedBitmap != this) {
            this.recycle()
        }
        return rotatedBitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
    }
}

private val ExifInterface.rotationDegrees: Int
    get() = when (getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }