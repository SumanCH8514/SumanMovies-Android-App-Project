package com.sumanonline.sumanmovies.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchSortOption(val displayName: String) {
    RELEVANCE("Best Match"),
    RATING("Highest Rated"),
    NEWEST("Newest Release"),
    OLDEST("Oldest Release"),
    TITLE_AZ("Title (A to Z)")
}

enum class SearchTypeFilter(val displayName: String) {
    ALL("All"),
    MOVIES("Movies Only"),
    SERIES("TV Series Only")
}

data class CatalogUiState(
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
    val genres: List<String> = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary",
        "Drama", "Fantasy", "Horror", "Mystery", "Romance", "Science Fiction"
    ),
    val rawSearchResults: List<MediaItem> = emptyList(),
    val searchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val isFilterSheetOpen: Boolean = false,
    val sortOption: SearchSortOption = SearchSortOption.RELEVANCE,
    val typeFilter: SearchTypeFilter = SearchTypeFilter.ALL,
    val selectedYear: String? = null,
    val selectedGenre: String? = null,
    val availableYears: List<String> = emptyList()
) {
    val hasActiveFilters: Boolean
        get() = sortOption != SearchSortOption.RELEVANCE ||
                typeFilter != SearchTypeFilter.ALL ||
                selectedYear != null ||
                selectedGenre != null
}

class CatalogViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val debounceDelayMs: Long = 2000L
    private var lastSearchedQuery: String = ""

    init {
        loadSearchHistory()
    }

    fun loadSearchHistory() {
        _uiState.value = _uiState.value.copy(searchHistory = repository.getSearchHistory())
    }

    fun onQueryChange(newQuery: String) {
        if (newQuery.contains('\n') || newQuery.contains('\r')) {
            val sanitized = newQuery.replace("\n", "").replace("\r", "")
            _uiState.value = _uiState.value.copy(query = sanitized)
            searchImmediately(sanitized)
            return
        }

        val previousQuery = _uiState.value.query
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        val trimmed = newQuery.trim()
        if (trimmed.isBlank()) {
            lastSearchedQuery = ""
            _uiState.value = _uiState.value.copy(
                rawSearchResults = emptyList(),
                searchResults = emptyList(),
                isSearching = false
            )
            return
        }

        val isSpaceTrigger = newQuery.endsWith(" ") && !previousQuery.endsWith(" ")
        if (isSpaceTrigger) {
            if (trimmed != lastSearchedQuery) {
                searchJob = viewModelScope.launch {
                    executeSearch(trimmed)
                }
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(debounceDelayMs)
            if (trimmed != lastSearchedQuery) {
                executeSearch(trimmed)
            }
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
        lastSearchedQuery = query
        _uiState.value = _uiState.value.copy(isSearching = true)
        val results = repository.searchAll(query)
        val years = extractYears(results)
        val filtered = applyFiltersAndSort(
            results,
            _uiState.value.sortOption,
            _uiState.value.typeFilter,
            _uiState.value.selectedYear,
            _uiState.value.selectedGenre
        )
        if (query.length >= 2 && results.isNotEmpty()) {
            repository.addSearchQuery(query)
            _uiState.value = _uiState.value.copy(
                rawSearchResults = results,
                searchResults = filtered,
                availableYears = years,
                isSearching = false,
                searchHistory = repository.getSearchHistory()
            )
        } else {
            _uiState.value = _uiState.value.copy(
                rawSearchResults = results,
                searchResults = filtered,
                availableYears = years,
                isSearching = false
            )
        }
    }

    fun setSortOption(sort: SearchSortOption) {
        val s = _uiState.value
        val filtered = applyFiltersAndSort(s.rawSearchResults, sort, s.typeFilter, s.selectedYear, s.selectedGenre)
        _uiState.value = s.copy(sortOption = sort, searchResults = filtered)
    }

    fun setTypeFilter(type: SearchTypeFilter) {
        val s = _uiState.value
        val filtered = applyFiltersAndSort(s.rawSearchResults, s.sortOption, type, s.selectedYear, s.selectedGenre)
        _uiState.value = s.copy(typeFilter = type, searchResults = filtered)
    }

    fun setSelectedYear(year: String?) {
        val s = _uiState.value
        val filtered = applyFiltersAndSort(s.rawSearchResults, s.sortOption, s.typeFilter, year, s.selectedGenre)
        _uiState.value = s.copy(selectedYear = year, searchResults = filtered)
    }

    fun setSelectedGenre(genre: String?) {
        val s = _uiState.value
        val filtered = applyFiltersAndSort(s.rawSearchResults, s.sortOption, s.typeFilter, s.selectedYear, genre)
        _uiState.value = s.copy(selectedGenre = genre, searchResults = filtered)
    }

    fun clearFilters() {
        val s = _uiState.value
        val filtered = applyFiltersAndSort(
            s.rawSearchResults,
            SearchSortOption.RELEVANCE,
            SearchTypeFilter.ALL,
            null,
            null
        )
        _uiState.value = s.copy(
            sortOption = SearchSortOption.RELEVANCE,
            typeFilter = SearchTypeFilter.ALL,
            selectedYear = null,
            selectedGenre = null,
            searchResults = filtered
        )
    }

    fun openFilterSheet() {
        _uiState.value = _uiState.value.copy(isFilterSheetOpen = true)
    }

    fun closeFilterSheet() {
        _uiState.value = _uiState.value.copy(isFilterSheetOpen = false)
    }

    fun onChipClick(term: String) {
        searchImmediately(term)
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
        searchJob?.cancel()
        lastSearchedQuery = ""
        _uiState.value = _uiState.value.copy(
            query = "",
            rawSearchResults = emptyList(),
            searchResults = emptyList(),
            isSearching = false
        )
    }

    private fun extractYears(items: List<MediaItem>): List<String> {
        val extracted = items.mapNotNull {
            it.year?.toIntOrNull()
        }.filter { it in 1970..2030 }.distinct().sortedDescending()

        val recentYears = extracted.filter { it >= 2020 }.map { it.toString() }
        val list = mutableListOf<String>()
        list.addAll(recentYears)
        if (extracted.any { it in 2010..2019 }) {
            list.add("2010 - 2019")
        }
        if (extracted.any { it < 2010 }) {
            list.add("Before 2010")
        }
        return list
    }

    private fun applyFiltersAndSort(
        items: List<MediaItem>,
        sort: SearchSortOption,
        type: SearchTypeFilter,
        year: String?,
        genre: String?
    ): List<MediaItem> {
        var filtered = items

        when (type) {
            SearchTypeFilter.MOVIES -> filtered = filtered.filter { it.mediaType == MediaType.MOVIE }
            SearchTypeFilter.SERIES -> filtered = filtered.filter { it.mediaType != MediaType.MOVIE }
            SearchTypeFilter.ALL -> {}
        }

        if (!year.isNullOrBlank() && year != "All" && year != "All Years") {
            filtered = when (year) {
                "Before 2010", "< 2010" -> filtered.filter {
                    val y = it.year?.toIntOrNull() ?: 0
                    y in 1900..2009
                }
                "2010 - 2019" -> filtered.filter {
                    val y = it.year?.toIntOrNull() ?: 0
                    y in 2010..2019
                }
                "2020 - 2022" -> filtered.filter {
                    val y = it.year?.toIntOrNull() ?: 0
                    y in 2020..2022
                }
                else -> {
                    val yTarget = year.trim()
                    filtered.filter { it.year?.trim() == yTarget }
                }
            }
        }

        if (!genre.isNullOrBlank() && genre != "All" && genre != "All Genres") {
            val gTarget = genre.lowercase()
            filtered = filtered.filter { item ->
                item.genres.any { it.lowercase().contains(gTarget) } ||
                item.overview?.lowercase()?.contains(gTarget) == true
            }
        }

        return when (sort) {
            SearchSortOption.RELEVANCE -> filtered
            SearchSortOption.RATING -> filtered.sortedByDescending { it.rating ?: 0.0f }
            SearchSortOption.NEWEST -> filtered.sortedWith(
                compareByDescending<MediaItem> {
                    it.year?.toIntOrNull() ?: 0
                }.thenBy { it.title.lowercase() }
            )
            SearchSortOption.OLDEST -> filtered.sortedWith(
                compareBy<MediaItem> {
                    val yr = it.year?.toIntOrNull()
                    if (yr != null && yr > 1900) yr else 9999
                }.thenBy { it.title.lowercase() }
            )
            SearchSortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
        }
    }
}
