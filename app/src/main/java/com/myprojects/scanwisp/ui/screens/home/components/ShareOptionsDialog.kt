package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myprojects.scanwisp.domain.model.ExportFormat

@Composable
fun ShareOptionsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (format: ExportFormat, filename: String) -> Unit,
    pageCount: Int,
    defaultFilename: String
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var filename by remember(defaultFilename) { mutableStateOf(defaultFilename) }
    val isFilenameValid = filename.isNotBlank() && !filename.contains(Regex("[\\\\/:*?\"<>|]"))

    val jpegEnabled = pageCount == 1
    val zipEnabled = pageCount > 1

    // Если ранее выбранный формат стал недоступен, сбрасываем на PDF
    if ((selectedFormat == ExportFormat.JPEG && !jpegEnabled) || (selectedFormat == ExportFormat.ZIP && !zipEnabled)) {
        selectedFormat = ExportFormat.PDF
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Параметры экспорта") },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    FormatRadioButton(
                        format = ExportFormat.PDF,
                        selected = selectedFormat == ExportFormat.PDF,
                        onClick = { selectedFormat = ExportFormat.PDF }
                    )
                    FormatRadioButton(
                        format = ExportFormat.JPEG,
                        selected = selectedFormat == ExportFormat.JPEG,
                        enabled = jpegEnabled,
                        onClick = { selectedFormat = ExportFormat.JPEG }
                    )
                    FormatRadioButton(
                        format = ExportFormat.ZIP,
                        selected = selectedFormat == ExportFormat.ZIP,
                        enabled = zipEnabled,
                        onClick = { selectedFormat = ExportFormat.ZIP }
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it.take(120) }, // Ограничение длины
                    label = { Text("Имя файла") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = !isFilenameValid,
                    supportingText = {
                        if (!isFilenameValid && filename.isNotBlank()) {
                            Text("Имя содержит недопустимые символы")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedFormat, filename.trim()) },
                enabled = isFilenameValid && filename.isNotBlank()
            ) {
                Text("Продолжить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun FormatRadioButton(
    format: ExportFormat,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val title = when (format) {
        ExportFormat.PDF -> "PDF-документ"
        ExportFormat.JPEG -> "Изображение (JPEG)"
        ExportFormat.ZIP -> "ZIP-архив с изображениями"
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            )
        )
    }
}