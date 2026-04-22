package com.myprojects.scanwisp.ui.screens.preview.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myprojects.scanwisp.domain.model.EditableWord
import com.myprojects.scanwisp.domain.model.TextEditMode

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TokenEditorSheet(
    editableWords: List<EditableWord>,
    activeWordId: String?,
    editMode: TextEditMode,
    freeTextBuffer: String,
    showSwitchModeDialog: Boolean,
    onWordTap: (String) -> Unit,
    onWordLongPress: (String) -> Unit,
    onWordTextCommit: (String, String) -> Unit,
    onWordEditCancel: () -> Unit,
    onFreeTextChanged: (String) -> Unit,
    onSwitchToFreeTextRequest: () -> Unit,
    onSwitchToTokenMode: () -> Unit,
    onSwitchModeConfirmed: () -> Unit,
    onSwitchModeDismissed: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Заголовок
        Text(
            text = "Редактирование текста",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(12.dp))

        // Переключатель режима
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                onClick = onSwitchToTokenMode,
                selected = editMode is TextEditMode.Token
            ) { Text("По словам") }

            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                onClick = onSwitchToFreeTextRequest,
                selected = editMode is TextEditMode.FreeText
            ) { Text("Свободно") }
        }

        Spacer(Modifier.height(4.dp))

        // Подсказка под переключателем
        Text(
            text = when (editMode) {
                is TextEditMode.Token ->
                    "Tap — изменить слово · Long press — удалить / восстановить"
                is TextEditMode.FreeText ->
                    "Позиции будут восстановлены приближённо при сохранении"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        // Контент
        when (editMode) {
            is TextEditMode.Token -> {
                TokenModeContent(
                    words = editableWords,
                    activeWordId = activeWordId,
                    onWordTap = onWordTap,
                    onWordLongPress = onWordLongPress,
                    onWordTextCommit = onWordTextCommit,
                    onWordEditCancel = onWordEditCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
            is TextEditMode.FreeText -> {
                OutlinedTextField(
                    value = freeTextBuffer,
                    onValueChange = onFreeTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 240.dp),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Кнопки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) { Text("Отмена") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave) { Text("Сохранить") }
        }

        Spacer(Modifier.height(16.dp))
    }

    // Диалог предупреждения при переключении в свободный режим
    if (showSwitchModeDialog) {
        AlertDialog(
            onDismissRequest = onSwitchModeDismissed,
            title = { Text("Переключить режим?") },
            text = {
                Text(
                    "В свободном режиме позиции слов будут восстановлены приближённо " +
                            "через алгоритм сравнения. Поиск в PDF сохранится."
                )
            },
            confirmButton = {
                TextButton(onClick = onSwitchModeConfirmed) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = onSwitchModeDismissed) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenModeContent(
    words: List<EditableWord>,
    activeWordId: String?,
    onWordTap: (String) -> Unit,
    onWordLongPress: (String) -> Unit,
    onWordTextCommit: (String, String) -> Unit,
    onWordEditCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (words.isEmpty()) {
        Text(
            text = "Слова не загружены. Запустите распознавание текста.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        words.forEach { word ->
            WordTokenChip(
                word = word,
                isActive = word.id == activeWordId,
                onTap = { onWordTap(word.id) },
                onLongPress = { onWordLongPress(word.id) },
                onTextCommit = { newText -> onWordTextCommit(word.id, newText) },
                onEditCancel = onWordEditCancel
            )
        }
    }
}