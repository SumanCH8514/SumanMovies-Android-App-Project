package com.sumanonline.sumanmovies.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.ui.components.ContinueWatchingCard
import com.sumanonline.sumanmovies.ui.components.HeroBannerCarousel
import com.sumanonline.sumanmovies.ui.components.LandscapeCard
import com.sumanonline.sumanmovies.ui.components.SectionHeader
import com.sumanonline.sumanmovies.ui.components.TopGlassBar
import com.sumanonline.sumanmovies.ui.components.VerticalPosterCard
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onSearchClick: () -> Unit,
    onSeeAllCategory: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (state.isLoading && state.heroItem == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        HeroBannerCarousel(
                            item = state.heroItem,
                            isBookmarked = state.isHeroBookmarked,
                            onPlayClick = { state.heroItem?.let { onPlayClick(it) } },
                            onBookmarkClick = { viewModel.toggleBookmarkHero() },
                            onInfoClick = { state.heroItem?.let { onMediaClick(it) } }
                        )

                        TopGlassBar(
                            selectedCategory = state.selectedCategory,
                            categories = viewModel.categories,
                            onSelectCategory = { viewModel.selectCategory(it) },
                            onSearchClick = onSearchClick,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }

                if (state.continueWatching.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Continue Watching")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.continueWatching) { item ->
                                ContinueWatchingCard(
                                    item = item,
                                    onResumeClick = {
                                        val media = MediaItem(
                                            id = item.mediaId,
                                            title = item.title,
                                            posterUrl = item.posterUrl,
                                            mediaType = com.sumanonline.sumanmovies.data.models.MediaType.valueOf(item.mediaType)
                                        )
                                        onPlayClick(media)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                when (state.selectedCategory) {
                    "Movies" -> {
                        mediaSectionItem("Trending Movies", state.trendingMovies, "trending_movies", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Indian Movies", state.indianMovies, "indian_movies", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Popular Movies", state.popularMovies, "popular_movies", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Now Playing in Theaters", state.nowPlaying, "now_playing", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Top Rated Movies", state.topRated, "top_rated", onMediaClick, onSeeAllCategory)
                    }
                    "Series" -> {
                        mediaSectionItem("Trending TV Series", state.trendingTv, "trending_tv", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Indian TV Series", state.indianTv, "indian_tv", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Hollywood TV Series", state.hollywoodTv, "hollywood_tv", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Korean TV Series", state.koreanTv, "korean_tv", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Chinese TV Series", state.chineseTv, "chinese_tv", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Turkish TV Series", state.turkishTv, "turkish_tv", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Popular TV Shows", state.popularTv, "popular_tv", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Airing Today", state.airingToday, "airing_today", onMediaClick, onSeeAllCategory)
                    }
                    "Anime" -> {
                        mediaSectionItem("Trending Anime", state.trendingAnime, "trending_anime", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("New Season Anime", state.newSeasonAnime, "new_season_anime", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Popular Anime", state.popularAnime, "popular_anime", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Japanese Anime", state.japaneseAnime, "japanese_anime", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Chinese Anime & Donghua", state.chineseAnime, "chinese_anime", onMediaClick, onSeeAllCategory)
                    }
                    "Drama" -> {
                        mediaSectionItem("K-Drama", state.kDrama, "k_drama", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Chinese Drama", state.chineseDrama, "chinese_drama", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Indian Drama", state.indianDrama, "indian_drama", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Turkish Drama", state.turkishDrama, "turkish_drama", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Pakistani Drama", state.pakistaniDrama, "pakistani_drama", onMediaClick, onSeeAllCategory)
                        mediaSectionItem("Other Popular Dramas", state.popularDramas, "popular_dramas", onMediaClick, onSeeAllCategory)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mediaSectionItem(
    title: String,
    items: List<MediaItem>,
    categoryKey: String,
    onMediaClick: (MediaItem) -> Unit,
    onSeeAllCategory: (String, String) -> Unit
) {
    if (items.isNotEmpty()) {
        item {
            SectionHeader(
                title = title,
                onSeeAllClick = { onSeeAllCategory(categoryKey, title) }
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items) { item ->
                    VerticalPosterCard(item = item, onClick = { onMediaClick(item) })
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
