package com.sumanonline.sumanmovies.ui.screens.animexin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.AnimeXinGenre
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AnimeXinFilterTab(val label: String, val categoryPath: String?) {
    ALL("All", "all"),
    TODAY("Today", "today"),
    LATEST("Latest", "latest")
}

data class AnimeXinUiState(
    val selectedTab: AnimeXinFilterTab = AnimeXinFilterTab.ALL,
    val selectedGenre: AnimeXinGenre? = null,
    val isGenreSheetOpen: Boolean = false,
    val availableGenres: List<AnimeXinGenre> = emptyList(),
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val totalCount: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

class AnimeXinViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeXinUiState())
    val uiState: StateFlow<AnimeXinUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val debounceDelayMs: Long = 800L

    init {
        loadGenres()
        loadCatalog(page = 1)
    }

    private fun loadGenres() {
        viewModelScope.launch {
            try {
                val genres = repository.getAnimeXinGenres()
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

    fun selectGenre(genre: AnimeXinGenre) {
        if (_uiState.value.selectedGenre == genre && _uiState.value.query.isBlank()) {
            closeGenreSheet()
            return
        }
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            isGenreSheetOpen = false,
            query = "",
            currentPage = 1,
            hasMore = true,
            items = emptyList()
        )
        loadCatalog(path = genre.path, page = 1)
    }

    fun clearGenre() {
        if (_uiState.value.selectedGenre == null) return
        _uiState.value = _uiState.value.copy(
            selectedGenre = null,
            currentPage = 1,
            hasMore = true,
            items = emptyList()
        )
        loadCatalog(path = _uiState.value.selectedTab.categoryPath, page = 1)
    }

    fun selectTab(tab: AnimeXinFilterTab) {
        if (_uiState.value.selectedTab == tab && _uiState.value.selectedGenre == null && _uiState.value.query.isBlank()) return
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            selectedGenre = null,
            query = "",
            currentPage = 1,
            hasMore = true,
            items = emptyList()
        )
        loadCatalog(path = tab.categoryPath, page = 1)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(isSearching = false, hasMore = true)
            val path = _uiState.value.selectedGenre?.path ?: _uiState.value.selectedTab.categoryPath
            loadCatalog(path = path, page = 1)
            return
        }

        searchJob = viewModelScope.launch {
            delay(debounceDelayMs)
            executeSearch(newQuery.trim(), page = 1, append = false)
        }
    }

    fun searchImmediately(overrideQuery: String? = null) {
        val targetQuery = (overrideQuery ?: _uiState.value.query).trim()
        if (targetQuery.isBlank()) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = targetQuery)
        searchJob = viewModelScope.launch {
            executeSearch(targetQuery, page = 1, append = false)
        }
    }

    private suspend fun executeSearch(query: String, page: Int = 1, append: Boolean = false) {
        if (!append) {
            _uiState.value = _uiState.value.copy(isSearching = true)
        } else {
            _uiState.value = _uiState.value.copy(isLoading = true)
        }
        try {
            val results = repository.searchAnimeXin(query, page = page)
            val existingIds = if (append) _uiState.value.items.map { it.id }.toSet() else emptySet()
            val uniqueNew = results.filter { !existingIds.contains(it.id) }
            val combined = if (append) _uiState.value.items + uniqueNew else results.distinctBy { it.id }

            _uiState.value = _uiState.value.copy(
                items = combined,
                totalCount = combined.size,
                currentPage = page,
                hasMore = results.isNotEmpty() && (!append || uniqueNew.isNotEmpty()),
                isSearching = false,
                isLoading = false,
                errorMessage = if (combined.isEmpty()) "No titles found on AnimeXin for '$query'" else null
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                isLoading = false,
                errorMessage = e.message ?: "Failed to search AnimeXin"
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = "", isSearching = false, hasMore = true)
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
        if (s.isLoading || s.isSearching || !s.hasMore) return
        if (s.query.isNotBlank()) {
            viewModelScope.launch {
                executeSearch(s.query.trim(), page = s.currentPage + 1, append = true)
            }
        } else {
            val path = s.selectedGenre?.path ?: s.selectedTab.categoryPath
            loadCatalog(path = path, page = s.currentPage + 1, append = true)
        }
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
                val (newItems, _) = repository.getAnimeXinCatalog(
                    categoryPath = path,
                    page = page
                )

                val existingIds = if (append) _uiState.value.items.map { it.id }.toSet() else emptySet()
                val uniqueNew = newItems.filter { !existingIds.contains(it.id) }

                val combined = if (append) {
                    _uiState.value.items + uniqueNew
                } else {
                    newItems.distinctBy { it.id }
                }

                _uiState.value = _uiState.value.copy(
                    items = combined,
                    isLoading = false,
                    totalCount = combined.size,
                    currentPage = page,
                    hasMore = newItems.isNotEmpty() && (!append || uniqueNew.isNotEmpty()),
                    errorMessage = if (combined.isEmpty()) "No titles found in this category" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load AnimeXin"
                )
            }
        }
    }
}
