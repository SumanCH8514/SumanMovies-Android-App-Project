package com.sumanonline.sumanmovies.ui.screens.moviesmod

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

enum class MoviesModFilterTab(val label: String, val categoryPath: String?) {
    ALL("All", "all"),
    MOVIES("Movies", "movies"),
    SERIES("Web Series", "web-series"),
    FHD("1080p", "quality/1080p"),
    HD("720p", "quality/720p"),
    SD("480p", "quality/480p")
}

data class MoviesModUiState(
    val selectedTab: MoviesModFilterTab = MoviesModFilterTab.ALL,
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val totalCount: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

class MoviesModViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesModUiState())
    val uiState: StateFlow<MoviesModUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val debounceDelayMs: Long = 800L

    init {
        loadCatalog(page = 1)
    }

    fun selectTab(tab: MoviesModFilterTab) {
        if (_uiState.value.selectedTab == tab && _uiState.value.query.isBlank()) return
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            query = "",
            currentPage = 1,
            items = emptyList()
        )
        loadCatalog(tab = tab, page = 1)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(isSearching = false)
            loadCatalog(tab = _uiState.value.selectedTab, page = 1)
            return
        }

        searchJob = viewModelScope.launch {
            delay(debounceDelayMs)
            executeSearch(newQuery.trim())
        }
    }

    fun searchImmediately(overrideQuery: String? = null) {
        val targetQuery = (overrideQuery ?: _uiState.value.query).trim()
        if (targetQuery.isBlank()) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = targetQuery)
        searchJob = viewModelScope.launch {
            executeSearch(targetQuery)
        }
    }

    private suspend fun executeSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true)
        try {
            val results = repository.searchMoviesMod(query).distinctBy { it.id }
            _uiState.value = _uiState.value.copy(
                items = results,
                totalCount = results.size,
                isSearching = false,
                errorMessage = if (results.isEmpty()) "No titles found on MoviesMod for '$query'" else null
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                errorMessage = e.message ?: "Failed to search MoviesMod"
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = "", isSearching = false)
        loadCatalog(tab = _uiState.value.selectedTab, page = 1)
    }

    fun refresh() {
        if (_uiState.value.query.isNotBlank()) {
            searchImmediately()
        } else {
            loadCatalog(tab = _uiState.value.selectedTab, page = 1)
        }
    }

    fun loadNextPage() {
        val s = _uiState.value
        if (s.isLoading || s.isSearching || !s.hasMore || s.query.isNotBlank()) return
        loadCatalog(tab = s.selectedTab, page = s.currentPage + 1, append = true)
    }

    private fun loadCatalog(
        tab: MoviesModFilterTab = _uiState.value.selectedTab,
        page: Int = 1,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            try {
                val (newItems, total) = repository.getMoviesModCatalog(
                    categoryPath = tab.categoryPath,
                    page = page
                )

                val combined = if (append) {
                    (_uiState.value.items + newItems).distinctBy { it.id }
                } else {
                    newItems.distinctBy { it.id }
                }

                _uiState.value = _uiState.value.copy(
                    items = combined,
                    isLoading = false,
                    totalCount = if (total > 0) total else combined.size,
                    currentPage = page,
                    hasMore = newItems.isNotEmpty(),
                    errorMessage = if (combined.isEmpty()) "No titles found in this category" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load MoviesMod"
                )
            }
        }
    }
}
