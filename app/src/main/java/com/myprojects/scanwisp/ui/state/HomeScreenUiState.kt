package com.myprojects.scanwisp.ui.state

import androidx.compose.runtime.Stable
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
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
    data class Data(
        val screenTitle: String,
        val documents: List<DocumentWithPages>,
        val allFolders: List<FolderEntity>,
        val nativeAd: NativeAd?,
        val loadingState: LoadingState,
        val selectedDocumentIds: Set<String>,
        val searchQuery: String = "",
        // START: AI_MODIFIED_BLOCK - ДОБАВЛЕНО НЕДОСТАЮЩЕЕ ПОЛЕ
        val viewMode: ViewMode = ViewMode.GRID
        // END: AI_MODIFIED_BLOCK
    ) : HomeScreenUiState {
        val isSelectionModeActive: Boolean get() = selectedDocumentIds.isNotEmpty()
    }

    /**
     * Состояние, когда произошла ошибка при загрузке данных.
     */
    data class Error(val message: String) : HomeScreenUiState
}