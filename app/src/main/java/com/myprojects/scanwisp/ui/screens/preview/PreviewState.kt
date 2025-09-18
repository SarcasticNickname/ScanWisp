package com.myprojects.scanwisp.ui.screens.preview

import androidx.compose.runtime.Immutable
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.AppError

@Immutable
sealed interface PreviewUiState {
    object Loading : PreviewUiState
    data class Success(val page: PageEntity) : PreviewUiState
    data class Error(val error: AppError.LoadDataError) : PreviewUiState
}