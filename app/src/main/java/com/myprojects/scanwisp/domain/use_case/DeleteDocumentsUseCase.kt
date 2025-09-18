package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.data.local.DocumentDao
import javax.inject.Inject

/**
 * Use Case для управления "мягким" удалением документов.
 * Инкапсулирует логику маркировки документов для удаления и отмены этого действия.
 */
class DeleteDocumentsUseCase @Inject constructor(
    // Используем DAO, так как это специфичная операция soft-delete,
    // которая не требует полной абстракции репозитория.
    private val documentDao: DocumentDao
) {
    /**
     * Помечает список документов как удаленные, устанавливая временную метку.
     * @param documentIds Список ID документов для "мягкого" удаления.
     */
    suspend operator fun invoke(documentIds: List<String>) {
        if (documentIds.isEmpty()) return
        documentDao.markDocumentsForDeletion(documentIds, System.currentTimeMillis())
    }

    /**
     * Отменяет "мягкое" удаление для списка документов, сбрасывая временную метку.
     * @param documentIds Список ID документов для восстановления.
     */
    suspend fun undo(documentIds: List<String>) {
        if (documentIds.isEmpty()) return
        documentDao.unmarkDocumentsForDeletion(documentIds)
    }
}