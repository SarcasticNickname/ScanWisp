package com.myprojects.scanwisp.domain.repository

import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ThemePreference
// START: AI_MODIFIED_BLOCK
import com.myprojects.scanwisp.domain.model.ViewMode
// END: AI_MODIFIED_BLOCK
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для управления настройками приложения.
 */
interface SettingsRepository {

    /**
     * Поток, предоставляющий текущий выбранный профиль экспорта в PDF.
     */
    val pdfExportProfile: Flow<PdfExportProfile>

    /**
     * Сохраняет новый профиль экспорта в PDF.
     * @param profile Новый профиль для сохранения.
     */
    suspend fun savePdfExportProfile(profile: PdfExportProfile)

    /**
     * Поток, предоставляющий текущую выбранную настройку темы.
     */
    val themePreference: Flow<ThemePreference>

    /**
     * Сохраняет новую настройку темы.
     * @param themePreference Новая настройка для сохранения.
     */
    suspend fun saveThemePreference(themePreference: ThemePreference)

    /**
     * Поток, предоставляющий текущий критерий сортировки.
     */
    val sortBy: Flow<SortBy>

    /**
     * Сохраняет новый критерий сортировки.
     */
    suspend fun saveSortBy(sortBy: SortBy)

    /**
     * Поток, предоставляющий текущее направление сортировки.
     */
    val sortOrder: Flow<SortOrder>

    /**
     * Сохраняет новое направление сортировки.
     */
    suspend fun saveSortOrder(sortOrder: SortOrder)

    /**
     * Поток, предоставляющий статус завершения онбординга.
     */
    val onboardingCompleted: Flow<Boolean>

    /**
     * Помечает онбординг как завершенный.
     */
    suspend fun setOnboardingCompleted(completed: Boolean)

    // START: AI_MODIFIED_BLOCK
    /**
     * Поток, предоставляющий текущий режим отображения (сетка или список).
     */
    val viewMode: Flow<ViewMode>

    /**
     * Сохраняет новый режим отображения.
     */
    suspend fun saveViewMode(viewMode: ViewMode)
    // END: AI_MODIFIED_BLOCK
}