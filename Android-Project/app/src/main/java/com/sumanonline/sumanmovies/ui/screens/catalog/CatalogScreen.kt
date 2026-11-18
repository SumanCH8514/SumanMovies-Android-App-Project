package com.sumanonline.sumanmovies.ui.screens.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.ui.components.GenreCard
import com.sumanonline.sumanmovies.ui.components.VerticalPosterCard
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPlay4U: (() -> Unit)? = null,
    onNavigateToMovieBox: (() -> Unit)? = null,
    onGenreClick: ((String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (state.isSearching) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchHeader(
                    query = state.query,
                    onQueryChange = { viewModel.onQueryChange(it) },
                    onSearchTriggered = { viewModel.searchImmediately() },
                    onClearSearch = { viewModel.clearSearch() },
                    onFilterClick = { viewModel.openFilterSheet() },
                    hasActiveFilters = state.hasActiveFilters,
                    accent = accent
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accent)
                }
            }
        } else if (state.query.isNotBlank()) {
            if (state.searchResults.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SearchHeader(
                        query = state.query,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onSearchTriggered = { viewModel.searchImmediately() },
                        onClearSearch = { viewModel.clearSearch() },
                        onFilterClick = { viewModel.openFilterSheet() },
                        hasActiveFilters = state.hasActiveFilters,
                        accent = accent
                    )

                    ActiveFiltersRow(
                        state = state,
                        accent = accent,
                        onRemoveSort = { viewModel.setSortOption(SearchSortOption.RELEVANCE) },
                        onRemoveType = { viewModel.setTypeFilter(SearchTypeFilter.ALL) },
                        onRemoveYear = { viewModel.setSelectedYear(null) },
                        onRemoveGenre = { viewModel.setSelectedGenre(null) },
                        onClearAll = { viewModel.clearFilters() }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (state.hasActiveFilters) Icons.Default.FilterListOff else Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (state.hasActiveFilters && state.rawSearchResults.isNotEmpty()) {
                                    "No titles match active filters"
                                } else {
                                    "No matching results found on TMDb"
                                },
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (state.hasActiveFilters && state.rawSearchResults.isNotEmpty()) {
                                    "Try clearing filters or changing sort criteria."
                                } else {
                                    "Check your spelling or try another title."
                                },
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )

                            if (state.hasActiveFilters) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.clearFilters() },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Reset Filters",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            SearchHeader(
                                query = state.query,
                                onQueryChange = { viewModel.onQueryChange(it) },
                                onSearchTriggered = { viewModel.searchImmediately() },
                                onClearSearch = { viewModel.clearSearch() },
                                onFilterClick = { viewModel.openFilterSheet() },
                                hasActiveFilters = state.hasActiveFilters,
                                accent = accent,
                                contentPaddingHorizontal = 0.dp
                            )

                            ActiveFiltersRow(
                                state = state,
                                accent = accent,
                                onRemoveSort = { viewModel.setSortOption(SearchSortOption.RELEVANCE) },
                                onRemoveType = { viewModel.setTypeFilter(SearchTypeFilter.ALL) },
                                onRemoveYear = { viewModel.setSelectedYear(null) },
                                onRemoveGenre = { viewModel.setSelectedGenre(null) },
                                onClearAll = { viewModel.clearFilters() },
                                contentPaddingHorizontal = 0.dp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${state.searchResults.size} TMDb results",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (state.sortOption != SearchSortOption.RELEVANCE) {
                                    Text(
                                        text = "Sorted by ${state.sortOption.displayName}",
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    items(state.searchResults) { item ->
                        VerticalPosterCard(
                            item = item,
                            onClick = { onMediaClick(item) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    SearchHeader(
                        query = state.query,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onClearSearch = { viewModel.clearSearch() },
                        onFilterClick = { viewModel.openFilterSheet() },
                        hasActiveFilters = state.hasActiveFilters,
                        accent = accent,
                        contentPaddingHorizontal = 16.dp
                    )
                }

                if (state.hasActiveFilters) {
                    item {
                        ActiveFiltersRow(
                            state = state,
                            accent = accent,
                            onRemoveSort = { viewModel.setSortOption(SearchSortOption.RELEVANCE) },
                            onRemoveType = { viewModel.setTypeFilter(SearchTypeFilter.ALL) },
                            onRemoveYear = { viewModel.setSelectedYear(null) },
                            onRemoveGenre = { viewModel.setSelectedGenre(null) },
                            onClearAll = { viewModel.clearFilters() },
                            contentPaddingHorizontal = 16.dp
                        )
                    }
                }

                if (state.searchHistory.isNotEmpty()) {
                    item {
                        CatalogSectionTitle(
                            title = "Recent Searches",
                            actionLabel = "Clear all",
                            onActionClick = { viewModel.clearAllHistory() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.searchHistory) { term ->
                                Box(
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF131926))
                                        .border(
                                            BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.onChipClick(term) }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = term,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clickable { viewModel.removeHistoryItem(term) }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                item {
                    CatalogSectionTitle(title = "Streaming Providers")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderHubCard(
                            title = "Play4U",
                            badge = "1080p HLS",
                            description = "Lossless 1080p HLS streams with multi-audio",
                            icon = Icons.Default.PlayArrow,
                            accentColor = accent,
                            onClick = { onNavigateToPlay4U?.invoke() },
                            modifier = Modifier.weight(1f)
                        )

                        ProviderHubCard(
                            title = "MovieBox",
                            badge = "1080p DASH",
                            description = "Direct DASH streams & multi-language catalog",
                            icon = Icons.Default.Movie,
                            accentColor = accent,
                            onClick = { onNavigateToMovieBox?.invoke() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    CatalogSectionTitle(title = "Browse by Genre")
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.genres.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GenreCard(
                                    name = pair[0],
                                    onClick = {
                                        if (onGenreClick != null) {
                                            onGenreClick(pair[0])
                                        } else {
                                            viewModel.onChipClick(pair[0])
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (pair.size > 1) {
                                    GenreCard(
                                        name = pair[1],
                                        onClick = {
                                            if (onGenreClick != null) {
                                                onGenreClick(pair[1])
                                            } else {
                                                viewModel.onChipClick(pair[1])
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.isFilterSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeFilterSheet() },
            sheetState = sheetState,
            containerColor = Color(0xFF131926),
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            SearchFilterSheetContent(
                state = state,
                accent = accent,
                onSelectSort = { viewModel.setSortOption(it) },
                onSelectType = { viewModel.setTypeFilter(it) },
                onSelectYear = { viewModel.setSelectedYear(it) },
                onSelectGenre = { viewModel.setSelectedGenre(it) },
                onClearAll = { viewModel.clearFilters() },
                onApply = { viewModel.closeFilterSheet() }
            )
        }
    }
}

@Composable
private fun ActiveFiltersRow(
    state: CatalogUiState,
    accent: Color,
    onRemoveSort: () -> Unit,
    onRemoveType: () -> Unit,
    onRemoveYear: () -> Unit,
    onRemoveGenre: () -> Unit,
    onClearAll: () -> Unit,
    contentPaddingHorizontal: androidx.compose.ui.unit.Dp = 16.dp
) {
    AnimatedVisibility(
        visible = state.hasActiveFilters,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPaddingHorizontal, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.sortOption != SearchSortOption.RELEVANCE) {
                item {
                    ActiveSearchFilterChip(
                        label = state.sortOption.displayName,
                        accent = accent,
                        onRemove = onRemoveSort
                    )
                }
            }
            if (state.typeFilter != SearchTypeFilter.ALL) {
                item {
                    ActiveSearchFilterChip(
                        label = state.typeFilter.displayName,
                        accent = accent,
                        onRemove = onRemoveType
                    )
                }
            }
            if (state.selectedYear != null) {
                item {
                    ActiveSearchFilterChip(
                        label = "Year: ${state.selectedYear}",
                        accent = accent,
                        onRemove = onRemoveYear
                    )
                }
            }
            if (state.selectedGenre != null) {
                item {
                    ActiveSearchFilterChip(
                        label = "Genre: ${state.selectedGenre}",
                        accent = accent,
                        onRemove = onRemoveGenre
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onClearAll() }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Clear All",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSearchFilterChip(
    label: String,
    accent: Color,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), RoundedCornerShape(20.dp))
            .clickable { onRemove() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun SearchFilterSheetContent(
    state: CatalogUiState,
    accent: Color,
    onSelectSort: (SearchSortOption) -> Unit,
    onSelectType: (SearchTypeFilter) -> Unit,
    onSelectYear: (String?) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filter & Sort",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (state.hasActiveFilters) {
                TextButton(onClick = onClearAll) {
                    Text(
                        text = "Reset All",
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "SORT BY",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SearchSortOption.values()) { opt ->
                    val selected = state.sortOption == opt
                    SearchFilterPill(
                        text = opt.displayName,
                        isSelected = selected,
                        accent = accent,
                        onClick = { onSelectSort(opt) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "CONTENT TYPE",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SearchTypeFilter.values().forEach { type ->
                    val selected = state.typeFilter == type
                    SearchFilterPill(
                        text = type.displayName,
                        isSelected = selected,
                        accent = accent,
                        onClick = { onSelectType(type) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "RELEASE YEAR",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val defaultYears = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2010 - 2019", "Before 2010")
            val allYearOptions = remember(state.availableYears) {
                val combined = (defaultYears + state.availableYears).distinct()
                listOf("All Years") + combined
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allYearOptions) { yr ->
                    val selected = if (yr == "All Years") state.selectedYear == null else state.selectedYear == yr
                    SearchFilterPill(
                        text = yr,
                        isSelected = selected,
                        accent = accent,
                        onClick = {
                            if (yr == "All Years") onSelectYear(null) else onSelectYear(yr)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "GENRE",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val allGenreOptions = listOf("All Genres") + state.genres

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allGenreOptions) { g ->
                    val selected = if (g == "All Genres") state.selectedGenre == null else state.selectedGenre == g
                    SearchFilterPill(
                        text = g,
                        isSelected = selected,
                        accent = accent,
                        onClick = {
                            if (g == "All Genres") onSelectGenre(null) else onSelectGenre(g)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onApply,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val resultCount = state.searchResults.size
            Text(
                text = if (state.rawSearchResults.isNotEmpty()) "Show $resultCount Results" else "Apply Filters",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchFilterPill(
    text: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val bg = if (isSelected) accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)
    val border = if (isSelected) BorderStroke(1.2.dp, accent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    val textColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 4.dp)
            )
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CatalogSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        )

        if ((actionLabel != null) && (onActionClick != null)) {
            Text(
                text = actionLabel,
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onActionClick() }
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchTriggered: () -> Unit = {},
    onClearSearch: () -> Unit,
    onFilterClick: () -> Unit = {},
    hasActiveFilters: Boolean = false,
    accent: Color,
    modifier: Modifier = Modifier,
    contentPaddingHorizontal: androidx.compose.ui.unit.Dp = 16.dp
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarHeight > 0.dp) (statusBarHeight - 14.dp).coerceAtLeast(2.dp) else 4.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
    ) {
        Text(
            text = "Search",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(horizontal = contentPaddingHorizontal, vertical = 2.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF131926))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = accent,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onSearchTriggered() }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search movies, series, directors...",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { onSearchTriggered() }
                            ),
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(accent),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF131926))
                    .border(
                        BorderStroke(
                            1.dp,
                            if (hasActiveFilters) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.07f)
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onFilterClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filter & Sort",
                    tint = if (hasActiveFilters) accent else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
                if (hasActiveFilters) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ProviderHubCard(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131926))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Text(
                        text = badge,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Explore",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
