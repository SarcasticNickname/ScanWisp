package com.myprojects.scanwisp.ui.screens.trash

import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.ui.events.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
) : ViewModel() {

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<TrashUiState> = combine(
        repository.getDeletedDocumentRows(),
        _selectedIds
    ) { documents, selected ->
        TrashUiState.Success(documents, selected)
    }
        .map { it as TrashUiState }
        .catch { e ->
            crashlytics.recordException(e)
            emit(TrashUiState.Error(AppError.LoadDataError))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrashUiState.Loading
        )

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    fun onDocumentClick(documentId: String) {
        if ((uiState.value as? TrashUiState.Success)?.isSelectionModeActive == true) {
            _selectedIds.update { if (documentId in it) it - documentId else it + documentId }
        }
    }

    fun onDocumentLongClick(documentId: String) {
        _selectedIds.update { it + documentId }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun restoreSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                ids.forEach { repository.restoreDocument(it) }
                analytics.logEvent("documents_restored", bundleOf("count" to ids.size.toLong()))
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документы восстановлены"))
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            } finally {
                clearSelection()
            }
        }
    }

    fun deleteSelectedPermanently() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.deleteDocumentsPermanently(ids)
                analytics.logEvent(
                    "documents_deleted_permanently",
                    bundleOf("count" to ids.size.toLong())
                )
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документы удалены навсегда"))
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            } finally {
                clearSelection()
            }
        }
    }

    fun restoreDocument(documentId: String) {
        viewModelScope.launch {
            try {
                repository.restoreDocument(documentId)
                analytics.logEvent("documents_restored", bundleOf("count" to 1L))
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документ восстановлен"))
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun deleteDocumentPermanently(documentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteDocumentsPermanently(listOf(documentId))
                analytics.logEvent("documents_deleted_permanently", bundleOf("count" to 1L))
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Документ удален навсегда"))
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }

    fun emptyTrash() {
        val allIds = (uiState.value as? TrashUiState.Success)?.documents?.map { it.id } ?: return
        if (allIds.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.deleteDocumentsPermanently(allIds)
                analytics.logEvent("trash_emptied", bundleOf("count" to allIds.size.toLong()))
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Корзина очищена"))
            } catch (e: Exception) {
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.DatabaseOperationError))
            }
        }
    }
}