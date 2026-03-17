package com.myprojects.scanwisp.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.ocr.OcrService
import com.myprojects.scanwisp.domain.model.OcrLanguage
import com.myprojects.scanwisp.domain.model.OcrMode
import com.myprojects.scanwisp.domain.model.OcrStatus
import com.myprojects.scanwisp.domain.model.WordBox
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentDao: DocumentDao,
    private val ocrService: OcrService,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DOCUMENT_ID    = "document_id"
        const val KEY_DOCUMENT_TITLE = "document_title"
        const val KEY_OCR_MODE       = "ocr_mode"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_DOCUMENT_TITLE) ?: ""
        return ForegroundInfo(
            OCR_NOTIFICATION_ID,
            OcrNotificationHelper.buildProgressNotification(context, title, 0, 1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result {
        val documentId    = inputData.getString(KEY_DOCUMENT_ID)    ?: return Result.failure()
        val documentTitle = inputData.getString(KEY_DOCUMENT_TITLE) ?: ""

        val pages = documentDao.getPendingPagesForDocument(documentId)
        if (pages.isEmpty()) return Result.success()

        val totalPages = pages.size

        val ocrMode = inputData.getString(KEY_OCR_MODE)?.let { name ->
            runCatching { OcrMode.valueOf(name) }.getOrNull()
        } ?: runCatching {
            settingsRepository.defaultOcrMode.first()
        }.getOrDefault(OcrMode.FAST)

        val ocrLanguage = runCatching {
            settingsRepository.defaultOcrLanguage.first()
        }.getOrDefault(OcrLanguage.RUSSIAN_ENGLISH)

        pages.forEachIndexed { index, page ->
            val currentPage = index + 1
            setForeground(
                ForegroundInfo(
                    OCR_NOTIFICATION_ID,
                    OcrNotificationHelper.buildProgressNotification(
                        context, documentTitle, currentPage, totalPages
                    ),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            )

            documentDao.updatePageOcrStatus(page.id, OcrStatus.IN_PROGRESS)

            try {
                val result = ocrService.recognizeText(
                    imagePath = page.processedImagePath,
                    mode      = ocrMode.tessDir,
                    lang      = ocrLanguage.tessLang
                )

                val plainText = result.plainText.ifBlank { null }
                documentDao.updatePage(
                    page.copy(
                        extractedText    = plainText,
                        wordBoxesJson    = if (result.wordBoxes.isNotEmpty())
                            WordBox.toJson(result.wordBoxes) else null,
                        isTextUserEdited = false,
                        ocrStatus        = OcrStatus.DONE
                    )
                )

                if (!plainText.isNullOrBlank()) {
                    documentDao.upsertFtsPageEntry(
                        pageId          = page.id,
                        documentOwnerId = page.documentOwnerId,
                        pageNumber      = page.pageNumber,
                        text            = plainText
                    )
                } else {
                    documentDao.deleteFtsPageEntry(page.id)
                }

            } catch (e: Exception) {
                Timber.e(e, "OCR failed for page ${page.id}")
                documentDao.updatePageOcrStatus(page.id, OcrStatus.FAILED)
            }
        }

        val notifManager = context.getSystemService(android.app.NotificationManager::class.java)
        notifManager.notify(
            OCR_NOTIFICATION_ID + 1,
            OcrNotificationHelper.buildDoneNotification(context, documentTitle, totalPages)
        )

        return Result.success()
    }
}