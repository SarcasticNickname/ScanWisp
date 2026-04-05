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

    // --- Диалог создания ---
    private val _isCreateDialogVisible = MutableStateFlow(false)
    val isCreateDialogVisible = _isCreateDialogVisible.asStateFlow()

    // --- Диалог переименования ---
    private val _renamingFolder = MutableStateFlow<FolderWithDocumentCount?>(null)
    val renamingFolder = _renamingFolder.asStateFlow()

    // --- Диалог подтверждения удаления ---
    private val _deletingFolder = MutableStateFlow<FolderWithDocumentCount?>(null)
    val deletingFolder = _deletingFolder.asStateFlow()

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.getFoldersWithDocumentCount()
                .catch { e ->
                    crashlytics.recordException(e)
                    _uiState.value = FoldersUiState.Error(AppError.LoadDataError)
                }
                .collect { folders ->
                    _uiState.value = FoldersUiState.Success(items = insertAdsIntoList(folders))
                }
        }
    }

    private fun insertAdsIntoList(folders: List<FolderWithDocumentCount>): List<Any> {
        if (!remoteConfigRepository.isNativeAdEnabled() || folders.isEmpty()) return folders
        val startPosition = remoteConfigRepository.getNativeAdStartPosition()
        val interval = remoteConfigRepository.getNativeAdInterval()
        val combinedList = mutableListOf<Any>()
        var itemsSinceLastAd = 0
        folders.forEachIndexed { index, folder ->
            combinedList.add(folder)
            itemsSinceLastAd++
            if ((index + 1) >= startPosition && itemsSinceLastAd >= interval) {
                adPoolManager.getAd()?.let { ad ->
                    combinedList.add(ad)
                    itemsSinceLastAd = 0
                }
            }
        }
        return combinedList
    }

    // --- Создание ---

    fun onAddFolderRequest() { _isCreateDialogVisible.value = true }
    fun onDialogDismiss() { _isCreateDialogVisible.value = false }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
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

    // --- Переименование ---

    fun onRenameRequest(folder: FolderWithDocumentCount) { _renamingFolder.value = folder }
    fun onRenameDismiss() { _renamingFolder.value = null }

    fun onRenameConfirm(newName: String) {
        val folder = _renamingFolder.value ?: return
        if (newName.isBlank()) { onRenameDismiss(); return }
        viewModelScope.launch {
            try {
                analytics.logEvent("folder_renamed", null)
                repository.renameFolder(folder.folder.id, newName)
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            } finally {
                onRenameDismiss()
            }
        }
    }

    // --- Удаление ---

    fun onDeleteRequest(folder: FolderWithDocumentCount) { _deletingFolder.value = folder }
    fun onDeleteDismiss() { _deletingFolder.value = null }

    fun onDeleteConfirm() {
        val folder = _deletingFolder.value ?: return
        viewModelScope.launch {
            try {
                analytics.logEvent("folder_deleted", null)
                repository.deleteFolder(folder.folder.id)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Папка «${folder.folder.name}» удалена"))
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            } finally {
                onDeleteDismiss()
            }
        }
    }
}