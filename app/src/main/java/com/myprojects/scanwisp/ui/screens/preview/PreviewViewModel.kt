package com.myprojects.scanwisp.ui.screens.preview

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.myprojects.scanwisp.core.storage.StorageService
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.OcrMode
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.StringProvider
import com.myprojects.scanwisp.domain.use_case.RecognizePageUseCase
import com.myprojects.scanwisp.domain.use_case.SaveEditedTextUseCase
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.state.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
    private val stringProvider: StringProvider,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private val storageService: StorageService,
    private val recognizePageUseCase: RecognizePageUseCase,
    private val saveEditedTextUseCase: SaveEditedTextUseCase
) : ViewModel() {

    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    val uiState: StateFlow<PreviewUiState> = repository.getPageById(pageId)
        .map { page ->
            if (page != null) {
                PreviewUiState.Success(page)
            } else {
                // Если страница не найдена, это тоже ошибка данных
                PreviewUiState.Error(AppError.LoadDataError)
            }
        }
        .catch { e ->
            Timber.e(e, "Error loading page")
            crashlytics.recordException(e)
            emit(PreviewUiState.Error(AppError.LoadDataError))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreviewUiState.Loading
        )

    private val _loadingState = MutableStateFlow(LoadingState())
    val loadingState = _loadingState.asStateFlow()

    private val _uiEventFlow = MutableSharedFlow<UiEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    fun onRescanClicked(activity: Activity) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Подготовка сканера...") }

            val scannerOptions = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
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

    // Метод
    fun replaceImage(newImageUri: Uri) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Замена изображения...") }

            val requiredSpace = storageService.estimateForPages(1)
            val operationDir = storageService.appFilesDir()

            var reservation = storageService.tryReserve(requiredSpace, dir = operationDir)

            reservation?.use { _ ->
                try {
                    analytics.logEvent("page_image_replaced", null)
                    repository.replacePageImage(pageId, newImageUri)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to replace page image.")
                    crashlytics.recordException(e)
                    _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.ImageProcessingError))
                } finally {
                    _loadingState.update { it.copy(isBusy = false) }
                }
            } ?: run {
                _loadingState.update { it.copy(isBusy = false) }
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.NotEnoughStorageError))
            }
        }
    }

    // Flow для показа текста
    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText = _recognizedText.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode = _isEditMode.asStateFlow()

    // Буфер редактирования — отдельно от _recognizedText,
    // чтобы отмена правок не затирала отображаемый текст
    private val _editBuffer = MutableStateFlow("")
    val editBuffer = _editBuffer.asStateFlow()

    private val _showOverwriteWarning = MutableStateFlow(false)
    val showOverwriteWarning = _showOverwriteWarning.asStateFlow()

    // Храним pending-mode для случая когда юзер подтвердил overwrite
    private var pendingOcrMode: OcrMode? = null

    private val _lastOcrMode = MutableStateFlow<OcrMode?>(null)

    val canImprove: StateFlow<Boolean> = combine(_recognizedText, _lastOcrMode) { text, mode ->
        text != null && mode == OcrMode.FAST
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onRecognizeTextClicked() {
        launchOcr(mode = OcrMode.FAST, forceRerun = false)
    }

    fun onImproveRecognitionClicked() {
        launchOcr(mode = OcrMode.FULL, forceRerun = true)
    }

    // --- Методы редактирования ---

    fun onEditTextClicked() {
        _editBuffer.value = _recognizedText.value ?: ""
        _isEditMode.value = true
    }

    fun onEditBufferChanged(text: String) {
        _editBuffer.value = text
    }

    fun onSaveEditClicked() {
        viewModelScope.launch {
            val newText = _editBuffer.value
            saveEditedTextUseCase(pageId, newText)
            _recognizedText.value = newText
            _isEditMode.value = false
        }
    }

    fun onCancelEditClicked() {
        _isEditMode.value = false
        _editBuffer.value = ""
    }

    private fun launchOcr(mode: OcrMode, forceRerun: Boolean) {
        viewModelScope.launch {
            // Если текст был отредактирован вручную — предупреждаем перед перезаписью
            val page = (uiState.value as? PreviewUiState.Success)?.page
            if (forceRerun && page?.isTextUserEdited == true) {
                pendingOcrMode = mode
                _showOverwriteWarning.value = true
                return@launch
            }
            executeLaunchOcr(mode, forceRerun)
        }
    }

    fun onOverwriteConfirmed() {
        _showOverwriteWarning.value = false
        val mode = pendingOcrMode ?: return
        pendingOcrMode = null
        viewModelScope.launch { executeLaunchOcr(mode, forceRerun = true) }
    }

    fun onOverwriteDismissed() {
        _showOverwriteWarning.value = false
        pendingOcrMode = null
    }

    private suspend fun executeLaunchOcr(mode: OcrMode, forceRerun: Boolean) {
        val message = if (mode == OcrMode.FAST) "Распознавание текста..."
        else "Улучшенное распознавание (Full)..."
        _loadingState.update { it.copy(isBusy = true, message = message) }
        try {
            val text = recognizePageUseCase(pageId, mode = mode, forceRerun = forceRerun)
            if (text.isNotBlank()) {
                _recognizedText.value = text
                _lastOcrMode.value = mode
                analytics.logEvent("ocr_success", null)
            } else {
                _uiEventFlow.emit(UiEvent.ShowSnackbar("Текст не найден"))
            }
        } catch (e: Exception) {
            Timber.e(e, "OCR failed")
            crashlytics.recordException(e)
            _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.General("Ошибка распознавания")))
        } finally {
            _loadingState.update { it.copy(isBusy = false) }
        }
    }

    fun dismissTextDialog() {
        _recognizedText.value = null
        _lastOcrMode.value = null
        _isEditMode.value = false
        _editBuffer.value = ""
    }
}