package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.utils.SafeNamePolicy
import javax.inject.Inject

/**
 * Use Case для объединения нескольких документов в один новый.
 */
class MergeDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val safeNamePolicy: SafeNamePolicy
) {
    /**
     * Объединяет документы.
     *
     * @param documentIds Список ID документов для объединения.
     * @param targetFolderId ID папки, в которую будет помещен новый документ (null для корневой директории).
     * @return Название нового созданного документа.
     * @throws IllegalArgumentException если для объединения предоставлено менее двух документов.
     */
    suspend operator fun invoke(documentIds: List<String>, targetFolderId: String?): String {
        if (documentIds.size < 2) {
            throw IllegalArgumentException("Для объединения требуется как минимум два документа.")
        }
        val newTitle = safeNamePolicy.newDocumentTitle()
        documentRepository.mergeDocuments(documentIds, newTitle, targetFolderId)
        return newTitle
    }
}