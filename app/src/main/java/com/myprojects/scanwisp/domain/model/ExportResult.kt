package com.myprojects.scanwisp.domain.model

import android.net.Uri
import java.io.File

/**
 * Представляет результат операции экспорта файла.
 *
 * @property uri Безопасный content:// Uri для передачи другим приложениям.
 * @property tempFile Ссылка на временный файл в кэше, который необходимо удалить после использования.
 */
data class ExportResult(
    val uri: Uri,
    val tempFile: File
)