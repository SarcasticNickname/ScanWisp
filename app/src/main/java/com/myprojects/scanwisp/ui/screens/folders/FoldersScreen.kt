package com.myprojects.scanwisp.ui.screens.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.gms.ads.nativead.NativeAd
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.domain.model.AppError
import com.myprojects.scanwisp.ui.components.ConfirmationDialog
import com.myprojects.scanwisp.ui.components.EmptyState
import com.myprojects.scanwisp.ui.components.ErrorDialog
import com.myprojects.scanwisp.ui.components.ErrorState
import com.myprojects.scanwisp.ui.events.UiEvent
import com.myprojects.scanwisp.ui.navigation.Screen
import com.myprojects.scanwisp.ui.screens.home.components.NativeAdListItem
import com.myprojects.scanwisp.ui.screens.home.components.ScanWispBottomAppBar
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    navController: NavController,
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.isCreateDialogVisible.collectAsStateWithLifecycle()
    val renamingFolder by viewModel.renamingFolder.collectAsStateWithLifecycle()
    val deletingFolder by viewModel.deletingFolder.collectAsStateWithLifecycle()

    val analytics = Firebase.analytics
    var errorToShowInDialog by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowErrorDialog -> errorToShowInDialog = event.error
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.folders_screen_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddFolderRequest() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_cd_create_folder))
            }
        },
        bottomBar = { ScanWispBottomAppBar(navController = navController) }
    ) { innerPadding ->
        when (val state = uiState) {
            is FoldersUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is FoldersUiState.Error -> ErrorState(
                error = state.error,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )

            is FoldersUiState.Success -> {
                if (state.items.filterIsInstance<FolderWithDocumentCount>().isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.folders_empty_state_title),
                        subtitle = stringResource(R.string.folders_empty_state_subtitle),
                        ctaText = stringResource(R.string.folders_empty_state_cta),
                        onCtaClick = { viewModel.onAddFolderRequest() }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = state.items,
                            key = { index, item ->
                                when (item) {
                                    is FolderWithDocumentCount -> "folder-${item.folder.id}"
                                    is NativeAd -> "ad-$index"
                                    else -> "other-$index"
                                }
                            },
                            contentType = { _, item ->
                                if (item is FolderWithDocumentCount) "folder" else "ad"
                            }
                        ) { _, item ->
                            when (item) {
                                is FolderWithDocumentCount -> {
                                    FolderItem(
                                        folderWithCount = item,
                                        onClick = {
                                            analytics.logEvent("folder_opened", null)
                                            navController.navigate(
                                                Screen.Home.createRouteWithFolder(item.folder.id)
                                            ) { launchSingleTop = true }
                                        },
                                        onRenameClick = { viewModel.onRenameRequest(item) },
                                        onDeleteClick = { viewModel.onDeleteRequest(item) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                                is NativeAd -> {
                                    NativeAdListItem(nativeAd = item, modifier = Modifier.animateItem())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Диалог создания папки ---
    if (showCreateDialog) {
        FolderNameDialog(
            title = stringResource(R.string.dialog_create_folder_title),
            label = stringResource(R.string.dialog_create_folder_label),
            initialName = "",
            confirmText = stringResource(R.string.action_create),
            onDismiss = { viewModel.onDialogDismiss() },
            onConfirm = { name -> viewModel.createFolder(name) }
        )
    }

    // --- Диалог переименования ---
    renamingFolder?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.dialog_create_folder_label),
            initialName = folder.folder.name,
            confirmText = stringResource(R.string.action_save),
            onDismiss = { viewModel.onRenameDismiss() },
            onConfirm = { name -> viewModel.onRenameConfirm(name) }
        )
    }

    // --- Диалог подтверждения удаления ---
    deletingFolder?.let { folder ->
        ConfirmationDialog(
            title = "Удалить папку?",
            text = "Папка «${folder.folder.name}» будет удалена. Документы из неё переместятся на главный экран.",
            onConfirm = { viewModel.onDeleteConfirm() },
            onDismiss = { viewModel.onDeleteDismiss() }
        )
    }

    errorToShowInDialog?.let {
        ErrorDialog(error = it, onDismiss = { errorToShowInDialog = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItem(
    folderWithCount: FolderWithDocumentCount,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(R.string.folders_cd_icon),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderWithCount.folder.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.document_count,
                            folderWithCount.documentCount,
                            folderWithCount.documentCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, Modifier.size(20.dp)) },
                onClick = { menuExpanded = false; onRenameClick() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menuExpanded = false; onDeleteClick() }
            )
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    label: String,
    initialName: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun CreateFolderDialog(onDismissRequest: () -> Unit, onConfirm: (String) -> Unit) {
    FolderNameDialog(
        title = stringResource(R.string.dialog_create_folder_title),
        label = stringResource(R.string.dialog_create_folder_label),
        initialName = "",
        confirmText = stringResource(R.string.action_create),
        onDismiss = onDismissRequest,
        onConfirm = onConfirm
    )
}