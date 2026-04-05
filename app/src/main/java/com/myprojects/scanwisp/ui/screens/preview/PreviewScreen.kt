package com.myprojects.scanwisp.ui.screens.preview

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
import com.myprojects.scanwisp.ui.components.FullScreenLoader
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.navigation.Screen
import com.myprojects.scanwisp.ui.screens.preview.components.TokenEditorSheet
import kotlinx.coroutines.flow.collectLatest
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    navController: NavController,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val recognizedText by viewModel.recognizedText.collectAsStateWithLifecycle()
    val isSheetVisible by viewModel.isSheetVisible.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val textEditMode by viewModel.textEditMode.collectAsStateWithLifecycle()
    val editableWords by viewModel.editableWords.collectAsStateWithLifecycle()
    val activeWordId by viewModel.activeWordId.collectAsStateWithLifecycle()
    val freeTextBuffer by viewModel.freeTextBuffer.collectAsStateWithLifecycle()
    val showOverwriteWarning by viewModel.showOverwriteWarning.collectAsStateWithLifecycle()
    val showRescanConf by viewModel.showRescanConfirmation.collectAsStateWithLifecycle()
    val showSwitchModeDialog by viewModel.showSwitchModeDialog.collectAsStateWithLifecycle()
    val prevPageId by viewModel.prevPageId.collectAsStateWithLifecycle()
    val nextPageId by viewModel.nextPageId.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardMgr = LocalClipboardManager.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    var errorToShow by remember { mutableStateOf<AppError?>(null) }

    // Лаунчер сканера (пересъёмка)
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = GmsDocumentScanningResult
                .fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                }
                try {
                    context.grantUriPermission(context.packageName, uri, flags)
                } catch (_: SecurityException) {
                }
                viewModel.replaceImage(uri)
            } else {
                Toast.makeText(context, R.string.toast_replace_cancelled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) viewModel.replaceImage(uri)
            else Toast.makeText(context, R.string.toast_replace_cancelled, Toast.LENGTH_SHORT)
                .show()
        }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.TriggerRescan -> {
                    if (activity != null) viewModel.launchRescan(activity)
                    else Timber.e("Context is not an Activity")
                }

                is UiEvent.LaunchScanner -> {
                    val req = IntentSenderRequest.Builder(event.intentSender)
                        .setFillInIntent(
                            Intent().addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                        ).build()
                    scannerLauncher.launch(req)
                }

                is UiEvent.ShowSnackbar -> {
                    if (event.actionLabel == "OPEN_GALLERY") {
                        galleryLauncher.launch("image/*")
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                }

                is UiEvent.ShowErrorDialog -> {
                    errorToShow = event.error
                }

                else -> Unit
            }
        }
    }

    val pageNumber = (uiState as? PreviewUiState.Success)?.page?.pageNumber ?: 0
    val title = if (totalPages > 0) "Страница $pageNumber из $totalPages"
    else stringResource(R.string.preview_screen_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preview_cd_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.onRecognizeTextClicked() }) {
                        Text("Распознать текст")
                    }
                    TextButton(onClick = { viewModel.onRescanButtonClicked() }) {
                        Icon(
                            Icons.Default.Replay, contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.preview_action_rescan))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is PreviewUiState.Loading -> CircularProgressIndicator()

                is PreviewUiState.Error ->
                    ErrorState(error = state.error, modifier = Modifier.fillMaxSize())

                is PreviewUiState.Success -> {
                    // Изображение с pinch-to-zoom
                    ZoomableAsyncImage(
                        model = state.page.processedImagePath,
                        contentDescription = stringResource(R.string.preview_cd_page_content),
                        modifier = Modifier.fillMaxSize()
                    )

                    // Стрелки prev / next
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (prevPageId != null) {
                            FilledTonalIconButton(
                                onClick = {
                                    navController.navigate(Screen.PreviewPage.createRoute(prevPageId!!)) {
                                        popUpTo(Screen.PreviewPage.route) { inclusive = true }
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                )
                            ) { Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Предыдущая") }
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }

                        if (nextPageId != null) {
                            FilledTonalIconButton(
                                onClick = {
                                    navController.navigate(Screen.PreviewPage.createRoute(nextPageId!!)) {
                                        popUpTo(Screen.PreviewPage.route) { inclusive = true }
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                )
                            ) { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, "Следующая") }
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }
                    }
                }
            }

            FullScreenLoader(loadingState = loadingState)
        }
    }

    // ─── Шторка ────────────────────────────────────────────────────────────

    if (isSheetVisible && recognizedText != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onSheetDismissed() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            if (isEditMode) {
                // ── Режим редактирования ──────────────────────────────────
                TokenEditorSheet(
                    editableWords = editableWords,
                    activeWordId = activeWordId,
                    editMode = textEditMode,
                    freeTextBuffer = freeTextBuffer,
                    showSwitchModeDialog = showSwitchModeDialog,
                    onWordTap = viewModel::onWordTap,
                    onWordLongPress = { id ->
                        val word = editableWords.find { it.id == id }
                        if (word?.isDeleted == true) viewModel.onWordRestore(id)
                        else viewModel.onWordDelete(id)
                    },
                    onWordTextCommit = viewModel::onWordTextCommit,
                    onWordEditCancel = viewModel::onWordEditCancel,
                    onFreeTextChanged = viewModel::onFreeTextBufferChanged,
                    onSwitchToFreeTextRequest = viewModel::onSwitchToFreeTextRequest,
                    onSwitchToTokenMode = viewModel::onSwitchToTokenMode,
                    onSwitchModeConfirmed = viewModel::onSwitchModeConfirmed,
                    onSwitchModeDismissed = viewModel::onSwitchModeDismissed,
                    onSave = viewModel::onSaveEditClicked,
                    onCancel = viewModel::onCancelEditClicked,
                    modifier = Modifier.heightIn(max = (screenHeightDp * 0.6f).dp)
                )
            } else {
                // ── Режим просмотра ───────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (screenHeightDp * 0.55f).dp)
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Распознанный текст",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        // Копировать всё
                        IconButton(onClick = {
                            clipboardMgr.setText(AnnotatedString(recognizedText!!))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Копировать всё")
                        }
                        // Войти в режим редактирования
                        IconButton(onClick = { viewModel.onEditTextClicked() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SelectionContainer {
                        Text(
                            text = recognizedText!!,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(Modifier.heightIn(min = 24.dp))
                }
            }
        }
    }

    // ─── Диалог: перезаписать отредактированный текст? ─────────────────────

    if (showOverwriteWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.onOverwriteDismissed() },
            title = { Text("Перезаписать текст?") },
            text = { Text("Вы редактировали текст вручную. OCR заменит ваши правки.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onOverwriteConfirmed() }) { Text("Перезаписать") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onOverwriteDismissed() }) { Text("Отмена") }
            }
        )
    }

    // ─── Диалог: подтверждение пересъёмки ──────────────────────────────────

    if (showRescanConf) {
        AlertDialog(
            onDismissRequest = { viewModel.onRescanDismissed() },
            title = { Text("Переснять страницу?") },
            text = { Text("Текущее изображение будет заменено. Распознанный текст сбросится.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onRescanConfirmed() }) { Text("Переснять") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onRescanDismissed() }) { Text("Отмена") }
            }
        )
    }

    errorToShow?.let { error ->
        ErrorDialog(error = error, onDismiss = { errorToShow = null })
    }
}