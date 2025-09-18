package com.myprojects.scanwisp.domain.use_case

import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SplitPagesUseCaseTest {

    private val mockRepository: DocumentRepository = mockk(relaxUnitFun = true)

    private fun createPage(id: String, docId: String): PageEntity {
        return PageEntity(id, docId, 0, "", "", "", 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invoke with zero pages to split throws exception`() = runTest {
        val useCase = SplitPagesUseCase(mockRepository)
        val doc = DocumentEntity("doc1", "Title", 1L, "", null)
        val pages = listOf(createPage("p1", "doc1"), createPage("p2", "doc1"))
        val docWithPages = DocumentWithPages(doc, pages)
        coEvery { mockRepository.getDocumentById("doc1") } returns MutableStateFlow(docWithPages)

        useCase("doc1", emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invoke with all pages to split throws exception`() = runTest {
        val useCase = SplitPagesUseCase(mockRepository)
        val doc = DocumentEntity("doc1", "Title", 1L, "", null)
        val pages = listOf(createPage("p1", "doc1"), createPage("p2", "doc1"))
        val docWithPages = DocumentWithPages(doc, pages)
        coEvery { mockRepository.getDocumentById("doc1") } returns MutableStateFlow(docWithPages)

        useCase("doc1", listOf("p1", "p2"))
    }

    @Test
    fun `invoke calls repository with correct parameters`() = runTest {
        val useCase = SplitPagesUseCase(mockRepository)
        val doc = DocumentEntity("doc1", "My Title", 1L, "", "folder1")
        val pages = listOf(createPage("p1", "doc1"), createPage("p2", "doc1"))
        val docWithPages = DocumentWithPages(doc, pages)
        coEvery { mockRepository.getDocumentById("doc1") } returns MutableStateFlow(docWithPages)

        val pagesToSplit = listOf("p1")
        val resultCount = useCase("doc1", pagesToSplit)

        coVerify {
            mockRepository.splitPagesIntoNewDocuments(
                originalDocumentId = "doc1",
                pageIds = pagesToSplit,
                baseTitle = "My Title",
                folderId = "folder1"
            )
        }

        assert(resultCount == 1)
    }
}