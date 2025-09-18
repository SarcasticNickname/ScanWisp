package com.myprojects.scanwisp.ui.screens.trash

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.ui.components.ConfirmationDialog
import com.myprojects.scanwisp.ui.components.EmptyState
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.model.UiAction
import com.myprojects.scanwisp.ui.screens.home.components.DocumentGrid
import com.myprojects.scanwisp.ui.screens.home.components.ScanWispBottomAppBar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var errorToShowInDialog by remember { mutableStateOf<AppError?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }

                is UiEvent.ShowErrorDialog -> {
                    errorToShowInDialog = event.error
                }

                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val currentState = uiState
            if (currentState is TrashUiState.Success) {
                TrashTopAppBar(
                    isSelectionMode = currentState.isSelectionModeActive,
                    selectedCount = currentState.selectedIds.size,
                    onNavigateBack = { navController.popBackStack() },
                    onClearSelection = { viewModel.clearSelection() },
                    onRestore = { viewModel.restoreSelected() },
                    onDeleteForever = { viewModel.deleteSelectedPermanently() },
                    onEmptyTrash = { showEmptyTrashDialog = true },
                    canEmptyTrash = currentState.documents.isNotEmpty()
                )
            }
        },
        bottomBar = { ScanWispBottomAppBar(navController = navController) }
    ) { innerPadding ->
        when (val state = uiState) {
            is TrashUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is TrashUiState.Error -> ErrorState(
                error = state.error,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            is TrashUiState.Success -> {
                if (state.documents.isEmpty()) {
                    EmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        icon = Icons.Outlined.DeleteSweep,
                        title = stringResource(id = R.string.trash_empty_state_title),
                        subtitle = stringResource(id = R.string.trash_empty_state_subtitle)
                    )
                } else {
                    val documentActionsBuilder =
                        @androidx.compose.runtime.Composable { documentId: String ->
                            listOf(
                                UiAction(
                                    title = stringResource(R.string.trash_action_restore),
                                    icon = Icons.Default.RestoreFromTrash,
                                    onClick = { viewModel.restoreDocument(documentId) }
                                ),
                                UiAction(
                                    title = stringResource(R.string.trash_action_delete_forever),
                                    icon = Icons.Default.DeleteForever,
                                    onClick = { viewModel.deleteDocumentPermanently(documentId) },
                                    isDestructive = true
                                )
                            )
                        }

                    DocumentGrid(
                        modifier = Modifier.padding(innerPadding),
                        items = state.documents,
                        selectedIds = state.selectedIds,
                        isSelectionMode = state.isSelectionModeActive,
                        onDocumentClick = { documentId ->
                            if (state.isSelectionModeActive) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onDocumentClick(documentId)
                            }
                        },
                        onDocumentLongClick = { documentId ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onDocumentLongClick(documentId)
                        },
                        documentActionsBuilder = documentActionsBuilder,
                        widthSizeClass = widthSizeClass
                    )
                }
            }
        }
    }

    if (showEmptyTrashDialog) {
        ConfirmationDialog(
            title = "Очистить корзину?",
            text = "Все документы в корзине будут удалены навсегда. Это действие нельзя отменить.",
            onConfirm = {
                viewModel.emptyTrash()
                showEmptyTrashDialog = false
            },
            onDismiss = { showEmptyTrashDialog = false }
        )
    }

    errorToShowInDialog?.let {
        ErrorDialog(error = it, onDismiss = { errorToShowInDialog = null })
    }
}