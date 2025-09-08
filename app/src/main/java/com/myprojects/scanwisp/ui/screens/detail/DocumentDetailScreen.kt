package com.myprojects.scanwisp.ui.screens.detail

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.ui.components.FullScreenLoader
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.navigation.Screen
import com.myprojects.scanwisp.ui.screens.detail.components.DefaultTopAppBar
import com.myprojects.scanwisp.ui.screens.detail.components.PageActionMenu
import com.myprojects.scanwisp.ui.screens.detail.components.PageThumbnailCard
import com.myprojects.scanwisp.ui.screens.detail.components.SelectionTopAppBar
import com.myprojects.scanwisp.ui.screens.detail.components.SortModeTopAppBar
import com.myprojects.scanwisp.ui.screens.home.components.ExportBottomSheet
import com.myprojects.scanwisp.ui.screens.home.components.MoveToFolderDialog
import com.myprojects.scanwisp.ui.screens.home.components.RenameDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyGridState
import org.burnoutcrew.reorderable.reorderable
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentDetailScreen(
    navController: NavController,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val documentState by viewModel.documentState.collectAsState()
    val isRenameDialogVisible by viewModel.isRenameDialogVisible.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val shareDialogState by viewModel.shareDialogState.collectAsState()
    val pages = documentState?.pages ?: emptyList()

    val selectedPageIds by viewModel.selectedPageIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionModeActive.collectAsState()
    val isSortMode by viewModel.isSortModeActive.collectAsState()
    val expandedMenuPageId by viewModel.expandedMenuPageId.collectAsState()

    val isMoveDialogVisible by viewModel.isMoveDialogVisible.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var tempFileToClean: File? by remember { mutableStateOf(null) }

    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        tempFileToClean?.let { viewModel.cleanUpTempFile(it) }
        tempFileToClean = null
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = event.actionLabel,
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            // ФИКС: Используем строковый ресурс вместо хардкода
                            if (event.actionLabel == context.getString(R.string.action_cancel)) {
                                viewModel.undoDeletePages()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                }

                is UiEvent.LaunchShareIntent -> {
                    try {
                        tempFileToClean = event.tempFile
                        shareLauncher.launch(event.intent)
                    } catch (e: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.toast_no_app_to_share))
                        }
                        viewModel.cleanUpTempFile(event.tempFile)
                        tempFileToClean = null
                    }
                }

                is UiEvent.NavigateBack -> {
                    navController.popBackStack()
                }

                // ФИКС: Добавляем else ветку, чтобы when был исчерпывающим
                else -> Unit
            }
        }
    }

    if (isRenameDialogVisible) {
        documentState?.document?.let {
            RenameDialog(
                currentTitle = it.title,
                onDismissRequest = { viewModel.onRenameDialogDismiss() },
                onConfirm = { newTitle -> viewModel.onRenameConfirm(newTitle) }
            )
        }
    }

    if (isMoveDialogVisible) {
        MoveToFolderDialog(
            folders = allFolders,
            onDismissRequest = { viewModel.onMoveDialogDismiss() },
            onFolderSelected = { folderId -> viewModel.onMoveConfirm(folderId) }
        )
    }

    BackHandler(enabled = isSelectionMode || isSortMode) {
        if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (isSortMode) {
            viewModel.toggleSortMode()
        }
    }

    val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(10)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()
    val scanner = GmsDocumentScanning.getClient(scannerOptions)

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.let { newPages ->
                val uris = newPages.map { it.imageUri }
                if (uris.isNotEmpty()) {
                    viewModel.addPages(uris)
                }
            }
        } else {
            Toast.makeText(context, R.string.toast_add_pages_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    if (shareDialogState.isVisible) {
        ExportBottomSheet(
            onDismissRequest = { viewModel.onShareDialogDismiss() },
            onConfirm = { format, filename ->
                viewModel.onShareDialogConfirm(format, filename)
            },
            pageCount = shareDialogState.pageCount,
            defaultFilename = shareDialogState.defaultName,
            action = shareDialogState.action
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                val topBarState = when {
                    isSortMode -> "sort"
                    isSelectionMode -> "selection"
                    else -> "default"
                }
                AnimatedContent(
                    targetState = topBarState,
                    label = "detail_top_bar_animation",
                    transitionSpec = {
                        slideInVertically { height -> -height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    }
                ) { state ->
                    when (state) {
                        "sort" -> {
                            SortModeTopAppBar(onDoneClick = { viewModel.toggleSortMode() })
                        }

                        "selection" -> {
                            val canSplit =
                                selectedPageIds.isNotEmpty() && selectedPageIds.size < pages.size
                            SelectionTopAppBar(
                                selectedCount = selectedPageIds.size,
                                onClearSelection = { viewModel.clearSelection() },
                                onDeleteSelected = { viewModel.deleteSelectedPages() },
                                onExportSelected = { viewModel.onShareRequest() },
                                onSplitSelected = { viewModel.splitSelectedPages() },
                                canSplit = canSplit
                            )
                        }

                        else -> {
                            DefaultTopAppBar(
                                title = documentState?.document?.title
                                    ?: stringResource(R.string.loading),
                                onNavigateBack = { navController.popBackStack() },
                                onSortClick = { viewModel.toggleSortMode() },
                                onTitleClick = { viewModel.onRenameRequest() },
                                onShareClick = { viewModel.onShareAllPagesRequest() },
                                onMoveClick = { viewModel.onMoveRequest() }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isSelectionMode && !isSortMode,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    FloatingActionButton(onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            scanner.getStartScanIntent(activity)
                                .addOnSuccessListener { intentSender ->
                                    scannerLauncher.launch(
                                        IntentSenderRequest.Builder(intentSender).build()
                                    )
                                }
                                .addOnFailureListener { e: Exception ->
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.toast_scanner_failed_opening_gallery,
                                            e.message
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        } else {
                            Log.e(
                                "DocumentDetailScreen",
                                "Context is not an Activity, cannot start scanner."
                            )
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.toast_scanner_unavailable_opening_gallery,
                                    "Context is not an activity"
                                ),
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = stringResource(R.string.fab_cd_add_pages)
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (documentState == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val columns = when (widthSizeClass) {
                    WindowWidthSizeClass.Compact -> 3
                    WindowWidthSizeClass.Medium -> 4
                    else -> 5
                }

                val reorderState = rememberReorderableLazyGridState(
                    onMove = { from, to ->
                        viewModel.reorderPages(from.index, to.index)
                    }
                )

                var reorderModifier: Modifier = Modifier.reorderable(reorderState)
                if (isSortMode) {
                    reorderModifier = reorderModifier.detectReorderAfterLongPress(reorderState)
                }

                LazyVerticalGrid(
                    state = reorderState.gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .then(reorderModifier),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        val isSelected = page.id in selectedPageIds
                        ReorderableItem(
                            reorderableState = reorderState,
                            key = page.id
                        ) {
                            Box {
                                PageThumbnailCard(
                                    page = page,
                                    pageIndex = index,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.onPageClick(page.id)
                                        } else if (!isSortMode) {
                                            navController.navigate(
                                                Screen.PreviewPage.createRoute(
                                                    page.id
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSortMode) {
                                            viewModel.onPageLongClick(page.id)
                                        }
                                    },
                                    onMoreClick = { viewModel.onPageMenuRequested(page.id) },
                                    showDragHandle = isSortMode,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = spring<IntOffset>(
                                            stiffness = Spring.StiffnessMediumLow,
                                            visibilityThreshold = IntOffset.VisibilityThreshold
                                        )
                                    )
                                )
                                PageActionMenu(
                                    expanded = expandedMenuPageId == page.id,
                                    onDismissRequest = { viewModel.onPageMenuDismissed() },
                                    onSetAsCoverClick = { viewModel.setPageAsCover(page.id) },
                                    onShareClick = { viewModel.shareSinglePage(page.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
        FullScreenLoader(loadingState = loadingState)
    }
}