package com.myprojects.scanwisp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.PublishedWithChanges
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.AppError

@Composable
fun ErrorState(
    error: AppError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Объявляем переменные, которые будут инициализированы внутри when
    val title: String
    val subtitle: String?

    // 'when' теперь возвращает значение для 'icon',
    // а внутри веток присваивает значения для 'title' и 'subtitle'
    val icon = when (error) {
        is AppError.DatabaseOperationError -> {
            title = stringResource(R.string.error_database_title)
            subtitle = stringResource(R.string.error_database_subtitle)
            Icons.Outlined.FolderZip
        }

        is AppError.FileSystemError -> {
            title = stringResource(R.string.error_filesystem_title)
            subtitle = stringResource(R.string.error_filesystem_subtitle)
            Icons.Outlined.FolderZip
        }

        is AppError.General -> {
            title = stringResource(R.string.error_general_title)
            subtitle = error.message
            Icons.Outlined.ErrorOutline
        }

        AppError.ExportError -> {
            title = stringResource(R.string.error_dialog_title_export)
            subtitle = stringResource(R.string.error_dialog_message_export)
            Icons.Outlined.UploadFile
        }

        AppError.ImageProcessingError -> {
            title = stringResource(R.string.error_dialog_title_image)
            subtitle = stringResource(R.string.error_dialog_message_image)
            Icons.Outlined.ImageNotSupported
        }

        AppError.LoadDataError -> {
            title = stringResource(R.string.error_database_title)
            subtitle = stringResource(R.string.error_database_subtitle)
            Icons.Outlined.FolderZip
        }

        AppError.ScannerLaunchError -> {
            title = stringResource(R.string.error_dialog_title_scanner)
            subtitle = stringResource(R.string.error_dialog_message_scanner)
            Icons.Outlined.PublishedWithChanges
        }

        // Обработка забытого случая NotEnoughStorageError
        AppError.NotEnoughStorageError -> {
            title =
                stringResource(R.string.error_filesystem_title) // Переиспользуем подходящую строку
            subtitle =
                stringResource(R.string.error_dialog_message_export) // Переиспользуем подходящую строку
            Icons.Outlined.SdStorage
        }
    }

    EmptyState(
        modifier = modifier,
        icon = icon,
        title = title,
        subtitle = subtitle,
        ctaText = if (onRetry != null) stringResource(R.string.action_retry) else null,
        onCtaClick = onRetry
    )
}