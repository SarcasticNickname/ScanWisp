package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.utils.SafeNamePolicy
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeDocumentsUseCaseTest {

    @Test(expected = IllegalArgumentException::class)
    fun `invoke with less than two documents throws IllegalArgumentException`() = runTest {
        // Arrange
        val mockRepository = mockk<DocumentRepository>()
        val mockNamePolicy = mockk<SafeNamePolicy>()
        val mergeDocumentsUseCase = MergeDocumentsUseCase(mockRepository, mockNamePolicy)

        // Act & Assert
        mergeDocumentsUseCase(listOf("doc1"), null)
    }

    @Test
    fun `invoke calls mergeDocuments on repository with correct parameters`() = runTest {
        // Arrange
        val mockRepository = mockk<DocumentRepository>(relaxed = true)
        val mockNamePolicy = mockk<SafeNamePolicy> {
            every { newDocumentTitle() } returns "Merged Document"
        }
        val mergeDocumentsUseCase = MergeDocumentsUseCase(mockRepository, mockNamePolicy)
        val documentIds = listOf("doc1", "doc2")
        val targetFolderId = "folder1"

        // Act
        val resultTitle = mergeDocumentsUseCase(documentIds, targetFolderId)

        // Assert
        assertEquals("Merged Document", resultTitle)
        coVerify {
            mockRepository.mergeDocuments(
                documentIds = documentIds,
                newTitle = "Merged Document",
                folderId = targetFolderId
            )
        }
    }
}