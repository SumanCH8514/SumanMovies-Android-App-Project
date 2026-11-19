package com.sumanonline.sumanmovies.ui.screens.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.TvChannel
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LiveTvFilterTab(val label: String, val categoryRegex: String?) {
    ALL("All", null),
    INDIA("India & Hindi", "india|hindi|zee|star|sony|colors|aaj|ndtv|bengali|tamil|telugu"),
    SPORTS("Sports", "sports|cricket|football|espn|ten|sony ten|star sports|red bull"),
    NEWS("News", "news|aaj|abp|ndtv|cnn|bbc|al jazeera|republic|france|sky"),
    ENTERTAINMENT("Entertainment", "entertainment|general|series|comedy|dd national"),
    MOVIES("Movies", "movies|cinema|film|goldmines|star gold|sony max"),
    MUSIC("Music", "music|mtv|9xm|b4u|zoom"),
    DOCUMENTARY("Documentary", "documentary|nat geo|discovery|history|dw|nasa|bloomberg")
}

data class LiveTvUiState(
    val selectedTab: LiveTvFilterTab = LiveTvFilterTab.ALL,
    val query: String = "",
    val allChannels: List<TvChannel> = emptyList(),
    val displayedChannels: List<TvChannel> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

class LiveTvViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val debounceDelayMs: Long = 1000L

    init {
        loadChannels()
    }

    fun selectTab(tab: LiveTvFilterTab) {
        if (_uiState.value.selectedTab == tab && _uiState.value.query.isBlank()) return
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            query = ""
        )
        applyFilters(_uiState.value.allChannels, tab, "")
    }

    fun onQueryChange(newQuery: String) {
        val previousQuery = _uiState.value.query
        val sanitized = newQuery.replace("\r", "").replace("\n", "")
        _uiState.value = _uiState.value.copy(query = sanitized)
        searchJob?.cancel()

        if (sanitized.isBlank()) {
            _uiState.value = _uiState.value.copy(isSearching = false)
            applyFilters(_uiState.value.allChannels, _uiState.value.selectedTab, "")
            return
        }

        val trimmed = sanitized.trim()
        val isSpaceTrigger = sanitized.endsWith(" ") && !previousQuery.endsWith(" ")
        if (isSpaceTrigger && trimmed.length >= 2) {
            executeSearch(trimmed)
            return
        }

        searchJob = viewModelScope.launch {
            delay(debounceDelayMs)
            executeSearch(trimmed)
        }
    }

    fun searchImmediately(overrideQuery: String? = null) {
        val targetQuery = (overrideQuery ?: _uiState.value.query).replace("\r", "").replace("\n", "").trim()
        if (targetQuery.isBlank()) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = targetQuery)
        executeSearch(targetQuery)
    }

    private fun executeSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true)
        applyFilters(_uiState.value.allChannels, _uiState.value.selectedTab, query)
        _uiState.value = _uiState.value.copy(
            isSearching = false,
            errorMessage = if (_uiState.value.displayedChannels.isEmpty()) "No live channels found matching '$query'" else null
        )
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = "", isSearching = false)
        applyFilters(_uiState.value.allChannels, _uiState.value.selectedTab, "")
    }

    fun refresh() {
        loadChannels(forceRefresh = true)
    }

    private fun loadChannels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val channels = repository.getLiveChannels(forceRefresh = forceRefresh)
                _uiState.value = _uiState.value.copy(
                    allChannels = channels,
                    isLoading = false
                )
                applyFilters(channels, _uiState.value.selectedTab, _uiState.value.query)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load Live TV channels"
                )
            }
        }
    }

    private fun applyFilters(channels: List<TvChannel>, tab: LiveTvFilterTab, query: String) {
        var filtered = channels

        if (tab.categoryRegex != null) {
            val regex = Regex(tab.categoryRegex, RegexOption.IGNORE_CASE)
            filtered = filtered.filter { ch ->
                val group = ch.groupTitle ?: ""
                val name = ch.name
                val country = ch.country ?: ""
                val language = ch.language ?: ""
                regex.containsMatchIn(group) || regex.containsMatchIn(name) || regex.containsMatchIn(country) || regex.containsMatchIn(language)
            }
        }

        if (query.isNotBlank()) {
            val cleanQuery = query.trim().lowercase(java.util.Locale.ROOT)
            filtered = filtered.filter { ch ->
                ch.name.lowercase(java.util.Locale.ROOT).contains(cleanQuery) ||
                        (ch.groupTitle?.lowercase(java.util.Locale.ROOT)?.contains(cleanQuery) == true) ||
                        (ch.country?.lowercase(java.util.Locale.ROOT)?.contains(cleanQuery) == true) ||
                        (ch.language?.lowercase(java.util.Locale.ROOT)?.contains(cleanQuery) == true)
            }
        }

        _uiState.value = _uiState.value.copy(
            displayedChannels = filtered,
            totalCount = filtered.size,
            errorMessage = if (filtered.isEmpty() && (query.isNotBlank() || tab != LiveTvFilterTab.ALL)) {
                "No channels found under ${tab.label}"
            } else null
        )
    }
}
