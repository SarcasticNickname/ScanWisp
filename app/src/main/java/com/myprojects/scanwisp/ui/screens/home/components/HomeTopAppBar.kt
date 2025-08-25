package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            isSearchActive = false
        }
    }

    AnimatedContent(
        targetState = isSelectionMode,
        label = "top_bar_animation",
        transitionSpec = {
            if (targetState) { // entering selection mode
                slideInVertically { height -> -height } + fadeIn() togetherWith
                        slideOutVertically { height -> height } + fadeOut()
            } else { // exiting selection mode
                slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + fadeOut()
            }
        }
    ) { selectionModeActive ->
        if (selectionModeActive) {
            HomeSelectionTopAppBar(
                selectedCount = selectedCount,
                onClearSelection = onClearSelection,
                onDeleteSelected = onDeleteSelected,
                onShare = onShare,
                onSave = onSave,
                onMoveSelected = onMoveSelected,
                onMergeSelected = onMergeSelected
            )
        } else {
            Column {
                TopAppBar(
                    title = {
                        if (!isSearchActive) {
                            Text(screenTitle)
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = onQueryChange,
                                onBack = {
                                    isSearchActive = false
                                    onQueryChange("")
                                }
                            )
                        } else {
                            IconButton(onClick = onViewModeToggle) {
                                Icon(
                                    imageVector = if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.ViewModule,
                                    contentDescription = stringResource(R.string.home_top_bar_cd_toggle_view)
                                )
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.home_top_bar_cd_more_actions)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.home_top_bar_menu_select_all)) },
                                        onClick = {
                                            onSelectAll()
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (!isSearchActive) {
                    ActionBar(
                        onSearchClick = { isSearchActive = true },
                        onSortClick = onSortClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBar(
    onSearchClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = Icons.Default.Search,
            text = stringResource(R.string.home_top_bar_action_search),
            onClick = onSearchClick
        )
        ActionButton(
            icon = Icons.Default.Sort,
            text = stringResource(R.string.home_top_bar_action_sort),
            onClick = onSortClick
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = text)
            Text(text = text, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.home_top_bar_cd_back)
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.home_top_bar_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_top_bar_cd_clear_search)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onMoveSelected: () -> Unit,
    onMergeSelected: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = selectedCount,
                    label = "counter_animation",
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
                    // START: AI_MODIFIED_BLOCK - Использование plural
                    text = " " + pluralStringResource(
                        R.plurals.selection_count,
                        selectedCount
                    ).substringAfter(" "),
                    // END: AI_MODIFIED_BLOCK
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.home_selection_bar_cd_clear_selection)
                )
            }
        },
        actions = {
            if (selectedCount > 1) {
                IconButton(onClick = onMergeSelected) {
                    Icon(
                        Icons.Default.MergeType,
                        stringResource(R.string.home_selection_bar_cd_merge)
                    )
                }
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Default.Delete, stringResource(R.string.home_selection_bar_cd_delete))
            }
            IconButton(onClick = onSave) {
                Icon(
                    Icons.Default.FileDownload,
                    stringResource(R.string.home_selection_bar_cd_save)
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, stringResource(R.string.home_selection_bar_cd_share))
            }
            IconButton(onClick = onMoveSelected) {
                Icon(
                    Icons.Default.DriveFileMove,
                    stringResource(R.string.home_selection_bar_cd_move)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    )
}