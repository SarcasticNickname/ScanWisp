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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.NavController
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.ui.components.EmptyState
import com.myprojects.scanwisp.ui.navigation.Screen
import com.myprojects.scanwisp.ui.screens.home.components.NativeAdListItem
import com.myprojects.scanwisp.ui.screens.home.components.ScanWispBottomAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    navController: NavController,
    viewModel: FoldersViewModel = hiltViewModel()
) {
    val state by viewModel.foldersState.collectAsState()
    val showCreateFolderDialog by viewModel.isDialogVisible.collectAsState()

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
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val folders = state.folders
                val nativeAd = state.nativeAd

                if (folders.isEmpty() && nativeAd == null) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Default.CreateNewFolder,
                        title = stringResource(R.string.folders_empty_state_title),
                        subtitle = stringResource(R.string.folders_empty_state_subtitle),
                        ctaText = stringResource(R.string.folders_empty_state_cta),
                        onCtaClick = { viewModel.onAddFolderRequest() }
                    )
                } else {
                    val listItems = remember(folders, nativeAd) {
                        val items: MutableList<Any> = folders.toMutableList()
                        val adPosition = 1
                        if (nativeAd != null && items.size >= adPosition) {
                            items.add(adPosition, nativeAd)
                        } else if (nativeAd != null && items.isEmpty()) {
                            items.add(nativeAd)
                        }
                        items
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = listItems,
                            key = { item -> if (item is FolderWithDocumentCount) item.folder.id else "ad_folder_item" },
                            contentType = { item -> if (item is FolderWithDocumentCount) "folder" else "ad" }
                        ) { item ->
                            when (item) {
                                is FolderWithDocumentCount -> {
                                    FolderItem(
                                        folderWithCount = item,
                                        onClick = {
                                            navController.navigate(
                                                Screen.Home.createRouteWithFolder(
                                                    item.folder.id
                                                )
                                            )
                                        },
                                        modifier = Modifier.animateItemPlacement()
                                    )
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
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismissRequest = { viewModel.onDialogDismiss() },
            onConfirm = { name ->
                viewModel.createFolder(name)
            }
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