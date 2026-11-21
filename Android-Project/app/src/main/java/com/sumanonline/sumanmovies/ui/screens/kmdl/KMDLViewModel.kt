package com.sumanonline.sumanmovies.ui.screens.kmdl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class KMDLFilterTab(val label: String, val driveIndex: Int) {
    ALL("All", 1),
    MOVIES("Movies", 1),
    DRAMA("Drama", 0),
    ONGOING("Ongoing", 2),
    SERVER2("Server 2", 4),
    SERVER3("Server 3", 5)
}

data class KMDLUiState(
    val selectedTab: KMDLFilterTab = KMDLFilterTab.ALL,
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val totalCount: Int = 0,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

class KMDLViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KMDLUiState())
    val uiState: StateFlow<KMDLUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var artworkJob: Job? = null
    private val debounceDelayMs: Long = 800L

    init {
        loadCatalog(page = 1)
    }

    fun selectTab(tab: KMDLFilterTab) {
        if (_uiState.value.selectedTab == tab && _uiState.value.query.isBlank()) return
        artworkJob?.cancel()
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
        artworkJob?.cancel()
        _uiState.value = _uiState.value.copy(isSearching = true)
        try {
            val targetDrive = if (_uiState.value.selectedTab == KMDLFilterTab.ALL) null else _uiState.value.selectedTab.driveIndex
            val results = repository.searchKMDL(query, targetDrive).distinctBy { it.id }
            val processedResults = if (_uiState.value.selectedTab == KMDLFilterTab.MOVIES) {
                results.map { item ->
                    val hasSeries = item.title.contains("Season", true) ||
                        item.title.contains("S0", true) ||
                        Regex("""\bS\d{1,2}\b""", RegexOption.IGNORE_CASE).containsMatchIn(item.title)
                    if (!hasSeries && item.mediaType != com.sumanonline.sumanmovies.data.models.MediaType.MOVIE) {
                        item.copy(mediaType = com.sumanonline.sumanmovies.data.models.MediaType.MOVIE)
                    } else {
                        item
                    }
                }
            } else {
                results
            }
            _uiState.value = _uiState.value.copy(
                items = processedResults,
                totalCount = processedResults.size,
                isSearching = false,
                errorMessage = if (processedResults.isEmpty()) "No cloud titles found on KMDL for '$query'" else null
            )
            enrichMissingArtworks(processedResults)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                errorMessage = e.message ?: "Failed to search KMDL"
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        artworkJob?.cancel()
        _uiState.value = _uiState.value.copy(query = "", isSearching = false)
        loadCatalog(tab = _uiState.value.selectedTab, page = 1)
    }

    fun refresh() {
        artworkJob?.cancel()
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
        tab: KMDLFilterTab = _uiState.value.selectedTab,
        page: Int = 1,
        append: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            try {
                val (newItems, total) = repository.getKMDLCatalog(
                    driveIndex = tab.driveIndex,
                    page = page
                )

                val processedItems = if (tab == KMDLFilterTab.MOVIES || tab == KMDLFilterTab.ALL) {
                    newItems.map { item ->
                        val hasSeries = item.title.contains("Season", true) ||
                            item.title.contains("S0", true) ||
                            Regex("""\bS\d{1,2}\b""", RegexOption.IGNORE_CASE).containsMatchIn(item.title)
                        if (!hasSeries && item.mediaType != com.sumanonline.sumanmovies.data.models.MediaType.MOVIE) {
                            item.copy(mediaType = com.sumanonline.sumanmovies.data.models.MediaType.MOVIE)
                        } else {
                            item
                        }
                    }
                } else {
                    newItems
                }

                val combined = if (append) {
                    (_uiState.value.items + processedItems).distinctBy { it.id }
                } else {
                    processedItems.distinctBy { it.id }
                }

                _uiState.value = _uiState.value.copy(
                    items = combined,
                    isLoading = false,
                    totalCount = if (total > 0) total else combined.size,
                    currentPage = page,
                    hasMore = newItems.isNotEmpty() && newItems.size >= 30,
                    errorMessage = if (combined.isEmpty()) "No titles found in this drive" else null
                )
                enrichMissingArtworks(combined)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load KMDL drive"
                )
            }
        }
    }

    private fun enrichMissingArtworks(itemsList: List<MediaItem>) {
        val missing = itemsList.filter { it.posterUrl.isNullOrBlank() }
        if (missing.isEmpty()) return

        artworkJob?.cancel()
        artworkJob = viewModelScope.launch(Dispatchers.IO) {
            missing.chunked(6).forEach { batch ->
                val resolved = batch.map { item ->
                    async {
                        val artwork = repository.getArtworkForTitle(item.title, item.year, item.mediaType)
                        if (!artwork.first.isNullOrBlank()) {
                            item.copy(
                                posterUrl = artwork.first,
                                backdropUrl = artwork.second ?: item.backdropUrl
                            )
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()

                if (resolved.isNotEmpty()) {
                    val updateMap = resolved.associateBy { it.id }
                    _uiState.value = _uiState.value.copy(
                        items = _uiState.value.items.map { cur ->
                            updateMap[cur.id] ?: cur
                        }
                    )
                    resolved.forEach { repository.cacheItem(it) }
                }
            }
        }
    }
}
