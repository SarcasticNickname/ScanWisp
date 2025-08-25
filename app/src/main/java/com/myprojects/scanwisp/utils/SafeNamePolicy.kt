package com.myprojects.scanwisp.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая политика безопасных имён для документов и экспортируемых файлов.
 *
 * Цели:
 * - Безопасно для Windows/macOS/Linux/мессенджеров/почтовиков.
 * - Никаких двоеточий, слэшей, кавычек и прочей «грязи».
 * - Прогнозируемая уникальность: "Name (2).ext", "Name (3).ext", ...
 */
@Singleton
class SafeNamePolicy @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Формат даты для названий по умолчанию: 2025-08-23 22-58-31 (без двоеточий) */
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault())

    /** Зарезервированные имена Windows (без расширений) */
    private val windowsReserved = setOf(
        "CON","PRN","AUX","NUL",
        "COM1","COM2","COM3","COM4","COM5","COM6","COM7","COM8","COM9",
        "LPT1","LPT2","LPT3","LPT4","LPT5","LPT6","LPT7","LPT8","LPT9"
    )

    /** Разрешаем буквы/цифры/пробел/подчёркивание/дефис/скобки/точку. Остальное вычищаем. */
    private val allowRegex = Regex("[^\\p{L}\\p{N} _()\\.-]+")

    /**
     * Имя документа по умолчанию при создании скана.
     * Пример: "Scan 2025-08-23 22-58-31"
     */
    fun newDocumentTitle(now: Long = System.currentTimeMillis()): String {
        val prefix = "Scan"
        val stamp = dateFmt.format(Date(now))
        return "$prefix $stamp"
    }

    /**
     * Базовое имя для экспорта (PDF/ZIP/JPEG-пакет) на основе названия документа.
     * Отчищает «грязь» и подрезает длину.
     */
    fun exportBaseNameFromTitle(title: String?, pageCount: Int? = null): String {
        val base0 = (title ?: newDocumentTitle()).trim()
        val base1 = sanitizeBase(base0)
        val suffix = if (pageCount != null && pageCount > 1) " ($pageCount p)" else ""
        val candidate = (base1 + suffix).trim()
        return if (candidate.isBlank()) "Scan" else candidate
    }

    /**
     * Гарантированно уникальный файл в директории для заданного baseName и расширения.
     * Возвращает File, который ещё не существует.
     */
    fun uniqueFile(dir: File, baseName: String, extWithDot: String): File {
        dir.mkdirs()
        var base = sanitizeBase(baseName)
        if (base.isBlank()) base = "Scan"
        // Защита от Windows-резервов
        if (windowsReserved.contains(base.uppercase(Locale.ROOT))) {
            base = "${base}_file"
        }
        val safeExt = sanitizeExt(extWithDot)

        var file = File(dir, "$base$safeExt")
        var idx = 2
        while (file.exists()) {
            file = File(dir, "$base (${idx})$safeExt")
            idx++
        }
        return file
    }

    // === helpers ===

    /** Санитизация базового имени без расширения. */
    fun sanitizeBase(raw: String, maxLen: Int = 120): String {
        // NFKC нормализация (латинизация НЕ выполняется специально — Unicode ок)
        var s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        // Запрещённые символы → удаляем
        s = s.replace(allowRegex, " ")
        // Точки/пробелы в начале/конце — срезаем
        s = s.trim().trim('.')
        // Конденсация пробелов
        s = s.replace(Regex("\\s+"), " ")
        // Ограничение длины
        if (s.length > maxLen) s = s.substring(0, maxLen).trim().trim('.')
        // Пустышка
        if (s.isBlank()) s = "Scan"
        // Резервы Windows
        if (windowsReserved.contains(s.uppercase(Locale.ROOT))) s = "${s}_file"
        return s
    }

    /** Санитизация расширения (с точкой). */
    fun sanitizeExt(extWithDot: String?): String {
        if (extWithDot.isNullOrBlank()) return ""
        val e = if (extWithDot.startsWith(".")) extWithDot else ".$extWithDot"
        // Убираем пробелы/слэши/управляющие
        return e.replace(Regex("[\\s/\\\\]"), "").lowercase(Locale.ROOT).take(10)
    }
}
