package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.myprojects.scanwisp.ui.model.UiAction

/**
 * Универсальное выпадающее меню, которое рендерит список действий UiAction.
 */
@Composable
fun ActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<UiAction>
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    val color = if (action.isDestructive) MaterialTheme.colorScheme.error else Color.Unspecified
                    Text(text = action.title, color = color)
                },
                onClick = {
                    action.onClick()
                    onDismissRequest()
                },
                leadingIcon = {
                    val color = if (action.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.title,
                        tint = color
                    )
                }
            )
        }
    }
}
