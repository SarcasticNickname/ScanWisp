package com.myprojects.scanwisp.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myprojects.scanwisp.data.local.PageSearchResult
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _hasSearched = MutableStateFlow(false)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val _results = _query
        .debounce(300L)
        .distinctUntilChanged()
        .onEach { q ->
            _isSearching.value = q.isNotBlank()
            if (q.isNotBlank()) _hasSearched.value = true
        }
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(emptyList<PageSearchResult>())
            } else {
                repository.searchByContent(q)
                    .onEach { _isSearching.value = false }
                    .catch { e ->
                        Timber.e(e, "Content search error")
                        _isSearching.value = false
                        emit(emptyList<PageSearchResult>())
                    }
            }
        }

    val uiState = combine(
        _query,
        _results,
        _isSearching,
        _hasSearched
    ) { query, results, isSearching, hasSearched ->
        SearchUiState(
            query      = query,
            results    = results,
            isSearching = isSearching,
            hasSearched = hasSearched
        )
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5_000),
        initialValue   = SearchUiState()
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _isSearching.value = false
        }
    }

    fun clearQuery() {
        _query.value = ""
        _isSearching.value = false
    }
}