package com.myprojects.scanwisp.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Универсальная модель для представления действия в UI, например, пункта в меню.
 *
 * @param title Текст, отображаемый пользователю.
 * @param icon Иконка действия.
 * @param onClick Лямбда, которая будет вызвана при нажатии.
 * @param isDestructive Флаг для "опасных" действий (например, удаление),
 *                      позволяющий UI окрасить их в цвет ошибки.
 */
@Immutable
data class UiAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false
)