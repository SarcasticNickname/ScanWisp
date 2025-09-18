package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use Case для разделения страниц из одного документа в несколько новых.
 */
class SplitPagesUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    /**
     * Разделяет указанные страницы из исходного документа в новые, отдельные документы.
     *
     * @param originalDocumentId ID документа, из которого разделяются страницы.
     * @param pageIdsToSplit Список ID страниц для разделения.
     * @return Количество созданных новых документов.
     * @throws IllegalStateException если исходный документ не найден.
     * @throws IllegalArgumentException если количество страниц для разделения некорректно.
     */
    suspend operator fun invoke(originalDocumentId: String, pageIdsToSplit: List<String>): Int {
        val originalDocumentWithPages =
            documentRepository.getDocumentById(originalDocumentId).first()
                ?: throw IllegalStateException("Original document not found for splitting.")

        val totalPages = originalDocumentWithPages.pages.size
        val splitCount = pageIdsToSplit.size

        if (splitCount == 0 || splitCount >= totalPages) {
            throw IllegalArgumentException("Выберите от 1 до ${totalPages - 1} страниц для разделения.")
        }

        val baseTitle = originalDocumentWithPages.document.title
        val folderId = originalDocumentWithPages.document.folderId

        documentRepository.splitPagesIntoNewDocuments(
            originalDocumentId = originalDocumentId,
            pageIds = pageIdsToSplit,
            baseTitle = baseTitle,
            folderId = folderId
        )

        return splitCount
    }
}