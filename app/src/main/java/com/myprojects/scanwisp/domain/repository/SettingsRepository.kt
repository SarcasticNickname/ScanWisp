package com.myprojects.scanwisp.domain.repository

import com.myprojects.scanwisp.domain.model.OcrLanguage
import com.myprojects.scanwisp.domain.model.OcrMode
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.domain.model.ViewMode
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

    /**
     * Поток, предоставляющий текущий режим отображения (сетка или список).
     */
    val viewMode: Flow<ViewMode>

    /**
     * Сохраняет новый режим отображения.
     */
    suspend fun saveViewMode(viewMode: ViewMode)

    /**
     * Поток, указывающий, нужно ли вписывать изображения в стандартную страницу A4.
     */
    val fitToA4: Flow<Boolean>

    /**
     * Сохраняет настройку вписывания в A4.
     */
    suspend fun saveFitToA4(fit: Boolean)

    /**
     * Поток, указывающий, была ли уже показана подсказка о сортировке.
     */
    val sortHintShown: Flow<Boolean>

    /**
     * Сохраняет флаг о том, что подсказка о сортировке была показана.
     */
    suspend fun setSortHintShown(shown: Boolean)

    val defaultOcrMode: Flow<OcrMode>
    suspend fun saveDefaultOcrMode(mode: OcrMode)

    val defaultOcrLanguage: Flow<OcrLanguage>
    suspend fun saveDefaultOcrLanguage(language: OcrLanguage)
}