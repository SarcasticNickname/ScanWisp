package com.myprojects.scanwisp.ui.screens.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myprojects.scanwisp.R

/**
 * TopAppBar для стандартного режима экрана деталей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefaultTopAppBar(
    title: String,
    onNavigateBack: () -> Unit,
    onSortClick: () -> Unit,
    onTitleClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoveClick: () -> Unit,
    onOcrClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTitleClick
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.detail_top_bar_cd_edit_title),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.detail_top_bar_cd_back)
                )
            }
        },
        actions = {
            IconButton(onClick = onOcrClick) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = stringResource(R.string.detail_top_bar_cd_ocr)
                )
            }
            IconButton(onClick = onShareClick) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.detail_top_bar_cd_share)
                )
            }
            IconButton(onClick = onMoveClick) {
                Icon(
                    Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = stringResource(R.string.detail_top_bar_cd_move)
                )
            }
            IconButton(onClick = onSortClick) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = stringResource(R.string.detail_top_bar_cd_toggle_sort)
                )
            }
        }
    )
}

/**
 * TopAppBar для режима выделения страниц.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
    onSplitSelected: () -> Unit,
    onOcrSelected: () -> Unit,
    canSplit: Boolean
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = selectedCount,
                    label = "counter_animation_detail",
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInVertically { height -> height } + fadeIn() togetherWith
                                    slideOutVertically { height -> -height } + fadeOut()
                        } else {
                            slideInVertically { height -> -height } + fadeIn() togetherWith
                                    slideOutVertically { height -> height } + fadeOut()
                        }
                    }
                ) { count ->
                    Text(
                        text = "$count",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = " " + pluralStringResource(
                        R.plurals.selection_count,
                        selectedCount
                    ).substringAfter(" "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.detail_selection_bar_cd_cancel)
                )
            }
        },
        actions = {
            IconButton(onClick = onOcrSelected) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = stringResource(R.string.detail_selection_bar_cd_ocr)
                )
            }
            if (canSplit) {
                IconButton(onClick = onSplitSelected) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = stringResource(R.string.detail_selection_bar_cd_split)
                    )
                }
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.detail_selection_bar_cd_delete)
                )
            }
            IconButton(onClick = onExportSelected) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.detail_selection_bar_cd_share)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/**
 * TopAppBar для режима сортировки страниц.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortModeTopAppBar(onDoneClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.detail_sort_mode_bar_title)) },
        navigationIcon = {
            IconButton(onClick = onDoneClick) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = stringResource(R.string.detail_sort_mode_bar_cd_done)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}