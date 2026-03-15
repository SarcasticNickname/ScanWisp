package com.myprojects.scanwisp.ui.screens.settings

import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.OcrLanguage
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.ui.events.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
) : ViewModel() {

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.pdfExportProfile,
        settingsRepository.themePreference,
        settingsRepository.fitToA4,
        settingsRepository.defaultOcrLanguage
    ) { pdfProfile, theme, fitA4, ocrLanguage ->
        SettingsUiState.Success(
            AllSettings(
                pdfExportProfile = pdfProfile,
                themePreference = theme,
                fitToA4 = fitA4,
                defaultOcrLanguage = ocrLanguage
            )
        )
    }
        .map { successState ->
            // Явно приводим тип потока к общему интерфейсу SettingsUiState
            successState as SettingsUiState
        }
        .catch { e ->
            crashlytics.recordException(e)
            // Добавляем обработку ошибок
            emit(SettingsUiState.Error(AppError.DatabaseOperationError))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            // Начальное значение - Loading. UI будет показывать спиннер, пока combine не сработает.
            initialValue = SettingsUiState.Loading
        )

    fun onProfileSelected(profile: PdfExportProfile) {
        viewModelScope.launch {
            try {
                // --- АНАЛИТИКА ---
                analytics.logEvent("pdf_quality_changed", bundleOf("new_profile" to profile.name))
                settingsRepository.savePdfExportProfile(profile)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save PDF profile")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun onThemeSelected(themePreference: ThemePreference) {
        viewModelScope.launch {
            try {
                // --- АНАЛИТИКА ---
                analytics.logEvent("theme_changed", bundleOf("new_theme" to themePreference.name))
                settingsRepository.saveThemePreference(themePreference)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save theme")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun onFitToA4Changed(fit: Boolean) {
        viewModelScope.launch {
            try {
                // --- АНАЛИТИКА ---
                analytics.logEvent("fit_to_a4_changed", bundleOf("new_value" to fit))
                settingsRepository.saveFitToA4(fit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save A4 setting")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun onOcrLanguageSelected(language: OcrLanguage) {
        viewModelScope.launch {
            try {
                settingsRepository.saveDefaultOcrLanguage(language)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save OCR language")
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }
}