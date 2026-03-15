package com.myprojects.scanwisp.domain.use_case

import android.content.Context
import androidx.core.content.FileProvider
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.model.ExportResult
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.utils.JpegExportService
import com.myprojects.scanwisp.utils.PdfExportService
import com.myprojects.scanwisp.utils.SafeNamePolicy
import com.myprojects.scanwisp.utils.ZipExportService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Use Case (Interactor) для выполнения бизнес-логики экспорта документа.
 * Инкапсулирует всю логику, связанную с подготовкой и созданием файла для экспорта.
 */
class ExportDocumentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
    private val zipExportService: ZipExportService,
    private val jpegExportService: JpegExportService,
    private val safeNamePolicy: SafeNamePolicy
) {
    /**
     * Выполняет экспорт указанных страниц в файл заданного формата.
     *
     * @param pageIds Список ID страниц для экспорта.
     * @param displayName Имя файла для экспорта (без расширения).
     * @param format Желаемый формат экспорта [ExportFormat].
     * @return [ExportResult], содержащий Uri и временный файл, или null в случае ошибки.
     */
    suspend operator fun invoke(
        pageIds: List<String>, displayName: String, format: ExportFormat
    ): ExportResult? {
        if (pageIds.isEmpty()) {
            Timber.w("Cannot export with empty page list.")
            return null
        }

        // 1. Получаем сущности страниц из репозитория
        val pagesToExport = documentRepository.getPagesByIds(pageIds)
        val pagesMap = pagesToExport.associateBy { it.id }
        val imagePaths = pageIds.mapNotNull { id -> pagesMap[id]?.processedImagePath }

        if (imagePaths.size != pageIds.size) {
            Timber.e("Some pages not found in DB for export.")
            return null
        }

        // 2. Нормализуем базовое имя единообразно для всех форматов
        val baseName = safeNamePolicy.exportBaseNameFromTitle(displayName, pageCount = imagePaths.size)

        // 3. В зависимости от формата, вызываем соответствующий сервис
        val tempFile = when (format) {

            ExportFormat.PDF -> {
                val exportProfile = settingsRepository.pdfExportProfile.first()
                val fitToA4 = settingsRepository.fitToA4.first()
                pdfExportService.exportToPdf(
                    pages   = pagesToExport,
                    title   = baseName,
                    profile = exportProfile,
                    fitToA4 = fitToA4
                )
            }

            ExportFormat.JPEG -> {
                if (imagePaths.size > 1) {
                    Timber.w(
                        "JPEG export is only supported for a single page."
                    )
                    return null
                }
                jpegExportService.copyToCache(imagePaths.first(), baseName)
            }

            ExportFormat.ZIP -> {
                zipExportService.createZipArchive(imagePaths, baseName)
            }
        }

        // 4. Если файл был успешно создан, преобразуем его в ExportResult.
        return tempFile?.let { file ->
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            ExportResult(uri, file)
        }
    }
}