package com.myprojects.scanwisp.ui.screens.settings

import androidx.compose.runtime.Immutable
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.ThemePreference

/**
 * Контейнер для всех настроек, которые отображаются на экране.
 */
@Immutable
data class AllSettings(
    val pdfExportProfile: PdfExportProfile,
    val themePreference: ThemePreference,
    val fitToA4: Boolean
)

/**
 * Представляет все возможные состояния UI для экрана настроек.
 */
@Immutable
sealed interface SettingsUiState {
    /**
     * Состояние первоначальной загрузки настроек.
     */
    object Loading : SettingsUiState

    /**
     * Состояние, когда все настройки успешно загружены.
     * @param settings Объект, содержащий все необходимые данные для UI.
     */
    data class Success(val settings: AllSettings) : SettingsUiState

    data class Error(val error: AppError) : SettingsUiState
}