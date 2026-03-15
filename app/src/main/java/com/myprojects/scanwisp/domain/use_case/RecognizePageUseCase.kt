package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.ocr.OcrService
import com.myprojects.scanwisp.domain.model.OcrLanguage
import com.myprojects.scanwisp.domain.model.OcrMode
import com.myprojects.scanwisp.domain.model.WordBox
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RecognizePageUseCase @Inject constructor(
    private val ocrService: OcrService,
    private val documentDao: DocumentDao,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        pageId: String,
        mode: OcrMode = OcrMode.FAST,
        forceRerun: Boolean = false
    ): String {
        val page = documentDao.getPageByIdOnce(pageId)
            ?: throw IllegalArgumentException("Page not found: $pageId")

        if (!forceRerun && !page.extractedText.isNullOrBlank()) {
            return page.extractedText
        }

        val lang = runCatching {
            settingsRepository.defaultOcrLanguage.first()
        }.getOrDefault(OcrLanguage.RUSSIAN_ENGLISH)

        val result = ocrService.recognizeText(
            imagePath = page.processedImagePath,
            mode = mode.tessDir,
            lang = lang.tessLang
        )

        if (result.plainText.isNotBlank()) {
            documentDao.updatePage(
                page.copy(
                    extractedText = result.plainText,
                    wordBoxesJson = if (result.wordBoxes.isNotEmpty())
                        WordBox.toJson(result.wordBoxes) else null,
                    isTextUserEdited = false
                )
            )
            documentDao.upsertFtsPageEntry(
                pageId = page.id,
                documentOwnerId = page.documentOwnerId,
                pageNumber = page.pageNumber,
                text = result.plainText
            )
        }

        return result.plainText
    }
}