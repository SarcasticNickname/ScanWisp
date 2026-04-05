package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.domain.model.EditableWord
import com.myprojects.scanwisp.domain.model.OcrStatus
import com.myprojects.scanwisp.domain.model.WordBoxReconciler
import com.myprojects.scanwisp.domain.model.toExtractedText
import com.myprojects.scanwisp.domain.model.toWordBoxJson
import javax.inject.Inject

class SaveEditedTextUseCase @Inject constructor(
    private val documentDao: DocumentDao
) {
    /**
     * Путь А — токенный редактор.
     * Позиции слов сохраняются неизменными, обновляется только текст.
     * wordBoxesJson полностью корректен после сохранения.
     */
    suspend fun invokeFromTokens(pageId: String, editedWords: List<EditableWord>) {
        val page = documentDao.getPageByIdOnce(pageId) ?: return
        val newText = editedWords.toExtractedText()
        val newBoxJson = editedWords.toWordBoxJson()

        documentDao.updatePage(
            page.copy(
                extractedText = newText,
                wordBoxesJson = newBoxJson,
                isTextUserEdited = true,
                ocrStatus = OcrStatus.DONE
            )
        )
        updateFts(page.id, page.documentOwnerId, page.pageNumber, newText)
    }

    /**
     * Путь Б — свободный редактор.
     * Запускает reconciliation: пытается сохранить позиции через LCS diff.
     * В худшем случае позиции приближённые, но никогда не null.
     */
    suspend operator fun invoke(pageId: String, newText: String) {
        val page = documentDao.getPageByIdOnce(pageId) ?: return

        // Загружаем исходные боксы для reconciliation
        val originalWords = page.wordBoxesJson
            ?.runCatching { EditableWord.fromJson(this) }
            ?.getOrNull()
            ?: emptyList()

        val reconciledWords = WordBoxReconciler.reconcile(originalWords, newText)

        val finalText = if (reconciledWords.isNotEmpty()) newText else ""
        val finalBoxJson = if (reconciledWords.isNotEmpty()) {
            reconciledWords.toWordBoxJson()
        } else {
            page.wordBoxesJson  // Пустой ввод — оставляем старые боксы нетронутыми
        }

        documentDao.updatePage(
            page.copy(
                extractedText = finalText,
                wordBoxesJson = finalBoxJson,
                isTextUserEdited = true,
                ocrStatus = OcrStatus.DONE
            )
        )
        updateFts(page.id, page.documentOwnerId, page.pageNumber, finalText)
    }

    private suspend fun updateFts(
        pageId: String,
        documentOwnerId: String,
        pageNumber: Int,
        text: String
    ) {
        if (text.isNotBlank()) {
            documentDao.upsertFtsPageEntry(
                pageId = pageId,
                documentOwnerId = documentOwnerId,
                pageNumber = pageNumber,
                text = text
            )
        } else {
            documentDao.deleteFtsPageEntry(pageId)
        }
    }
}