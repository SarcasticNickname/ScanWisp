package com.myprojects.scanwisp.ui.delegate

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.core.storage.StorageService
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.domain.repository.StringProvider
import com.myprojects.scanwisp.domain.use_case.ExportDocumentUseCase
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.screens.home.ExportAction
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

@ViewModelScoped
class ExportManagerDelegate @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val exportDocumentUseCase: ExportDocumentUseCase,
    @ApplicationContext private val context: Context,
    private val stringProvider: StringProvider,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private val storageService: StorageService,
    private val settingsRepository: SettingsRepository
) {

    data class ShareDialogState(
        val isVisible: Boolean = false,
        val pageCount: Int = 0,
        val defaultName: String = "",
        val action: ExportAction = ExportAction.SHARE,
        val estimatedBytes: Long = 0L
    )

    private val _shareDialogState = MutableStateFlow(ShareDialogState())
    val shareDialogState: StateFlow<ShareDialogState> = _shareDialogState.asStateFlow()

    private var pageIdsToExport: List<String> = emptyList()

    // --- НОВОЕ ПОЛЕ ---
    // Флаг, который показывает, что процесс экспорта уже запущен.
    private val isExporting = MutableStateFlow(false)


    fun onDialogDismiss() {
        _shareDialogState.value = ShareDialogState(isVisible = false)
        // --- ИЗМЕНЕНИЕ ---
        // Не очищаем список, если экспорт уже начался.
        // Очистка произойдет в `finally` блока onConfirmExport.
        if (!isExporting.value) {
            Timber.d("Export dialog dismissed. Clearing pending export pages.")
            pageIdsToExport = emptyList()
        }
    }

    /** Открытие диалога по документам (собираем все страницы). */
    suspend fun requestExportForDocuments(
        documents: List<DocumentRow>,
        defaultName: String,
        action: ExportAction
    ) {
        if (documents.isEmpty()) {
            Timber.w("requestExportForDocuments called with empty list.")
            return
        }

        val documentIds = documents.map { it.id }
        val allPages = mutableListOf<String>()
        for (docId in documentIds) {
            val ids = documentRepository.getDocumentById(docId).first()?.pages?.map { it.id }
                ?: emptyList()
            allPages += ids
        }
        pageIdsToExport = allPages

        Timber.d(
            "Requesting export for ${documentIds.size} documents. Total pages: ${pageIdsToExport.size}. Action: $action"
        )
        if (pageIdsToExport.isEmpty()) {
            Timber.e("No pages found for selected documents: $documentIds")
        }

        _shareDialogState.value = ShareDialogState(
            isVisible = true,
            pageCount = pageIdsToExport.size,
            defaultName = defaultName,
            action = action,
            estimatedBytes = documentRepository.getPagesByIds(pageIdsToExport)
                .sumOf { storageService.sizeOf(it.processedImagePath) }
        )
    }

    /** Открытие диалога по конкретным страницам. */
    suspend fun requestExportForPages(pageIds: List<String>, defaultName: String, action: ExportAction) {
        if (pageIds.isEmpty()) {
            Timber.w("requestExportForPages called with empty pageIds.")
            return
        }
        pageIdsToExport = pageIds
        Timber.d("Requesting export for ${pageIds.size} pages. Action: $action")

        _shareDialogState.value = ShareDialogState(
            isVisible = true,
            pageCount = pageIds.size,
            defaultName = defaultName,
            action = action,
            estimatedBytes = documentRepository.getPagesByIds(pageIds)
                .sumOf { storageService.sizeOf(it.processedImagePath) }
        )
    }

    /** Подтверждение экспорта из диалога. */
    suspend fun onConfirmExport(
        format: ExportFormat,
        filename: String,
        pdfProfile: PdfExportProfile? = null,
        fitToA4: Boolean? = null
    ): UiEvent {
        // --- ИЗМЕНЕНИЕ 1: СОЗДАЕМ НЕИЗМЕНЯЕМУЮ КОПИЮ ---
        // Этот `snapshot` не изменится, даже если `pageIdsToExport` будет очищен где-то еще.
        val pagesToExportSnapshot = pageIdsToExport.toList()
        val action = _shareDialogState.value.action

        // --- ИЗМЕНЕНИЕ 2: УСТАНАВЛИВАЕМ ФЛАГ И СКРЫВАЕМ ДИАЛОГ ---
        isExporting.value = true
        _shareDialogState.value = _shareDialogState.value.copy(isVisible = false)

        if (pagesToExportSnapshot.isEmpty()) {
            isExporting.value = false // Сбрасываем флаг
            return UiEvent.ShowSnackbar("Нет страниц для экспорта", isError = true)
        }

        crashlytics.log("Exporting ${pagesToExportSnapshot.size} pages as ${format.name} for action ${action.name}")

        try {
            // 1) Оценка размера с учётом профиля PDF из настроек
            val pages = documentRepository.getPagesByIds(pagesToExportSnapshot)
            val sumProcessedBytes = pages.sumOf { storageService.sizeOf(it.processedImagePath) }
            val profile = pdfProfile ?: settingsRepository.pdfExportProfile.first()
            val estimatedExportSize = when (format) {
                ExportFormat.JPEG -> sumProcessedBytes
                ExportFormat.ZIP -> (sumProcessedBytes * 1.05).toLong()
                ExportFormat.PDF -> when (profile) {
                    PdfExportProfile.HIGH -> sumProcessedBytes
                    PdfExportProfile.BALANCED -> (sumProcessedBytes * 0.7).toLong()
                    PdfExportProfile.SMALL -> (sumProcessedBytes * 0.3).toLong()
                }
            }.coerceAtLeast(2L * 1024L * 1024L) // ≥ 2MB для надёжности

            // 2) Резервирование места
            val exportDir = storageService.appCacheDir()
            val reservation = storageService.tryReserve(estimatedExportSize, dir = exportDir)

            // Локальная suspend-функция экспорта
            suspend fun doExport(): UiEvent {
                // --- ИЗМЕНЕНИЕ 3: ИСПОЛЬЗУЕМ КОПИЮ ---
                val exportResult = exportDocumentUseCase(
                    pagesToExportSnapshot, filename, format,
                    pdfProfile = pdfProfile,
                    fitToA4Override = fitToA4
                )
                if (exportResult != null) {
                    val mimeType = when (format) {
                        ExportFormat.PDF -> "application/pdf"
                        ExportFormat.JPEG -> "image/jpeg"
                        ExportFormat.ZIP -> "application/zip"
                    }
                    analytics.logEvent(
                        "export_success",
                        bundleOf("format" to format.name.lowercase(Locale.ROOT))
                    )
                    return when (action) {
                        ExportAction.SHARE -> createShareEvent(mimeType, exportResult)
                        ExportAction.SAVE -> createSaveEvent(mimeType, exportResult)
                    }
                } else {
                    throw RuntimeException("Export failed, result is null.")
                }
            }

            // 3) Выполняем экспорт
            if (reservation != null) {
                return try {
                    doExport()
                } finally {
                    reservation.close()
                }
            } else {
                return try {
                    doExport()
                } catch (e: IOException) {
                    if ((e.message ?: "").contains("ENOSPC", ignoreCase = true)) {
                        Timber.e(e, "Export failed: ENOSPC")
                        UiEvent.ShowErrorDialog(AppError.NotEnoughStorageError)
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Export process failed")
            crashlytics.recordException(e)
            return UiEvent.ShowErrorDialog(AppError.ExportError)
        } finally {
            // --- ИЗМЕНЕНИЕ 4: ОЧИСТКА В ЛЮБОМ СЛУЧАЕ ПОСЛЕ ЗАВЕРШЕНИЯ ---
            pageIdsToExport = emptyList()
            isExporting.value = false
        }
    }

    private fun createShareEvent(
        mimeType: String,
        result: com.myprojects.scanwisp.domain.model.ExportResult
    ): UiEvent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, result.tempFile.name, result.uri)
        }
        return UiEvent.LaunchShareIntent(
            Intent.createChooser(shareIntent, context.getString(R.string.action_share)),
            result.tempFile
        )
    }

    private fun createSaveEvent(
        mimeType: String,
        result: com.myprojects.scanwisp.domain.model.ExportResult
    ): UiEvent {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, result.tempFile.name)
        }
        return UiEvent.LaunchSaveIntent(saveIntent, result.tempFile)
    }

    fun cleanUpTempFile(tempFile: File) {
        try {
            if (tempFile.exists()) tempFile.delete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean temp file")
            crashlytics.recordException(e)
        }
    }

    suspend fun handleSaveResult(destinationUri: Uri?, sourceFile: File): UiEvent = withContext(
        Dispatchers.IO
    ) {
        if (destinationUri == null) {
            return@withContext UiEvent.ShowSnackbar("Сохранение отменено")
        }

        try {
            context.contentResolver.openOutputStream(destinationUri, "w")?.use { out ->
                FileInputStream(sourceFile).use { inp ->
                    inp.copyTo(out)
                }
            } ?: return@withContext UiEvent.ShowErrorDialog(AppError.ExportError)

            UiEvent.ShowSnackbar("Файл сохранён")
        } catch (e: IOException) {
            Timber.e(e, "Save to destination failed")
            crashlytics.recordException(e)
            if ((e.message ?: "").contains("ENOSPC", ignoreCase = true)) {
                UiEvent.ShowErrorDialog(AppError.NotEnoughStorageError)
            } else {
                UiEvent.ShowErrorDialog(AppError.ExportError)
            }
        } catch (t: Throwable) {
            Timber.e(t, "Save to destination unexpected failure")
            crashlytics.recordException(t)
            UiEvent.ShowErrorDialog(AppError.ExportError)
        }
    }
}