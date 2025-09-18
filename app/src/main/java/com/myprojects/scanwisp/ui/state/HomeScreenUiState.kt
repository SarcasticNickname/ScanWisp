package com.myprojects.scanwisp.ui.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.ViewMode

/**
 * Представляет все возможные состояния UI для главного экрана (HomeScreen).
 */
@Stable
sealed interface HomeScreenUiState {
    /**
     * Состояние первоначальной загрузки данных.
     */
    object Loading : HomeScreenUiState

    /**
     * Состояние, когда данные успешно загружены и готовы к отображению.
     */
    @Immutable
    data class Data(
        val screenTitle: String,
        /**
         * ИЗМЕНЕНИЕ: Вместо отдельных списков `documents` и `nativeAd`
         * теперь используется один смешанный список `items`, который может
         * содержать как объекты `DocumentRow`, так и `NativeAd`.
         */
        val items: List<Any>,
        val allFolders: List<FolderEntity>,
        val loadingState: LoadingState,
        val selectedDocumentIds: Set<String>,
        val viewMode: ViewMode = ViewMode.GRID
        // ИЗМЕНЕНИЕ: `nativeAd` и `adPosition` удалены, так как эта логика
        // теперь обрабатывается при формировании списка `items`.
    ) : HomeScreenUiState {
        val isSelectionModeActive: Boolean get() = selectedDocumentIds.isNotEmpty()
    }

    /**
     * Состояние, когда произошла ошибка при загрузке данных.
     */
    @Immutable
    data class Error(val error: AppError.LoadDataError) : HomeScreenUiState
}