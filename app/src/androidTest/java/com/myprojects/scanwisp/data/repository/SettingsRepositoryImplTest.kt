package com.myprojects.scanwisp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.domain.model.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val TEST_DATASTORE_NAME = "test_settings"

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryImplTest {

    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var dataStore: DataStore<Preferences>
    private val testContext: Context = ApplicationProvider.getApplicationContext()
    private val testCoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @Before
    fun setUp() {
        // Создаем DataStore в тестовом файле, чтобы не затрагивать реальные настройки
        dataStore = PreferenceDataStoreFactory.create(
            scope = testCoroutineScope,
            produceFile = { testContext.preferencesDataStoreFile(TEST_DATASTORE_NAME) }
        )
        // Внедряем этот DataStore в наш репозиторий
        repository = SettingsRepositoryImpl(testContext)
    }

    @After
    fun tearDown() {
        // Удаляем тестовый файл DataStore и отменяем корутины
        File(testContext.filesDir, "datastore/$TEST_DATASTORE_NAME.preferences_pb").delete()
        testCoroutineScope.cancel()
    }

    @Test
    fun saveAndReadPdfExportProfile_returnsCorrectValue() = runTest {
        // Act
        repository.savePdfExportProfile(PdfExportProfile.HIGH)

        // Assert
        val profile = repository.pdfExportProfile.first()
        assertEquals(PdfExportProfile.HIGH, profile)
    }

    @Test
    fun readDefaultPdfExportProfile_returnsBalanced() = runTest {
        // Assert
        val profile = repository.pdfExportProfile.first()
        assertEquals(PdfExportProfile.BALANCED, profile)
    }

    @Test
    fun saveAndReadThemePreference_returnsCorrectValue() = runTest {
        // Act
        repository.saveThemePreference(ThemePreference.GREEN_DARK)

        // Assert
        val theme = repository.themePreference.first()
        assertEquals(ThemePreference.GREEN_DARK, theme)
    }

    @Test
    fun saveAndReadSortBy_returnsCorrectValue() = runTest {
        // Act
        repository.saveSortBy(SortBy.NAME)

        // Assert
        val sortBy = repository.sortBy.first()
        assertEquals(SortBy.NAME, sortBy)
    }

    @Test
    fun saveAndReadSortOrder_returnsCorrectValue() = runTest {
        // Act
        repository.saveSortOrder(SortOrder.ASCENDING)

        // Assert
        val sortOrder = repository.sortOrder.first()
        assertEquals(SortOrder.ASCENDING, sortOrder)
    }

    @Test
    fun setAndReadOnboardingCompleted_returnsCorrectValue() = runTest {
        // Assert initial state
        assertFalse(repository.onboardingCompleted.first())

        // Act
        repository.setOnboardingCompleted(true)

        // Assert final state
        assertTrue(repository.onboardingCompleted.first())
    }

    @Test
    fun saveAndReadViewMode_returnsCorrectValue() = runTest {
        // Assert default
        assertEquals(ViewMode.GRID, repository.viewMode.first())

        // Act
        repository.saveViewMode(ViewMode.LIST)

        // Assert saved value
        assertEquals(ViewMode.LIST, repository.viewMode.first())
    }

    @Test
    fun saveAndReadFitToA4_returnsCorrectValue() = runTest {
        // Assert default
        assertTrue("По умолчанию fitToA4 должно быть true", repository.fitToA4.first())

        // Act
        repository.saveFitToA4(false)

        // Assert saved value
        assertFalse(repository.fitToA4.first())
    }
}