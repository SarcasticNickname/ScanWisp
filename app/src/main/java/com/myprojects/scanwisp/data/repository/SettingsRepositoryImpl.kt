package com.myprojects.scanwisp.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.domain.model.ViewMode
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {


    private object PreferencesKeys {
        val PDF_EXPORT_PROFILE = stringPreferencesKey("pdf_export_profile")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val FIT_TO_A4 = booleanPreferencesKey("fit_to_a4")

        // Вот недостающая строка:
        val SORT_HINT_SHOWN = booleanPreferencesKey("sort_hint_shown")
    }

    override val pdfExportProfile: Flow<PdfExportProfile> = context.dataStore.data
        .map { preferences ->
            val profileName = preferences[PreferencesKeys.PDF_EXPORT_PROFILE]
                ?: PdfExportProfile.BALANCED.name
            try {
                PdfExportProfile.valueOf(profileName)
            } catch (e: IllegalArgumentException) {
                PdfExportProfile.BALANCED
            }
        }

    override suspend fun savePdfExportProfile(profile: PdfExportProfile) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PDF_EXPORT_PROFILE] = profile.name
        }
    }

    override val themePreference: Flow<ThemePreference> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[PreferencesKeys.THEME_PREFERENCE]
                ?: ThemePreference.SYSTEM.name
            try {
                ThemePreference.valueOf(themeName)
            } catch (e: IllegalArgumentException) {
                ThemePreference.SYSTEM
            }
        }

    override suspend fun saveThemePreference(themePreference: ThemePreference) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_PREFERENCE] = themePreference.name
        }
    }

    override val sortBy: Flow<SortBy> = context.dataStore.data
        .map { preferences ->
            val sortByName = preferences[PreferencesKeys.SORT_BY] ?: SortBy.DATE.name
            try {
                SortBy.valueOf(sortByName)
            } catch (e: IllegalArgumentException) {
                SortBy.DATE
            }
        }

    override suspend fun saveSortBy(sortBy: SortBy) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_BY] = sortBy.name
        }
    }

    override val sortOrder: Flow<SortOrder> = context.dataStore.data
        .map { preferences ->
            val sortOrderName = preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.DESCENDING.name
            try {
                SortOrder.valueOf(sortOrderName)
            } catch (e: IllegalArgumentException) {
                SortOrder.DESCENDING
            }
        }

    override suspend fun saveSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ORDER] = sortOrder.name
        }
    }

    override val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    override val viewMode: Flow<ViewMode> = context.dataStore.data
        .map { preferences ->
            val viewModeName = preferences[PreferencesKeys.VIEW_MODE] ?: ViewMode.GRID.name
            try {
                ViewMode.valueOf(viewModeName)
            } catch (e: IllegalArgumentException) {
                ViewMode.GRID
            }
        }

    override suspend fun saveViewMode(viewMode: ViewMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIEW_MODE] = viewMode.name
        }
    }

    override val fitToA4: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.FIT_TO_A4] ?: true
        }

    override suspend fun saveFitToA4(fit: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIT_TO_A4] = fit
        }
    }

    override val sortHintShown: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SORT_HINT_SHOWN] ?: false
        }

    override suspend fun setSortHintShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_HINT_SHOWN] = shown
        }
    }
}