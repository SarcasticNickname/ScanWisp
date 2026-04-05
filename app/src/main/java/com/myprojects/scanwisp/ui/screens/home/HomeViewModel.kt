package com.myprojects.scanwisp.ui.screens.home

import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.ads.AdPoolManager
import com.myprojects.scanwisp.core.storage.StorageService
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ViewMode
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.domain.repository.StringProvider
import com.myprojects.scanwisp.domain.use_case.DeleteDocumentsUseCase
import com.myprojects.scanwisp.domain.use_case.MergeDocumentsUseCase
import com.myprojects.scanwisp.ui.delegate.ExportManagerDelegate
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.state.HomeScreenUiState
import com.myprojects.scanwisp.ui.state.LoadingState
import com.myprojects.scanwisp.utils.SafeNamePolicy
import com.myprojects.scanwisp.worker.OcrWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class ExportAction { SHARE, SAVE }

@Immutable
data class ShareDialogState(
    val isVisible: Boolean = false,
    val pageCount: Int = 0,
    val defaultName: String = "",
    val action: ExportAction = ExportAction.SHARE,
    val estimatedBytes: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val stringProvider: StringProvider,
    private val deleteDocumentsUseCase: DeleteDocumentsUseCase,
    private val mergeDocumentsUseCase: MergeDocumentsUseCase,
    private val exportManager: ExportManagerDelegate,
    private val adPoolManager: AdPoolManager,
    private val savedStateHandle: SavedStateHandle,
    private val safeNamePolicy: SafeNamePolicy,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private val storageService: StorageService,
    private val workManager: WorkManager
) : ViewModel() {

    private val searchQueryKey = "homeSearchQuery"
    private val selectedIdsKey = "homeSelectedIds"

    private val folderId: String? = savedStateHandle["folderId"]

    private val _uiState = MutableStateFlow<HomeScreenUiState>(HomeScreenUiState.Loading)
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    private val _loadingState = MutableStateFlow(LoadingState())

    private val _selectedDocumentIds =
        savedStateHandle.getStateFlow(key = selectedIdsKey, initialValue = emptySet<String>())

    private val _searchQuery = savedStateHandle.getStateFlow(searchQueryKey, "")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showSortSheet = MutableStateFlow(false)
    val showSortSheet = _showSortSheet.asStateFlow()

    private val _documentsPendingUndo = MutableStateFlow<List<String>>(emptyList())

    val shareDialogState: StateFlow<ShareDialogState> =
        exportManager.shareDialogState
            .map { s ->
                ShareDialogState(
                    isVisible = s.isVisible,
                    pageCount = s.pageCount,
                    defaultName = s.defaultName,
                    action = s.action,
                    estimatedBytes = s.estimatedBytes
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ShareDialogState()
            )

    val sortBy = settingsRepository.sortBy.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SortBy.DATE
    )

    val sortOrder = settingsRepository.sortOrder.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SortOrder.DESCENDING
    )

    init {
        crashlytics.setCustomKey("current_screen", if (folderId == null) "home" else "folder_view")

        // -------- 1) ПОТОК ТОЛЬКО ДЛЯ ДОКУМЕНТОВ (с debounce) --------
        @OptIn(FlowPreview::class)
        val documentsFlow = combine(
            _searchQuery.debounce(300L),
            sortBy,
            sortOrder
        ) { query, sb, so ->
            if (query.isNotBlank()) {
                analytics.logEvent(
                    "search_performed",
                    bundleOf("query_length" to query.length.toLong())
                )
            }
            Triple(query, sb, so)
        }.flatMapLatest { (query, currentSortBy, currentSortOrder) ->
            documentRepository.getDocumentRows(folderId, query, currentSortBy, currentSortOrder)
        }


        val itemsFlow = documentsFlow
            .distinctUntilChanged()
            .map { docs -> insertAdsIntoList(docs) }

        // -------- 2) АСИНХРОННЫЙ ПОТОК ДЛЯ ЗАГОЛОВКА ЭКРАНА --------
        val screenTitleFlow: StateFlow<String> = flow {
            val title = if (folderId != null) {
                documentRepository.getFolderById(folderId)?.name
                    ?: stringProvider.getString(R.string.home_screen_title_default)
            } else {
                stringProvider.getString(R.string.home_screen_title_default)
            }
            emit(title)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = stringProvider.getString(R.string.loading)
        )

        // -------- 3) ФИНАЛЬНЫЙ COMBINE ДЛЯ 6 ПОТОКОВ (с ручным приведением типов) --------
        combine(
            listOf( // Передаем потоки как список
                itemsFlow,
                documentRepository.getAllFolders(),
                _selectedDocumentIds,
                _loadingState,
                screenTitleFlow,
                settingsRepository.viewMode
            )
        ) { values ->
            // Получаем значения из массива и приводим их к нужным типам
            @Suppress("UNCHECKED_CAST")
            val items = values[0] as List<Any>

            @Suppress("UNCHECKED_CAST")
            val folders = values[1] as List<FolderEntity>

            @Suppress("UNCHECKED_CAST")
            val selectedIds = values[2] as Set<String>

            @Suppress("UNCHECKED_CAST")
            val loading = values[3] as LoadingState

            @Suppress("UNCHECKED_CAST")
            val title = values[4] as String

            @Suppress("UNCHECKED_CAST")
            val viewMode = values[5] as ViewMode

            // Создаем объект состояния
            HomeScreenUiState.Data(
                screenTitle = title,
                items = items,
                allFolders = folders,
                loadingState = loading,
                selectedDocumentIds = selectedIds,
                viewMode = viewMode
            )
        }
            .flowOn(Dispatchers.Default)
            .catch { e ->
                Timber.e(e, "Error in state flow")
                crashlytics.recordException(e)
                _uiState.value = HomeScreenUiState.Error(AppError.LoadDataError)
            }
            .onEach { dataState -> _uiState.value = dataState }
            .launchIn(viewModelScope)
    }

    fun insertAdsIntoList(documents: List<DocumentRow>): List<Any> {
        if (!remoteConfigRepository.isNativeAdEnabled() || documents.isEmpty()) {
            return documents
        }

        val startPosition = remoteConfigRepository.getNativeAdStartPosition()
        val interval = remoteConfigRepository.getNativeAdInterval()

        val combinedList = mutableListOf<Any>()
        var itemsSinceLastAd = 0

        documents.forEachIndexed { index, document ->
            combinedList.add(document)
            itemsSinceLastAd++

            val isAfterStartPosition = (index + 1) >= startPosition
            val isIntervalReached = itemsSinceLastAd >= interval

            if (isAfterStartPosition && isIntervalReached) {
                adPoolManager.getAd()?.let { ad ->
                    combinedList.add(ad)
                    itemsSinceLastAd = 0
                }
            }
        }
        return combinedList
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun onScanButtonClicked(activity: Activity) {
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

    fun mergeSelectedDocuments() {
        viewModelScope.launch {
            val selectedIds = _selectedDocumentIds.value.toList()

            crashlytics.setCustomKey("operation", "merge")
            crashlytics.setCustomKey("merge_document_count", selectedIds.size)

            _loadingState.update { it.copy(isBusy = true, message = "Объединение...") }
            try {
                val newTitle = mergeDocumentsUseCase(selectedIds, folderId)

                analytics.logEvent(
                    "documents_merged", bundleOf(
                        "merged_count" to selectedIds.size.toLong()
                    )
                )
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документы успешно объединены в '$newTitle'"))
            } catch (e: IllegalArgumentException) {
                _uiEventFlow.emit(UiEvent.ShowSnackbar(e.message ?: "Ошибка валидации"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to merge documents")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            } finally {
                clearSelection()
                _loadingState.update { it.copy(isBusy = false) }
                crashlytics.setCustomKey("operation", "none")
            }
        }
    }

    fun onSortClick() = _showSortSheet.update { true }
    fun onSortDismiss() = _showSortSheet.update { false }

    fun onSortByChanged(newSortBy: SortBy) {
        analytics.logEvent("sort_changed", bundleOf("type" to "sort_by", "value" to newSortBy.name))
        viewModelScope.launch {
            try {
                settingsRepository.saveSortBy(newSortBy)
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun onSortOrderChanged(newSortOrder: SortOrder) {
        analytics.logEvent(
            "sort_changed",
            bundleOf("type" to "sort_order", "value" to newSortOrder.name)
        )
        viewModelScope.launch {
            try {
                settingsRepository.saveSortOrder(newSortOrder)
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            try {
                val currentMode = settingsRepository.viewMode.first()
                val newMode = if (currentMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                analytics.logEvent(
                    "view_mode_toggled",
                    bundleOf("new_mode" to newMode.name.lowercase())
                )
                settingsRepository.saveViewMode(newMode)
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun selectAllDocuments() {
        analytics.logEvent("select_all_clicked", null)
        val currentState = _uiState.value
        if (currentState is HomeScreenUiState.Data) {
            val allIds = currentState.items.filterIsInstance<DocumentRow>().map { it.id }.toSet()
            savedStateHandle[selectedIdsKey] = allIds
        }
    }

    fun onSearchQueryChanged(query: String) {
        if (query != _searchQuery.value) {
            savedStateHandle[searchQueryKey] = query
        }
    }

    fun onDocumentClick(documentId: String) {
        if ((_uiState.value as? HomeScreenUiState.Data)?.isSelectionModeActive == true) {
            toggleSelection(documentId)
        }
    }

    fun onDocumentLongClick(documentId: String) {
        toggleSelection(documentId)
    }

    private fun toggleSelection(documentId: String) {
        val currentIds = _selectedDocumentIds.value
        val newIds =
            if (documentId in currentIds) currentIds - documentId else currentIds + documentId
        savedStateHandle[selectedIdsKey] = newIds
    }

    fun clearSelection() {
        savedStateHandle[selectedIdsKey] = emptySet<String>()
    }

    fun createNewDocument(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val requiredSpace = storageService.estimateForPages(uris.size)
            val operationDir = storageService.appFilesDir()

            var reservation = storageService.tryReserve(requiredSpace, dir = operationDir)

            reservation?.use { _ ->
                try {
                    crashlytics.log("Creating new document with ${uris.size} pages.")
                    val title = safeNamePolicy.newDocumentTitle()

                    Timber.d("ViewModel: Attempting to create document...")
                    val documentId = documentRepository.createDocument(title, uris, folderId)
                    Timber.d("ViewModel: Document creation call finished.")

                    val firstUri = uris.firstOrNull()?.toString() ?: ""
                    analytics.logEvent(
                        "document_created", bundleOf(
                            "page_count" to uris.size.toLong(),
                            "source" to if (firstUri.contains("com.google.android.gms")) "scanner" else "gallery"
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create new document")
                    crashlytics.recordException(e)
                    _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.ImageProcessingError))
                }
            } ?: run {
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.NotEnoughStorageError))
            }
        }
    }

    private fun enqueueOcr(documentId: String, documentTitle: String) {
        val inputData = workDataOf(
            OcrWorker.KEY_DOCUMENT_ID to documentId,
            OcrWorker.KEY_DOCUMENT_TITLE to documentTitle
        )
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("ocr_$documentId")
            .build()

        workManager.enqueueUniqueWork(
            "ocr_$documentId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun deleteDocument(documentId: String) = initiateDelete(listOf(documentId))

    fun deleteSelectedDocuments() {
        if ((_uiState.value as? HomeScreenUiState.Data)?.isSelectionModeActive == true) {
            initiateDelete(_selectedDocumentIds.value.toList())
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            try {
                val idsToUndo = _documentsPendingUndo.value
                if (idsToUndo.isEmpty()) return@launch
                deleteDocumentsUseCase.undo(idsToUndo)
                _documentsPendingUndo.value = emptyList()
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Удаление отменено"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to undo deletion")
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    private fun initiateDelete(documentIds: List<String>) {
        if (documentIds.isEmpty()) return

        analytics.logEvent("documents_deleted", bundleOf("count" to documentIds.size.toLong()))

        // Если есть предыдущие pending-документы, на которые пользователь не нажал undo,
        // считаем удаление подтверждённым — очищаем старый список
        _documentsPendingUndo.value = documentIds

        viewModelScope.launch {
            try {
                deleteDocumentsUseCase(documentIds)
                clearSelection()
                _uiEventFlow.emit(
                    UiEvent.ShowSnackbar(
                        message = "Документы удалены",
                        actionLabel = stringProvider.getString(R.string.action_cancel)
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to soft-delete documents")
                _documentsPendingUndo.value = emptyList()
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun onShareRequest() {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            val allDocuments = state.items.filterIsInstance<DocumentRow>()
            val selectedDocs = allDocuments.filter { it.id in _selectedDocumentIds.value }
            exportManager.requestExportForDocuments(
                documents = selectedDocs,
                defaultName = defaultExportName(selectedDocs),
                action = ExportAction.SHARE
            )
        }
    }

    fun onSaveRequest() {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            val allDocuments = state.items.filterIsInstance<DocumentRow>()
            val selectedDocs = allDocuments.filter { it.id in _selectedDocumentIds.value }
            exportManager.requestExportForDocuments(
                documents = selectedDocs,
                defaultName = defaultExportName(selectedDocs),
                action = ExportAction.SAVE
            )
        }
    }

    fun onShareSingleDocument(documentId: String) {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            val allDocuments = state.items.filterIsInstance<DocumentRow>()
            val selectedDocs = allDocuments.filter { it.id == documentId }
            exportManager.requestExportForDocuments(
                documents = selectedDocs,
                defaultName = defaultExportName(selectedDocs),
                action = ExportAction.SHARE
            )
        }
    }

    fun onDownloadSingleDocument(documentId: String) {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            val allDocuments = state.items.filterIsInstance<DocumentRow>()
            val selectedDocs = allDocuments.filter { it.id == documentId }
            exportManager.requestExportForDocuments(
                documents = selectedDocs,
                defaultName = defaultExportName(selectedDocs),
                action = ExportAction.SAVE
            )
        }
    }

    fun onShareDialogDismiss() {
        exportManager.onDialogDismiss()
        clearSelection()
    }

    fun onShareDialogConfirm(
        format: ExportFormat,
        filename: String,
        pdfProfile: PdfExportProfile? = null,
        fitToA4: Boolean? = null
    ) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Экспорт...") }
            val event = exportManager.onConfirmExport(format, filename, pdfProfile, fitToA4)
            _uiEventFlow.emit(event)
            if (event is UiEvent.LaunchSaveIntent) {
                _uiEventFlow.emit(UiEvent.RequestInAppReview)
            }
            _loadingState.update { it.copy(isBusy = false) }
            clearSelection()
        }
    }

    fun handleSaveResult(destinationUri: Uri?, sourceFile: File) {
        viewModelScope.launch {
            val event = exportManager.handleSaveResult(destinationUri, sourceFile)
            _uiEventFlow.emit(event)
        }
    }

    fun cleanUpTempFile(tempFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            exportManager.cleanUpTempFile(tempFile)
        }
    }

    fun renameDocument(documentId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            try {
                analytics.logEvent("document_renamed", null)
                documentRepository.renameDocument(documentId, newTitle)
            } catch (e: Exception) {
                Timber.e(e, "Failed to rename document")
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun moveSelectedDocuments(folderId: String?) {
        if ((_uiState.value as? HomeScreenUiState.Data)?.isSelectionModeActive == true) {
            viewModelScope.launch {
                try {
                    val idsToMove = _selectedDocumentIds.value.toList()
                    analytics.logEvent(
                        "document_moved",
                        bundleOf("count" to idsToMove.size.toLong())
                    )
                    documentRepository.moveDocumentsToFolder(idsToMove, folderId)
                    clearSelection()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to move selected documents")
                    _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
                }
            }
        }
    }

    fun moveDocument(documentId: String, folderId: String?) {
        viewModelScope.launch {
            try {
                analytics.logEvent("document_moved", bundleOf("count" to 1L))
                documentRepository.moveDocumentsToFolder(listOf(documentId), folderId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to move document")
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                documentRepository.createFolder(name)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create folder")
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    private fun defaultExportName(selected: List<DocumentRow>): String {
        if (selected.isEmpty()) return "export"
        val first = selected.first().title.ifBlank { "export" }
        return if (selected.size == 1) first else "$first + ${selected.size - 1}"
    }
}