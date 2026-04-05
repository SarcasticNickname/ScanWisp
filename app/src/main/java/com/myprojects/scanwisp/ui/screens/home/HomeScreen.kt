package com.myprojects.scanwisp.ui.screens.home

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.ViewMode
import com.myprojects.scanwisp.ui.components.EmptyState
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
import com.myprojects.scanwisp.ui.components.FullScreenLoader
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.model.UiAction
import com.myprojects.scanwisp.ui.navigation.Screen
import com.myprojects.scanwisp.ui.screens.home.components.DocumentGrid
import com.myprojects.scanwisp.ui.screens.home.components.DocumentListItem
import com.myprojects.scanwisp.ui.screens.home.components.ExportBottomSheet
import com.myprojects.scanwisp.ui.screens.home.components.HomeTopAppBar
import com.myprojects.scanwisp.ui.screens.home.components.MoveToFolderDialog
import com.myprojects.scanwisp.ui.screens.home.components.NativeAdListItem
import com.myprojects.scanwisp.ui.screens.home.components.RenameDialog
import com.myprojects.scanwisp.ui.screens.home.components.ScanWispBottomAppBar
import com.myprojects.scanwisp.ui.screens.home.components.SortBottomSheet
import com.myprojects.scanwisp.ui.state.HomeScreenUiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tempFileToClean: File? by remember { mutableStateOf(null) }

    val haptics = LocalHapticFeedback.current

    var errorToShowInDialog by remember { mutableStateOf<AppError?>(null) }

    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        tempFileToClean?.let { viewModel.cleanUpTempFile(it) }
        tempFileToClean = null
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val destinationUri = result.data?.data
        tempFileToClean?.let {
            viewModel.handleSaveResult(destinationUri, it)
        }
        tempFileToClean = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.createNewDocument(uris)
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUris = scanningResult?.pages?.map { it.imageUri }.orEmpty()

            val cr = context.contentResolver
            pageUris.forEach { uri ->
                try {
                    cr.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) { /* провайдер мог не дать persist */
                }
                try {
                    context.grantUriPermission(
                        context.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }

            if (pageUris.isNotEmpty()) {
                Timber.d("Scanner success. URIs received: $pageUris")
                viewModel.createNewDocument(pageUris)
            } else {
                Timber.w("Scanner returned empty or null URI list.")
            }

        } else {
            Toast.makeText(
                context,
                context.getString(R.string.toast_failed_to_get_images),
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    LaunchedEffect(key1 = true) {
        viewModel.uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    if (event.actionLabel == "OPEN_GALLERY") {
                        galleryLauncher.launch(arrayOf("image/*"))
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    } else {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = event.message,
                                actionLabel = event.actionLabel
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                if (event.actionLabel == context.getString(R.string.action_cancel)) {
                                    viewModel.undoDelete()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                    }
                }

                is UiEvent.LaunchScanner -> {
                    val req = IntentSenderRequest.Builder(event.intentSender)
                        .setFillInIntent(
                            Intent().addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            )
                        )
                        .build()
                    scannerLauncher.launch(req)
                }

                is UiEvent.LaunchShareIntent -> {
                    tempFileToClean = event.tempFile
                    shareLauncher.launch(event.intent)
                }

                is UiEvent.LaunchSaveIntent -> {
                    tempFileToClean = event.tempFile
                    saveLauncher.launch(event.intent)
                }

                is UiEvent.RequestInAppReview -> {
                    if (activity != null) {
                        scope.launch {
                            try {
                                val reviewManager = ReviewManagerFactory.create(activity)
                                val reviewInfo = reviewManager.requestReviewFlow().await()
                                reviewManager.launchReviewFlow(activity, reviewInfo)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to launch review flow")
                            }
                        }
                    }
                }

                is UiEvent.ShowErrorDialog -> {
                    errorToShowInDialog = event.error
                }

                else -> Unit
            }
        }
    }

    val onScanClick: () -> Unit = {
        if (activity != null) {
            viewModel.onScanButtonClicked(activity)
        } else {
            Timber.e("Context is not an Activity, cannot start scanner.")
        }
    }

    val shareDialogState by viewModel.shareDialogState.collectAsStateWithLifecycle()
    val showSortSheet by viewModel.showSortSheet.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    var showMoveDialogForSelection by remember { mutableStateOf(false) }
    var showMoveDialogForDocumentId by remember { mutableStateOf<String?>(null) }
    var showRenameDialogForDocumentId by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val isSelectionMode = (uiState as? HomeScreenUiState.Data)?.isSelectionModeActive ?: false
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val dataState = uiState as? HomeScreenUiState.Data
            HomeTopAppBar(
                screenTitle = dataState?.screenTitle ?: stringResource(R.string.loading),
                searchQuery = searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                scrollBehavior = scrollBehavior,
                isSelectionMode = dataState?.isSelectionModeActive ?: false,
                selectedCount = dataState?.selectedDocumentIds?.size ?: 0,
                onClearSelection = viewModel::clearSelection,
                onDeleteSelected = viewModel::deleteSelectedDocuments,
                onShare = viewModel::onShareRequest,
                onSave = viewModel::onSaveRequest,
                onMoveSelected = { showMoveDialogForSelection = true },
                onSortClick = viewModel::onSortClick,
                viewMode = dataState?.viewMode ?: ViewMode.GRID,
                onViewModeToggle = viewModel::toggleViewMode,
                onSelectAll = viewModel::selectAllDocuments,
                onMergeSelected = viewModel::mergeSelectedDocuments
            )
        },
        bottomBar = {
            ScanWispBottomAppBar(navController = navController)
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(onClick = onScanClick) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.fab_cd_scan_document)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is HomeScreenUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is HomeScreenUiState.Error -> {
                    ErrorState(
                        error = state.error,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is HomeScreenUiState.Data -> {
                    HomeScreenContent(
                        state = state,
                        navController = navController,
                        viewModel = viewModel,
                        widthSizeClass = widthSizeClass,
                        onScanClick = onScanClick,
                        onRenameRequest = { documentId ->
                            showRenameDialogForDocumentId = documentId
                        },
                        onMoveRequest = { documentId -> showMoveDialogForDocumentId = documentId },
                        onHapticFeedback = { type -> haptics.performHapticFeedback(type) }
                    )
                }
            }
            (uiState as? HomeScreenUiState.Data)?.let {
                FullScreenLoader(loadingState = it.loadingState)
            }
        }
    }

    if (shareDialogState.isVisible) {
        ExportBottomSheet(
            onDismissRequest = viewModel::onShareDialogDismiss,
            onConfirm = { format, filename, pdfProfile, fitToA4 ->
                viewModel.onShareDialogConfirm(format, filename, pdfProfile, fitToA4)
            },
            pageCount = shareDialogState.pageCount,
            defaultFilename = shareDialogState.defaultName,
            action = shareDialogState.action,
            estimatedSourceBytes = shareDialogState.estimatedBytes
        )
    }

    if (showSortSheet) {
        SortBottomSheet(
            onDismissRequest = viewModel::onSortDismiss,
            sortBy = sortBy,
            sortOrder = sortOrder,
            onSortByChanged = viewModel::onSortByChanged,
            onSortOrderChanged = viewModel::onSortOrderChanged
        )
    }

    showRenameDialogForDocumentId?.let { documentId ->
        val currentDocument =
            (uiState as? HomeScreenUiState.Data)?.items?.filterIsInstance<DocumentRow>()
                ?.find { it.id == documentId }
        if (currentDocument != null) {
            RenameDialog(
                currentTitle = currentDocument.title,
                onDismissRequest = { showRenameDialogForDocumentId = null },
                onConfirm = { newTitle ->
                    viewModel.renameDocument(documentId, newTitle)
                    showRenameDialogForDocumentId = null
                }
            )
        }
    }

    if (showMoveDialogForSelection) {
        MoveToFolderDialog(
            folders = (uiState as? HomeScreenUiState.Data)?.allFolders ?: emptyList(),
            onDismissRequest = { showMoveDialogForSelection = false },
            onFolderSelected = { folderId ->
                viewModel.moveSelectedDocuments(folderId)
                showMoveDialogForSelection = false
            },
            onCreateFolder = { name -> viewModel.createFolder(name) }
        )
    }

    showMoveDialogForDocumentId?.let { docId ->
        MoveToFolderDialog(
            folders = (uiState as? HomeScreenUiState.Data)?.allFolders ?: emptyList(),
            onDismissRequest = { showMoveDialogForDocumentId = null },
            onFolderSelected = { folderId ->
                viewModel.moveDocument(docId, folderId)
                showMoveDialogForDocumentId = null
            },
            onCreateFolder = { name -> viewModel.createFolder(name) }
        )
    }

    errorToShowInDialog?.let { error ->
        ErrorDialog(
            error = error,
            onDismiss = { errorToShowInDialog = null }
        )
    }
}

@Composable
private fun rememberDocumentActions(
    viewModel: HomeViewModel,
    onRenameRequest: (String) -> Unit,
    onMoveRequest: (String) -> Unit,
    documentId: String
): List<UiAction> {
    return remember(documentId) {
        listOf(
            UiAction(
                title = "Переименовать",
                icon = Icons.Outlined.DriveFileRenameOutline,
                onClick = { onRenameRequest(documentId) }
            ),
            UiAction(
                title = "Переместить",
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                onClick = { onMoveRequest(documentId) }
            ),
            UiAction(
                title = "Поделиться",
                icon = Icons.Outlined.Share,
                onClick = { viewModel.onShareSingleDocument(documentId) }
            ),
            UiAction(
                title = "Скачать",
                icon = Icons.Outlined.FileDownload,
                onClick = { viewModel.onDownloadSingleDocument(documentId) }
            ),
            UiAction(
                title = "Удалить",
                icon = Icons.Outlined.Delete,
                onClick = { viewModel.deleteDocument(documentId) },
                isDestructive = true
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    state: HomeScreenUiState.Data,
    navController: NavController,
    viewModel: HomeViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onScanClick: () -> Unit,
    onRenameRequest: (String) -> Unit,
    onMoveRequest: (String) -> Unit,
    onHapticFeedback: (HapticFeedbackType) -> Unit
) {


    if (state.items.isEmpty()) {
        EmptyState(
            icon = Icons.Default.AutoStories,
            title = stringResource(R.string.empty_state_title_no_documents),
            subtitle = stringResource(R.string.empty_state_subtitle_no_documents),
            ctaText = stringResource(R.string.empty_state_cta_scan_document),
            onCtaClick = onScanClick
        )
    } else {
        if (state.viewMode == ViewMode.GRID) {
            DocumentGrid(
                items = state.items,
                selectedIds = state.selectedDocumentIds,
                isSelectionMode = state.isSelectionModeActive,
                onDocumentClick = { documentId ->
                    if (state.isSelectionModeActive) {
                        onHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onDocumentClick(documentId)
                    } else {
                        navController.navigate(Screen.DocumentDetail.createRoute(documentId)) {
                            launchSingleTop = true
                        }                    }
                },
                onDocumentLongClick = { documentId ->
                    onHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onDocumentLongClick(documentId)
                },
                documentActionsBuilder = { documentId ->
                    rememberDocumentActions(
                        viewModel,
                        onRenameRequest,
                        onMoveRequest,
                        documentId
                    )
                },
                widthSizeClass = widthSizeClass
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = state.items,
                    key = { index, item ->
                        when (item) {
                            is DocumentRow -> "doc-${item.id}"
                            is NativeAd -> "ad-$index"
                            else -> "other-$index"
                        }
                    },
                    contentType = { _, item -> if (item is DocumentRow) "doc" else "ad" }
                ) { _, item ->
                    when (item) {
                        is DocumentRow -> {
                            val documentId = item.id
                            Box(modifier = Modifier.animateItem()) {
                                DocumentListItem(
                                    documentRow = item,
                                    isSelected = documentId in state.selectedDocumentIds,
                                    isSelectionMode = state.isSelectionModeActive,
                                    onClick = {
                                        if (state.isSelectionModeActive) {
                                            onHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.onDocumentClick(documentId)
                                        } else {
                                            navController.navigate(
                                                Screen.DocumentDetail.createRoute(
                                                    documentId
                                                )
                                            ) {
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        onHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.onDocumentLongClick(documentId)
                                    },
                                    actions = rememberDocumentActions(
                                        viewModel,
                                        onRenameRequest,
                                        onMoveRequest,
                                        documentId
                                    )
                                )
                            }
                        }

                        is NativeAd -> {
                            NativeAdListItem(
                                nativeAd = item,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}