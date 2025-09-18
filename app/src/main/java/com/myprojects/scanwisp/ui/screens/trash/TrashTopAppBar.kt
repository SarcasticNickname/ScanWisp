package com.myprojects.scanwisp.ui.screens.trash

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.myprojects.scanwisp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashTopAppBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    onNavigateBack: () -> Unit,
    onClearSelection: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onEmptyTrash: () -> Unit,
    canEmptyTrash: Boolean
) {
    if (isSelectionMode) {
        // TopBar для режима выделения
        TopAppBar(
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.selection_count,
                        selectedCount,
                        selectedCount
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_selection_bar_cd_clear_selection)
                    )
                }
            },
            actions = {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Default.RestoreFromTrash,
                        contentDescription = stringResource(R.string.trash_selection_bar_cd_restore)
                    )
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = stringResource(R.string.trash_selection_bar_cd_delete_forever)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    } else {
        // TopBar по умолчанию
        TopAppBar(
            title = { Text(stringResource(R.string.trash_screen_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_cd_back)
                    )
                }
            },
            actions = {
                if (canEmptyTrash) {
                    TextButton(onClick = onEmptyTrash) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Text(stringResource(R.string.trash_top_bar_empty_trash))
                    }
                }
            }
        )
    }
}