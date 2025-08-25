package com.myprojects.scanwisp.ui.screens.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.use_case.ExportDocumentUseCase
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.screens.home.ExportAction
import com.myprojects.scanwisp.ui.screens.home.ShareDialogState
import com.myprojects.scanwisp.ui.state.LoadingState
import com.myprojects.scanwisp.utils.FileManager
import com.myprojects.scanwisp.utils.SafeNamePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
    private val exportDocumentUseCase: ExportDocumentUseCase,
    private val fileManager: FileManager,
    private val safeNamePolicy: SafeNamePolicy,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val _documentState = MutableStateFlow<DocumentWithPages?>(null)
    val documentState = _documentState.asStateFlow()

    private val _selectedPageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPageIds = _selectedPageIds.asStateFlow()

    private val _allFolders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val allFolders = _allFolders.asStateFlow()

    private val _isMoveDialogVisible = MutableStateFlow(false)
    val isMoveDialogVisible = _isMoveDialogVisible.asStateFlow()

    val isSelectionModeActive: StateFlow<Boolean> = _selectedPageIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSortModeActive = MutableStateFlow(false)
    val isSortModeActive = _isSortModeActive.asStateFlow()

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    private val _loadingState = MutableStateFlow(LoadingState())
    val loadingState = _loadingState.asStateFlow()

    private val _shareDialogState = MutableStateFlow(ShareDialogState())
    val shareDialogState = _shareDialogState.asStateFlow()

    private val _isRenameDialogVisible = MutableStateFlow(false)
    val isRenameDialogVisible = _isRenameDialogVisible.asStateFlow()

    private val _expandedMenuPageId = MutableStateFlow<String?>(null)
    val expandedMenuPageId = _expandedMenuPageId.asStateFlow()

    private val deleteManager = PageDeleteManager(repository)

    init {
        viewModelScope.launch {
            combine(
                repository.getDocumentById(documentId),
                deleteManager.pendingDeletionPageIds
            ) { document, pendingIds ->
                document?.copy(
                    pages = document.pages
                        .filter { it.id !in pendingIds }
                        .sortedBy { it.position }
                )
            }.collect { documentWithPages ->
                _documentState.value = documentWithPages
            }
        }
        viewModelScope.launch {
            repository.getAllFolders().collect { folders ->
                _allFolders.value = folders
            }
        }
    }

    override fun onCleared() {
        deleteManager.cancelDeletion()
        super.onCleared()
    }

    fun splitSelectedPages() {
        viewModelScope.launch {
            val pageIds = _selectedPageIds.value.toList()
            val originalDocument = _documentState.value ?: return@launch

            if (pageIds.isEmpty() || pageIds.size >= originalDocument.pages.size) {
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Выберите от 1 до ${originalDocument.pages.size - 1} страниц для разъединения"))
                return@launch
            }
            _loadingState.update { it.copy(isBusy = true, message = "Разъединение...") }
            try {
                val newDocumentBaseTitle = originalDocument.document.title
                val folderId = originalDocument.document.folderId
                repository.splitPagesIntoNewDocuments(documentId, pageIds, newDocumentBaseTitle, folderId)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("${pageIds.size} страниц(ы) выделены в новые документы"))
                if (pageIds.size == originalDocument.pages.size) {
                    _uiEventFlow.emit(UiEvent.NavigateBack)
                }
            } catch(e: Exception) {
                Log.e("DetailViewModel", "Failed to split pages", e)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Ошибка при разъединении страниц", isError = true))
            } finally {
                clearSelection()
                _loadingState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun onMoveRequest() {
        _isMoveDialogVisible.value = true
    }

    fun onMoveDialogDismiss() {
        _isMoveDialogVisible.value = false
    }

    fun onMoveConfirm(folderId: String?) {
        viewModelScope.launch {
            repository.moveDocumentsToFolder(listOf(documentId), folderId)
            _isMoveDialogVisible.value = false
            _uiEventFlow.emit(UiEvent.ShowSnackbar("Документ перемещен"))
            _uiEventFlow.emit(UiEvent.NavigateBack)
        }
    }

    fun onShareAllPagesRequest() {
        val allPageIds = _documentState.value?.pages?.map { it.id }?.toSet() ?: emptySet()
        if (allPageIds.isNotEmpty()) {
            _selectedPageIds.value = allPageIds
            onShareRequest()
        } else {
            viewModelScope.launch {
                _uiEventFlow.emit(UiEvent.ShowSnackbar("В документе нет страниц для отправки"))
            }
        }
    }

    fun onPageMenuRequested(pageId: String) {
        _expandedMenuPageId.value = pageId
    }

    fun onPageMenuDismissed() {
        _expandedMenuPageId.value = null
    }

    fun setPageAsCover(pageId: String) {
        viewModelScope.launch {
            val page = _documentState.value?.pages?.find { it.id == pageId }
            if (page != null) {
                repository.updateDocumentCover(documentId, page.processedImagePath)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Обложка документа обновлена"))
            }
        }
    }

    fun shareSinglePage(pageId: String) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Экспорт страницы...") }
            try {
                val documentTitle = _documentState.value?.document?.title ?: "Страница"
                val exportResult =
                    exportDocumentUseCase(listOf(pageId), documentTitle, ExportFormat.JPEG)

                if (exportResult != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, exportResult.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    _uiEventFlow.emit(
                        UiEvent.LaunchShareIntent(
                            Intent.createChooser(shareIntent, "Поделиться страницей..."),
                            exportResult.tempFile
                        )
                    )
                } else {
                    throw Exception("Ошибка экспорта: результат не был получен.")
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Single page export failed", e)
                _uiEventFlow.emit(
                    UiEvent.ShowSnackbar(
                        "Ошибка при экспорте страницы",
                        isError = true
                    )
                )
            } finally {
                _loadingState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun onRenameRequest() {
        _isRenameDialogVisible.value = true
    }

    fun onRenameDialogDismiss() {
        _isRenameDialogVisible.value = false
    }

    fun onRenameConfirm(newTitle: String) {
        if (newTitle.isNotBlank()) {
            viewModelScope.launch {
                repository.renameDocument(documentId, newTitle)
            }
        }
        _isRenameDialogVisible.value = false
    }

    fun onPageClick(pageId: String) {
        if (isSelectionModeActive.value) {
            _selectedPageIds.update { currentIds ->
                if (pageId in currentIds) currentIds - pageId else currentIds + pageId
            }
        }
    }

    fun onPageLongClick(pageId: String) {
        if (!isSortModeActive.value) {
            _selectedPageIds.update { currentIds -> currentIds + pageId }
        }
    }

    fun clearSelection() {
        _selectedPageIds.value = emptySet()
    }

    fun toggleSortMode() {
        _isSortModeActive.value = !_isSortModeActive.value
        if (_isSortModeActive.value) {
            clearSelection()
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        val currentDoc = _documentState.value ?: return
        val currentPages = currentDoc.pages.toMutableList()

        currentPages.add(toIndex, currentPages.removeAt(fromIndex))
        _documentState.update { it?.copy(pages = currentPages) }

        viewModelScope.launch {
            repository.updatePageOrder(currentPages)
        }
    }

    fun addPages(scannedUris: List<Uri>) {
        viewModelScope.launch {
            val pageData = scannedUris.mapNotNull { uri ->
                fileManager.copyUriToAppStorage(uri)?.toString()?.let { path ->
                    path to path
                }
            }
            if (pageData.isNotEmpty()) {
                repository.addPagesToDocument(documentId, pageData)
            }
        }
    }

    fun deleteSelectedPages() {
        deleteManager.initiateDelete(_selectedPageIds.value.toList())
    }

    fun undoDeletePages() {
        deleteManager.undoDelete()
    }

    fun onShareRequest() {
        val pageCount = _selectedPageIds.value.size
        if (pageCount == 0) return

        val documentTitle = _documentState.value?.document?.title ?: "ScanWisp_Document"
        _shareDialogState.value = ShareDialogState(
            isVisible = true,
            pageCount = pageCount,
            defaultName = documentTitle,
            action = ExportAction.SHARE
        )
    }

    fun onShareDialogDismiss() {
        _shareDialogState.value = ShareDialogState(isVisible = false)
    }

    fun onShareDialogConfirm(format: ExportFormat, filename: String) {
        onShareDialogDismiss()
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = context.getString(R.string.loading)) }
            try {
                val pageIdsToExport = _selectedPageIds.value.toList()
                val exportResult = exportDocumentUseCase(pageIdsToExport, filename, format)

                if (exportResult != null) {
                    val mimeType = when (format) {
                        ExportFormat.PDF -> "application/pdf"
                        ExportFormat.JPEG -> "image/jpeg"
                        ExportFormat.ZIP -> "application/zip"
                    }

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, exportResult.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    _uiEventFlow.emit(
                        UiEvent.LaunchShareIntent(
                            Intent.createChooser(shareIntent, context.getString(R.string.action_share)),
                            exportResult.tempFile
                        )
                    )
                    clearSelection()
                } else {
                    throw Exception("Export failed, result is null.")
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Export process failed", e)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Ошибка при экспорте файла", isError = true))
            } finally {
                _loadingState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun cleanUpTempFile(tempFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Failed to clean up temp file", e)
            }
        }
    }

    private inner class PageDeleteManager(private val pageRepo: DocumentRepository) {
        private val _pendingDeletionPageIds = MutableStateFlow<Set<String>>(emptySet())
        val pendingDeletionPageIds: StateFlow<Set<String>> = _pendingDeletionPageIds.asStateFlow()

        private val pagesPendingDeletion = mutableListOf<String>()
        private var deleteJob: Job? = null

        fun initiateDelete(pageIds: List<String>) {
            if (pageIds.isEmpty()) return
            cancelDeletion()

            pagesPendingDeletion.clear()
            pagesPendingDeletion.addAll(pageIds)
            _pendingDeletionPageIds.update { it + pageIds.toSet() }
            clearSelection()

            viewModelScope.launch {
                _uiEventFlow.emit(
                    UiEvent.ShowSnackbar(
                        "Страницы удалены",
                        actionLabel = context.getString(R.string.action_cancel)
                    )
                )
            }

            deleteJob = viewModelScope.launch {
                delay(5000)
                pageRepo.deletePages(pageIds)

                val currentPages = _documentState.value?.pages?.map { it.id }?.toSet() ?: emptySet()
                val pendingPages = _pendingDeletionPageIds.value
                if (currentPages.all { it in pendingPages }) {
                    _uiEventFlow.emit(UiEvent.NavigateBack)
                }

                _pendingDeletionPageIds.update { it - pageIds.toSet() }
                pagesPendingDeletion.clear()
            }
        }

        fun undoDelete() {
            cancelDeletion()
            _pendingDeletionPageIds.update { it - pagesPendingDeletion.toSet() }
            pagesPendingDeletion.clear()
            viewModelScope.launch {
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Удаление отменено"))
            }
        }

        fun cancelDeletion() {
            deleteJob?.cancel()
        }
    }
}