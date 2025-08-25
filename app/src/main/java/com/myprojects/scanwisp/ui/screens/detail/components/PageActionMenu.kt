package com.myprojects.scanwisp.ui.screens.detail.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myprojects.scanwisp.R

@Composable
fun PageActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSetAsCoverClick: () -> Unit,
    onShareClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.detail_page_menu_set_as_cover)) },
            onClick = {
                onSetAsCoverClick()
                onDismissRequest()
            },
            leadingIcon = {
                Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.detail_page_menu_share_jpeg)) },
            onClick = {
                onShareClick()
                onDismissRequest()
            },
            leadingIcon = {
                Icon(Icons.Outlined.Share, contentDescription = null)
            }
        )
    }
}