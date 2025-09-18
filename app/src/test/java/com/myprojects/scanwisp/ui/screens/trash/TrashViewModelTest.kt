package com.myprojects.scanwisp.ui.screens.trash

import app.cash.turbine.test
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.repository.FakeDocumentRepository
import com.myprojects.scanwisp.rules.MainDispatcherRule
import com.myprojects.scanwisp.ui.events.UiEvent
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepository: FakeDocumentRepository
    private lateinit var viewModel: TrashViewModel

    private val deletedDoc1 = DocumentEntity("doc1", "Deleted 1", 1L, "", null, 1000L)
    private val deletedDoc2 = DocumentEntity("doc2", "Deleted 2", 2L, "", null, 2000L)
    private val activeDoc = DocumentEntity("doc3", "Active", 3L, "", null, null)

    @Before
    fun setUp() {
        fakeRepository = FakeDocumentRepository()
        viewModel = TrashViewModel(
            repository = fakeRepository,
            analytics = mockk(relaxed = true),
            crashlytics = mockk(relaxed = true)
        )
    }

    @Test
    fun `uiState correctly shows only deleted documents`() = runTest {
        fakeRepository.insertDocuments(listOf(deletedDoc1, deletedDoc2, activeDoc))

        viewModel.uiState.test {
            val successState = awaitItem() as TrashUiState.Success
            assertEquals(2, successState.documents.size)
            assertTrue(successState.documents.any { it.id == "doc1" })
            assertTrue(successState.documents.any { it.id == "doc2" })
        }
    }

    @Test
    fun `restoreSelected calls repository and clears selection`() = runTest {
        fakeRepository.insertDocuments(listOf(deletedDoc1, deletedDoc2))
        // viewModel уже инициализирован в setUp

        viewModel.onDocumentLongClick("doc1")
        viewModel.onDocumentClick("doc2")

        viewModel.restoreSelected()

        // Проверяем, что документы были восстановлены
        fakeRepository.getDeletedDocumentRows().test {
            // Получаем список после восстановления
            val docsAfterRestore = awaitItem()
            assertTrue("Список удаленных документов должен быть пуст", docsAfterRestore.isEmpty())
        }

        // Проверяем, что выбор был сброшен
        viewModel.uiState.test {
            val successState = awaitItem() as TrashUiState.Success
            assertTrue("Выбор должен быть сброшен", successState.selectedIds.isEmpty())
        }
    }

    @Test
    fun `deleteSelectedPermanently calls repository and shows snackbar`() = runTest {
        fakeRepository.insertDocuments(listOf(deletedDoc1, deletedDoc2))
        // viewModel уже инициализирован в setUp

        viewModel.onDocumentLongClick("doc1")

        viewModel.deleteSelectedPermanently()

        // Проверяем, что документ удален
        fakeRepository.getDeletedDocumentRows().test {
            val docsAfterDelete = awaitItem()
            assertEquals("Должен остаться один документ", 1, docsAfterDelete.size)
            assertEquals("Оставшийся документ должен быть doc2", "doc2", docsAfterDelete.first().id)
        }

        // Проверяем snackbar
        viewModel.uiEventFlow.test {
            val event = awaitItem() as UiEvent.ShowSnackbar
            assertTrue(event.message.contains("удалены навсегда"))
        }
    }
}