package com.myprojects.scanwisp.ui.screens.preview

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.rules.MainDispatcherRule
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.state.LoadingState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockRepository: DocumentRepository
    private lateinit var viewModel: PreviewViewModel

    private val testPage =
        PageEntity("p1", "doc1", 1, "source.jpg", "processed.jpg", "thumb.jpg", 0L)

    @Before
    fun setUp() {
        // Здесь нам не нужен наш большой FakeDocumentRepository,
        // так как логика простая. Достаточно обычного мока.
        mockRepository = mockk(relaxUnitFun = true)
    }

    private fun createViewModel(pageId: String): PreviewViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("pageId" to pageId))
        return PreviewViewModel(
            repository = mockRepository,
            savedStateHandle = savedStateHandle,
            stringProvider = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
            crashlytics = mockk(relaxed = true),
            storageService = mockk(relaxed = true) {
                // Имитируем, что место всегда есть
                every { tryReserve(any(), any(), any()) } returns mockk()
            }
        )
    }

    @Test
    fun `init loads page and emits Success state`() = runTest {
        // Arrange
        val pageFlow = MutableStateFlow<PageEntity?>(testPage)
        every { mockRepository.getPageById("p1") } returns pageFlow

        viewModel = createViewModel("p1")

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(PreviewUiState.Loading, awaitItem())

            val successState = awaitItem() as PreviewUiState.Success
            assertEquals("p1", successState.page.id)
            assertEquals("processed.jpg", successState.page.processedImagePath)
        }
    }

    @Test
    fun `when page is not found emits Error state`() = runTest {
        // Arrange
        val pageFlow = MutableStateFlow<PageEntity?>(null)
        every { mockRepository.getPageById("p1") } returns pageFlow

        viewModel = createViewModel("p1")

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(PreviewUiState.Loading, awaitItem())
            assertTrue(awaitItem() is PreviewUiState.Error)
        }
    }

    @Test
    fun `replaceImage calls repository and shows loading state`() = runTest {
        // Arrange
        val newImageUri = mockk<Uri>()
        // Нам нужна ViewModel, чтобы проверить состояние загрузки
        every { mockRepository.getPageById(any()) } returns MutableStateFlow(testPage)
        viewModel = createViewModel("p1")

        // Act & Assert
        viewModel.loadingState.test {
            assertEquals(LoadingState(isBusy = false), awaitItem())

            viewModel.replaceImage(newImageUri)

            // Проверяем, что появился индикатор загрузки
            val busyState = awaitItem()
            assertTrue(busyState.isBusy)
            assertEquals("Замена изображения...", busyState.message)

            // Проверяем, что был вызван метод репозитория
            coVerify { mockRepository.replacePageImage("p1", newImageUri) }

            // Проверяем, что индикатор загрузки исчез
            val finalState = awaitItem()
            assertEquals(finalState.isBusy, false)
        }
    }

    @Test
    fun `replaceImage shows NotEnoughStorageError if storage is not available`() = runTest {
        // Arrange
        val newImageUri = mockk<Uri>()
        val mockStorageService = mockk<com.myprojects.scanwisp.core.storage.StorageService> {
            every { estimateForPages(any()) } returns 100L
            every { appFilesDir() } returns mockk()
            // Имитируем, что места нет
            every { tryReserve(any(), any(), any()) } returns null
        }

        val viewModel = PreviewViewModel(
            repository = mockRepository,
            savedStateHandle = SavedStateHandle(mapOf("pageId" to "p1")),
            stringProvider = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
            crashlytics = mockk(relaxed = true),
            storageService = mockStorageService
        )

        // Act & Assert
        viewModel.uiEventFlow.test {
            viewModel.replaceImage(newImageUri)

            val event = awaitItem() as UiEvent.ShowErrorDialog
            assertTrue(event.error is AppError.NotEnoughStorageError)
        }
    }
}