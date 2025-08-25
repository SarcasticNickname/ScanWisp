package com.myprojects.scanwisp.ui.screens.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.model.ViewMode
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.domain.use_case.ExportDocumentUseCase
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.state.HomeScreenUiState
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

enum class ExportAction { SHARE, SAVE }

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
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val exportDocumentUseCase: ExportDocumentUseCase,
    private val fileManager: FileManager,
    private val safeNamePolicy: SafeNamePolicy
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

    private val deleteManager = DocumentDeleteManager(documentRepository)
    private val exportManager = DocumentExportManager(exportDocumentUseCase)

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
        viewModelScope.launch {
            // START: AI_MODIFIED_BLOCK - Логика получения заголовка изменена для использования ресурсов
            val screenTitleFlow = if (folderId != null) {
                documentRepository.getFolderById(folderId)?.let { folder ->
                    MutableStateFlow(folder.name)
                } ?: MutableStateFlow(context.getString(R.string.home_screen_title_default))
            } else {
                MutableStateFlow(context.getString(R.string.home_screen_title_default))
            }
            // END: AI_MODIFIED_BLOCK

            combine(
                documentRepository.getDocuments(folderId),
                documentRepository.getAllFolders(),
                deleteManager.pendingDeletionIds,
                _selectedDocumentIds,
                _loadingState,
                screenTitleFlow,
                _searchQuery,
                sortBy,
                sortOrder,
                settingsRepository.viewMode
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val documents = values[0] as List<DocumentWithPages>

                @Suppress("UNCHECKED_CAST")
                val folders = values[1] as List<FolderEntity>

                @Suppress("UNCHECKED_CAST")
                val pendingIds = values[2] as Set<String>

                @Suppress("UNCHECKED_CAST")
                val selectedIds = values[3] as Set<String>

                @Suppress("UNCHECKED_CAST")
                val loading = values[4] as LoadingState

                @Suppress("UNCHECKED_CAST")
                val title = values[5] as String

                @Suppress("UNCHECKED_CAST")
                val query = values[6] as String

                @Suppress("UNCHECKED_CAST")
                val currentSortBy = values[7] as SortBy

                @Suppress("UNCHECKED_CAST")
                val currentSortOrder = values[8] as SortOrder

                @Suppress("UNCHECKED_CAST")
                val viewMode = values[9] as ViewMode

                val searchedDocuments = if (query.isBlank()) {
                    documents
                } else {
                    documents.filter { it.document.title.contains(query, ignoreCase = true) }
                }

                val sortedDocuments = when (currentSortBy) {
                    SortBy.DATE -> {
                        if (currentSortOrder == SortOrder.DESCENDING) {
                            searchedDocuments.sortedByDescending { it.document.creationTimestamp }
                        } else {
                            searchedDocuments.sortedBy { it.document.creationTimestamp }
                        }
                    }

                    SortBy.NAME -> {
                        if (currentSortOrder == SortOrder.DESCENDING) {
                            searchedDocuments.sortedByDescending { it.document.title }
                        } else {
                            searchedDocuments.sortedBy { it.document.title }
                        }
                    }
                }

                HomeScreenUiState.Data(
                    screenTitle = title,
                    documents = sortedDocuments.filter { it.document.id !in pendingIds },
                    allFolders = folders,
                    nativeAd = (_uiState.value as? HomeScreenUiState.Data)?.nativeAd,
                    loadingState = loading,
                    selectedDocumentIds = selectedIds,
                    searchQuery = query,
                    viewMode = viewMode
                )
            }.map { data ->
                data as HomeScreenUiState
            }.catch { e ->
                Log.e("HomeViewModel", "Error in state flow", e)
                emit(HomeScreenUiState.Error(context.getString(R.string.error_failed_to_load_documents)))
            }.collect { dataState ->
                _uiState.value = dataState
            }
        }
        loadNativeAd()
    }

    fun mergeSelectedDocuments() {
        viewModelScope.launch {
            val selectedIds = _selectedDocumentIds.value.toList()
            if (selectedIds.size < 2) {
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Выберите хотя бы два документа для объединения"))
                return@launch
            }
            _loadingState.update { it.copy(isBusy = true, message = "Объединение...") }
            try {
                val newTitle = safeNamePolicy.newDocumentTitle()
                documentRepository.mergeDocuments(selectedIds, newTitle, folderId)
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документы успешно объединены в '$newTitle'"))
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to merge documents", e)
                _uiEventFlow.emit(
                    UiEvent.ShowSnackbar(
                        "Ошибка при объединении документов",
                        isError = true
                    )
                )
            } finally {
                clearSelection()
                _loadingState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun onSortClick() {
        _showSortSheet.value = true
    }

    fun onSortDismiss() {
        _showSortSheet.value = false
    }

    fun onSortByChanged(newSortBy: SortBy) {
        viewModelScope.launch {
            settingsRepository.saveSortBy(newSortBy)
        }
    }

    fun onSortOrderChanged(newSortOrder: SortOrder) {
        viewModelScope.launch {
            settingsRepository.saveSortOrder(newSortOrder)
        }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val currentMode = (uiState.value as? HomeScreenUiState.Data)?.viewMode ?: ViewMode.GRID
            val newMode = if (currentMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
            settingsRepository.saveViewMode(newMode)
        }
    }

    fun selectAllDocuments() {
        val currentState = _uiState.value
        if (currentState is HomeScreenUiState.Data) {
            val allDocumentIds = currentState.documents.map { it.document.id }.toSet()
            _selectedDocumentIds.value = allDocumentIds
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
        deleteManager.cancelDeletion()
        super.onCleared()
    }

    fun onDocumentClick(documentId: String) {
        val currentState = _uiState.value
        if (currentState is HomeScreenUiState.Data && currentState.isSelectionModeActive) {
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
            val title = safeNamePolicy.newDocumentTitle()

            val pageData = scannedUris.mapNotNull { uri ->
                fileManager.copyUriToAppStorage(uri)?.toString()?.let { path ->
                    path to path
                }
            }

            if (pageData.isNotEmpty()) {
                documentRepository.createDocument(title, pageData, folderId)
            }
        }
    }

    fun deleteDocument(documentId: String) {
        deleteManager.initiateDelete(listOf(documentId))
    }

    fun deleteSelectedDocuments() {
        val currentState = _uiState.value
        if (currentState is HomeScreenUiState.Data) {
            deleteManager.initiateDelete(currentState.selectedDocumentIds.toList())
        }
    }

    fun undoDelete() {
        deleteManager.undoDelete()
    }

    fun onShareRequest() {
        exportManager.onRequest(ExportAction.SHARE)
    }

    fun onSaveRequest() {
        exportManager.onRequest(ExportAction.SAVE)
    }

    fun onShareSingleDocument(documentId: String) {
        _selectedDocumentIds.value = setOf(documentId)
        exportManager.onRequest(ExportAction.SHARE)
    }

    fun onDownloadSingleDocument(documentId: String) {
        _selectedDocumentIds.value = setOf(documentId)
        exportManager.onRequest(ExportAction.SAVE)
    }

    fun onShareDialogDismiss() {
        exportManager.onDialogDismiss()
    }

    fun onShareDialogConfirm(format: ExportFormat, filename: String) {
        exportManager.onDialogConfirm(format, filename)
    }

    fun handleSaveResult(destinationUri: Uri?, sourceFile: File) {
        exportManager.handleSaveResult(destinationUri, sourceFile)
    }

    fun cleanUpTempFile(tempFile: File) {
        exportManager.cleanUpTempFile(tempFile)
    }

    fun renameDocument(documentId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { documentRepository.renameDocument(documentId, newTitle) }
    }

    fun moveSelectedDocuments(folderId: String?) {
        val currentState = _uiState.value
        if (currentState is HomeScreenUiState.Data) {
            viewModelScope.launch {
                val idsToMove = currentState.selectedDocumentIds.toList()
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

    private inner class DocumentDeleteManager(private val docRepo: DocumentRepository) {
        private val _pendingDeletionIds = MutableStateFlow<Set<String>>(emptySet())
        val pendingDeletionIds: StateFlow<Set<String>> = _pendingDeletionIds.asStateFlow()

        private val documentsPendingDeletion = mutableListOf<String>()
        private var deleteJob: Job? = null

        fun initiateDelete(documentIds: List<String>) {
            if (documentIds.isEmpty()) return
            cancelDeletion()

            documentsPendingDeletion.clear()
            documentsPendingDeletion.addAll(documentIds)
            _pendingDeletionIds.update { it + documentIds.toSet() }
            clearSelection()

            viewModelScope.launch {
                _uiEventFlow.emit(
                    UiEvent.ShowSnackbar(
                        "Документы удалены",
                        actionLabel = "Отменить"
                    )
                )
            }

            deleteJob = viewModelScope.launch {
                delay(5000)
                docRepo.deleteDocumentsByIds(documentIds)
                _pendingDeletionIds.update { it - documentIds.toSet() }
                documentsPendingDeletion.clear()
            }
        }

        fun undoDelete() {
            cancelDeletion()
            _pendingDeletionIds.update { it - documentsPendingDeletion.toSet() }
            documentsPendingDeletion.clear()
            viewModelScope.launch {
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Удаление отменено"))
            }
        }

        fun cancelDeletion() {
            deleteJob?.cancel()
        }
    }

    private inner class DocumentExportManager(private val exportDocument: ExportDocumentUseCase) {
        private val _shareDialogState = MutableStateFlow(ShareDialogState())
        val shareDialogState: StateFlow<ShareDialogState> = _shareDialogState.asStateFlow()

        fun onRequest(action: ExportAction) {
            val currentState = _uiState.value as? HomeScreenUiState.Data ?: return
            val pageCount = calculateSelectedPageCount(currentState)
            val isSingleItemAction = currentState.selectedDocumentIds.size == 1
            _shareDialogState.value = ShareDialogState(
                isVisible = true,
                pageCount = pageCount,
                defaultName = generateDefaultFilename(currentState),
                action = action
            )
            if (isSingleItemAction) {
                clearSelection()
            }
        }

        fun onDialogDismiss() {
            _shareDialogState.value = ShareDialogState(isVisible = false)
        }

        fun onDialogConfirm(format: ExportFormat, filename: String) {
            val action = _shareDialogState.value.action
            onDialogDismiss()

            viewModelScope.launch {
                _loadingState.update { it.copy(isBusy = true, message = "Экспорт...") }
                try {
                    val currentState = _uiState.value as? HomeScreenUiState.Data ?: return@launch
                    val pageIdsToExport = calculateSelectedPageIds(currentState)

                    if (pageIdsToExport.isEmpty()) {
                        _uiEventFlow.emit(
                            UiEvent.ShowSnackbar(
                                "Нет страниц для экспорта",
                                isError = true
                            )
                        )
                        return@launch
                    }

                    val exportResult = exportDocument(pageIdsToExport, filename, format)

                    if (exportResult != null) {
                        val mimeType = when (format) {
                            ExportFormat.PDF -> "application/pdf"
                            ExportFormat.JPEG -> "image/jpeg"
                            ExportFormat.ZIP -> "application/zip"
                        }

                        if (action == ExportAction.SHARE) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, exportResult.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = ClipData.newUri(
                                    context.contentResolver,
                                    exportResult.tempFile.name,
                                    exportResult.uri
                                )
                            }

                            _uiEventFlow.emit(
                                UiEvent.LaunchShareIntent(
                                    Intent.createChooser(
                                        shareIntent,
                                        "Поделиться..."
                                    ), exportResult.tempFile
                                )
                            )
                        } else { // SAVE
                            val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = mimeType
                                putExtra(Intent.EXTRA_TITLE, exportResult.tempFile.name)
                            }
                            _uiEventFlow.emit(
                                UiEvent.LaunchSaveIntent(
                                    saveIntent,
                                    exportResult.tempFile
                                )
                            )
                        }
                    } else {
                        throw Exception("Export failed, resulted URI is null.")
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Export process failed", e)
                    _uiEventFlow.emit(
                        UiEvent.ShowSnackbar(
                            "Ошибка при экспорте файла",
                            isError = true
                        )
                    )
                } finally {
                    _loadingState.update { it.copy(isBusy = false) }
                }
            }
        }

        fun handleSaveResult(destinationUri: Uri?, sourceFile: File) {
            if (destinationUri == null) {
                viewModelScope.launch {
                    _uiEventFlow.emit(
                        UiEvent.ShowSnackbar(
                            "Сохранение отменено",
                            isError = true
                        )
                    )
                }
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(
                                outputStream
                            )
                        }
                    }
                    withContext(Dispatchers.Main) { _uiEventFlow.emit(UiEvent.ShowSnackbar("Файл успешно сохранен")) }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Failed to save file", e)
                    withContext(Dispatchers.Main) {
                        _uiEventFlow.emit(
                            UiEvent.ShowSnackbar(
                                "Не удалось сохранить файл",
                                isError = true
                            )
                        )
                    }
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
                    Log.e("HomeViewModel", "Failed to clean temp file", e)
                }
            }
        }

        private fun calculateSelectedPageCount(state: HomeScreenUiState.Data): Int {
            return state.selectedDocumentIds.sumOf { docId ->
                state.documents.find { it.document.id == docId }?.pages?.size ?: 0
            }
        }

        private fun generateDefaultFilename(state: HomeScreenUiState.Data): String {
            return if (state.selectedDocumentIds.size == 1) {
                state.documents.find { it.document.id == state.selectedDocumentIds.first() }?.document?.title
                    ?: "ScanWisp Document"
            } else {
                "ScanWisp Export"
            }
        }

        private fun calculateSelectedPageIds(state: HomeScreenUiState.Data): List<String> {
            return state.selectedDocumentIds.flatMap { docId ->
                state.documents.find { it.document.id == docId }?.pages?.map { it.id }
                    ?: emptyList()
            }
        }
    }
}

private suspend fun DocumentRepository.deleteDocumentsByIds(documentIds: List<String>) {
    documentIds.forEach { deleteDocumentById(it) }
}