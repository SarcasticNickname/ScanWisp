package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.ui.screens.home.ExportAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    onDismissRequest: () -> Unit,
    onConfirm: (format: ExportFormat, filename: String) -> Unit,
    pageCount: Int,
    defaultFilename: String,
    action: ExportAction
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var filename by remember(defaultFilename) { mutableStateOf(defaultFilename) }
    val isFilenameValid = filename.isNotBlank() && !filename.contains(Regex("[\\\\/:*?\"<>|]"))

    val jpegEnabled = pageCount == 1
    val zipEnabled = pageCount > 1

    if ((selectedFormat == ExportFormat.JPEG && !jpegEnabled) || (selectedFormat == ExportFormat.ZIP && !zipEnabled)) {
        selectedFormat = ExportFormat.PDF
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // START: AI_MODIFIED_BLOCK - Строки вынесены в ресурсы
            Text(
                text = if (action == ExportAction.SHARE) stringResource(R.string.export_sheet_title_share) else stringResource(
                    R.string.export_sheet_title_save
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(Modifier.selectableGroup()) {
                FormatOption(
                    format = ExportFormat.PDF,
                    selected = selectedFormat == ExportFormat.PDF,
                    onClick = { selectedFormat = ExportFormat.PDF }
                )
                FormatOption(
                    format = ExportFormat.JPEG,
                    selected = selectedFormat == ExportFormat.JPEG,
                    enabled = jpegEnabled,
                    onClick = { selectedFormat = ExportFormat.JPEG }
                )
                FormatOption(
                    format = ExportFormat.ZIP,
                    selected = selectedFormat == ExportFormat.ZIP,
                    enabled = zipEnabled,
                    onClick = { selectedFormat = ExportFormat.ZIP }
                )
            }
            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = selectedFormat == ExportFormat.PDF,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.export_pdf_quality_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = filename,
                onValueChange = { filename = it.take(120) },
                label = { Text(stringResource(R.string.export_filename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = !isFilenameValid,
                supportingText = {
                    if (!isFilenameValid && filename.isNotBlank()) {
                        Text(stringResource(R.string.export_filename_error))
                    }
                }
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    onConfirm(selectedFormat, filename.trim())
                    onDismissRequest()
                },
                enabled = isFilenameValid && filename.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (action == ExportAction.SHARE) stringResource(R.string.action_share) else stringResource(
                        R.string.action_save
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
            // END: AI_MODIFIED_BLOCK
        }
    }
}

@Composable
private fun FormatOption(
    format: ExportFormat,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    // START: AI_MODIFIED_BLOCK - Строки вынесены в ресурсы
    val (icon, title, description) = when (format) {
        ExportFormat.PDF -> Triple(
            Icons.Outlined.PictureAsPdf,
            stringResource(R.string.export_format_pdf),
            stringResource(R.string.export_format_pdf_desc)
        )

        ExportFormat.JPEG -> Triple(
            Icons.Outlined.Image,
            stringResource(R.string.export_format_jpeg),
            stringResource(R.string.export_format_jpeg_desc)
        )

        ExportFormat.ZIP -> Triple(
            Icons.Outlined.FileCopy,
            stringResource(R.string.export_format_zip),
            stringResource(R.string.export_format_zip_desc)
        )
    }
    // END: AI_MODIFIED_BLOCK

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
    }
}