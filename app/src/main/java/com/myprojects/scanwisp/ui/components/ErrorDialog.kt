package com.myprojects.scanwisp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.PublishedWithChanges
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.AppError

@Composable
fun ErrorDialog(
    error: AppError,
    onDismiss: () -> Unit,
) {
    val title: String
    val message: String
    val icon: ImageVector

    when (error) {
        is AppError.DatabaseOperationError -> {
            title = stringResource(R.string.error_dialog_title_database)
            message = stringResource(R.string.error_dialog_message_database)
            icon = Icons.Outlined.SdStorage
        }

        is AppError.ExportError -> {
            title = stringResource(R.string.error_dialog_title_export)
            message = stringResource(R.string.error_dialog_message_export)
            icon = Icons.Outlined.UploadFile
        }

        is AppError.ImageProcessingError -> {
            title = stringResource(R.string.error_dialog_title_image)
            message = stringResource(R.string.error_dialog_message_image)
            icon = Icons.Outlined.ImageNotSupported
        }

        is AppError.ScannerLaunchError -> {
            title = stringResource(R.string.error_dialog_title_scanner)
            message = stringResource(R.string.error_dialog_message_scanner)
            icon = Icons.Outlined.PublishedWithChanges
        }

        else -> { // General, LoadDataError (хотя он не должен появляться в диалоге)
            title = stringResource(R.string.error_general_title)
            message = stringResource(R.string.error_dialog_message_general)
            icon = Icons.Outlined.ErrorOutline
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}