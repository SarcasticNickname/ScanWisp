package com.myprojects.scanwisp

import app.cash.turbine.test
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockRemoteConfigRepository: RemoteConfigRepository
    private lateinit var viewModel: MainViewModel

    @Test
    fun `init calls fetchAndActivate on remote config repository`() {
        // Arrange
        mockSettingsRepository = mockk {
            every { onboardingCompleted } returns MutableStateFlow(true)
            every { themePreference } returns MutableStateFlow(mockk())
        }
        mockRemoteConfigRepository = mockk(relaxUnitFun = true)

        // Act
        viewModel = MainViewModel(mockSettingsRepository, mockRemoteConfigRepository)

        // Assert
        verify(exactly = 1) { mockRemoteConfigRepository.fetchAndActivate() }
    }

    @Test
    fun `onboardingCheckState emits Completed when repository returns true`() = runTest {
        // Arrange
        val onboardingFlow = MutableStateFlow(true)
        mockSettingsRepository = mockk {
            every { onboardingCompleted } returns onboardingFlow
            every { themePreference } returns MutableStateFlow(mockk())
        }
        mockRemoteConfigRepository = mockk(relaxUnitFun = true)

        // Act
        viewModel = MainViewModel(mockSettingsRepository, mockRemoteConfigRepository)

        // Assert
        viewModel.onboardingCheckState.test {
            // Пропускаем начальное состояние Loading
            skipItems(1)
            assertTrue(awaitItem() is OnboardingCheckState.Completed)
        }
    }

    @Test
    fun `onboardingCheckState emits NotCompleted when repository returns false`() = runTest {
        // Arrange
        val onboardingFlow = MutableStateFlow(false)
        mockSettingsRepository = mockk {
            every { onboardingCompleted } returns onboardingFlow
            every { themePreference } returns MutableStateFlow(mockk())
        }
        mockRemoteConfigRepository = mockk(relaxUnitFun = true)

        // Act
        viewModel = MainViewModel(mockSettingsRepository, mockRemoteConfigRepository)

        // Assert
        viewModel.onboardingCheckState.test {
            skipItems(1)
            assertTrue(awaitItem() is OnboardingCheckState.NotCompleted)
        }
    }
}