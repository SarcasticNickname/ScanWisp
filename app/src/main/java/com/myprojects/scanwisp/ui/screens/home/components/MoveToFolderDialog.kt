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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onFolderSelected: (String?) -> Unit,
    onCreateFolder: ((String) -> Unit)? = null   // null = фича отключена
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.dialog_move_title)) },
        text = {
            LazyColumn {
                // Главный экран (без папки)
                item(key = "no_folder") {
                    FolderSelectionItem(
                        name = stringResource(R.string.dialog_move_option_no_folder),
                        onClick = { onFolderSelected(null) }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }

                // Существующие папки
                items(items = folders, key = { it.id }) { folder ->
                    FolderSelectionItem(
                        name = folder.name,
                        onClick = { onFolderSelected(folder.id) }
                    )
                }

                // Кнопка «Создать папку» и поле ввода — только если колбэк передан
                if (onCreateFolder != null) {
                    item(key = "create_folder") {
                        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                        if (!showCreateField) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCreateField = true }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = "Новая папка…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newFolderName,
                                    onValueChange = { newFolderName = it },
                                    label = { Text(stringResource(R.string.dialog_create_folder_label)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        if (newFolderName.isNotBlank()) {
                                            onCreateFolder(newFolderName)
                                            newFolderName = ""
                                            showCreateField = false
                                        }
                                    },
                                    enabled = newFolderName.isNotBlank()
                                ) {
                                    Text(stringResource(R.string.action_create))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
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
        Icon(
            Icons.Default.Folder,
            contentDescription = stringResource(R.string.cd_icon_folder),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
    }
}