package com.myprojects.scanwisp.ui.screens.detail

import android.app.Activity
import android.net.Uri
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.core.storage.StorageService
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.model.OcrMode
import com.myprojects.scanwisp.domain.model.OcrStatus
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.domain.repository.StringProvider
import com.myprojects.scanwisp.domain.use_case.RecognizePageUseCase
import com.myprojects.scanwisp.domain.use_case.SplitPagesUseCase
import com.myprojects.scanwisp.ui.delegate.ExportManagerDelegate
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.screens.home.ExportAction
import com.myprojects.scanwisp.ui.state.LoadingState
import com.myprojects.scanwisp.worker.OcrWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val splitPagesUseCase: SplitPagesUseCase,
    private val exportManager: ExportManagerDelegate,
    private val stringProvider: StringProvider,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private val storageService: StorageService,
    private val recognizePageUseCase: RecognizePageUseCase,
    private val workManager: WorkManager,
    private val documentDao: DocumentDao
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    val initialPageId: String? = savedStateHandle["pageId"]

    private val selectedPageIdsKey = "detailSelectedPageIds"

    private val _pendingDeletionPageIds = MutableStateFlow<Set<String>>(emptySet())
    private val _reorderedPages = MutableStateFlow<List<PageEntity>?>(null)

    private val documentFlow: StateFlow<DocumentWithPages?> = repository.getDocumentById(documentId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val uiState: StateFlow<DocumentDetailUiState> = combine(
        documentFlow,
        _pendingDeletionPageIds,
    ) { document, pendingIds ->
        if (document == null) {
            return@combine DocumentDetailUiState.Loading
        }
        val processedDocument = document.copy(
            pages = document.pages
                .filter { it.id !in pendingIds }
                .sortedBy { it.position }
        )
        DocumentDetailUiState.Success(processedDocument)
    }
        .catch { e ->
            Timber.e(e, "Error loading document")
            crashlytics.recordException(e)
            emit(DocumentDetailUiState.Error(AppError.LoadDataError))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DocumentDetailUiState.Loading
        )

    val selectedPageIds =
        savedStateHandle.getStateFlow(key = selectedPageIdsKey, initialValue = emptySet<String>())

    private val _allFolders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val allFolders = _allFolders.asStateFlow()

    private val _isMoveDialogVisible = MutableStateFlow(false)
    val isMoveDialogVisible = _isMoveDialogVisible.asStateFlow()

    val isSelectionModeActive: StateFlow<Boolean> = selectedPageIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSortModeActive = MutableStateFlow(false)
    val isSortModeActive = _isSortModeActive.asStateFlow()

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    private val _loadingState = MutableStateFlow(LoadingState())
    val loadingState = _loadingState.asStateFlow()

    val shareDialogState: StateFlow<ExportManagerDelegate.ShareDialogState> =
        exportManager.shareDialogState

    private val _isRenameDialogVisible = MutableStateFlow(false)
    val isRenameDialogVisible = _isRenameDialogVisible.asStateFlow()

    private val _expandedMenuPageId = MutableStateFlow<String?>(null)
    val expandedMenuPageId = _expandedMenuPageId.asStateFlow()

    private var deletePagesJob: Job? = null
    private val pagesPendingDeletion = mutableListOf<String>()

    init {
        crashlytics.setCustomKey("current_screen", "document_detail")
        crashlytics.setCustomKey("document_id", documentId)


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

    fun onAddPagesClicked(activity: Activity) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Подготовка сканера...") }

            val scannerOptions = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(10)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            val scanner = GmsDocumentScanning.getClient(scannerOptions)

            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    viewModelScope.launch {
                        _loadingState.update { it.copy(isBusy = false) }
                        _uiEventFlow.emit(UiEvent.LaunchScanner(intentSender))
                    }
                }
                .addOnFailureListener { e ->
                    viewModelScope.launch {
                        crashlytics.recordException(e)
                        _loadingState.update { it.copy(isBusy = false) }
                        _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.ScannerLaunchError))
                    }
                }
        }
    }

    fun splitSelectedPages() {
        viewModelScope.launch {
            val pageIds = selectedPageIds.value.toList()
            val originalDoc = documentFlow.value ?: return@launch

            crashlytics.log("Splitting ${pageIds.size} pages from document ${originalDoc.document.id}")
            _loadingState.update { it.copy(isBusy = true, message = "Разъединение...") }
            try {
                val newDocsCount = splitPagesUseCase(documentId, pageIds)
                analytics.logEvent("pages_split", bundleOf("split_count" to newDocsCount.toLong()))
                _uiEventFlow.emit(UiEvent.ShowSnackbar("$newDocsCount страниц(ы) выделены в новые документы"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to split pages")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            } finally {
                clearSelection()
                _loadingState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun onMoveRequest() {
        if (documentFlow.value != null) {
            _isMoveDialogVisible.value = true
        }
    }

    fun onMoveDialogDismiss() {
        _isMoveDialogVisible.value = false
    }

    fun onMoveConfirm(folderId: String?) {
        viewModelScope.launch {
            try {
                analytics.logEvent("document_moved", bundleOf("count" to 1L))
                repository.moveDocumentsToFolder(listOf(documentId), folderId)
                _isMoveDialogVisible.value = false
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документ перемещен"))
                _uiEventFlow.emit(UiEvent.NavigateBack)
            } catch (e: Exception) {
                Timber.e(e, "Failed to move document")
                crashlytics.recordException(e)
                _isMoveDialogVisible.value = false
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun onShareAllPagesRequest() {
        val allPageIds = documentFlow.value?.pages?.map { it.id }?.toSet() ?: emptySet()
        if (allPageIds.isNotEmpty()) {
            savedStateHandle[selectedPageIdsKey] = allPageIds
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
            try {
                val page = documentFlow.value?.pages?.find { it.id == pageId }
                if (page != null) {
                    repository.updateDocumentCover(documentId, page.thumbnailPath)
                    analytics.logEvent("cover_changed", null)
                    _uiEventFlow.emit(UiEvent.ShowSnackbar("Обложка документа обновлена"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set page as cover")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun shareSinglePage(pageId: String) {
        val documentTitle = documentFlow.value?.document?.title ?: "Страница"
        exportManager.requestExportForPages(listOf(pageId), documentTitle, ExportAction.SHARE)
    }

    fun onRenameRequest() {
        if (documentFlow.value != null) {
            _isRenameDialogVisible.value = true
        }
    }

    fun onRenameDialogDismiss() {
        _isRenameDialogVisible.value = false
    }

    fun onRenameConfirm(newTitle: String) {
        if (newTitle.isNotBlank()) {
            viewModelScope.launch {
                try {
                    analytics.logEvent("document_renamed", null)
                    repository.renameDocument(documentId, newTitle)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to rename document")
                    crashlytics.recordException(e)
                    _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
                }
            }
        }
        _isRenameDialogVisible.value = false
    }

    fun onPageClick(pageId: String) {
        if (isSelectionModeActive.value) {
            val currentIds = selectedPageIds.value
            val newIds = if (pageId in currentIds) currentIds - pageId else currentIds + pageId
            savedStateHandle[selectedPageIdsKey] = newIds
        }
    }

    fun onPageLongClick(pageId: String) {
        if (!isSortModeActive.value) {
            val newIds = selectedPageIds.value + pageId
            savedStateHandle[selectedPageIdsKey] = newIds
        }
    }

    fun clearSelection() {
        savedStateHandle[selectedPageIdsKey] = emptySet<String>()
    }

    fun toggleSortMode() {
        val newSortModeState = !_isSortModeActive.value
        _isSortModeActive.value = newSortModeState

        if (newSortModeState) {
            viewModelScope.launch {
                if (!settingsRepository.sortHintShown.first()) {
                    _uiEventFlow.emit(UiEvent.ShowSnackbar("Удерживайте и перетаскивайте страницы для сортировки"))
                    settingsRepository.setSortHintShown(true)
                }
            }
        } else {
            persistReorderedPages()
        }
    }

    fun reorderPagesInMemory(reorderedList: List<PageEntity>) {
        _reorderedPages.value = reorderedList
    }

    private fun persistReorderedPages() {
        val pagesToPersist = _reorderedPages.value ?: return

        viewModelScope.launch {
            try {
                val updatedEntities = pagesToPersist.mapIndexed { index, page ->
                    page.copy(position = index.toLong())
                }
                repository.updatePageOrder(updatedEntities)
                analytics.logEvent("pages_reordered", null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist reordered pages")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            } finally {
                _reorderedPages.value = null
            }
        }
    }

    fun addPages(uris: List<Uri>) {
        viewModelScope.launch {
            val requiredSpace = storageService.estimateForPages(uris.size)
            val operationDir = storageService.appFilesDir()

            var reservation = storageService.tryReserve(requiredSpace, dir = operationDir)

            reservation?.use { _ ->
                try {
                    crashlytics.log("Adding ${uris.size} pages to document $documentId")
                    if (uris.isNotEmpty()) {
                        repository.addPagesToDocument(documentId, uris)
                        analytics.logEvent(
                            "pages_added", bundleOf("added_count" to uris.size.toLong())
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add pages")
                    crashlytics.recordException(e)
                    _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.ImageProcessingError))
                }
            } ?: run {
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.NotEnoughStorageError))
            }
        }
    }

    fun deleteSelectedPages() {
        val pageIds = selectedPageIds.value.toList()
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
                    actionLabel = stringProvider.getString(R.string.action_cancel)
                )
            )
        }
        deletePagesJob = viewModelScope.launch {
            try {
                delay(5000)
                val willBeEmpty = documentFlow.value?.pages
                    ?.all { it.id in pageIds.toSet() } == true
                analytics.logEvent("pages_deleted", bundleOf("count" to pageIds.size.toLong()))
                repository.deletePages(pageIds)
                _pendingDeletionPageIds.update { it - pageIds.toSet() }
                pagesPendingDeletion.clear()
                if (willBeEmpty) {
                    _uiEventFlow.emit(UiEvent.NavigateBack)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete pages")
                crashlytics.recordException(e)
                _pendingDeletionPageIds.update { it - pageIds.toSet() }
                pagesPendingDeletion.clear()
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun undoDeletePages() {
        deletePagesJob?.cancel()
        viewModelScope.launch {
            try {
                _pendingDeletionPageIds.update { it - pagesPendingDeletion.toSet() }
                pagesPendingDeletion.clear()
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Удаление отменено"))
            } catch (e: Exception) {
                Timber.e(e, "Failed during undo logic")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.General(e.message)))
            }
        }
    }

    fun onShareRequest() {
        val pageIds = selectedPageIds.value.toList()
        val documentTitle = documentFlow.value?.document?.title ?: "ScanWisp_Document"
        exportManager.requestExportForPages(pageIds, documentTitle, ExportAction.SHARE)
    }

    fun onShareDialogDismiss() {
        exportManager.onDialogDismiss()
        clearSelection()
    }

    fun onShareDialogConfirm(format: ExportFormat, filename: String) {
        viewModelScope.launch {
            _loadingState.update {
                it.copy(
                    isBusy = true,
                    message = stringProvider.getString(R.string.loading)
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

    fun recognizePage(pageId: String, mode: OcrMode) {
        viewModelScope.launch {
            try {
                // Сбрасываем статус → PENDING, чтобы воркер подхватил
                documentDao.updatePageOcrStatus(pageId, OcrStatus.PENDING)

                val doc = documentFlow.value ?: return@launch
                val documentId = doc.document.id
                val documentTitle = doc.document.title

                enqueueOcr(documentId, documentTitle, mode)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Распознавание запущено"))
                analytics.logEvent("ocr_from_detail", null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to enqueue OCR")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.General("Ошибка запуска распознавания")))
            }
        }
    }

    /** Распознать ВСЕ страницы документа */
    fun recognizeAllPages() {
        viewModelScope.launch {
            try {
                val doc = documentFlow.value ?: return@launch
                val pages = doc.pages
                if (pages.isEmpty()) return@launch

                // Сбрасываем все страницы в PENDING
                pages.forEach { page ->
                    documentDao.updatePageOcrStatus(page.id, OcrStatus.PENDING)
                }

                enqueueOcr(doc.document.id, doc.document.title)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Распознавание запущено (${pages.size} стр.)"))
                analytics.logEvent("ocr_all_pages", null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to enqueue OCR for all pages")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.General("Ошибка запуска распознавания")))
            }
        }
    }

    /** Распознать только ВЫБРАННЫЕ страницы */
    fun recognizeSelectedPages() {
        viewModelScope.launch {
            try {
                val doc = documentFlow.value ?: return@launch
                val selected = selectedPageIds.value
                if (selected.isEmpty()) return@launch

                // Сбрасываем только выбранные в PENDING
                selected.forEach { pageId ->
                    documentDao.updatePageOcrStatus(pageId, OcrStatus.PENDING)
                }

                enqueueOcr(doc.document.id, doc.document.title)
                clearSelection()
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Распознавание запущено (${selected.size} стр.)"))
                analytics.logEvent("ocr_selected_pages", null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to enqueue OCR for selected pages")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.General("Ошибка запуска распознавания")))
            }
        }
    }

    private fun enqueueOcr(documentId: String, documentTitle: String, ocrMode: OcrMode? = null) {
        val inputData = workDataOf(
            OcrWorker.KEY_DOCUMENT_ID to documentId,
            OcrWorker.KEY_DOCUMENT_TITLE to documentTitle,
            OcrWorker.KEY_OCR_MODE to ocrMode?.name
        )
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("ocr_$documentId")
            .build()

        val uniqueWorkName = "ocr_$documentId"

        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )

        // Наблюдаем за завершением, чтобы показать снэкбар
        viewModelScope.launch {
            val workInfos = workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
                .first { infos ->
                    infos.isNotEmpty() && infos.all { it.state.isFinished }
                }

            val allSucceeded = workInfos.all { it.state == WorkInfo.State.SUCCEEDED }
            val message = if (allSucceeded) {
                stringProvider.getString(R.string.snackbar_ocr_finished)
            } else {
                "Распознавание завершено с ошибками"
            }
            _uiEventFlow.emit(UiEvent.ShowSnackbar(message))
        }
    }
}