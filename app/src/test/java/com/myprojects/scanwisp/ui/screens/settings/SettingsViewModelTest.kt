package com.myprojects.scanwisp.ui.screens.settings

import app.cash.turbine.test
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.rules.MainDispatcherRule
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel

    // Создаем StateFlow для имитации данных из репозитория
    private val pdfProfileFlow = MutableStateFlow(PdfExportProfile.BALANCED)
    private val themePreferenceFlow = MutableStateFlow(ThemePreference.SYSTEM)
    private val fitToA4Flow = MutableStateFlow(true)

    @Before
    fun setUp() {
        mockSettingsRepository = mockk(relaxUnitFun = true) {
            // Настраиваем мок, чтобы он возвращал наши потоки
            every { pdfExportProfile } returns pdfProfileFlow
            every { themePreference } returns themePreferenceFlow
            every { fitToA4 } returns fitToA4Flow
        }

        viewModel = SettingsViewModel(
            settingsRepository = mockSettingsRepository,
            analytics = mockk(relaxed = true),
            crashlytics = mockk(relaxed = true)
        )
    }

    @Test
    fun `uiState combines flows from repository into Success state`() = runTest {
        viewModel.uiState.test {
            // Пропускаем начальное состояние Loading
            skipItems(1)

            val successState = awaitItem() as SettingsUiState.Success

            assertEquals(PdfExportProfile.BALANCED, successState.settings.pdfExportProfile)
            assertEquals(ThemePreference.SYSTEM, successState.settings.themePreference)
            assertTrue(successState.settings.fitToA4)

            // Имитируем изменение в репозитории
            pdfProfileFlow.value = PdfExportProfile.HIGH

            // Проверяем, что ViewModel отреагировал и обновил состояние
            val updatedState = awaitItem() as SettingsUiState.Success
            assertEquals(PdfExportProfile.HIGH, updatedState.settings.pdfExportProfile)
        }
    }

    @Test
    fun `onProfileSelected calls savePdfExportProfile on repository`() = runTest {
        val newProfile = PdfExportProfile.SMALL
        viewModel.onProfileSelected(newProfile)

        // Проверяем, что был вызван метод сохранения с правильным параметром
        coVerify { mockSettingsRepository.savePdfExportProfile(newProfile) }
    }

    @Test
    fun `onThemeSelected calls saveThemePreference on repository`() = runTest {
        val newTheme = ThemePreference.GREEN_DARK
        viewModel.onThemeSelected(newTheme)

        coVerify { mockSettingsRepository.saveThemePreference(newTheme) }
    }

    @Test
    fun `onFitToA4Changed calls saveFitToA4 on repository`() = runTest {
        viewModel.onFitToA4Changed(false)

        coVerify { mockSettingsRepository.saveFitToA4(false) }

        viewModel.onFitToA4Changed(true)

        coVerify { mockSettingsRepository.saveFitToA4(true) }
    }
}