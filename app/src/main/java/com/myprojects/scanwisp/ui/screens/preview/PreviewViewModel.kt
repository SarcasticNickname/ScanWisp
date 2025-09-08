package com.myprojects.scanwisp.ui.screens.preview

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    val pageState = repository.getPageById(pageId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * ==========================================================
     * ИЗМЕНЕНИЕ: Логика полностью делегирована репозиторию.
     * ViewModel больше не работает с файлами, а только передает команду.
     * ==========================================================
     */
    fun replaceImage(newImageUri: Uri) {
        viewModelScope.launch {
            try {
                repository.replacePageImage(pageId, newImageUri)
            } catch (e: Exception) {
                Log.e("PreviewViewModel", "Failed to replace page image.", e)
                // TODO: Показать ошибку пользователю через UiEvent (например, Snackbar)
            }
        }
    }
}