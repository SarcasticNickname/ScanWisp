package com.myprojects.scanwisp.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import com.myprojects.scanwisp.domain.model.WordBox
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wordFilter: OcrWordFilter
) {
    private val mutex = Mutex()

    private fun getTessBaseDir(mode: String): File =
        File(context.filesDir, "tesseract_$mode")

    suspend fun recognizeText(
        imagePath: String,
        mode: String = "fast",
        lang: String = "rus+eng"
    ): OcrResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                ensureInitialized(mode)

                val baseDir = getTessBaseDir(mode)
                val tessApi = TessBaseAPI()

                try {
                    val success = tessApi.init(
                        baseDir.absolutePath, lang, TessBaseAPI.OEM_LSTM_ONLY
                    )
                    if (!success) {
                        Timber.e("Tesseract init failed: ${baseDir.absolutePath}")
                        tessApi.recycle()
                        return@withLock OcrResult("", emptyList())
                    }

                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    if (bitmap == null) {
                        Timber.e("Failed to decode bitmap: $imagePath")
                        tessApi.recycle()
                        return@withLock OcrResult("", emptyList())
                    }

                    try {
                        tessApi.setImage(bitmap)

                        // 1. Получаем плоский текст
                        val plainText = tessApi.utF8Text?.trim() ?: ""

                        // 2. Собираем WordBox-ы через ResultIterator
                        val wordBoxes = mutableListOf<WordBox>()
                        val iterator = tessApi.resultIterator
                        if (iterator != null) {
                            try {
                                iterator.begin()
                                do {
                                    val word =
                                        iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                                    val box =
                                        iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                                    val conf =
                                        iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                                    if (!word.isNullOrBlank() && box != null && box.size == 4) {
                                        val trimmed = word.trim()
                                        if (wordFilter.isPlausible(trimmed, conf)) {
                                            wordBoxes.add(
                                                WordBox(
                                                    trimmed,
                                                    box[0],
                                                    box[1],
                                                    box[2],
                                                    box[3],
                                                    conf
                                                )
                                            )
                                        }
                                    }
                                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                            } finally {
                                iterator.delete()
                            }
                        }

                        // Пересобираем plainText из отфильтрованных слов
                        val cleanText = wordBoxes.joinToString(" ") { it.text }
                        return@withLock OcrResult(cleanText, wordBoxes)
                    } finally {
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "OCR execution error")
                    return@withLock OcrResult("", emptyList())
                } finally {
                    try {
                        tessApi.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        tessApi.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
        }

    private suspend fun ensureInitialized(mode: String) = withContext(Dispatchers.IO) {
        val destDir = File(getTessBaseDir(mode), "tessdata")
        if (!destDir.exists() && !destDir.mkdirs()) {
            Timber.e("Failed to create dir: ${destDir.absolutePath}")
            return@withContext
        }
        listOf("rus.traineddata", "eng.traineddata").forEach { fileName ->
            val destFile = File(destDir, fileName)
            if (!destFile.exists() || destFile.length() == 0L) {
                try {
                    context.assets.open("tessdata/$mode/$fileName").use { input ->
                        FileOutputStream(destFile).use { input.copyTo(it) }
                    }
                } catch (e: IOException) {
                    Timber.e(e, "Failed to copy $fileName (mode=$mode)")
                }
            }
        }
    }

    companion object {
        /** Минимальная уверенность Tesseract (0–100) */
        private const val MIN_CONFIDENCE = 35f

        /** Минимальная доля букв в слове */
        private const val MIN_LETTER_RATIO = 0.5f

        /** Максимум подряд идущих согласных (для рус/англ) */
        private const val MAX_CONSECUTIVE_CONSONANTS = 4

        private val CONSONANTS_RU = "бвгджзйклмнпрстфхцчшщ".toSet()
        private val CONSONANTS_EN = "bcdfghjklmnpqrstvwxyz".toSet()
        private val ALL_CONSONANTS = CONSONANTS_RU + CONSONANTS_EN

        /**
         * Проверяет, похоже ли слово на настоящее, а не на мусор OCR.
         */
        private fun isPlausibleWord(word: String, confidence: Float): Boolean {
            if (word.length < 2 && !word.first().isLetterOrDigit()) return false
            if (confidence < MIN_CONFIDENCE) return false

            val letters = word.count { it.isLetter() }
            val total = word.length

            // Слишком мало букв относительно длины ("||{}" и т.п.)
            if (total >= 3 && letters.toFloat() / total < MIN_LETTER_RATIO) return false

            // Проверяем невозможные цепочки согласных (>4 подряд — мусор)
            var consecutive = 0
            for (ch in word.lowercase()) {
                if (ch in ALL_CONSONANTS) {
                    consecutive++
                    if (consecutive > MAX_CONSECUTIVE_CONSONANTS) return false
                } else {
                    consecutive = 0
                }
            }

            // Повторяющийся один символ ("ааааа", "||||")
            if (total >= 3 && word.toSet().size == 1) return false

            return true
        }
    }
}