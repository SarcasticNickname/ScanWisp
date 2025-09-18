package com.myprojects.scanwisp.ui.screens.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
    val showCreateFolderDialog by viewModel.isDialogVisible.collectAsStateWithLifecycle()

    var errorToShowInDialog by remember { mutableStateOf<AppError?>(null) }

    val analytics = Firebase.analytics

    LaunchedEffect(key1 = true) {
        viewModel.uiEventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowErrorDialog -> {
                    errorToShowInDialog = event.error
                }
                // Можно добавить обработку других событий в будущем
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.folders_screen_title)) })
        },
        bottomBar = {
            ScanWispBottomAppBar(navController = navController)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddFolderRequest() }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.fab_cd_create_folder)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // ОБНОВЛЯЕМ ЛОГИКУ ОТОБРАЖЕНИЯ
            when (val state = uiState) {
                is FoldersUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is FoldersUiState.Error -> {
                    ErrorState(
                        error = state.error,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is FoldersUiState.Success -> {
                    if (state.items.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            icon = Icons.Default.CreateNewFolder,
                            title = stringResource(R.string.folders_empty_state_title),
                            subtitle = stringResource(R.string.folders_empty_state_subtitle),
                            ctaText = stringResource(R.string.folders_empty_state_cta),
                            onCtaClick = { viewModel.onAddFolderRequest() }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
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
                                contentType = { _, item -> if (item is FolderWithDocumentCount) "folder" else "ad" }
                            ) { _, item ->
                                when (item) {
                                    is FolderWithDocumentCount -> {
                                        FolderItem(
                                            folderWithCount = item,
                                            onClick = {
                                                // --- АНАЛИТИКА ---
                                                analytics.logEvent("folder_opened", null)
                                                navController.navigate(
                                                    Screen.Home.createRouteWithFolder(item.folder.id)
                                                )
                                            },
                                            modifier = Modifier.animateItem()
                                        )
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
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismissRequest = { viewModel.onDialogDismiss() },
            onConfirm = { name ->
                viewModel.createFolder(name)
            }
        )
    }

    errorToShowInDialog?.let { error ->
        ErrorDialog(
            error = error,
            onDismiss = { errorToShowInDialog = null }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderItem(
    folderWithCount: FolderWithDocumentCount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = stringResource(R.string.folders_cd_icon)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = folderWithCount.folder.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pluralStringResource(
                        id = R.plurals.document_count,
                        count = folderWithCount.documentCount,
                        folderWithCount.documentCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CreateFolderDialog(onDismissRequest: () -> Unit, onConfirm: (String) -> Unit) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.dialog_create_folder_title)) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.dialog_create_folder_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}