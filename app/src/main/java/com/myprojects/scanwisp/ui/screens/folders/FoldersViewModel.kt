package com.myprojects.scanwisp.ui.screens.folders

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.ads.AdPoolManager
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.ui.events.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ИЗМЕНЕНИЕ: Класс состояния теперь хранит один смешанный список `items`,
 * который может содержать как папки, так и рекламу.
 */
@Immutable
data class FoldersScreenState(
    val items: List<Any> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val adPoolManager: AdPoolManager,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoldersUiState>(FoldersUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isDialogVisible = MutableStateFlow(false)
    val isDialogVisible = _isDialogVisible.asStateFlow()

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.getFoldersWithDocumentCount()
                .catch { e ->
                    crashlytics.recordException(e) // <-- ДОБАВИТЬ
                    _uiState.value = FoldersUiState.Error(AppError.LoadDataError)
                }
                .collect { folders ->
                    val combinedList = insertAdsIntoList(folders)
                    // Обновляем состояние Success
                    _uiState.value = FoldersUiState.Success(items = combinedList)
                }
        }
    }

    /**
     * НОВЫЙ МЕТОД: Вставляет рекламу в список папок согласно правилам из Remote Config.
     * Логика идентична той, что в HomeViewModel.
     */
    private fun insertAdsIntoList(folders: List<FolderWithDocumentCount>): List<Any> {
        if (!remoteConfigRepository.isNativeAdEnabled() || folders.isEmpty()) {
            return folders
        }

        val startPosition = remoteConfigRepository.getNativeAdStartPosition()
        val interval = remoteConfigRepository.getNativeAdInterval()

        val combinedList = mutableListOf<Any>()
        var itemsSinceLastAd = 0

        folders.forEachIndexed { index, folder ->
            combinedList.add(folder)
            itemsSinceLastAd++

            val isAfterStartPosition = (index + 1) >= startPosition
            val isIntervalReached = itemsSinceLastAd >= interval

            if (isAfterStartPosition && isIntervalReached) {
                adPoolManager.getAd()?.let { ad ->
                    combinedList.add(ad)
                    itemsSinceLastAd = 0 // Сбрасываем счетчик
                }
            }
        }
        return combinedList
    }

    fun onAddFolderRequest() {
        _isDialogVisible.value = true
    }

    fun onDialogDismiss() {
        _isDialogVisible.value = false
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {

                // --- АНАЛИТИКА ---
                analytics.logEvent("folder_created", null)

                repository.createFolder(name)
                onDialogDismiss()
            } catch (e: Exception) {
                onDialogDismiss()
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }
}