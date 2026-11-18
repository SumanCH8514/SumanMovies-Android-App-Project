package com.sumanonline.sumanmovies.ui.screens.play4u

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

enum class Play4UFilterTab(val label: String, val type: String?, val industry: String?, val lang: String?) {
    ALL("All", null, null, null),
    MOVIES("Movies", "movie", null, null),
    SERIES("Series", "series", null, null),
    HOLLYWOOD("Hollywood", null, "hollywood", null),
    BOLLYWOOD("Bollywood", null, "bollywood", null),
    HINDI("Hindi", null, null, "hi"),
    ENGLISH("English", null, null, "en"),
    TAMIL("Tamil", null, null, "ta"),
    TELUGU("Telugu", null, null, "te")
}

data class Play4UUiState(
    val selectedTab: Play4UFilterTab = Play4UFilterTab.ALL,
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val totalCount: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

class Play4UViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(Play4UUiState())
    val uiState: StateFlow<Play4UUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadCatalog(page = 1)
    }

    fun selectTab(tab: Play4UFilterTab) {
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
            delay(350)
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                val results = repository.searchPlay4U(newQuery.trim())
                _uiState.value = _uiState.value.copy(
                    items = results,
                    totalCount = results.size,
                    isSearching = false,
                    errorMessage = if (results.isEmpty()) "No titles found on Play4U for '$newQuery'" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = e.message ?: "Failed to search Play4U"
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(query = "", isSearching = false)
        loadCatalog(tab = _uiState.value.selectedTab, page = 1)
    }

    fun loadNextPage() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore || _uiState.value.query.isNotBlank()) return
        val nextPage = _uiState.value.currentPage + 1
        loadCatalog(tab = _uiState.value.selectedTab, page = nextPage, append = true)
    }

    fun refresh() {
        if (_uiState.value.query.isNotBlank()) {
            onQueryChange(_uiState.value.query)
        } else {
            loadCatalog(tab = _uiState.value.selectedTab, page = 1)
        }
    }

    private fun loadCatalog(
        tab: Play4UFilterTab = _uiState.value.selectedTab,
        page: Int = 1,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            try {
                val (newItems, found) = repository.getPlay4UCatalog(
                    type = tab.type,
                    industry = tab.industry,
                    lang = tab.lang,
                    page = page
                )
                val combined = if (append) _uiState.value.items + newItems else newItems
                _uiState.value = _uiState.value.copy(
                    items = combined,
                    totalCount = found,
                    currentPage = page,
                    hasMore = newItems.isNotEmpty() && combined.size < found,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load Play4U catalog"
                )
            }
        }
    }
}
