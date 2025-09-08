package com.myprojects.scanwisp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface OnboardingCheckState {
    object Loading : OnboardingCheckState
    object Completed : OnboardingCheckState
    object NotCompleted : OnboardingCheckState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {

    init {
        /**
         * ==========================================================
         * ИЗМЕНЕНИЕ: Запускаем получение свежих значений Remote Config.
         * Это единственное правильное место для этого вызова.
         * ==========================================================
         */
        remoteConfigRepository.fetchAndActivate()
    }

    val themePreference = settingsRepository.themePreference

    val onboardingCheckState: StateFlow<OnboardingCheckState> =
        settingsRepository.onboardingCompleted
            .map { completed ->
                if (completed) OnboardingCheckState.Completed else OnboardingCheckState.NotCompleted
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = OnboardingCheckState.Loading
            )
}