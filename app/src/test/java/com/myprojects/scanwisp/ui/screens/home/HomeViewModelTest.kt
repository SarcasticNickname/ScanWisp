package com.myprojects.scanwisp.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.ads.AdPoolManager
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.repository.FakeDocumentRepository
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ViewMode
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.domain.repository.StringProvider
import com.myprojects.scanwisp.domain.use_case.DeleteDocumentsUseCase
import com.myprojects.scanwisp.rules.MainDispatcherRule
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.state.HomeScreenUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeDocumentRepository: FakeDocumentRepository
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockDeleteDocumentsUseCase: DeleteDocumentsUseCase
    private lateinit var mockRemoteConfig: RemoteConfigRepository
    private lateinit var mockAdPoolManager: AdPoolManager
    private lateinit var mockStringProvider: StringProvider
    private lateinit var viewModel: HomeViewModel

    // ИСПРАВЛЕНИЕ: Теперь используем DocumentEntity для тестовых данных
    private val doc1 = DocumentEntity("1", "Banana", 100L, "", null)
    private val doc2 = DocumentEntity("2", "Apple", 200L, "", null)
    private val doc3 = DocumentEntity("3", "Cherry", 300L, "", null)
    private val allDocs = listOf(doc1, doc2, doc3)

    private val sortByFlow = MutableStateFlow(SortBy.DATE)
    private val sortOrderFlow = MutableStateFlow(SortOrder.DESCENDING)
    private val viewModeFlow = MutableStateFlow(ViewMode.GRID)

    @Before
    fun setUp() {
        fakeDocumentRepository = FakeDocumentRepository()
        mockDeleteDocumentsUseCase = mockk(relaxUnitFun = true)
        coEvery { mockDeleteDocumentsUseCase.undo(any()) } returns Unit

        mockSettingsRepository = mockk(relaxUnitFun = true) {
            every { sortBy } returns sortByFlow
            every { sortOrder } returns sortOrderFlow
            every { viewMode } returns viewModeFlow
        }

        mockRemoteConfig = mockk(relaxed = true) {
            every { isNativeAdEnabled() } returns false
        }

        mockAdPoolManager = mockk(relaxed = true)

        mockStringProvider = mockk {
            every { getString(R.string.action_cancel) } returns "Отмена"
            every { getString(R.string.home_screen_title_default) } returns "Мои документы"
        }
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): HomeViewModel {
        return HomeViewModel(
            documentRepository = fakeDocumentRepository,
            settingsRepository = mockSettingsRepository,
            deleteDocumentsUseCase = mockDeleteDocumentsUseCase,
            remoteConfigRepository = mockRemoteConfig,
            stringProvider = mockStringProvider,
            mergeDocumentsUseCase = mockk(relaxed = true),
            exportManager = mockk(relaxed = true),
            adPoolManager = mockAdPoolManager,
            savedStateHandle = savedStateHandle,
            safeNamePolicy = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
            crashlytics = mockk(relaxed = true),
            storageService = mockk(relaxed = true)
        )
    }

    @Test
    fun `initial state is Loading then shows documents sorted by date descending`() = runTest {
        viewModel = createViewModel()
        fakeDocumentRepository.insertDocuments(allDocs)

        viewModel.uiState.test {
            assertEquals(HomeScreenUiState.Loading, awaitItem())

            val successState = awaitItem() as HomeScreenUiState.Data

            assertEquals(3, successState.items.size)
            assertEquals("3", (successState.items[0] as DocumentRow).id) // Cherry (300L)
            assertEquals("2", (successState.items[1] as DocumentRow).id) // Apple (200L)
            assertEquals("1", (successState.items[2] as DocumentRow).id) // Banana (100L)
        }
    }

    @Test
    fun `onDocumentLongClick selects the document and enters selection mode`() = runTest {
        viewModel = createViewModel()
        fakeDocumentRepository.insertDocuments(allDocs)

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // Initial data

            viewModel.onDocumentLongClick("2")

            val selectionState = awaitItem() as HomeScreenUiState.Data
            assertTrue(selectionState.isSelectionModeActive)
            assertEquals(setOf("2"), selectionState.selectedDocumentIds)
        }
    }

    @Test
    fun `deleteSelectedDocuments calls use case and sends snackbar event`() = runTest {
        viewModel = createViewModel()
        fakeDocumentRepository.insertDocuments(allDocs)

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // Initial data

            viewModel.onDocumentLongClick("1")
            viewModel.onDocumentClick("3")

            awaitItem() // Selection 1
            awaitItem() // Selection 2

            viewModel.deleteSelectedDocuments()

            coVerify {
                mockDeleteDocumentsUseCase(match { it.toSet() == setOf("1", "3") })
            }
        }

        viewModel.uiEventFlow.test {
            val event = awaitItem() as UiEvent.ShowSnackbar
            assertTrue(event.message.contains("удалены"))
            assertEquals("Отмена", event.actionLabel)
        }
    }

    @Test
    fun `undoDelete calls use case undo method`() = runTest {
        viewModel = createViewModel()

        // Сначала нужно что-то "удалить", чтобы было что отменять
        viewModel.deleteDocument("doc1")
        runCurrent()

        // Теперь отменяем
        viewModel.undoDelete()

        coVerify { mockDeleteDocumentsUseCase.undo(any()) }
    }

    @Test
    fun `insertAdsToList works correctly when ads are enabled`() = runTest {
        every { mockRemoteConfig.isNativeAdEnabled() } returns true
        every { mockRemoteConfig.getNativeAdStartPosition() } returns 2
        every { mockRemoteConfig.getNativeAdInterval() } returns 3
        val fakeAd = mockk<com.google.android.gms.ads.nativead.NativeAd>()
        every { mockAdPoolManager.getAd() } returns fakeAd andThen null

        viewModel = createViewModel()
        val docs = (1..6).map { DocumentEntity(it.toString(), "Doc $it", it.toLong(), "", null) }

        // Мы вызываем метод напрямую, так как он public
        val result = viewModel.insertAdsIntoList(
            docs.map {
                DocumentRow(
                    it.id,
                    it.title,
                    it.coverImagePath,
                    it.creationTimestamp,
                    1,
                    it.folderId
                )
            }
        )

        assertEquals(7, result.size) // 6 доков + 1 реклама
        assertTrue(result[2] is com.google.android.gms.ads.nativead.NativeAd)
    }

    @Test
    fun `insertAdsToList does nothing when ads are disabled`() = runTest {
        every { mockRemoteConfig.isNativeAdEnabled() } returns false
        viewModel = createViewModel()
        val docs = (1..6).map { DocumentEntity(it.toString(), "Doc $it", it.toLong(), "", null) }

        val result = viewModel.insertAdsIntoList(
            docs.map {
                DocumentRow(
                    it.id,
                    it.title,
                    it.coverImagePath,
                    it.creationTimestamp,
                    1,
                    it.folderId
                )
            }
        )

        assertEquals(6, result.size)
        assertTrue(result.none { it !is DocumentRow })
    }

    @Test
    fun `when folderId is provided screenTitle is folder name`() = runTest {
        coEvery { fakeDocumentRepository.getFolderById("folder1") } returns FolderEntity(
            "folder1",
            "Work Docs",
            1L
        )
        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "folder1"))
        viewModel = createViewModel(savedStateHandle)

        viewModel.uiState.test {
            assertEquals(HomeScreenUiState.Loading, awaitItem())
            val dataState = awaitItem() as HomeScreenUiState.Data
            assertEquals("Work Docs", dataState.screenTitle)
        }
    }

    @Test
    fun `search query filters the document list`() = runTest {
        viewModel = createViewModel()
        fakeDocumentRepository.insertDocuments(allDocs)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onSearchQueryChanged("App")
            advanceTimeBy(301)

            val filteredState = awaitItem() as HomeScreenUiState.Data
            assertEquals(1, filteredState.items.size)
            assertEquals("2", (filteredState.items.first() as DocumentRow).id)

            viewModel.onSearchQueryChanged("")
            advanceTimeBy(301)

            val unfilteredState = awaitItem() as HomeScreenUiState.Data
            assertEquals(3, unfilteredState.items.size)
        }
    }

    @Test
    fun `changing sort settings updates document order`() = runTest {
        viewModel = createViewModel()
        fakeDocumentRepository.insertDocuments(allDocs)

        viewModel.uiState.test {
            skipItems(2)

            sortByFlow.value = SortBy.NAME
            sortOrderFlow.value = SortOrder.ASCENDING

            val sortedByNameState = awaitItem() as HomeScreenUiState.Data
            assertEquals("2", (sortedByNameState.items[0] as DocumentRow).id) // Apple
            assertEquals("1", (sortedByNameState.items[1] as DocumentRow).id) // Banana
            assertEquals("3", (sortedByNameState.items[2] as DocumentRow).id) // Cherry
        }
    }

    @Test
    fun `viewModel restores search query and selection from SavedStateHandle`() = runTest {
        val savedState = SavedStateHandle(
            mapOf(
                "homeSearchQuery" to "Banana",
                "homeSelectedIds" to setOf("1")
            )
        )

        viewModel = createViewModel(savedState)
        fakeDocumentRepository.insertDocuments(allDocs)

        viewModel.uiState.test {
            skipItems(1)

            val restoredState = awaitItem() as HomeScreenUiState.Data

            assertEquals("Banana", restoredState.searchQuery)
            assertEquals(1, restoredState.items.size)
            assertEquals("1", (restoredState.items.first() as DocumentRow).id)

            assertEquals(setOf("1"), restoredState.selectedDocumentIds)
            assertTrue(restoredState.isSelectionModeActive)
        }
    }
}