package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.ui.screens.home.ExportAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    onDismissRequest: () -> Unit,
    onConfirm: (format: ExportFormat, filename: String, pdfProfile: PdfExportProfile?, fitToA4: Boolean?) -> Unit,
    pageCount: Int,
    defaultFilename: String,
    action: ExportAction,
    estimatedSourceBytes: Long = 0L   // сумма размеров processedImagePath из делегата
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var filename by remember(defaultFilename) { mutableStateOf(defaultFilename) }
    var selectedProfile by remember { mutableStateOf(PdfExportProfile.BALANCED) }
    var fitToA4 by remember { mutableStateOf(true) }

    val isFilenameValid = filename.isNotBlank() && !filename.contains(Regex("[\\\\/:*?\"<>|]"))
    val jpegEnabled = pageCount == 1
    val zipEnabled = pageCount > 1

    if (selectedFormat == ExportFormat.JPEG && !jpegEnabled) selectedFormat = ExportFormat.PDF
    if (selectedFormat == ExportFormat.ZIP && !zipEnabled) selectedFormat = ExportFormat.PDF

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (action == ExportAction.SHARE)
                    stringResource(R.string.export_sheet_title_share)
                else
                    stringResource(R.string.export_sheet_title_save),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ─── Формат ──────────────────────────────────────────────────────
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

            // ─── PDF-настройки ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedFormat == ExportFormat.PDF,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Качество PDF",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    // SegmentedButton: Малый / Балланс / Высокое
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        PdfExportProfile.entries.forEachIndexed { index, profile ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = PdfExportProfile.entries.size
                                ),
                                selected = selectedProfile == profile,
                                onClick = { selectedProfile = profile }
                            ) {
                                Text(
                                    text = when (profile) {
                                        PdfExportProfile.SMALL    -> "Малый"
                                        PdfExportProfile.BALANCED -> "Баланс"
                                        PdfExportProfile.HIGH     -> "Высокое"
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Подпись с описанием и приблизительным размером
                    Spacer(Modifier.height(6.dp))
                    val sizeLabel = estimatedSizeLabel(estimatedSourceBytes, selectedProfile)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (selectedProfile) {
                                        PdfExportProfile.SMALL    -> "Чёрно-белый, максимальное сжатие"
                                        PdfExportProfile.BALANCED -> "Цветной, умеренное сжатие"
                                        PdfExportProfile.HIGH     -> "Цветной, без потерь качества"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "≈ $sizeLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Вписать в A4
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AspectRatio,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_item_fit_to_a4),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Стандартные поля, удобно для печати",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = fitToA4,
                            onCheckedChange = { fitToA4 = it }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── Имя файла ───────────────────────────────────────────────────
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

            // ─── Кнопка ──────────────────────────────────────────────────────
            Button(
                onClick = {
                    onConfirm(
                        selectedFormat,
                        filename.trim(),
                        if (selectedFormat == ExportFormat.PDF) selectedProfile else null,
                        if (selectedFormat == ExportFormat.PDF) fitToA4 else null
                    )
                    onDismissRequest()
                },
                enabled = isFilenameValid && filename.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (action == ExportAction.SHARE)
                        stringResource(R.string.action_share)
                    else
                        stringResource(R.string.action_save)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Расчёт примерного размера ───────────────────────────────────────────────

private fun estimatedSizeLabel(sourceBytes: Long, profile: PdfExportProfile): String {
    if (sourceBytes <= 0L) return "—"
    val estimatedBytes = when (profile) {
        PdfExportProfile.HIGH     -> sourceBytes
        PdfExportProfile.BALANCED -> (sourceBytes * 0.7).toLong()
        PdfExportProfile.SMALL    -> (sourceBytes * 0.3).toLong()
    }
    return formatBytes(estimatedBytes)
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L              -> "$bytes Б"
        bytes < 1024L * 1024L      -> "${bytes / 1024} КБ"
        bytes < 1024L * 1024L * 10 -> "${"%.1f".format(bytes / 1_048_576.0)} МБ"
        else                       -> "${bytes / 1_048_576} МБ"
    }
}

// ─── FormatOption ─────────────────────────────────────────────────────────────

@Composable
private fun FormatOption(
    format: ExportFormat,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val (icon, title, description) = when (format) {
        ExportFormat.PDF  -> Triple(Icons.Outlined.PictureAsPdf,
            stringResource(R.string.export_format_pdf),
            stringResource(R.string.export_format_pdf_desc))
        ExportFormat.JPEG -> Triple(Icons.Outlined.Image,
            stringResource(R.string.export_format_jpeg),
            stringResource(R.string.export_format_jpeg_desc))
        ExportFormat.ZIP  -> Triple(Icons.Outlined.FileCopy,
            stringResource(R.string.export_format_zip),
            stringResource(R.string.export_format_zip_desc))
    }

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
        Icon(icon, contentDescription = title, tint = contentColor)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            Text(description, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}