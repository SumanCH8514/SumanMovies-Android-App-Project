package com.sumanonline.sumanmovies.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.ui.components.TopMinimalBar
import com.sumanonline.sumanmovies.ui.screens.customization.HomeTabsStyle
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onSearchClick: () -> Unit,
    homeTabsStyle: HomeTabsStyle = HomeTabsStyle.TOP_GLASS,
    onSeeAllCategory: (String, String) -> Unit = { _, _ -> },
    onNavigateHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color
    val context = LocalContext.current
    var showClearContinueWatchingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.userToastMessage) {
        state.userToastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading && state.heroItems.isEmpty() && state.heroItem == null) {
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
                            items = state.heroItems.ifEmpty { listOfNotNull(state.heroItem) },
                            isBookmarked = { item -> state.bookmarkedIds.contains(item.id) },
                            onPlayClick = { onPlayClick(it) },
                            onBookmarkClick = { viewModel.toggleBookmark(it) },
                            onInfoClick = { onMediaClick(it) }
                        )

                        if (homeTabsStyle == HomeTabsStyle.TOP_GLASS) {
                            TopGlassBar(
                                selectedCategory = state.selectedCategory,
                                categories = viewModel.categories,
                                onSelectCategory = { viewModel.selectCategory(it) },
                                onSearchClick = onSearchClick,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        } else {
                            TopMinimalBar(
                                onSearchClick = onSearchClick,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }
                }

                if (homeTabsStyle == HomeTabsStyle.SEGMENTED) {
                    item {
                        HomeSegmentedTabs(
                            selectedCategory = state.selectedCategory,
                            categories = viewModel.categories,
                            onSelectCategory = { viewModel.selectCategory(it) }
                        )
                    }
                }

                if (state.continueWatching.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Continue Watching",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = state.continueWatching.size.toString(),
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Clear All",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showClearContinueWatchingDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onNavigateHistory() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "See All",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.continueWatching) { item ->
                                val media = MediaItem(
                                    id = item.mediaId,
                                    title = item.title,
                                    posterUrl = item.posterUrl,
                                    mediaType = try {
                                        MediaType.valueOf(item.mediaType)
                                    } catch (_: Exception) {
                                        MediaType.MOVIE
                                    }
                                )

                                ContinueWatchingCard(
                                    item = item,
                                    onResumeClick = {
                                        onPlayClick(media)
                                    },
                                    onInfoClick = {
                                        onMediaClick(media)
                                    },
                                    onMarkWatchedClick = {
                                        viewModel.markWatched(item.id)
                                    },
                                    onDeleteClick = {
                                        viewModel.removeContinueWatching(item.id)
                                    },
                                    onRestartClick = {
                                        viewModel.restartProgress(item.id)
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

        if (showClearContinueWatchingDialog) {
            AlertDialog(
                onDismissRequest = { showClearContinueWatchingDialog = false },
                title = {
                    Text(
                        text = "Clear Continue Watching?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "This will remove all in-progress titles from your Continue Watching row. Your completed watch history will remain safe.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllContinueWatching()
                            showClearContinueWatchingDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearContinueWatchingDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = CardElevated,
                shape = RoundedCornerShape(16.dp)
            )
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

@Composable
private fun HomeSegmentedTabs(
    selectedCategory: String,
    categories: List<String>,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAppAccent.current.color

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    RoundedCornerShape(14.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) accent else Color.Transparent
                        )
                        .clickable { onSelectCategory(cat) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
