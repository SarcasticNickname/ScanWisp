package com.myprojects.scanwisp.ui.screens.detail

import androidx.compose.runtime.Immutable
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.domain.model.AppError

@Immutable
sealed interface DocumentDetailUiState {
    object Loading : DocumentDetailUiState
    data class Success(val documentWithPages: DocumentWithPages) : DocumentDetailUiState
    data class Error(val error: AppError.LoadDataError) : DocumentDetailUiState
}