package com.myprojects.scanwisp.ui.screens.detail

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.use_case.SplitPagesUseCase
import com.myprojects.scanwisp.ui.delegate.ExportManagerDelegate
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.screens.home.ExportAction
import com.myprojects.scanwisp.ui.screens.home.ShareDialogState
import com.myprojects.scanwisp.ui.state.LoadingState
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
    private val splitPagesUseCase: SplitPagesUseCase,
    // ИЗМЕНЕНИЕ: Внедряем наш новый делегат
    private val exportManager: ExportManagerDelegate,
    @ApplicationContext private val context: Context,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
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

    // ИЗМЕНЕНИЕ: Состояние диалога берем из делегата
    val shareDialogState: StateFlow<ShareDialogState> = exportManager.shareDialogState

    private val _isRenameDialogVisible = MutableStateFlow(false)
    val isRenameDialogVisible = _isRenameDialogVisible.asStateFlow()

    private val _expandedMenuPageId = MutableStateFlow<String?>(null)
    val expandedMenuPageId = _expandedMenuPageId.asStateFlow()

    private var deletePagesJob: Job? = null
    private val pagesPendingDeletion = mutableListOf<String>()
    private val _pendingDeletionPageIds = MutableStateFlow<Set<String>>(emptySet())


    init {
        // ... (init блок остается без изменений)
        crashlytics.setCustomKey("current_screen", "document_detail")
        crashlytics.setCustomKey("document_id", documentId)

        viewModelScope.launch {
            combine(
                repository.getDocumentById(documentId),
                _pendingDeletionPageIds
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
        deletePagesJob?.cancel()
        super.onCleared()
    }

    // ... (splitSelectedPages, onMoveRequest и другие методы остаются)
    fun splitSelectedPages() {
        viewModelScope.launch {
            val pageIds = _selectedPageIds.value.toList()
            val originalDocState = _documentState.value ?: return@launch

            crashlytics.log("Splitting ${pageIds.size} pages from document ${originalDocState.document.id}")
            crashlytics.setCustomKey("operation", "split_pages")
            crashlytics.setCustomKey("split_page_count", pageIds.size)

            _loadingState.update { it.copy(isBusy = true, message = "Разъединение...") }
            try {
                // Вызываем UseCase, который содержит всю бизнес-логику и валидацию
                val newDocsCount = splitPagesUseCase(documentId, pageIds)

                analytics.logEvent("pages_split") {
                    param("split_count", newDocsCount.toLong())
                }
                _uiEventFlow.emit(UiEvent.ShowSnackbar("$newDocsCount страниц(ы) выделены в новые документы"))

                // Проверяем, не стал ли исходный документ пустым
                if (pageIds.size == originalDocState.pages.size) {
                    _uiEventFlow.emit(UiEvent.NavigateBack)
                }
            } catch (e: IllegalArgumentException) {
                // Обрабатываем ошибку валидации, которую может выбросить UseCase
                _uiEventFlow.emit(UiEvent.ShowSnackbar(e.message ?: "Некорректный выбор страниц"))
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Failed to split pages", e)
                crashlytics.recordException(e)
                _uiEventFlow.emit(
                    UiEvent.ShowSnackbar(
                        "Ошибка при разъединении страниц",
                        isError = true
                    )
                )
            } finally {
                clearSelection()
                _loadingState.update { it.copy(isBusy = false) }
                crashlytics.setCustomKey("operation", "none")
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
        val allPageIds = _documentState.value?.pages?.map { it.id } ?: emptySet()
        if (allPageIds.isNotEmpty()) {
            _selectedPageIds.value = allPageIds as Set<String>
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
                repository.updateDocumentCover(documentId, page.thumbnailPath)
                analytics.logEvent("cover_changed", null)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Обложка документа обновлена"))
            }
        }
    }

    // ИЗМЕНЕНИЕ: Метод упрощен и делегирует вызов менеджеру
    fun shareSinglePage(pageId: String) {
        val documentTitle = _documentState.value?.document?.title ?: "Страница"
        exportManager.requestExportForPages(listOf(pageId), documentTitle, ExportAction.SHARE)
    }

    // ... (onRenameRequest и далее без изменений)
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
            analytics.logEvent("pages_reordered", null)
        }
    }

    fun addPages(scannedUris: List<Uri>) {
        viewModelScope.launch {
            crashlytics.log("Adding ${scannedUris.size} pages to document $documentId")
            if (scannedUris.isNotEmpty()) {
                repository.addPagesToDocument(documentId, scannedUris)
                analytics.logEvent("pages_added") {
                    param("added_count", scannedUris.size.toLong())
                }
            }
        }
    }

    fun deleteSelectedPages() {
        val pageIds = _selectedPageIds.value.toList()
        if (pageIds.isEmpty()) return
        deletePagesJob?.cancel()

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

        deletePagesJob = viewModelScope.launch {
            delay(5000)
            repository.deletePages(pageIds)
            _pendingDeletionPageIds.update { it - pageIds.toSet() }
            pagesPendingDeletion.clear()

            if (_documentState.value?.pages.isNullOrEmpty()) {
                _uiEventFlow.emit(UiEvent.NavigateBack)
            }
        }
    }

    fun undoDeletePages() {
        deletePagesJob?.cancel()
        _pendingDeletionPageIds.update { it - pagesPendingDeletion.toSet() }
        pagesPendingDeletion.clear()
        viewModelScope.launch {
            _uiEventFlow.emit(UiEvent.ShowSnackbar("Удаление отменено"))
        }
    }

    // ==========================================================
    // ИЗМЕНЕНИЕ: Все методы экспорта теперь делегируют вызовы ExportManagerDelegate
    // ==========================================================
    fun onShareRequest() {
        val pageIds = _selectedPageIds.value.toList()
        val documentTitle = _documentState.value?.document?.title ?: "ScanWisp_Document"
        exportManager.requestExportForPages(pageIds, documentTitle, ExportAction.SHARE)
    }

    fun onShareDialogDismiss() = exportManager.onDialogDismiss()

    fun onShareDialogConfirm(format: ExportFormat, filename: String) {
        viewModelScope.launch {
            _loadingState.update {
                it.copy(
                    isBusy = true,
                    message = context.getString(R.string.loading)
                )
            }
            val event = exportManager.onConfirmExport(format, filename)
            _uiEventFlow.emit(event)
            _loadingState.update { it.copy(isBusy = false) }
            clearSelection()
        }
    }

    fun cleanUpTempFile(tempFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            exportManager.cleanUpTempFile(tempFile)
        }
    }
}