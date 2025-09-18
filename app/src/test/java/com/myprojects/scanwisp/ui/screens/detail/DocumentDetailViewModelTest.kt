package com.myprojects.scanwisp.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.StringProvider
import com.myprojects.scanwisp.rules.MainDispatcherRule
import com.myprojects.scanwisp.ui.events.UiEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Объявляем моки на уровне класса, чтобы они были доступны во всех тестах
    private val mockRepository: DocumentRepository = mockk(relaxUnitFun = true)
    private val mockStringProvider: StringProvider = mockk(relaxed = true)

    private val doc = DocumentEntity("doc1", "Test Doc", 1L, "", null)
    private val page1 = PageEntity("p1", "doc1", 1, "", "", "", 0L)
    private val page2 = PageEntity("p2", "doc1", 2, "", "", "", 1L)
    private val docWithPages = DocumentWithPages(doc, listOf(page1, page2))

    private fun createViewModel(documentId: String = "doc1"): DocumentDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("documentId" to documentId))
        return DocumentDetailViewModel(
            repository = mockRepository,
            savedStateHandle = savedStateHandle,
            settingsRepository = mockk(relaxed = true),
            splitPagesUseCase = mockk(relaxed = true),
            exportManager = mockk(relaxed = true),
            stringProvider = mockStringProvider, // Используем объявленный мок
            analytics = mockk(relaxed = true),
            crashlytics = mockk(relaxed = true),
            storageService = mockk(relaxed = true)
        )
    }

    @Test
    fun `init loads document and emits Success state`() = runTest {
        coEvery { mockRepository.getDocumentById("doc1") } returns MutableStateFlow(docWithPages)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val successState = awaitItem() as DocumentDetailUiState.Success
            assertEquals("doc1", successState.documentWithPages.document.id)
            assertEquals(2, successState.documentWithPages.pages.size)
        }
    }

    @Test
    fun `onPageLongClick and onPageClick correctly update selection`() = runTest {
        coEvery { mockRepository.getDocumentById("doc1") } returns MutableStateFlow(docWithPages)
        val viewModel = createViewModel()

        viewModel.selectedPageIds.test {
            assertEquals(emptySet<String>(), awaitItem())

            viewModel.onPageLongClick("p1")
            assertEquals(setOf("p1"), awaitItem())

            viewModel.onPageClick("p2")
            assertEquals(setOf("p1", "p2"), awaitItem())

            viewModel.onPageClick("p1")
            assertEquals(setOf("p2"), awaitItem())
        }
    }

    @Test
    fun `deleteSelectedPages hides pages and shows snackbar with undo`() = runTest {
        // Arrange
        coEvery { mockRepository.getDocumentById("doc1") } returns MutableStateFlow(docWithPages)
        // Настраиваем мок StringProvider, чтобы он возвращал конкретную строку для любого ID
        every { mockStringProvider.getString(any()) } returns "Отмена"

        val viewModel = createViewModel()
        viewModel.onPageLongClick("p1") // Выбираем страницу для удаления

        viewModel.uiState.test {
            // Пропускаем начальное состояние
            awaitItem()

            viewModel.deleteSelectedPages()

            // Страница должна временно исчезнуть из UI
            val stateAfterDelete = awaitItem() as DocumentDetailUiState.Success
            assertEquals(1, stateAfterDelete.documentWithPages.pages.size)
            assertEquals("p2", stateAfterDelete.documentWithPages.pages.first().id)

            // Проверяем, что пришло событие Snackbar
            viewModel.uiEventFlow.test {
                val event = awaitItem() as UiEvent.ShowSnackbar
                assertTrue(event.message.contains("Страницы удалены"))
                assertEquals("Отмена", event.actionLabel)
            }
        }
    }

}