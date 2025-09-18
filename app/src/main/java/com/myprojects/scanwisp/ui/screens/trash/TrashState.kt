package com.myprojects.scanwisp.ui.screens.trash

import androidx.compose.runtime.Immutable
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.domain.model.AppError

@Immutable
sealed interface TrashUiState {
    object Loading : TrashUiState
    data class Success(
        val documents: List<DocumentRow>,
        val selectedIds: Set<String> = emptySet()
    ) : TrashUiState {
        val isSelectionModeActive: Boolean get() = selectedIds.isNotEmpty()
    }

    data class Error(val error: AppError.LoadDataError) : TrashUiState
}