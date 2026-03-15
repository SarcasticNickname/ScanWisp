package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.data.local.DocumentDao
import javax.inject.Inject

class SaveEditedTextUseCase @Inject constructor(
    private val documentDao: DocumentDao
) {
    suspend operator fun invoke(pageId: String, newText: String) {
        val page = documentDao.getPageByIdOnce(pageId) ?: return
        documentDao.updatePage(
            page.copy(
                extractedText = newText,
                isTextUserEdited = true
            )
        )
        if (newText.isNotBlank()) {
            documentDao.upsertFtsPageEntry(
                pageId = page.id,
                documentOwnerId = page.documentOwnerId,
                pageNumber = page.pageNumber,
                text = newText
            )
        } else {
            documentDao.deleteFtsPageEntry(page.id)
        }
    }
}