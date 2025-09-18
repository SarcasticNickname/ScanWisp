package com.myprojects.scanwisp.ui.screens.folders

import androidx.compose.runtime.Immutable
import com.myprojects.scanwisp.domain.model.AppError

/**
 * Представляет все возможные состояния UI для экрана папок.
 */
@Immutable
sealed interface FoldersUiState {
    /**
     * Состояние первоначальной загрузки данных.
     */
    object Loading : FoldersUiState

    /**
     * Состояние, когда данные успешно загружены и готовы к отображению.
     * @param items Смешанный список, содержащий объекты FolderWithDocumentCount и NativeAd.
     */
    data class Success(val items: List<Any>) : FoldersUiState

    /**
     * Состояние, когда произошла ошибка при загрузке данных.
     */
    data class Error(val error: AppError.LoadDataError) : FoldersUiState
}