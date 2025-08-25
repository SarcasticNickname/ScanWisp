package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.model.FolderEntity

@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    onDismissRequest: () -> Unit,
    onFolderSelected: (String?) -> Unit // String? для возможности перемещения в корень
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // START: AI_MODIFIED_BLOCK
        title = { Text(stringResource(R.string.dialog_move_title)) },
        // END: AI_MODIFIED_BLOCK
        text = {
            LazyColumn {
                // Опция для перемещения на главный экран (в корень)
                item {
                    FolderSelectionItem(
                        // START: AI_MODIFIED_BLOCK
                        name = stringResource(R.string.dialog_move_option_no_folder),
                        // END: AI_MODIFIED_BLOCK
                        onClick = { onFolderSelected(null) })
                    Divider()
                }
                items(folders) { folder ->
                    FolderSelectionItem(
                        name = folder.name,
                        onClick = { onFolderSelected(folder.id) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                // START: AI_MODIFIED_BLOCK
                Text(stringResource(R.string.action_cancel))
                // END: AI_MODIFIED_BLOCK
            }
        }
    )
}

@Composable
private fun FolderSelectionItem(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
    }
}