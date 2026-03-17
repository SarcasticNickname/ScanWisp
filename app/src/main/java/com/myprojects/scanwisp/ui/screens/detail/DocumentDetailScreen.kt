package com.myprojects.scanwisp.ui.screens.detail

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.domain.model.OcrMode
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentDetailScreen(
    navController: NavController,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRenameDialogVisible by viewModel.isRenameDialogVisible.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val shareDialogState by viewModel.shareDialogState.collectAsStateWithLifecycle()
    val selectedPageIds by viewModel.selectedPageIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionModeActive.collectAsStateWithLifecycle()
    val isSortMode by viewModel.isSortModeActive.collectAsStateWithLifecycle()
    val expandedMenuPageId by viewModel.expandedMenuPageId.collectAsStateWithLifecycle()
    val isMoveDialogVisible by viewModel.isMoveDialogVisible.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current


    var pendingOcrAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingOcrAction?.invoke()
        pendingOcrAction = null
        if (!isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_notification_permission_denied)
                )
            }
        }
    }

    val launchOcrWithPermission: (() -> Unit) -> Unit = remember(notificationPermissionLauncher) {
        { ocrAction: () -> Unit ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val ctx = context
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingOcrAction = ocrAction
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@remember
                }
            }
            ocrAction()
        }
    }

    var errorToShowInDialog by remember { mutableStateOf<AppError?>(null) }
    var tempFileToClean: File? by remember { mutableStateOf(null) }

    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        tempFileToClean?.let { viewModel.cleanUpTempFile(it) }
        tempFileToClean = null
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
                } catch (_: SecurityException) {
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
                viewModel.addPages(pageUris)
            }
        } else {
            Toast.makeText(context, R.string.toast_add_pages_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPages(uris)
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
                                actionLabel = event.actionLabel,
                                withDismissAction = true
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                if (event.actionLabel == context.getString(R.string.action_cancel)) {
                                    viewModel.undoDeletePages()
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

                is UiEvent.ShowErrorDialog -> {
                    errorToShowInDialog = event.error
                }

                else -> Unit
            }
        }
    }

    if (isRenameDialogVisible) {
        (uiState as? DocumentDetailUiState.Success)?.documentWithPages?.document?.let {
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
            // При нажатии "Назад" в режиме сортировки, сохраняем результат
            viewModel.toggleSortMode()
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
                            val pageCount =
                                (uiState as? DocumentDetailUiState.Success)?.documentWithPages?.pages?.size
                                    ?: 0
                            val canSplit =
                                selectedPageIds.isNotEmpty() && selectedPageIds.size < pageCount
                            SelectionTopAppBar(
                                selectedCount = selectedPageIds.size,
                                onClearSelection = { viewModel.clearSelection() },
                                onDeleteSelected = { viewModel.deleteSelectedPages() },
                                onExportSelected = { viewModel.onShareRequest() },
                                onSplitSelected = { viewModel.splitSelectedPages() },
                                onOcrSelected = { launchOcrWithPermission { viewModel.recognizeSelectedPages() } },
                                canSplit = canSplit
                            )
                        }

                        else -> {
                            val title =
                                (uiState as? DocumentDetailUiState.Success)?.documentWithPages?.document?.title
                                    ?: stringResource(R.string.loading)
                            DefaultTopAppBar(
                                title = title,
                                onNavigateBack = { navController.popBackStack() },
                                onSortClick = { viewModel.toggleSortMode() },
                                onTitleClick = { viewModel.onRenameRequest() },
                                onShareClick = { viewModel.onShareAllPagesRequest() },
                                onMoveClick = { viewModel.onMoveRequest() },
                                onOcrClick = {
                                    launchOcrWithPermission { viewModel.recognizeAllPages() }
                                })
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
                        if (activity != null) {
                            viewModel.onAddPagesClicked(activity)
                        } else {
                            Timber.e(
                                "Context is not an Activity, cannot start scanner."
                            )
                        }
                    }) {
                        Icon(
                            Icons.Filled.AddAPhoto,
                            contentDescription = stringResource(R.string.fab_cd_add_pages)
                        )
                    }
                }
            }
        ) { innerPadding ->
            when (val state = uiState) {
                is DocumentDetailUiState.Loading -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is DocumentDetailUiState.Error -> {
                    ErrorState(
                        error = state.error,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }

                is DocumentDetailUiState.Success -> {
                    val pages = state.documentWithPages.pages
                    val columns = when (widthSizeClass) {
                        WindowWidthSizeClass.Compact -> 3
                        WindowWidthSizeClass.Medium -> 4
                        else -> 5
                    }

                    val gridState = rememberLazyGridState()

                    // Прокрутка к странице из поиска — только один раз при первом появлении данных
                    var hasScrolledToInitialPage by remember { mutableStateOf(false) }

                    LaunchedEffect(pages) {
                        val targetId = viewModel.initialPageId
                        if (targetId != null && pages.isNotEmpty() && !hasScrolledToInitialPage) {
                            val idx = pages.indexOfFirst { it.id == targetId }
                            if (idx >= 0) {
                                gridState.scrollToItem(idx)
                                hasScrolledToInitialPage = true
                            }
                        }
                    }

                    // -------------------- РЕЖИМ СОРТИРОВКИ: ЛОКАЛЬНЫЙ СПИСОК --------------------
                    var localPages by remember { mutableStateOf<List<PageEntity>>(emptyList()) }

                    LaunchedEffect(pages, isSortMode) {
                        if (isSortMode) {
                            // Синхронизация при входе в режим сортировки
                            localPages = pages
                        }
                    }

                    val reorderState = rememberReorderableLazyGridState(
                        lazyGridState = gridState,
                        onMove = { from, to ->
                            // ВАЖНО для Grid: делаем SWAP, а не remove/insert
                            localPages = localPages.toMutableList().apply {
                                val fromItem = this[from.index]
                                val toItem = this[to.index]
                                this[from.index] = toItem
                                this[to.index] = fromItem
                            }
                            // Уведомим ViewModel (для последующего persist при выходе)
                            viewModel.reorderPagesInMemory(localPages)
                        }
                    )

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            // В режиме сортировки отображаем локальный список
                            items = if (isSortMode) localPages else pages,
                            key = { _, page -> page.id }
                        ) { index, page ->
                            val isSelected = page.id in selectedPageIds

                            ReorderableItem(reorderState, key = page.id) { isDragging ->
                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .shadow(if (isDragging) 8.dp else 0.dp)
                                ) {
                                    PageThumbnailCard(
                                        page = page,
                                        pageIndex = index,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.onPageClick(page.id)
                                            } else if (!isSortMode) {
                                                navController.navigate(
                                                    Screen.PreviewPage.createRoute(page.id)
                                                ) {
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSortMode) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.onPageLongClick(page.id)
                                            }
                                        },
                                        onMoreClick = { viewModel.onPageMenuRequested(page.id) },
                                        showDragHandle = isSortMode,
                                    )

                                    // Прозрачный оверлей-хэндл на ВСЮ карточку только в режиме сортировки,
                                    // чтобы жест long-press + drag всегда ловился библиотекой и не конфликтовал с кликами.
                                    if (isSortMode) {
                                        Box(
                                            modifier = with(this) {
                                                Modifier
                                                    .matchParentSize()
                                                    .longPressDraggableHandle()
                                            }
                                        )
                                    }

                                    PageActionMenu(
                                        expanded = expandedMenuPageId == page.id,
                                        onDismissRequest = { viewModel.onPageMenuDismissed() },
                                        onSetAsCoverClick = { viewModel.setPageAsCover(page.id) },
                                        onShareClick = { viewModel.shareSinglePage(page.id) },
                                        onRecognizeFastClick = {
                                            launchOcrWithPermission {
                                                viewModel.recognizePage(
                                                    page.id,
                                                    OcrMode.FAST
                                                )
                                            }
                                        },
                                        onRecognizeFullClick = {
                                            launchOcrWithPermission {
                                                viewModel.recognizePage(
                                                    page.id,
                                                    OcrMode.FULL
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        FullScreenLoader(loadingState = loadingState)

        errorToShowInDialog?.let { error ->
            ErrorDialog(
                error = error,
                onDismiss = { errorToShowInDialog = null }
            )
        }
    }
}