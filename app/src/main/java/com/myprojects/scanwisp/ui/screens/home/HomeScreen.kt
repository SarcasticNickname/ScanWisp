package com.myprojects.scanwisp.ui.screens.home

import android.app.Activity
import android.util.Log
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.ads.nativead.NativeAd
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.domain.model.ViewMode
import com.myprojects.scanwisp.ui.components.EmptyState
import com.myprojects.scanwisp.ui.components.FullScreenLoader
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val scannerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pageUris = scanningResult?.pages?.map { it.imageUri }
                if (!pageUris.isNullOrEmpty()) {
                    viewModel.createNewDocument(pageUris)
                } else {
                    // START: AI_MODIFIED_BLOCK - ИСПРАВЛЕНО
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_failed_to_get_images),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    // END: AI_MODIFIED_BLOCK
                }
            }
        }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.createNewDocument(uris)
        } else {
            // START: AI_MODIFIED_BLOCK - ИСПРАВЛЕНО
            Toast.makeText(
                context,
                context.getString(R.string.toast_image_selection_cancelled),
                Toast.LENGTH_SHORT
            ).show()
            // END: AI_MODIFIED_BLOCK
        }
    }

    val scanner = GmsDocumentScanning.getClient(
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true).setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
    )
    val onScanClick: () -> Unit = {
        val activity = context as? Activity
        if (activity != null) {
            try {
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_scanner_failed_opening_gallery,
                                e.message
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                        galleryLauncher.launch(arrayOf("image/*"))
                    }
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.toast_scanner_unavailable_opening_gallery,
                        t.message
                    ),
                    Toast.LENGTH_LONG
                ).show()
                galleryLauncher.launch(arrayOf("image/*"))
            }
        } else {
            Log.e("HomeScreen", "Context is not an Activity, cannot start scanner.")
        }
    }

    val shareDialogState by viewModel.shareDialogState.collectAsState()
    val showSortSheet by viewModel.showSortSheet.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
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
        topBar = {
            val dataState = uiState as? HomeScreenUiState.Data
            HomeTopAppBar(
                screenTitle = dataState?.screenTitle ?: stringResource(R.string.loading),
                searchQuery = dataState?.searchQuery ?: "",
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
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
                        onMoveRequest = { documentId -> showMoveDialogForDocumentId = documentId }
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
            onConfirm = viewModel::onShareDialogConfirm,
            pageCount = shareDialogState.pageCount,
            defaultFilename = shareDialogState.defaultName,
            action = shareDialogState.action
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
            (uiState as? HomeScreenUiState.Data)?.documents?.find { it.document.id == documentId }
        if (currentDocument != null) {
            RenameDialog(
                currentTitle = currentDocument.document.title,
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
            }
        )
    }

    showMoveDialogForDocumentId?.let { docId ->
        MoveToFolderDialog(
            folders = (uiState as? HomeScreenUiState.Data)?.allFolders ?: emptyList(),
            onDismissRequest = { showMoveDialogForDocumentId = null },
            onFolderSelected = { folderId ->
                viewModel.moveDocument(docId, folderId)
                showMoveDialogForDocumentId = null
            }
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
    onMoveRequest: (String) -> Unit
) {
    if (state.documents.isEmpty() && state.nativeAd == null) {
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
                documents = state.documents,
                nativeAd = state.nativeAd,
                selectedIds = state.selectedDocumentIds,
                isSelectionMode = state.isSelectionModeActive,
                onDocumentClick = { documentId ->
                    if (state.isSelectionModeActive) {
                        viewModel.onDocumentClick(documentId)
                    } else {
                        navController.navigate(Screen.DocumentDetail.createRoute(documentId))
                    }
                },
                onDocumentLongClick = viewModel::onDocumentLongClick,
                onRenameRequest = onRenameRequest,
                onMoveRequest = onMoveRequest,
                onShareRequest = viewModel::onShareSingleDocument,
                onDownloadRequest = viewModel::onDownloadSingleDocument,
                onDeleteRequest = viewModel::deleteDocument,
                widthSizeClass = widthSizeClass
            )
        } else { // ViewMode.LIST
            val listItems = remember(state.documents, state.nativeAd) {
                val items: MutableList<Any> = state.documents.toMutableList()
                val adPosition = 1
                if (state.nativeAd != null && items.size >= adPosition) {
                    items.add(adPosition, state.nativeAd)
                }
                items
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = listItems,
                    key = { item -> if (item is DocumentWithPages) item.document.id else "ad_list_item" },
                    contentType = { item -> if (item is DocumentWithPages) "doc" else "ad" }
                ) { item ->
                    when (item) {
                        is DocumentWithPages -> {
                            val documentId = item.document.id
                            Box(modifier = Modifier.animateItemPlacement()) {
                                DocumentListItem(
                                    documentWithPages = item,
                                    isSelected = documentId in state.selectedDocumentIds,
                                    isSelectionMode = state.isSelectionModeActive,
                                    onClick = {
                                        if (state.isSelectionModeActive) {
                                            viewModel.onDocumentClick(documentId)
                                        } else {
                                            navController.navigate(
                                                Screen.DocumentDetail.createRoute(
                                                    documentId
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = { viewModel.onDocumentLongClick(documentId) },
                                    onRenameRequest = { onRenameRequest(documentId) },
                                    onMoveRequest = { onMoveRequest(documentId) },
                                    onShareRequest = { viewModel.onShareSingleDocument(documentId) },
                                    onDownloadRequest = {
                                        viewModel.onDownloadSingleDocument(
                                            documentId
                                        )
                                    },
                                    onDeleteRequest = { viewModel.deleteDocument(documentId) }
                                )
                            }
                        }

                        is NativeAd -> {
                            NativeAdListItem(
                                nativeAd = item,
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }
                }
            }
        }
    }
}