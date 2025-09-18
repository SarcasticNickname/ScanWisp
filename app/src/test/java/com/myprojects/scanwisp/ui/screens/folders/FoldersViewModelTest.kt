package com.myprojects.scanwisp.ui.screens.folders

import app.cash.turbine.test
import com.myprojects.scanwisp.ads.AdPoolManager
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.repository.FakeDocumentRepository
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.rules.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepository: FakeDocumentRepository
    private lateinit var mockRemoteConfig: RemoteConfigRepository
    private lateinit var mockAdPoolManager: AdPoolManager
    private lateinit var viewModel: FoldersViewModel

    private val folder1 = FolderEntity("f1", "Work", 1L)
    private val folder2 = FolderEntity("f2", "Personal", 2L)
    private val folderWithCount1 = FolderWithDocumentCount(folder1, 5)
    private val folderWithCount2 = FolderWithDocumentCount(folder2, 10)
    private val allFoldersWithCount = listOf(folderWithCount1, folderWithCount2)

    @Before
    fun setUp() {
        fakeRepository = FakeDocumentRepository()
        mockRemoteConfig = mockk(relaxed = true) {
            every { isNativeAdEnabled() } returns false // Реклама по умолчанию выключена
        }
        mockAdPoolManager = mockk(relaxed = true)

        viewModel = FoldersViewModel(
            repository = fakeRepository,
            remoteConfigRepository = mockRemoteConfig,
            adPoolManager = mockAdPoolManager,
            analytics = mockk(relaxed = true),
            crashlytics = mockk(relaxed = true)
        )
    }

    @Test
    fun `initial state is Loading then shows folders from repository`() = runTest {
        // Arrange
        // Наполняем фейковый репозиторий данными
        fakeRepository.insertFolders(listOf(folder1, folder2))
        // (Для этого теста нам не нужны документы, т.к. getFoldersWithDocumentCount в фейке вернет 0)

        viewModel.uiState.test {
            assertEquals(FoldersUiState.Loading, awaitItem())

            val successState = awaitItem() as FoldersUiState.Success
            assertEquals(2, successState.items.size)
            assertTrue(successState.items.any { (it as FolderWithDocumentCount).folder.id == "f1" })
        }
    }

    @Test
    fun `when repository has no folders state is Success with empty list`() = runTest {
        viewModel.uiState.test {
            assertEquals(FoldersUiState.Loading, awaitItem())
            val successState = awaitItem() as FoldersUiState.Success
            assertTrue(successState.items.isEmpty())
        }
    }

    @Test
    fun `ads are inserted correctly when enabled`() = runTest {
        // Arrange
        every { mockRemoteConfig.isNativeAdEnabled() } returns true
        every { mockRemoteConfig.getNativeAdStartPosition() } returns 1
        every { mockRemoteConfig.getNativeAdInterval() } returns 1
        val fakeAd = mockk<com.google.android.gms.ads.nativead.NativeAd>()
        every { mockAdPoolManager.getAd() } returns fakeAd

        val folders = (1..3).map { FolderEntity("f$it", "Folder $it", it.toLong()) }
        fakeRepository.insertFolders(folders)

        // Act & Assert
        viewModel.uiState.test {
            skipItems(1) // Пропускаем Loading

            val successState = awaitItem() as FoldersUiState.Success
            assertEquals(6, successState.items.size) // 3 папки + 3 рекламы
            assertTrue(successState.items[1] is com.google.android.gms.ads.nativead.NativeAd)
            assertTrue(successState.items[3] is com.google.android.gms.ads.nativead.NativeAd)
            assertTrue(successState.items[5] is com.google.android.gms.ads.nativead.NativeAd)
        }
    }

    @Test
    fun `dialog visibility is controlled correctly`() = runTest {
        viewModel.isDialogVisible.test {
            assertFalse("Dialog should be hidden initially", awaitItem())

            viewModel.onAddFolderRequest()
            assertTrue("Dialog should be visible after request", awaitItem())

            viewModel.onDialogDismiss()
            assertFalse("Dialog should be hidden after dismiss", awaitItem())
        }
    }

    @Test
    fun `createFolder calls repository and dismisses dialog`() = runTest {
        val folderName = "New Test Folder"
        viewModel.onAddFolderRequest() // Открываем диалог
        viewModel.createFolder(folderName)

        // Проверяем, что репозиторий был вызван
        coVerify { fakeRepository.createFolder(folderName) }

        // Проверяем, что диалог был закрыт
        assertFalse(viewModel.isDialogVisible.value)
    }

    @Test
    fun `createFolder with blank name does not call repository`() = runTest {
        viewModel.createFolder("   ") // Пустое имя

        // Проверяем, что репозиторий НЕ был вызван
        coVerify(exactly = 0) { fakeRepository.createFolder(any()) }
    }
}