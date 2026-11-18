package com.sumanonline.sumanmovies.ui.screens.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CategorySortOption(val displayName: String) {
    POPULAR("Most Popular"),
    NEWEST("Newest Release"),
    OLDEST("Oldest Release"),
    TITLE_AZ("Title (A to Z)"),
    TITLE_ZA("Title (Z to A)")
}

enum class CategoryTypeFilter(val displayName: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series")
}

data class CategoryMediaUiState(
    val title: String = "Popular Movies",
    val categoryKey: String = "popular_movies",
    val allItems: List<MediaItem> = emptyList(),
    val displayedItems: List<MediaItem> = emptyList(),
    val totalCount: Int = 20001,
    val currentPage: Int = 1,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val sortOption: CategorySortOption = CategorySortOption.POPULAR,
    val typeFilter: CategoryTypeFilter = CategoryTypeFilter.ALL,
    val selectedYear: String? = null,
    val availableYears: List<String> = emptyList(),
    val isFilterSheetOpen: Boolean = false
) {
    val items: List<MediaItem> get() = displayedItems
    val hasActiveFilters: Boolean get() = sortOption != CategorySortOption.POPULAR || typeFilter != CategoryTypeFilter.ALL || selectedYear != null
}

class CategoryMediaViewModel(
    private val repository: MediaRepository,
    private val categoryKey: String,
    private val title: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CategoryMediaUiState(
            title = title,
            categoryKey = categoryKey
        )
    )
    val uiState: StateFlow<CategoryMediaUiState> = _uiState.asStateFlow()
    private var isFetching = false

    init {
        loadData()
    }

    private fun loadData() {
        if (isFetching) return
        isFetching = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = repository.getCategoryMedia(categoryKey, 1)
                val initialList = result.first
                val total = if (result.second > 0) result.second else 20001
                val years = extractYears(initialList)
                val filtered = filterAndSort(
                    initialList,
                    _uiState.value.sortOption,
                    _uiState.value.typeFilter,
                    _uiState.value.selectedYear
                )
                _uiState.value = _uiState.value.copy(
                    allItems = initialList,
                    displayedItems = filtered,
                    totalCount = total,
                    currentPage = 1,
                    availableYears = years,
                    isLoading = false,
                    canLoadMore = initialList.isNotEmpty() && initialList.size < total
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            } finally {
                isFetching = false
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (isFetching || state.isLoading || state.isLoadingMore || !state.canLoadMore) return

        isFetching = true
        _uiState.value = state.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val nextPage = state.currentPage + 1
                val result = repository.getCategoryMedia(categoryKey, nextPage)
                val newItems = result.first
                val total = if (result.second > 0) result.second else state.totalCount

                if (newItems.isNotEmpty()) {
                    val existingIds = state.allItems.map { it.id }.toSet()
                    val distinctNew = newItems.filter { it.id !in existingIds }
                    val combinedAll = state.allItems + distinctNew

                    val updatedYears = extractYears(combinedAll)
                    val filtered = filterAndSort(
                        combinedAll,
                        state.sortOption,
                        state.typeFilter,
                        state.selectedYear
                    )

                    _uiState.value = _uiState.value.copy(
                        allItems = combinedAll,
                        displayedItems = filtered,
                        totalCount = total,
                        currentPage = nextPage,
                        availableYears = updatedYears,
                        isLoadingMore = false,
                        canLoadMore = combinedAll.size < total && newItems.isNotEmpty()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        canLoadMore = false
                    )
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            } finally {
                isFetching = false
            }
        }
    }

    fun setSortOption(sort: CategorySortOption) {
        val state = _uiState.value
        val filtered = filterAndSort(state.allItems, sort, state.typeFilter, state.selectedYear)
        _uiState.value = state.copy(sortOption = sort, displayedItems = filtered)
    }

    fun setTypeFilter(type: CategoryTypeFilter) {
        val state = _uiState.value
        val filtered = filterAndSort(state.allItems, state.sortOption, type, state.selectedYear)
        _uiState.value = state.copy(typeFilter = type, displayedItems = filtered)
        if (filtered.size < 6 && state.canLoadMore) {
            loadNextPage()
        }
    }

    fun setSelectedYear(year: String?) {
        val state = _uiState.value
        val filtered = filterAndSort(state.allItems, state.sortOption, state.typeFilter, year)
        _uiState.value = state.copy(selectedYear = year, displayedItems = filtered)
        if (filtered.size < 6 && state.canLoadMore) {
            loadNextPage()
        }
    }

    fun clearFilters() {
        val state = _uiState.value
        val filtered = filterAndSort(
            state.allItems,
            CategorySortOption.POPULAR,
            CategoryTypeFilter.ALL,
            null
        )
        _uiState.value = state.copy(
            sortOption = CategorySortOption.POPULAR,
            typeFilter = CategoryTypeFilter.ALL,
            selectedYear = null,
            displayedItems = filtered
        )
    }

    fun openFilterSheet() {
        _uiState.value = _uiState.value.copy(isFilterSheetOpen = true)
    }

    fun closeFilterSheet() {
        _uiState.value = _uiState.value.copy(isFilterSheetOpen = false)
    }

    fun refresh() {
        loadData()
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

    private fun filterAndSort(
        items: List<MediaItem>,
        sort: CategorySortOption,
        type: CategoryTypeFilter,
        year: String?
    ): List<MediaItem> {
        var filtered = items

        when (type) {
            CategoryTypeFilter.MOVIES -> {
                filtered = filtered.filter { it.mediaType == MediaType.MOVIE }
            }
            CategoryTypeFilter.SERIES -> {
                filtered = filtered.filter { it.mediaType != MediaType.MOVIE }
            }
            CategoryTypeFilter.ALL -> {}
        }

        if (!year.isNullOrBlank() && year != "All" && year != "All Years") {
            filtered = when (year) {
                "Before 2010", "< 2010" -> {
                    filtered.filter {
                        val y = it.year?.toIntOrNull() ?: 0
                        y in 1900..2009
                    }
                }
                "2010 - 2019" -> {
                    filtered.filter {
                        val y = it.year?.toIntOrNull() ?: 0
                        y in 2010..2019
                    }
                }
                "2020 - 2022" -> {
                    filtered.filter {
                        val y = it.year?.toIntOrNull() ?: 0
                        y in 2020..2022
                    }
                }
                else -> {
                    val yTarget = year.trim()
                    filtered.filter {
                        val y = it.year?.trim()
                        y == yTarget
                    }
                }
            }
        }

        return when (sort) {
            CategorySortOption.POPULAR -> filtered
            CategorySortOption.NEWEST -> filtered.sortedWith(
                compareByDescending<MediaItem> {
                    it.year?.toIntOrNull() ?: 0
                }.thenBy { it.title.lowercase() }
            )
            CategorySortOption.OLDEST -> filtered.sortedWith(
                compareBy<MediaItem> {
                    val yr = it.year?.toIntOrNull()
                    if (yr != null && yr > 1900) yr else 9999
                }.thenBy { it.title.lowercase() }
            )
            CategorySortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            CategorySortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
        }
    }
}
