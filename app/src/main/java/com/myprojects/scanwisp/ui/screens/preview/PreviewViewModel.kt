package com.myprojects.scanwisp.ui.screens.preview

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.utils.FileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
    private val fileManager: FileManager
) : ViewModel() {

    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    val pageState = repository.getPageById(pageId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Запускает процесс замены изображения для текущей страницы.
     * Сначала копирует временный файл от сканера в постоянное хранилище,
     * а затем обновляет путь в базе данных.
     *
     * @param newImageUri URI нового изображения, полученного от сканера.
     */
    fun replaceImage(newImageUri: Uri) {
        viewModelScope.launch {
            // ViewModel использует FileManager для копирования файла в постоянное хранилище
            val permanentUri = fileManager.copyUriToAppStorage(newImageUri)
            if (permanentUri != null) {
                repository.replacePageImage(pageId, permanentUri)
            } else {
                Log.e("PreviewViewModel", "Failed to copy new page image to storage.")
                // TODO: Показать ошибку пользователю через UiEvent (например, Snackbar)
            }
        }
    }
}