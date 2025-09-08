package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.domain.model.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(
    screenTitle: String,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onMoveSelected: () -> Unit,
    onSortClick: () -> Unit,
    viewMode: ViewMode,
    onViewModeToggle: () -> Unit,
    onSelectAll: () -> Unit,
    onMergeSelected: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = isSelectionMode,
        label = "top_bar_animation",
        transitionSpec = {
            if (targetState) { // entering selection mode
                slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + fadeOut()
            } else { // exiting selection mode
                slideInVertically { height -> -height } + fadeIn() togetherWith
                        slideOutVertically { height -> height } + fadeOut()
            }
        }
    ) { selectionModeActive ->
        if (selectionModeActive) {
            SelectionTopAppBar(
                selectedCount = selectedCount,
                onClearSelection = onClearSelection,
                onDeleteSelected = onDeleteSelected,
                onShareSelected = onShare,
                onSaveSelected = onSave,
                onMoveSelected = onMoveSelected,
                onSelectAll = onSelectAll,
                onMergeSelected = onMergeSelected
            )
        } else {
            DefaultTopAppBar(
                title = screenTitle,
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onQueryChange = onQueryChange,
                onSearchToggle = { isSearchActive = !isSearchActive },
                scrollBehavior = scrollBehavior,
                onSortClick = onSortClick,
                viewMode = viewMode,
                onViewModeToggle = onViewModeToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopAppBar(
    title: String,
    isSearchActive: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onSortClick: () -> Unit,
    viewMode: ViewMode,
    onViewModeToggle: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.home_top_bar_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
            } else {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (isSearchActive) {
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.home_top_bar_cd_back)
                    )
                }
            }
        },
        actions = {
            if (isSearchActive) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.home_top_bar_cd_clear_search)
                        )
                    }
                }
            } else {
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.home_top_bar_action_search)
                    )
                }
                IconButton(onClick = onSortClick) {
                    Icon(
                        Icons.Default.Sort,
                        contentDescription = stringResource(R.string.home_top_bar_action_sort)
                    )
                }
                IconButton(onClick = onViewModeToggle) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.GRID) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = stringResource(R.string.home_top_bar_cd_toggle_view)
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onSaveSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onSelectAll: () -> Unit,
    onMergeSelected: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(
                    id = R.plurals.selection_count,
                    count = selectedCount,
                    selectedCount
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_selection_bar_cd_clear_selection)
                )
            }
        },
        actions = {
            IconButton(onClick = onShareSelected) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.home_selection_bar_cd_share)
                )
            }
            IconButton(onClick = onMoveSelected) {
                Icon(
                    Icons.Outlined.DriveFileMove,
                    contentDescription = stringResource(R.string.home_selection_bar_cd_move)
                )
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_selection_bar_cd_delete)
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.home_top_bar_cd_more_actions)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_top_bar_menu_select_all)) },
                        onClick = {
                            onSelectAll()
                            menuExpanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.SelectAll, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save)) },
                        onClick = {
                            onSaveSelected()
                            menuExpanded = false
                        },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }
                    )
                    if (selectedCount > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_merge)) },
                            onClick = {
                                onMergeSelected()
                                menuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.MergeType, null) }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}