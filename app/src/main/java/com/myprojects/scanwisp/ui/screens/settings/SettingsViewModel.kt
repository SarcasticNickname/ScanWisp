package com.myprojects.scanwisp.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // START: AI_MODIFIED_BLOCK
    val pdfExportProfile: StateFlow<PdfExportProfile?> = settingsRepository.pdfExportProfile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val themePreference: StateFlow<ThemePreference?> = settingsRepository.themePreference
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    // END: AI_MODIFIED_BLOCK

    fun onProfileSelected(profile: PdfExportProfile) {
        viewModelScope.launch {
            settingsRepository.savePdfExportProfile(profile)
        }
    }

    fun onThemeSelected(themePreference: ThemePreference) {
        viewModelScope.launch {
            settingsRepository.saveThemePreference(themePreference)
        }
    }
}