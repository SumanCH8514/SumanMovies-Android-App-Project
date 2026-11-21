package com.sumanonline.sumanmovies.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedCategory: String = "Movies",
    val heroItem: MediaItem? = null,
    val heroItems: List<MediaItem> = emptyList(),
    val isHeroBookmarked: Boolean = false,
    val bookmarkedIds: Set<String> = emptySet(),
    val continueWatching: List<WatchHistoryEntity> = emptyList(),

    val trendingMovies: List<MediaItem> = emptyList(),
    val indianCinemaMovies: List<MediaItem> = emptyList(),
    val animeMovies: List<MediaItem> = emptyList(),
    val popularMovies: List<MediaItem> = emptyList(),
    val actionAdventureMovies: List<MediaItem> = emptyList(),
    val sciFiFantasyMovies: List<MediaItem> = emptyList(),
    val comedyMovies: List<MediaItem> = emptyList(),
    val horrorThrillerMovies: List<MediaItem> = emptyList(),
    val romanceMovies: List<MediaItem> = emptyList(),
    val crimeMysteryMovies: List<MediaItem> = emptyList(),
    val topRatedMovies: List<MediaItem> = emptyList(),

    val indianMovies: List<MediaItem> = emptyList(),
    val nowPlaying: List<MediaItem> = emptyList(),
    val topRated: List<MediaItem> = emptyList(),

    val trendingTv: List<MediaItem> = emptyList(),
    val indianWebseries: List<MediaItem> = emptyList(),
    val popularTv: List<MediaItem> = emptyList(),
    val dramaSeries: List<MediaItem> = emptyList(),
    val crimeStories: List<MediaItem> = emptyList(),
    val comedySeries: List<MediaItem> = emptyList(),
    val sciFiFantasySeries: List<MediaItem> = emptyList(),
    val actionAdventureSeries: List<MediaItem> = emptyList(),
    val mysterySuspenseSeries: List<MediaItem> = emptyList(),
    val kDramaSensations: List<MediaItem> = emptyList(),
    val topRatedTv: List<MediaItem> = emptyList(),

    val indianTv: List<MediaItem> = emptyList(),
    val hollywoodTv: List<MediaItem> = emptyList(),
    val koreanTv: List<MediaItem> = emptyList(),
    val chineseTv: List<MediaItem> = emptyList(),
    val turkishTv: List<MediaItem> = emptyList(),
    val airingToday: List<MediaItem> = emptyList(),

    val trendingAnime: List<MediaItem> = emptyList(),
    val newSeasonAnime: List<MediaItem> = emptyList(),
    val popularAnime: List<MediaItem> = emptyList(),
    val japaneseAnime: List<MediaItem> = emptyList(),
    val chineseAnime: List<MediaItem> = emptyList(),

    val kDrama: List<MediaItem> = emptyList(),
    val chineseDrama: List<MediaItem> = emptyList(),
    val indianDrama: List<MediaItem> = emptyList(),
    val turkishDrama: List<MediaItem> = emptyList(),
    val pakistaniDrama: List<MediaItem> = emptyList(),
    val popularDramas: List<MediaItem> = emptyList(),

    val isLoading: Boolean = true,
    val userToastMessage: String? = null
)

class HomeViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val categories = listOf("Movies", "Series", "Anime", "Drama")

    init {
        loadData()
        observeContinueWatching()
        observeFavorites()
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            repository.getContinueWatchingFlow().collectLatest { list ->
                _uiState.value = _uiState.value.copy(continueWatching = list)
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.getFavoritesFlow().collectLatest { favorites ->
                val set = favorites.map { it.id }.toSet()
                _uiState.value = _uiState.value.copy(
                    bookmarkedIds = set,
                    isHeroBookmarked = _uiState.value.heroItem?.let { set.contains(it.id) } ?: false
                )
            }
        }
    }

    private fun extractFeatured(primary: List<MediaItem>, fallback: List<MediaItem> = emptyList()): List<MediaItem> {
        val combined = (primary + fallback).distinctBy { it.id }
        val withBackdrops = combined.filter { !it.backdropUrl.isNullOrBlank() }
        val result = if (withBackdrops.size >= 3) {
            withBackdrops.take(5)
        } else if (combined.isNotEmpty()) {
            combined.take(5)
        } else {
            emptyList()
        }
        return result
    }

    fun selectCategory(cat: String) {
        val currentItems = when (cat) {
            "Movies" -> extractFeatured(_uiState.value.trendingMovies, _uiState.value.popularMovies)
            "Series" -> extractFeatured(_uiState.value.trendingTv, _uiState.value.popularTv)
            "Anime" -> extractFeatured(_uiState.value.trendingAnime, _uiState.value.popularAnime)
            "Drama" -> extractFeatured(_uiState.value.kDrama, _uiState.value.popularDramas)
            else -> extractFeatured(_uiState.value.trendingMovies, _uiState.value.popularMovies)
        }
        val first = currentItems.firstOrNull() ?: _uiState.value.heroItem
        _uiState.value = _uiState.value.copy(
            selectedCategory = cat,
            heroItem = first,
            heroItems = currentItems,
            isHeroBookmarked = first?.let { _uiState.value.bookmarkedIds.contains(it.id) } ?: false
        )

        when (cat) {
            "Series" -> if (_uiState.value.trendingTv.isEmpty()) loadSeriesData()
            "Anime" -> if (_uiState.value.trendingAnime.isEmpty()) loadAnimeData()
            "Drama" -> if (_uiState.value.popularDramas.isEmpty() && _uiState.value.kDrama.isEmpty()) loadDramaData()
            "Movies" -> if (_uiState.value.trendingMovies.isEmpty()) loadMoviesData()
        }
    }

    fun toggleBookmark(item: MediaItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
        }
    }

    fun toggleBookmarkHero() {
        val hero = _uiState.value.heroItem ?: return
        toggleBookmark(hero)
    }

    fun removeContinueWatching(id: String, mediaId: String = "") {
        viewModelScope.launch {
            val target = _uiState.value.continueWatching.firstOrNull { it.id == id || it.mediaId == id || (mediaId.isNotBlank() && it.mediaId == mediaId) }
            val resolvedMediaId = mediaId.ifBlank { target?.mediaId ?: id.substringBefore(":") }
            _uiState.value = _uiState.value.copy(
                continueWatching = _uiState.value.continueWatching.filter { it.id != id && it.mediaId != resolvedMediaId },
                userToastMessage = "Removed from Continue Watching"
            )
            repository.removeContinueWatchingItem(id, resolvedMediaId)
        }
    }

    fun markWatched(id: String, mediaId: String = "") {
        viewModelScope.launch {
            val target = _uiState.value.continueWatching.firstOrNull { it.id == id || it.mediaId == id || (mediaId.isNotBlank() && it.mediaId == mediaId) }
            val resolvedMediaId = mediaId.ifBlank { target?.mediaId ?: id.substringBefore(":") }
            _uiState.value = _uiState.value.copy(
                continueWatching = _uiState.value.continueWatching.filter { it.id != id && it.mediaId != resolvedMediaId },
                userToastMessage = "Marked as completed"
            )
            repository.markContinueWatchingCompleted(id, resolvedMediaId)
        }
    }

    fun restartProgress(id: String) {
        viewModelScope.launch {
            repository.resetWatchProgress(id)
        }
    }

    fun clearAllContinueWatching() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                continueWatching = emptyList(),
                userToastMessage = "Continue Watching cleared"
            )
            repository.clearContinueWatching()
        }
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(userToastMessage = null)
    }

    fun loadData() {
        loadMoviesData()
    }

    private fun loadMoviesData() {
        viewModelScope.launch {
            if (_uiState.value.trendingMovies.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            try {
                val trendingMovies = repository.getTrendingMovies()
                val heroMovies = extractFeatured(trendingMovies)
                val activeHero = heroMovies.firstOrNull() ?: _uiState.value.heroItem

                _uiState.value = _uiState.value.copy(
                    heroItem = activeHero,
                    heroItems = heroMovies,
                    trendingMovies = trendingMovies,
                    isHeroBookmarked = activeHero?.let { _uiState.value.bookmarkedIds.contains(it.id) } ?: false,
                    isLoading = false
                )

                coroutineScope {
                    val indianDef = async(Dispatchers.IO) { repository.getIndianMovies() }
                    val animeMoviesDef = async(Dispatchers.IO) { repository.getAnimeMovies() }
                    val popularDef = async(Dispatchers.IO) { repository.getPopularMovies() }
                    val actionDef = async(Dispatchers.IO) { repository.getActionAdventureMovies() }
                    val sciFiDef = async(Dispatchers.IO) { repository.getSciFiFantasyMovies() }
                    val comedyDef = async(Dispatchers.IO) { repository.getComedyMovies() }
                    val horrorDef = async(Dispatchers.IO) { repository.getHorrorThrillerMovies() }
                    val romanceDef = async(Dispatchers.IO) { repository.getRomanceMovies() }
                    val crimeDef = async(Dispatchers.IO) { repository.getCrimeMysteryMovies() }
                    val topRatedDef = async(Dispatchers.IO) { repository.getTopRatedMovies() }
                    val nowPlayingDef = async(Dispatchers.IO) { repository.getNowPlayingMovies() }

                    val indian = indianDef.await()
                    val animeM = animeMoviesDef.await()
                    val popular = popularDef.await()
                    val action = actionDef.await()
                    val sciFi = sciFiDef.await()
                    val comedy = comedyDef.await()
                    val horror = horrorDef.await()
                    val romance = romanceDef.await()
                    val crime = crimeDef.await()
                    val topRated = topRatedDef.await()
                    val nowPlaying = nowPlayingDef.await()

                    _uiState.value = _uiState.value.copy(
                        indianCinemaMovies = indian,
                        animeMovies = animeM,
                        popularMovies = popular,
                        actionAdventureMovies = action,
                        sciFiFantasyMovies = sciFi,
                        comedyMovies = comedy,
                        horrorThrillerMovies = horror,
                        romanceMovies = romance,
                        crimeMysteryMovies = crime,
                        topRatedMovies = topRated,
                        indianMovies = indian,
                        nowPlaying = nowPlaying,
                        topRated = topRated,
                        isLoading = false
                    )
                }

                launch(Dispatchers.IO) {
                    delay(1200L)
                    if (_uiState.value.trendingTv.isEmpty()) {
                        loadSeriesData(background = true)
                    }
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadSeriesData(background: Boolean = false) {
        viewModelScope.launch {
            if (!background && _uiState.value.trendingTv.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            try {
                coroutineScope {
                    val trendingTvDef = async(Dispatchers.IO) { repository.getTrendingTv() }
                    val indianTvDef = async(Dispatchers.IO) { repository.getIndianTv() }
                    val popularTvDef = async(Dispatchers.IO) { repository.getPopularTv() }
                    val dramaSeriesDef = async(Dispatchers.IO) { repository.getDramaSeries() }
                    val crimeStoriesDef = async(Dispatchers.IO) { repository.getCrimeSeries() }
                    val comedySeriesDef = async(Dispatchers.IO) { repository.getComedySeries() }
                    val sciFiSeriesDef = async(Dispatchers.IO) { repository.getSciFiFantasySeries() }
                    val actionSeriesDef = async(Dispatchers.IO) { repository.getActionAdventureSeries() }
                    val mysterySeriesDef = async(Dispatchers.IO) { repository.getMysterySuspenseSeries() }
                    val kDramaDef = async(Dispatchers.IO) { repository.getKDrama() }
                    val topRatedTvDef = async(Dispatchers.IO) { repository.getTopRatedTv() }
                    val hollywoodTvDef = async(Dispatchers.IO) { repository.getHollywoodTv() }
                    val koreanTvDef = async(Dispatchers.IO) { repository.getKoreanTv() }
                    val chineseTvDef = async(Dispatchers.IO) { repository.getChineseTv() }
                    val turkishTvDef = async(Dispatchers.IO) { repository.getTurkishTv() }
                    val airingTodayDef = async(Dispatchers.IO) { repository.getAiringTodayTv() }

                    val trendingTv = trendingTvDef.await()
                    val indianWebseries = indianTvDef.await()
                    val popularTv = popularTvDef.await()
                    val dramaSeries = dramaSeriesDef.await()
                    val crimeStories = crimeStoriesDef.await()
                    val comedySeries = comedySeriesDef.await()
                    val sciFiFantasySeries = sciFiSeriesDef.await()
                    val actionAdventureSeries = actionSeriesDef.await()
                    val mysterySuspenseSeries = mysterySeriesDef.await()
                    val kDramaSensations = kDramaDef.await()
                    val topRatedTv = topRatedTvDef.await()
                    val hollywoodTv = hollywoodTvDef.await()
                    val koreanTv = koreanTvDef.await()
                    val chineseTv = chineseTvDef.await()
                    val turkishTv = turkishTvDef.await()
                    val airingToday = airingTodayDef.await()

                    val isSeriesActive = _uiState.value.selectedCategory == "Series"
                    val finalHeroItems = if (isSeriesActive) extractFeatured(trendingTv, popularTv) else _uiState.value.heroItems
                    val activeHero = if (isSeriesActive) finalHeroItems.firstOrNull() ?: _uiState.value.heroItem else _uiState.value.heroItem

                    _uiState.value = _uiState.value.copy(
                        heroItem = activeHero,
                        heroItems = finalHeroItems,
                        isHeroBookmarked = activeHero?.let { _uiState.value.bookmarkedIds.contains(it.id) } ?: false,
                        trendingTv = trendingTv,
                        indianWebseries = indianWebseries,
                        popularTv = popularTv,
                        dramaSeries = dramaSeries,
                        crimeStories = crimeStories,
                        comedySeries = comedySeries,
                        sciFiFantasySeries = sciFiFantasySeries,
                        actionAdventureSeries = actionAdventureSeries,
                        mysterySuspenseSeries = mysterySuspenseSeries,
                        kDramaSensations = kDramaSensations,
                        topRatedTv = topRatedTv,
                        indianTv = indianWebseries,
                        hollywoodTv = hollywoodTv,
                        koreanTv = koreanTv,
                        chineseTv = chineseTv,
                        turkishTv = turkishTv,
                        airingToday = airingToday,
                        isLoading = if (isSeriesActive) false else _uiState.value.isLoading
                    )
                }
            } catch (_: Exception) {
                if (_uiState.value.selectedCategory == "Series") {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun loadAnimeData() {
        viewModelScope.launch {
            if (_uiState.value.trendingAnime.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            try {
                coroutineScope {
                    val trendingAnimeDef = async(Dispatchers.IO) { repository.getTrendingAnime() }
                    val newSeasonAnimeDef = async(Dispatchers.IO) { repository.getNewSeasonAnime() }
                    val popularAnimeDef = async(Dispatchers.IO) { repository.getPopularAnime() }
                    val japaneseAnimeDef = async(Dispatchers.IO) { repository.getJapaneseAnime() }
                    val chineseAnimeDef = async(Dispatchers.IO) { repository.getChineseAnime() }

                    val trendingAnime = trendingAnimeDef.await()
                    val newSeasonAnime = newSeasonAnimeDef.await()
                    val popularAnime = popularAnimeDef.await()
                    val japaneseAnime = japaneseAnimeDef.await()
                    val chineseAnime = chineseAnimeDef.await()

                    val isAnimeActive = _uiState.value.selectedCategory == "Anime"
                    val finalHeroItems = if (isAnimeActive) extractFeatured(trendingAnime, popularAnime) else _uiState.value.heroItems
                    val activeHero = if (isAnimeActive) finalHeroItems.firstOrNull() ?: _uiState.value.heroItem else _uiState.value.heroItem

                    _uiState.value = _uiState.value.copy(
                        heroItem = activeHero,
                        heroItems = finalHeroItems,
                        isHeroBookmarked = activeHero?.let { _uiState.value.bookmarkedIds.contains(it.id) } ?: false,
                        trendingAnime = trendingAnime,
                        newSeasonAnime = newSeasonAnime,
                        popularAnime = popularAnime,
                        japaneseAnime = japaneseAnime,
                        chineseAnime = chineseAnime,
                        isLoading = if (isAnimeActive) false else _uiState.value.isLoading
                    )
                }
            } catch (_: Exception) {
                if (_uiState.value.selectedCategory == "Anime") {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun loadDramaData() {
        viewModelScope.launch {
            if (_uiState.value.popularDramas.isEmpty() && _uiState.value.kDrama.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            try {
                coroutineScope {
                    val chineseDramaDef = async(Dispatchers.IO) { repository.getChineseDrama() }
                    val indianDramaDef = async(Dispatchers.IO) { repository.getIndianDrama() }
                    val turkishDramaDef = async(Dispatchers.IO) { repository.getTurkishDrama() }
                    val pakistaniDramaDef = async(Dispatchers.IO) { repository.getPakistaniDrama() }
                    val popularDramasDef = async(Dispatchers.IO) { repository.getPopularDramas() }
                    val kDramaDef = async(Dispatchers.IO) { repository.getKDrama() }

                    val chineseDrama = chineseDramaDef.await()
                    val indianDrama = indianDramaDef.await()
                    val turkishDrama = turkishDramaDef.await()
                    val pakistaniDrama = pakistaniDramaDef.await()
                    val popularDramas = popularDramasDef.await()
                    val kDrama = kDramaDef.await()

                    val isDramaActive = _uiState.value.selectedCategory == "Drama"
                    val finalHeroItems = if (isDramaActive) extractFeatured(kDrama, popularDramas) else _uiState.value.heroItems
                    val activeHero = if (isDramaActive) finalHeroItems.firstOrNull() ?: _uiState.value.heroItem else _uiState.value.heroItem

                    _uiState.value = _uiState.value.copy(
                        heroItem = activeHero,
                        heroItems = finalHeroItems,
                        isHeroBookmarked = activeHero?.let { _uiState.value.bookmarkedIds.contains(it.id) } ?: false,
                        kDrama = kDrama,
                        chineseDrama = chineseDrama,
                        indianDrama = indianDrama,
                        turkishDrama = turkishDrama,
                        pakistaniDrama = pakistaniDrama,
                        popularDramas = popularDramas,
                        isLoading = if (isDramaActive) false else _uiState.value.isLoading
                    )
                }
            } catch (_: Exception) {
                if (_uiState.value.selectedCategory == "Drama") {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
}
