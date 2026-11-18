package com.sumanonline.sumanmovies.ui.screens.category

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.CardDark
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryMediaScreen(
    viewModel: CategoryMediaViewModel,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarHeight > 0.dp) (statusBarHeight - 14.dp).coerceAtLeast(2.dp) else 4.dp
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems to lastVisible
        }.collect { (totalItems, lastVisible) ->
            if (totalItems > 0 && lastVisible >= totalItems - 4) {
                viewModel.loadNextPage()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(top = topPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val subtitleText = if (state.hasActiveFilters) {
                    "${state.displayedItems.size} filtered • ${String.format("%,d", state.totalCount)} total"
                } else {
                    "${state.displayedItems.size} of ${String.format("%,d", state.totalCount)} items"
                }

                Text(
                    text = subtitleText,
                    color = TextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { viewModel.openFilterSheet() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = if (state.sortOption != CategorySortOption.POPULAR) accent else Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (state.sortOption != CategorySortOption.POPULAR) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .align(Alignment.TopEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { viewModel.openFilterSheet() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter",
                        tint = if (state.typeFilter != CategoryTypeFilter.ALL || state.selectedYear != null) accent else Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                if (state.typeFilter != CategoryTypeFilter.ALL || state.selectedYear != null) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.hasActiveFilters,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.sortOption != CategorySortOption.POPULAR) {
                    item {
                        ActiveFilterChip(
                            label = state.sortOption.displayName,
                            accent = accent,
                            onRemove = { viewModel.setSortOption(CategorySortOption.POPULAR) }
                        )
                    }
                }

                if (state.typeFilter != CategoryTypeFilter.ALL) {
                    item {
                        ActiveFilterChip(
                            label = state.typeFilter.displayName,
                            accent = accent,
                            onRemove = { viewModel.setTypeFilter(CategoryTypeFilter.ALL) }
                        )
                    }
                }

                if (state.selectedYear != null) {
                    item {
                        ActiveFilterChip(
                            label = "Year: ${state.selectedYear}",
                            accent = accent,
                            onRemove = { viewModel.setSelectedYear(null) }
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(20.dp))
                            .clickable { viewModel.clearFilters() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accent)
            }
        } else if (state.displayedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No titles match your filter",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Try clearing filters or changing sort criteria to view more items.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

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
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(state.displayedItems, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMediaClick(item) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.68f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDark)
                        ) {
                            if (!item.posterUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.posterUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .align(Alignment.TopStart),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (item.mediaType) {
                                        MediaType.SERIES, MediaType.ANIME, MediaType.DRAMA -> Icons.Default.Tv
                                        MediaType.TV_CHANNEL -> Icons.Default.LiveTv
                                        else -> Icons.Default.LocalMovies
                                    },
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        val meta = buildString {
                            if (!item.year.isNullOrBlank()) append("${item.year} • ")
                            append(if (item.mediaType == MediaType.MOVIE) "movies" else "series")
                        }

                        Text(
                            text = meta,
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = accent,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Loading more titles...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else if (state.canLoadMore) {
                    item(span = { GridItemSpan(2) }) {
                        LaunchedEffect(Unit) {
                            viewModel.loadNextPage()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { viewModel.loadNextPage() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Load More Titles",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else if (state.displayedItems.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "End of collection • ${state.allItems.size} titles loaded",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
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
            FilterSortSheetContent(
                state = state,
                accent = accent,
                onSelectSort = { viewModel.setSortOption(it) },
                onSelectType = { viewModel.setTypeFilter(it) },
                onSelectYear = { viewModel.setSelectedYear(it) },
                onClearAll = { viewModel.clearFilters() },
                onApply = { viewModel.closeFilterSheet() }
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(
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
private fun FilterSortSheetContent(
    state: CategoryMediaUiState,
    accent: Color,
    onSelectSort: (CategorySortOption) -> Unit,
    onSelectType: (CategoryTypeFilter) -> Unit,
    onSelectYear: (String?) -> Unit,
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
                items(CategorySortOption.values()) { opt ->
                    val selected = state.sortOption == opt
                    FilterSelectablePill(
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
                CategoryTypeFilter.values().forEach { type ->
                    val selected = state.typeFilter == type
                    FilterSelectablePill(
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
                    FilterSelectablePill(
                        text = yr,
                        isSelected = selected,
                        accent = accent,
                        onClick = {
                            if (yr == "All Years") {
                                onSelectYear(null)
                            } else {
                                onSelectYear(yr)
                            }
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
            Text(
                text = "Show ${state.displayedItems.size} Results",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FilterSelectablePill(
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
