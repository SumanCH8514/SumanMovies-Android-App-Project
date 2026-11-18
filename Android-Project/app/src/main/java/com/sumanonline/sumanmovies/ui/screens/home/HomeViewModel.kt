package com.sumanonline.sumanmovies.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val indianMovies: List<MediaItem> = emptyList(),
    val popularMovies: List<MediaItem> = emptyList(),
    val nowPlaying: List<MediaItem> = emptyList(),
    val topRated: List<MediaItem> = emptyList(),

    val trendingTv: List<MediaItem> = emptyList(),
    val indianTv: List<MediaItem> = emptyList(),
    val hollywoodTv: List<MediaItem> = emptyList(),
    val koreanTv: List<MediaItem> = emptyList(),
    val chineseTv: List<MediaItem> = emptyList(),
    val turkishTv: List<MediaItem> = emptyList(),
    val popularTv: List<MediaItem> = emptyList(),
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
        val withBackdrop = combined.filter { !it.backdropUrl.isNullOrBlank() }
        val result = if (withBackdrop.size >= 5) {
            withBackdrop.take(5)
        } else if (combined.isNotEmpty()) {
            combined.take(5)
        } else {
            emptyList()
        }
        return result
    }

    fun selectCategory(cat: String) {
        val items = when (cat) {
            "Movies" -> extractFeatured(_uiState.value.trendingMovies, _uiState.value.popularMovies)
            "Series" -> extractFeatured(_uiState.value.trendingTv, _uiState.value.popularTv)
            "Anime" -> extractFeatured(_uiState.value.trendingAnime, _uiState.value.popularAnime)
            "Drama" -> extractFeatured(_uiState.value.kDrama, _uiState.value.popularDramas)
            else -> extractFeatured(_uiState.value.trendingMovies, _uiState.value.popularMovies)
        }
        val first = items.firstOrNull() ?: _uiState.value.heroItem
        _uiState.value = _uiState.value.copy(
            selectedCategory = cat,
            heroItem = first,
            heroItems = items,
            isHeroBookmarked = first?.let { _uiState.value.bookmarkedIds.contains(it.id) } ?: false
        )
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

    fun removeContinueWatching(id: String) {
        viewModelScope.launch {
            repository.deleteWatchProgress(id)
            _uiState.value = _uiState.value.copy(userToastMessage = "Removed from Continue Watching")
        }
    }

    fun markWatched(id: String) {
        viewModelScope.launch {
            repository.markCompleted(id, true)
            _uiState.value = _uiState.value.copy(userToastMessage = "Marked as completed")
        }
    }

    fun restartProgress(id: String) {
        viewModelScope.launch {
            repository.resetWatchProgress(id)
        }
    }

    fun clearAllContinueWatching() {
        viewModelScope.launch {
            val current = _uiState.value.continueWatching
            current.forEach { item ->
                repository.deleteWatchProgress(item.id)
            }
            _uiState.value = _uiState.value.copy(userToastMessage = "Continue Watching cleared")
        }
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(userToastMessage = null)
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                coroutineScope {
                    val trendingMoviesDef = async(Dispatchers.IO) { repository.getTrendingMovies() }
                    val indianMoviesDef = async(Dispatchers.IO) { repository.getIndianMovies() }
                    val popularMoviesDef = async(Dispatchers.IO) { repository.getPopularMovies() }
                    val nowPlayingDef = async(Dispatchers.IO) { repository.getNowPlayingMovies() }
                    val topRatedDef = async(Dispatchers.IO) { repository.getTopRatedMovies() }

                    val trendingTvDef = async(Dispatchers.IO) { repository.getTrendingTv() }
                    val indianTvDef = async(Dispatchers.IO) { repository.getIndianTv() }
                    val hollywoodTvDef = async(Dispatchers.IO) { repository.getHollywoodTv() }
                    val koreanTvDef = async(Dispatchers.IO) { repository.getKoreanTv() }
                    val chineseTvDef = async(Dispatchers.IO) { repository.getChineseTv() }
                    val turkishTvDef = async(Dispatchers.IO) { repository.getTurkishTv() }
                    val popularTvDef = async(Dispatchers.IO) { repository.getPopularTv() }
                    val airingTodayDef = async(Dispatchers.IO) { repository.getAiringTodayTv() }

                    val trendingAnimeDef = async(Dispatchers.IO) { repository.getTrendingAnime() }
                    val newSeasonAnimeDef = async(Dispatchers.IO) { repository.getNewSeasonAnime() }
                    val popularAnimeDef = async(Dispatchers.IO) { repository.getPopularAnime() }
                    val japaneseAnimeDef = async(Dispatchers.IO) { repository.getJapaneseAnime() }
                    val chineseAnimeDef = async(Dispatchers.IO) { repository.getChineseAnime() }

                    val kDramaDef = async(Dispatchers.IO) { repository.getKDrama() }
                    val chineseDramaDef = async(Dispatchers.IO) { repository.getChineseDrama() }
                    val indianDramaDef = async(Dispatchers.IO) { repository.getIndianDrama() }
                    val turkishDramaDef = async(Dispatchers.IO) { repository.getTurkishDrama() }
                    val pakistaniDramaDef = async(Dispatchers.IO) { repository.getPakistaniDrama() }
                    val popularDramasDef = async(Dispatchers.IO) { repository.getPopularDramas() }

                    val trendingMovies = trendingMoviesDef.await()
                    val heroMovies = extractFeatured(trendingMovies)
                    val hero = heroMovies.firstOrNull()

                    _uiState.value = _uiState.value.copy(
                        heroItem = hero,
                        heroItems = heroMovies,
                        trendingMovies = trendingMovies,
                        isLoading = false
                    )

                    val indianMovies = indianMoviesDef.await()
                    val popularMovies = popularMoviesDef.await()
                    val nowPlaying = nowPlayingDef.await()
                    val topRated = topRatedDef.await()

                    val trendingTv = trendingTvDef.await()
                    val indianTv = indianTvDef.await()
                    val hollywoodTv = hollywoodTvDef.await()
                    val koreanTv = koreanTvDef.await()
                    val chineseTv = chineseTvDef.await()
                    val turkishTv = turkishTvDef.await()
                    val popularTv = popularTvDef.await()
                    val airingToday = airingTodayDef.await()

                    val trendingAnime = trendingAnimeDef.await()
                    val newSeasonAnime = newSeasonAnimeDef.await()
                    val popularAnime = popularAnimeDef.await()
                    val japaneseAnime = japaneseAnimeDef.await()
                    val chineseAnime = chineseAnimeDef.await()

                    val kDrama = kDramaDef.await()
                    val chineseDrama = chineseDramaDef.await()
                    val indianDrama = indianDramaDef.await()
                    val turkishDrama = turkishDramaDef.await()
                    val pakistaniDrama = pakistaniDramaDef.await()
                    val popularDramas = popularDramasDef.await()

                    val finalHeroItems = when (_uiState.value.selectedCategory) {
                        "Movies" -> extractFeatured(trendingMovies, popularMovies)
                        "Series" -> extractFeatured(trendingTv, popularTv)
                        "Anime" -> extractFeatured(trendingAnime, popularAnime)
                        "Drama" -> extractFeatured(kDrama, popularDramas)
                        else -> extractFeatured(trendingMovies, popularMovies)
                    }

                    val activeHero = finalHeroItems.firstOrNull() ?: hero

                    _uiState.value = _uiState.value.copy(
                        heroItem = activeHero,
                        heroItems = finalHeroItems,
                        isHeroBookmarked = activeHero?.let { _uiState.value.bookmarkedIds.contains(it.id) } ?: false,
                        trendingMovies = trendingMovies,
                        indianMovies = indianMovies,
                        popularMovies = popularMovies,
                        nowPlaying = nowPlaying,
                        topRated = topRated,
                        trendingTv = trendingTv,
                        indianTv = indianTv,
                        hollywoodTv = hollywoodTv,
                        koreanTv = koreanTv,
                        chineseTv = chineseTv,
                        turkishTv = turkishTv,
                        popularTv = popularTv,
                        airingToday = airingToday,
                        trendingAnime = trendingAnime,
                        newSeasonAnime = newSeasonAnime,
                        popularAnime = popularAnime,
                        japaneseAnime = japaneseAnime,
                        chineseAnime = chineseAnime,
                        kDrama = kDrama,
                        chineseDrama = chineseDrama,
                        indianDrama = indianDrama,
                        turkishDrama = turkishDrama,
                        pakistaniDrama = pakistaniDrama,
                        popularDramas = popularDramas,
                        isLoading = false
                    )
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
