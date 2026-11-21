package com.sumanonline.sumanmovies.ui.screens.moviesleech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MoviesLeechGenre
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MoviesLeechFilterTab(val label: String, val categoryPath: String?) {
    ALL("All", "all"),
    MOVIES("Movies", "movies"),
    SERIES("Series", "web-series"),
    BOLLYWOOD("Bollywood", "movies/bollywood"),
    SOUTH_DUBBED("South Dubbed", "movies/south-movies")
}

data class MoviesLeechUiState(
    val selectedTab: MoviesLeechFilterTab = MoviesLeechFilterTab.ALL,
    val selectedGenre: MoviesLeechGenre? = null,
    val isGenreSheetOpen: Boolean = false,
    val availableGenres: List<MoviesLeechGenre> = emptyList(),
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val totalCount: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

class MoviesLeechViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesLeechUiState())
    val uiState: StateFlow<MoviesLeechUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val debounceDelayMs: Long = 800L

    init {
        loadGenres()
        loadCatalog(page = 1)
    }

    private fun loadGenres() {
        viewModelScope.launch {
            try {
                val genres = repository.getMoviesLeechGenres()
                _uiState.value = _uiState.value.copy(availableGenres = genres)
            } catch (_: Exception) {}
        }
    }

    fun openGenreSheet() {
        _uiState.value = _uiState.value.copy(isGenreSheetOpen = true)
    }

    fun closeGenreSheet() {
        _uiState.value = _uiState.value.copy(isGenreSheetOpen = false)
    }

    fun selectGenre(genre: MoviesLeechGenre) {
        if (_uiState.value.selectedGenre == genre && _uiState.value.query.isBlank()) {
            closeGenreSheet()
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            isGenreSheetOpen = false,
            query = "",
            currentPage = 1,
            items = emptyList()
        )
        loadCatalog(path = genre.path, page = 1)
    }

    fun clearGenre() {
        if (_uiState.value.selectedGenre == null) return
        _uiState.value = _uiState.value.copy(
            selectedGenre = null,
            currentPage = 1,
            items = emptyList()
        )
        loadCatalog(path = _uiState.value.selectedTab.categoryPath, page = 1)
    }

    fun selectTab(tab: MoviesLeechFilterTab) {
        if (_uiState.value.selectedTab == tab && _uiState.value.selectedGenre == null && _uiState.value.query.isBlank()) return
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            selectedGenre = null,
            query = "",
            currentPage = 1,
            items = emptyList()
        )
        loadCatalog(path = tab.categoryPath, page = 1)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(isSearching = false)
            val path = _uiState.value.selectedGenre?.path ?: _uiState.value.selectedTab.categoryPath
            loadCatalog(path = path, page = 1)
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
            val results = repository.searchMoviesLeech(query).distinctBy { it.id }
            _uiState.value = _uiState.value.copy(
                items = results,
                totalCount = results.size,
                isSearching = false,
                errorMessage = if (results.isEmpty()) "No titles found on MoviesLeech for '$query'" else null
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                errorMessage = e.message ?: "Failed to search MoviesLeech"
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = "", isSearching = false)
        val path = _uiState.value.selectedGenre?.path ?: _uiState.value.selectedTab.categoryPath
        loadCatalog(path = path, page = 1)
    }

    fun refresh() {
        if (_uiState.value.query.isNotBlank()) {
            searchImmediately()
        } else {
            val path = _uiState.value.selectedGenre?.path ?: _uiState.value.selectedTab.categoryPath
            loadCatalog(path = path, page = 1)
        }
    }

    fun loadNextPage() {
        val s = _uiState.value
        if (s.isLoading || s.isSearching || !s.hasMore || s.query.isNotBlank()) return
        val path = s.selectedGenre?.path ?: s.selectedTab.categoryPath
        loadCatalog(path = path, page = s.currentPage + 1, append = true)
    }

    private fun loadCatalog(
        path: String? = _uiState.value.selectedGenre?.path ?: _uiState.value.selectedTab.categoryPath,
        page: Int = 1,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            try {
                val (newItems, total) = repository.getMoviesLeechCatalog(
                    categoryPath = path,
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
                    errorMessage = e.message ?: "Failed to load MoviesLeech"
                )
            }
        }
    }
}
