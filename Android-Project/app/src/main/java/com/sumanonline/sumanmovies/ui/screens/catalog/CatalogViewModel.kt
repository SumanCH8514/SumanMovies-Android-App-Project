package com.sumanonline.sumanmovies.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
    val genres: List<String> = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary",
        "Drama", "Fantasy", "Horror", "Mystery", "Romance", "Science Fiction"
    ),
    val searchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false
)

class CatalogViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadSearchHistory()
    }

    fun loadSearchHistory() {
        _uiState.value = _uiState.value.copy(searchHistory = repository.getSearchHistory())
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(400)
            _uiState.value = _uiState.value.copy(isSearching = true)
            val results = repository.searchAll(newQuery)
            if (newQuery.trim().length >= 2 && results.isNotEmpty()) {
                repository.addSearchQuery(newQuery.trim())
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false,
                    searchHistory = repository.getSearchHistory()
                )
            } else {
                _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
            }
        }
    }

    fun onChipClick(term: String) {
        onQueryChange(term)
    }

    fun removeHistoryItem(term: String) {
        repository.removeSearchQuery(term)
        loadSearchHistory()
    }

    fun clearAllHistory() {
        repository.clearSearchHistory()
        loadSearchHistory()
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(query = "", searchResults = emptyList(), isSearching = false)
    }
}
