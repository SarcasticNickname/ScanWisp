package com.myprojects.scanwisp.ui.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.data.local.DocumentRow
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
    @Immutable
    data class Data(
        val screenTitle: String,
        val documents: List<DocumentRow>,
        val allFolders: List<FolderEntity>,
        val nativeAd: NativeAd?,
        val loadingState: LoadingState,
        val selectedDocumentIds: Set<String>,
        val searchQuery: String = "",
        val viewMode: ViewMode = ViewMode.GRID,
        val adPosition: Int = 1
    ) : HomeScreenUiState {
        val isSelectionModeActive: Boolean get() = selectedDocumentIds.isNotEmpty()
    }

    /**
     * Состояние, когда произошла ошибка при загрузке данных.
     */
    @Immutable // ДОБАВЛЕНО
    data class Error(val message: String) : HomeScreenUiState
}