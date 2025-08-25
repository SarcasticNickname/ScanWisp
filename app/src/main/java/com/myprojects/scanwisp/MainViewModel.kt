package com.myprojects.scanwisp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// START: AI_MODIFIED_BLOCK
sealed interface OnboardingCheckState {
    object Loading : OnboardingCheckState
    object Completed : OnboardingCheckState
    object NotCompleted : OnboardingCheckState
}
// END: AI_MODIFIED_BLOCK

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val themePreference = settingsRepository.themePreference

    // START: AI_MODIFIED_BLOCK
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
    // END: AI_MODIFIED_BLOCK
}