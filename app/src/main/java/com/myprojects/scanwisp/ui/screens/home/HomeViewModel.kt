package com.myprojects.scanwisp.ui.screens.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ViewMode
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.domain.use_case.DeleteDocumentsUseCase
import com.myprojects.scanwisp.domain.use_case.MergeDocumentsUseCase
import com.myprojects.scanwisp.ui.delegate.ExportManagerDelegate
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.state.HomeScreenUiState
import com.myprojects.scanwisp.ui.state.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ExportAction { SHARE, SAVE }

@Immutable
data class ShareDialogState(
    val isVisible: Boolean = false,
    val pageCount: Int = 0,
    val defaultName: String = "",
    val action: ExportAction = ExportAction.SHARE
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    @ApplicationContext private val context: Context,
    private val deleteDocumentsUseCase: DeleteDocumentsUseCase,
    private val mergeDocumentsUseCase: MergeDocumentsUseCase,
    // ИЗМЕНЕНИЕ: Внедряем наш новый делегат для экспорта
    private val exportManager: ExportManagerDelegate,
    savedStateHandle: SavedStateHandle,
    private val safeNamePolicy: com.myprojects.scanwisp.utils.SafeNamePolicy,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
) : ViewModel() {

    private val folderId: String? = savedStateHandle["folderId"]

    private val _uiState = MutableStateFlow<HomeScreenUiState>(HomeScreenUiState.Loading)
    val uiState: StateFlow<HomeScreenUiState> = _uiState.asStateFlow()

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    private val _loadingState = MutableStateFlow(LoadingState())
    private val _selectedDocumentIds = MutableStateFlow<Set<String>>(emptySet())
    private val _searchQuery = MutableStateFlow("")

    private val _showSortSheet = MutableStateFlow(false)
    val showSortSheet = _showSortSheet.asStateFlow()

    private val _documentsPendingUndo = MutableStateFlow<List<String>>(emptyList())

    // ИЗМЕНЕНИЕ: Состояние диалога теперь берем напрямую из делегата
    val shareDialogState: StateFlow<ShareDialogState> = exportManager.shareDialogState

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
        // ... (init блок остается без изменений)
        crashlytics.setCustomKey("current_screen", if (folderId == null) "home" else "folder_view")

        viewModelScope.launch {
            val screenTitleFlow = if (folderId != null) {
                documentRepository.getFolderById(folderId)?.let { folder ->
                    MutableStateFlow(folder.name)
                } ?: MutableStateFlow(context.getString(R.string.home_screen_title_default))
            } else {
                MutableStateFlow(context.getString(R.string.home_screen_title_default))
            }

            @OptIn(FlowPreview::class)
            val documentsFlow = _searchQuery
                .debounce(300L)
                .flatMapLatest { query ->
                    documentRepository.getDocumentRows(folderId, query)
                }

            combine(
                documentsFlow,
                documentRepository.getAllFolders(),
                _selectedDocumentIds,
                _loadingState,
                screenTitleFlow,
                _searchQuery,
                sortBy,
                sortOrder,
                settingsRepository.viewMode
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val documents = values[0] as List<DocumentRow>

                @Suppress("UNCHECKED_CAST")
                val folders = values[1] as List<FolderEntity>

                @Suppress("UNCHECKED_CAST")
                val selectedIds = values[2] as Set<String>

                @Suppress("UNCHECKED_CAST")
                val loading = values[3] as LoadingState

                @Suppress("UNCHECKED_CAST")
                val title = values[4] as String

                @Suppress("UNCHECKED_CAST")
                val query = values[5] as String

                @Suppress("UNCHECKED_CAST")
                val currentSortBy = values[6] as SortBy

                @Suppress("UNCHECKED_CAST")
                val currentSortOrder = values[7] as SortOrder

                @Suppress("UNCHECKED_CAST")
                val viewMode = values[8] as ViewMode

                val adPosition = remoteConfigRepository.getNativeAdPosition()

                val sortedDocuments = when (currentSortBy) {
                    SortBy.DATE -> {
                        if (currentSortOrder == SortOrder.DESCENDING) {
                            documents.sortedByDescending { it.creationTimestamp }
                        } else {
                            documents.sortedBy { it.creationTimestamp }
                        }
                    }

                    SortBy.NAME -> {
                        if (currentSortOrder == SortOrder.DESCENDING) {
                            documents.sortedByDescending { it.title }
                        } else {
                            documents.sortedBy { it.title }
                        }
                    }
                }

                HomeScreenUiState.Data(
                    screenTitle = title,
                    documents = sortedDocuments,
                    allFolders = folders,
                    nativeAd = (_uiState.value as? HomeScreenUiState.Data)?.nativeAd,
                    loadingState = loading,
                    selectedDocumentIds = selectedIds,
                    searchQuery = query,
                    viewMode = viewMode,
                    adPosition = adPosition
                )
            }.map { data ->
                data as HomeScreenUiState
            }.catch { e ->
                Log.e("HomeViewModel", "Error in state flow", e)
                crashlytics.recordException(e)
                emit(HomeScreenUiState.Error(context.getString(R.string.error_failed_to_load_documents)))
            }.collect { dataState ->
                _uiState.value = dataState
            }
        }
        loadNativeAd()
    }

    // ... (mergeSelectedDocuments, onSortClick и т.д. остаются без изменений)
    fun mergeSelectedDocuments() {
        viewModelScope.launch {
            val selectedIds = _selectedDocumentIds.value.toList()

            crashlytics.setCustomKey("operation", "merge")
            crashlytics.setCustomKey("merge_document_count", selectedIds.size)

            _loadingState.update { it.copy(isBusy = true, message = "Объединение...") }
            try {
                // Вызываем UseCase, который содержит всю бизнес-логику
                val newTitle = mergeDocumentsUseCase(selectedIds, folderId)

                // Логика, специфичная для ViewModel (аналитика, UI-события) остается здесь
                analytics.logEvent("documents_merged") {
                    param("merged_count", selectedIds.size.toLong())
                }
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документы успешно объединены в '$newTitle'"))
            } catch (e: IllegalArgumentException) {
                // Обрабатываем конкретную ошибку от UseCase
                _uiEventFlow.emit(UiEvent.ShowSnackbar(e.message ?: "Ошибка валидации"))
            } catch (e: Exception) {
                // Обрабатываем все остальные ошибки
                Log.e("HomeViewModel", "Failed to merge documents", e)
                crashlytics.recordException(e)
                _uiEventFlow.emit(
                    UiEvent.ShowSnackbar(
                        "Ошибка при объединении документов",
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

    fun onSortClick() = _showSortSheet.update { true }
    fun onSortDismiss() = _showSortSheet.update { false }

    fun onSortByChanged(newSortBy: SortBy) {
        viewModelScope.launch { settingsRepository.saveSortBy(newSortBy) }
    }

    fun onSortOrderChanged(newSortOrder: SortOrder) {
        viewModelScope.launch { settingsRepository.saveSortOrder(newSortOrder) }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val currentMode = (uiState.value as? HomeScreenUiState.Data)?.viewMode ?: ViewMode.GRID
            val newMode = if (currentMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
            analytics.logEvent("view_mode_toggled") { param("new_mode", newMode.name.lowercase()) }
            settingsRepository.saveViewMode(newMode)
        }
    }

    fun selectAllDocuments() {
        val currentState = _uiState.value
        if (currentState is HomeScreenUiState.Data) {
            _selectedDocumentIds.value = currentState.documents.map { it.id }.toSet()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun loadNativeAd() {
        val adUnitId = "ca-app-pub-3940256099942544/2247696110"
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad: NativeAd ->
                val currentState = _uiState.value
                if (currentState is HomeScreenUiState.Data) {
                    currentState.nativeAd?.destroy()
                    _uiState.value = currentState.copy(nativeAd = ad)
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdMob", "Native ad failed to load: ${adError.message}")
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    override fun onCleared() {
        (_uiState.value as? HomeScreenUiState.Data)?.nativeAd?.destroy()
        super.onCleared()
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
        _selectedDocumentIds.update { currentIds ->
            if (documentId in currentIds) currentIds - documentId else currentIds + documentId
        }
    }

    fun clearSelection() {
        _selectedDocumentIds.value = emptySet()
    }

    fun createNewDocument(scannedUris: List<Uri>) {
        if (scannedUris.isEmpty()) return
        viewModelScope.launch {
            crashlytics.log("Creating new document with ${scannedUris.size} pages.")
            val title = safeNamePolicy.newDocumentTitle()
            documentRepository.createDocument(title, scannedUris, folderId)
            analytics.logEvent("document_created") {
                param("page_count", scannedUris.size.toLong())
                val firstUri = scannedUris.firstOrNull()?.toString() ?: ""
                param(
                    "source",
                    if (firstUri.contains("com.google.android.gms")) "scanner" else "gallery"
                )
            }
        }
    }

    fun deleteDocument(documentId: String) = initiateDelete(listOf(documentId))

    fun deleteSelectedDocuments() {
        if ((_uiState.value as? HomeScreenUiState.Data)?.isSelectionModeActive == true) {
            initiateDelete(_selectedDocumentIds.value.toList())
        }
    }

    fun undoDelete() {
        val idsToUndo = _documentsPendingUndo.value
        if (idsToUndo.isEmpty()) return

        viewModelScope.launch {
            deleteDocumentsUseCase.undo(idsToUndo)
            _documentsPendingUndo.value = emptyList()
            _uiEventFlow.emit(UiEvent.ShowSnackbar("Удаление отменено"))
        }
    }

    private fun initiateDelete(documentIds: List<String>) {
        if (documentIds.isEmpty()) return

        _documentsPendingUndo.value = documentIds

        viewModelScope.launch {
            deleteDocumentsUseCase(documentIds)
            clearSelection()
            _uiEventFlow.emit(
                UiEvent.ShowSnackbar(
                    message = "Документы удалены",
                    actionLabel = context.getString(R.string.action_cancel)
                )
            )
        }
    }

    // ==========================================================
    // ИЗМЕНЕНИЕ: Все методы экспорта теперь делегируют вызовы ExportManagerDelegate
    // ==========================================================
    fun onShareRequest() {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            exportManager.requestExportForDocuments(
                _selectedDocumentIds.value.toList(),
                state.documents,
                ExportAction.SHARE
            )
            clearSelection()
        }
    }

    fun onSaveRequest() {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            exportManager.requestExportForDocuments(
                _selectedDocumentIds.value.toList(),
                state.documents,
                ExportAction.SAVE
            )
            clearSelection()
        }
    }

    fun onShareSingleDocument(documentId: String) {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            exportManager.requestExportForDocuments(
                listOf(documentId),
                state.documents,
                ExportAction.SHARE
            )
        }
    }

    fun onDownloadSingleDocument(documentId: String) {
        viewModelScope.launch {
            val state = _uiState.value as? HomeScreenUiState.Data ?: return@launch
            exportManager.requestExportForDocuments(
                listOf(documentId),
                state.documents,
                ExportAction.SAVE
            )
        }
    }

    fun onShareDialogDismiss() = exportManager.onDialogDismiss()

    fun onShareDialogConfirm(format: ExportFormat, filename: String) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Экспорт...") }
            val event = exportManager.onConfirmExport(format, filename)
            _uiEventFlow.emit(event)
            // Дополнительно эмитим событие для In-App Review, если это было сохранение
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
    // ==========================================================

    fun renameDocument(documentId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { documentRepository.renameDocument(documentId, newTitle) }
    }

    fun moveSelectedDocuments(folderId: String?) {
        if ((_uiState.value as? HomeScreenUiState.Data)?.isSelectionModeActive == true) {
            viewModelScope.launch {
                val idsToMove = _selectedDocumentIds.value.toList()
                documentRepository.moveDocumentsToFolder(idsToMove, folderId)
                clearSelection()
            }
        }
    }

    fun moveDocument(documentId: String, folderId: String?) {
        viewModelScope.launch {
            documentRepository.moveDocumentsToFolder(listOf(documentId), folderId)
        }
    }
}