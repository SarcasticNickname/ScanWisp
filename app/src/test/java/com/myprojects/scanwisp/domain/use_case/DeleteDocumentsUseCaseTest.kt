package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.data.local.DocumentDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeleteDocumentsUseCaseTest {

    @Test
    fun `invoke calls markDocumentsForDeletion on dao`() = runTest {
        // Arrange (Подготовка)
        val mockDao = mockk<DocumentDao>(relaxed = true)
        val deleteDocumentsUseCase = DeleteDocumentsUseCase(mockDao)
        val documentIds = listOf("doc1", "doc2")

        // Act (Действие)
        deleteDocumentsUseCase(documentIds)

        // Assert (Проверка)
        // coVerify используется для проверки вызова suspend-функций
        coVerify { mockDao.markDocumentsForDeletion(documentIds, any()) }
    }

    @Test
    fun `undo calls unmarkDocumentsForDeletion on dao`() = runTest {
        // Arrange
        val mockDao = mockk<DocumentDao>(relaxed = true)
        val deleteDocumentsUseCase = DeleteDocumentsUseCase(mockDao)
        val documentIds = listOf("doc1", "doc2")

        // Act
        deleteDocumentsUseCase.undo(documentIds)

        // Assert
        coVerify { mockDao.unmarkDocumentsForDeletion(documentIds) }
    }

    @Test
    fun `invoke with empty list does not call dao`() = runTest {
        // Arrange
        val mockDao = mockk<DocumentDao>(relaxed = true)
        val deleteDocumentsUseCase = DeleteDocumentsUseCase(mockDao)

        // Act
        deleteDocumentsUseCase(emptyList())

        // Assert
        // Проверяем, что метод НЕ был вызван
        coVerify(exactly = 0) { mockDao.markDocumentsForDeletion(any(), any()) }
    }
}