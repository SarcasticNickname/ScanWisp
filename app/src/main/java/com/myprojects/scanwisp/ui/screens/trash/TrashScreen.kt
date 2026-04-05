package com.myprojects.scanwisp.ui.screens.trash

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.TrashDocumentRow
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.ui.components.ConfirmationDialog
import com.myprojects.scanwisp.ui.components.EmptyState
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.model.UiAction
import com.myprojects.scanwisp.ui.screens.home.components.DocumentCard
import com.myprojects.scanwisp.ui.screens.home.components.ScanWispBottomAppBar
import com.myprojects.scanwisp.data.local.DocumentRow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max

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
                is UiEvent.ShowSnackbar -> scope.launch { snackbarHostState.showSnackbar(event.message) }
                is UiEvent.ShowErrorDialog -> errorToShowInDialog = event.error
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
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is TrashUiState.Error -> ErrorState(
                error = state.error,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )

            is TrashUiState.Success -> {
                if (state.documents.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        icon = Icons.Outlined.DeleteSweep,
                        title = stringResource(id = R.string.trash_empty_state_title),
                        subtitle = stringResource(id = R.string.trash_empty_state_subtitle)
                    )
                } else {
                    val columns = when (widthSizeClass) {
                        WindowWidthSizeClass.Expanded -> 4
                        WindowWidthSizeClass.Medium -> 3
                        else -> 2
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.padding(innerPadding),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = state.documents,
                            key = { it.id }
                        ) { trashDoc ->
                            val actions = listOf(
                                UiAction(
                                    title = stringResource(R.string.trash_action_restore),
                                    icon = Icons.Default.RestoreFromTrash,
                                    onClick = { viewModel.restoreDocument(trashDoc.id) }
                                ),
                                UiAction(
                                    title = stringResource(R.string.trash_action_delete_forever),
                                    icon = Icons.Default.DeleteForever,
                                    onClick = { viewModel.deleteDocumentPermanently(trashDoc.id) },
                                    isDestructive = true
                                )
                            )

                            Column(modifier = Modifier.animateItem()) {
                                DocumentCard(
                                    documentRow = trashDoc.toDocumentRow(),
                                    isSelected = trashDoc.id in state.selectedIds,
                                    isSelectionMode = state.isSelectionModeActive,
                                    onClick = {
                                        if (state.isSelectionModeActive) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.onDocumentClick(trashDoc.id)
                                        }
                                    },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.onDocumentLongClick(trashDoc.id)
                                    },
                                    actions = actions
                                )

                                // Подпись «осталось N дней»
                                val daysLeft = daysUntilDeletion(trashDoc.deletionTimestamp)
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                                    color = if (daysLeft <= 1)
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = when (daysLeft) {
                                            0L -> "Удаляется сегодня"
                                            1L -> "Удаляется завтра"
                                            else -> "Удаляется через $daysLeft дн."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (daysLeft <= 1)
                                            MaterialTheme.colorScheme.onErrorContainer
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyTrashDialog) {
        ConfirmationDialog(
            title = "Очистить корзину?",
            text = "Все документы будут удалены навсегда. Это действие нельзя отменить.",
            onConfirm = { viewModel.emptyTrash(); showEmptyTrashDialog = false },
            onDismiss = { showEmptyTrashDialog = false }
        )
    }

    errorToShowInDialog?.let {
        ErrorDialog(error = it, onDismiss = { errorToShowInDialog = null })
    }
}

/** Сколько дней осталось до автоудаления (на основе deletionTimestamp + 7 дней). */
private fun daysUntilDeletion(deletionTimestamp: Long): Long {
    val deleteAt = deletionTimestamp + TimeUnit.DAYS.toMillis(7)
    val remaining = deleteAt - System.currentTimeMillis()
    return max(0L, TimeUnit.MILLISECONDS.toDays(remaining))
}

/** Конвертирует TrashDocumentRow в DocumentRow для повторного использования DocumentCard. */
private fun TrashDocumentRow.toDocumentRow() = DocumentRow(
    id = id,
    title = title,
    coverImagePath = coverImagePath,
    creationTimestamp = creationTimestamp,
    pageCount = pageCount,
    folderId = null,
    ocrDoneCount = pageCount // В корзине не показываем OCR-badge
)