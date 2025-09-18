package com.myprojects.scanwisp.ui.screens.onboarding

import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.rules.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onOnboardingFinished calls setOnboardingCompleted on repository`() = runTest {
        // Arrange
        val mockSettingsRepository = mockk<SettingsRepository>(relaxUnitFun = true)
        val viewModel = OnboardingViewModel(mockSettingsRepository)

        // Act
        viewModel.onOnboardingFinished()

        // Assert
        coVerify { mockSettingsRepository.setOnboardingCompleted(true) }
    }
}