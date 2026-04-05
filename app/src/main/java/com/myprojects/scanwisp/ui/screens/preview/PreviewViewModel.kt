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
import com.myprojects.scanwisp.domain.model.EditableWord
import com.myprojects.scanwisp.domain.model.OcrMode
import com.myprojects.scanwisp.domain.model.TextEditMode
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private val storageService: StorageService,
    private val recognizePageUseCase: RecognizePageUseCase,
    private val saveEditedTextUseCase: SaveEditedTextUseCase,
    @Suppress("UNUSED_PARAMETER") stringProvider: StringProvider
) : ViewModel() {

    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    // ─── Основное состояние страницы ─────────────────────────────────────────

    val uiState: StateFlow<PreviewUiState> = repository.getPageById(pageId)
        .map { page ->
            if (page != null) PreviewUiState.Success(page)
            else PreviewUiState.Error(AppError.LoadDataError)
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

    // ─── Текст и шторка ──────────────────────────────────────────────────────

    /** Распознанный или отредактированный plain-text. Не обнуляется при закрытии шторки. */
    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText = _recognizedText.asStateFlow()

    /** Видимость шторки — отдельно от наличия текста. */
    private val _isSheetVisible = MutableStateFlow(false)
    val isSheetVisible = _isSheetVisible.asStateFlow()

    // ─── Навигация ───────────────────────────────────────────────────────────

    private val _prevPageId = MutableStateFlow<String?>(null)
    val prevPageId = _prevPageId.asStateFlow()

    private val _nextPageId = MutableStateFlow<String?>(null)
    val nextPageId = _nextPageId.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages = _totalPages.asStateFlow()

    // ─── Редактирование ──────────────────────────────────────────────────────

    /** Активен ли режим редактирования (токенный или свободный). */
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode = _isEditMode.asStateFlow()

    /** Подрежим: токены (позиции сохраняются) или свободный текст (reconcile). */
    private val _textEditMode = MutableStateFlow<TextEditMode>(TextEditMode.Token)
    val textEditMode = _textEditMode.asStateFlow()

    /** Список слов-токенов для редактора. */
    private val _editableWords = MutableStateFlow<List<EditableWord>>(emptyList())
    val editableWords = _editableWords.asStateFlow()

    /** ID слова, которое сейчас редактируется в инлайн-поле. */
    private val _activeWordId = MutableStateFlow<String?>(null)
    val activeWordId = _activeWordId.asStateFlow()

    /** Буфер для свободного текстового редактора. */
    private val _freeTextBuffer = MutableStateFlow("")
    val freeTextBuffer = _freeTextBuffer.asStateFlow()

    // ─── Диалоги ─────────────────────────────────────────────────────────────

    private val _showOverwriteWarning = MutableStateFlow(false)
    val showOverwriteWarning = _showOverwriteWarning.asStateFlow()

    private val _showRescanConfirmation = MutableStateFlow(false)
    val showRescanConfirmation = _showRescanConfirmation.asStateFlow()

    /** Диалог при переключении в свободный режим — предупреждение о позициях. */
    private val _showSwitchModeDialog = MutableStateFlow(false)
    val showSwitchModeDialog = _showSwitchModeDialog.asStateFlow()

    /** Флаг: пользователь уже видел предупреждение про режим в этой сессии. */
    private var switchModeWarningShown = false

    private var pendingOcrMode: OcrMode? = null

    // ─── Init ────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            val state = uiState.filterIsInstance<PreviewUiState.Success>().first()
            val page = state.page

            // Авто-показ уже распознанного текста без повторного OCR
            if (!page.extractedText.isNullOrBlank()) {
                _recognizedText.value = page.extractedText
                _isSheetVisible.value = true
            }

            // Соседние страницы для навигации
            val (prev, next) = repository.getAdjacentPageIds(page.documentOwnerId, page.position)
            _prevPageId.value = prev
            _nextPageId.value = next
            _totalPages.value = repository.getPageCount(page.documentOwnerId)
        }
    }

    // ─── Rescan ──────────────────────────────────────────────────────────────

    fun onRescanButtonClicked() {
        if (!_recognizedText.value.isNullOrBlank()) {
            _showRescanConfirmation.value = true
        } else {
            viewModelScope.launch { _uiEventFlow.emit(UiEvent.TriggerRescan) }
        }
    }

    fun onRescanConfirmed() {
        _showRescanConfirmation.value = false
        viewModelScope.launch { _uiEventFlow.emit(UiEvent.TriggerRescan) }
    }

    fun onRescanDismissed() { _showRescanConfirmation.value = false }

    fun launchRescan(activity: Activity) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Подготовка сканера...") }
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
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

    fun replaceImage(newImageUri: Uri) {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Замена изображения...") }
            storageService.tryReserve(
                storageService.estimateForPages(1),
                dir = storageService.appFilesDir()
            )
                ?.use {
                    try {
                        analytics.logEvent("page_image_replaced", null)
                        repository.replacePageImage(pageId, newImageUri)
                        _recognizedText.value = null
                        _isSheetVisible.value = false
                        _editableWords.value = emptyList()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to replace page image.")
                        crashlytics.recordException(e)
                        _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.ImageProcessingError))
                    }
                } ?: _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.NotEnoughStorageError))
            _loadingState.update { it.copy(isBusy = false) }
        }
    }

    // ─── OCR ─────────────────────────────────────────────────────────────────

    fun onRecognizeTextClicked() {
        if (_recognizedText.value != null) {
            _isSheetVisible.value = true
            return
        }
        viewModelScope.launch {
            val mode = runCatching { settingsRepository.defaultOcrMode.first() }
                .getOrDefault(OcrMode.FAST)
            launchOcr(mode = mode, forceRerun = false)
        }
    }

    private fun launchOcr(mode: OcrMode, forceRerun: Boolean) {
        viewModelScope.launch {
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

    fun onOverwriteDismissed() { _showOverwriteWarning.value = false; pendingOcrMode = null }

    private suspend fun executeLaunchOcr(mode: OcrMode, forceRerun: Boolean) {
        _loadingState.update { it.copy(isBusy = true, message = "Распознавание текста...") }
        try {
            val text = recognizePageUseCase(pageId, mode = mode, forceRerun = forceRerun)
            if (text.isNotBlank()) {
                _recognizedText.value = text
                _isSheetVisible.value = true
                // Обновляем токены если редактор был открыт
                if (_isEditMode.value) loadEditableWords()
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

    // ─── Шторка ──────────────────────────────────────────────────────────────

    fun onSheetDismissed() {
        _isSheetVisible.value = false
        _isEditMode.value = false
        _activeWordId.value = null
    }

    // ─── Вход в режим редактирования ─────────────────────────────────────────

    fun onEditTextClicked() {
        loadEditableWords()
        _freeTextBuffer.value = _recognizedText.value ?: ""
        _textEditMode.value = TextEditMode.Token
        _isEditMode.value = true
    }

    private fun loadEditableWords() {
        val page = (uiState.value as? PreviewUiState.Success)?.page ?: return
        _editableWords.value = page.wordBoxesJson
            ?.runCatching { EditableWord.fromJson(this) }
            ?.getOrNull()
            ?: emptyList()
    }

    // ─── Токенный редактор ────────────────────────────────────────────────────

    /** Начать редактирование конкретного слова. */
    fun onWordTap(wordId: String) {
        _activeWordId.value = if (_activeWordId.value == wordId) null else wordId
    }

    /** Обновить текст слова после завершения инлайн-редактирования. */
    fun onWordTextCommit(wordId: String, newText: String) {
        _activeWordId.value = null
        if (newText.isBlank()) return
        _editableWords.update { words ->
            words.map { if (it.id == wordId) it.copy(text = newText) else it }
        }
    }

    /** Отменить редактирование слова без сохранения. */
    fun onWordEditCancel() { _activeWordId.value = null }

    /** Пометить слово как удалённое (long press). */
    fun onWordDelete(wordId: String) {
        _editableWords.update { words ->
            words.map { if (it.id == wordId) it.copy(isDeleted = true) else it }
        }
    }

    /** Восстановить удалённое слово. */
    fun onWordRestore(wordId: String) {
        _editableWords.update { words ->
            words.map { if (it.id == wordId) it.copy(isDeleted = false) else it }
        }
    }

    // ─── Переключение режима ──────────────────────────────────────────────────

    fun onSwitchToFreeTextRequest() {
        if (switchModeWarningShown) {
            doSwitchToFreeText()
        } else {
            _showSwitchModeDialog.value = true
        }
    }

    fun onSwitchModeConfirmed() {
        switchModeWarningShown = true
        _showSwitchModeDialog.value = false
        doSwitchToFreeText()
    }

    fun onSwitchModeDismissed() { _showSwitchModeDialog.value = false }

    private fun doSwitchToFreeText() {
        // Переносим текущие токены в буфер свободного редактора
        val currentText = _editableWords.value
            .filter { !it.isDeleted }
            .joinToString(" ") { it.text }
        _freeTextBuffer.value = currentText.ifBlank { _recognizedText.value ?: "" }
        _textEditMode.value = TextEditMode.FreeText
    }

    fun onSwitchToTokenMode() {
        // Обновляем токены из текущего free-text буфера через reconcile
        _textEditMode.value = TextEditMode.Token
        // Токены остались в памяти из loadEditableWords(), free-text изменения отбрасываются
        // пока пользователь не нажал Save
    }

    fun onFreeTextBufferChanged(text: String) { _freeTextBuffer.value = text }

    // ─── Сохранение ──────────────────────────────────────────────────────────

    fun onSaveEditClicked() {
        viewModelScope.launch {
            _loadingState.update { it.copy(isBusy = true, message = "Сохранение...") }
            try {
                when (_textEditMode.value) {
                    is TextEditMode.Token -> {
                        // Путь A: токены — позиции сохраняются точно
                        val words = _editableWords.value
                        saveEditedTextUseCase.invokeFromTokens(pageId, words)
                        _recognizedText.value = words
                            .filter { !it.isDeleted }
                            .joinToString(" ") { it.text }
                    }
                    is TextEditMode.FreeText -> {
                        // Путь Б: свободный текст — reconcile через LCS
                        val text = _freeTextBuffer.value
                        saveEditedTextUseCase(pageId, text)
                        _recognizedText.value = text
                    }
                }
                _isEditMode.value = false
                _activeWordId.value = null
            } catch (e: Exception) {
                Timber.e(e, "Failed to save edited text")
                crashlytics.recordException(e)
                _uiEventFlow.emit(UiEvent.ShowErrorDialog(AppError.General("Ошибка сохранения")))
            } finally {
                _loadingState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun onCancelEditClicked() {
        _isEditMode.value = false
        _activeWordId.value = null
        _editableWords.value = emptyList()
    }

    fun onShareTextClicked() {
        val text = _recognizedText.value ?: return
        viewModelScope.launch {
            _uiEventFlow.emit(UiEvent.ShareText(text))
        }
    }
}