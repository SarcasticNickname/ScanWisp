package com.myprojects.scanwisp.ui.screens.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.PageSearchResult
import com.myprojects.scanwisp.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value            = uiState.query,
                        onValueChange    = viewModel::onQueryChanged,
                        placeholder      = { Text(stringResource(R.string.search_content_placeholder)) },
                        singleLine       = true,
                        keyboardOptions  = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions  = KeyboardActions(onSearch = { /* скрыть клавиатуру */ }),
                        colors           = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier         = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.search_cd_back)
                        )
                    }
                },
                actions = {
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = viewModel::clearQuery) {
                            Icon(
                                imageVector        = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.search_cd_clear)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isSearching -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.hasSearched && uiState.results.isEmpty() -> {
                    NoResultsState(
                        query    = uiState.query,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                !uiState.hasSearched -> {
                    Text(
                        text     = stringResource(R.string.search_content_hint),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }

                else -> {
                    SearchResultsList(
                        results     = uiState.results,
                        onItemClick = { result ->
                            navController.navigate(
                                Screen.DocumentDetail.createRouteWithPage(result.documentId, result.pageId)
                            ) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<PageSearchResult>,
    onItemClick: (PageSearchResult) -> Unit
) {
    LazyColumn(
        contentPadding  = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(
            items = results,
            key   = { "${it.documentId}_${it.pageId}" }
        ) { result ->
            SearchResultItem(
                result    = result,
                onClick   = { onItemClick(result) }
            )
            HorizontalDivider(
                modifier  = Modifier.padding(start = 80.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: PageSearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Миниатюра документа
        SubcomposeAsyncImage(
            model           = result.coverImagePath,
            contentDescription = null,
            contentScale    = ContentScale.Crop,
            modifier        = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            loading = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            },
            error = {
                Image(
                    painter            = painterResource(R.drawable.card_placeholder),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Название документа
            Text(
                text  = result.documentTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            // Номер страницы
            Text(
                text  = stringResource(R.string.search_result_page_label, result.pageNumber),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            // Сниппет с подсветкой совпадений
            Text(
                text  = highlightSnippet(result.snippet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}

/**
 * Разбирает сниппет с маркерами «<<» и «>>» и возвращает AnnotatedString,
 * где совпадения выделены жирным шрифтом цветом primary.
 */
@Composable
private fun highlightSnippet(raw: String): androidx.compose.ui.text.AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var remaining = raw
        while (remaining.isNotEmpty()) {
            val start = remaining.indexOf("<<")
            if (start == -1) {
                append(remaining)
                break
            }
            // Текст до маркера
            if (start > 0) append(remaining.substring(0, start))
            val end = remaining.indexOf(">>", start + 2)
            if (end == -1) {
                // Незакрытый маркер — выводим как есть
                append(remaining.substring(start))
                break
            }
            // Выделенное слово
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color      = highlightColor
                )
            ) {
                append(remaining.substring(start + 2, end))
            }
            remaining = remaining.substring(end + 2)
        }
    }
}

@Composable
private fun NoResultsState(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier           = Modifier.size(48.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = stringResource(R.string.search_no_results_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = stringResource(R.string.search_no_results_subtitle, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}