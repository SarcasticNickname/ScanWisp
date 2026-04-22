package com.myprojects.scanwisp.ui.screens.preview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myprojects.scanwisp.domain.model.EditableWord

/**
 * Чип-слово в редакторе токенов.
 *
 * - Цвет фона отражает confidence Tesseract (зелёный / жёлтый / красный).
 * - Tap → инлайн-редактирование прямо в чипе.
 * - Long press → помечает как удалённое (зачёркивание), повторный long press → восстанавливает.
 * - Удалённые слова видны (полупрозрачные, зачёркнутые) — пользователь может передумать.
 */
@Composable
fun WordTokenChip(
    word: EditableWord,
    isActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onTextCommit: (String) -> Unit,
    onEditCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chipColors(word.confidence)
    val shape = RoundedCornerShape(20.dp)

    val baseModifier = modifier
        .border(width = if (isActive) 1.5.dp else 0.5.dp, color = colors.border, shape = shape)
        .background(
            color = colors.background.copy(alpha = if (word.isDeleted) 0.4f else 1f),
            shape = shape
        )
        .combinedClickable(
            onClick = onTap,
            onLongClick = onLongPress
        )
        .padding(horizontal = 10.dp, vertical = 5.dp)

    if (isActive && !word.isDeleted) {
        // Инлайн-редактор поверх чипа
        val focusRequester = remember { FocusRequester() }
        var fieldValue by remember {
            mutableStateOf(TextFieldValue(word.text, selection = TextRange(word.text.length)))
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        BasicTextField(
            value = fieldValue,
            onValueChange = { fieldValue = it },
            modifier = baseModifier
                .widthIn(min = 32.dp)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = colors.text
            ),
            cursorBrush = SolidColor(colors.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onTextCommit(fieldValue.text) }
            )
        )
    } else {
        // Обычный чип
        Box(
            modifier = baseModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = word.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = colors.text.copy(alpha = if (word.isDeleted) 0.5f else 1f),
                    textDecoration = if (word.isDeleted) TextDecoration.LineThrough else TextDecoration.None
                )
            )
        }
    }
}

// ─── Цвета по confidence ──────────────────────────────────────────────────────

private data class ChipColors(val background: Color, val border: Color, val text: Color)

@Composable
private fun chipColors(confidence: Float): ChipColors {
    return when {
        confidence >= 0.75f -> ChipColors(
            background = Color(0xFFEAF3DE),
            border     = Color(0xFFC0DD97),
            text       = Color(0xFF27500A)
        )
        confidence >= 0.40f -> ChipColors(
            background = Color(0xFFFAEEDA),
            border     = Color(0xFFFAC775),
            text       = Color(0xFF633806)
        )
        else -> ChipColors(
            background = Color(0xFFFCEBEB),
            border     = Color(0xFFF7C1C1),
            text       = Color(0xFF791F1F)
        )
    }
}