package com.myprojects.scanwisp.ui.screens.search

import androidx.compose.runtime.Immutable
import com.myprojects.scanwisp.data.local.PageSearchResult

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<PageSearchResult> = emptyList(),
    /** true пока активен дебаунс или выполняется запрос */
    val isSearching: Boolean = false,
    /** false = ещё ничего не вводили; true = был хотя бы один запрос */
    val hasSearched: Boolean = false
)