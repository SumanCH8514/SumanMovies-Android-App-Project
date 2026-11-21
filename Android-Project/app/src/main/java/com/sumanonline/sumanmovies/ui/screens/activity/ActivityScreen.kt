package com.sumanonline.sumanmovies.ui.screens.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.CardDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onResumeMedia: (item: MediaItem, season: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateDetails: ((MediaItem) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color
    var isSearchVisible by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val baseList = when (state.activeTab) {
        ActivityFilterTab.ALL -> state.allList
        ActivityFilterTab.WATCHING -> state.watchingList
        ActivityFilterTab.COMPLETED -> state.completedList
    }

    val filteredList = if (state.filterQuery.isBlank()) {
        baseList
    } else {
        baseList.filter { it.title.contains(state.filterQuery.trim(), ignoreCase = true) }
    }

    val displayList = filteredList.distinctBy {
        val clean = it.title.lowercase()
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .replace(Regex("""[^a-z0-9]"""), "")
        "$clean:s${it.seasonNumber}e${it.episodeNumber}"
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CardElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Clear Watch History?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear your entire watch history? This action cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearDialog = false
                    }
                ) {
                    Text(
                        text = "Clear All",
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = "Cancel", color = TextMuted)
                }
            }
        )
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarHeight > 0.dp) (statusBarHeight - 12.dp).coerceAtLeast(4.dp) else 8.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPadding, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Watch History",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.allList.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = state.allList.size.toString(),
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) viewModel.setFilterQuery("")
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSearchVisible || state.filterQuery.isNotEmpty()) accent.copy(alpha = 0.18f) else CardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search History",
                            tint = if (isSearchVisible || state.filterQuery.isNotEmpty()) accent else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (state.allList.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CardElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear History",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = isSearchVisible || state.filterQuery.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardElevated)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.filterQuery.isEmpty()) {
                                Text(
                                    text = "Search in history...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = state.filterQuery,
                                onValueChange = { viewModel.setFilterQuery(it) },
                                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                cursorBrush = SolidColor(accent),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (state.filterQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setFilterQuery("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    Triple(ActivityFilterTab.ALL, "All", state.allList.size),
                    Triple(ActivityFilterTab.WATCHING, "In Progress", state.watchingList.size),
                    Triple(ActivityFilterTab.COMPLETED, "Completed", state.completedList.size)
                )
                tabs.forEach { (tab, label, count) ->
                    val isSelected = state.activeTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.18f) else CardElevated)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f)
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { viewModel.setTab(tab) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                color = if (isSelected) accent else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = count.toString(),
                                color = if (isSelected) accent.copy(alpha = 0.85f) else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (displayList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = accent.copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (state.filterQuery.isNotEmpty()) "No matching history found" else "No Watch History Yet",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (state.filterQuery.isNotEmpty()) {
                                "Try searching with different keywords."
                            } else {
                                "Movies and episodes you watch will appear here so you can easily pick up where you left off."
                            },
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(displayList, key = { index, item -> "${item.id}_$index" }) { _, item ->
                val isSeries = item.mediaType.equals("SERIES", ignoreCase = true) ||
                        item.mediaType.equals("ANIME", ignoreCase = true) ||
                        item.mediaType.equals("DRAMA", ignoreCase = true)

                val cleanBaseTitle = item.title
                    .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
                    .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
                    .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
                    .trim()
                    .ifBlank { item.title }

                val formattedMediaTitle = if (isSeries) {
                    "$cleanBaseTitle - S${item.seasonNumber}E${item.episodeNumber}"
                } else {
                    cleanBaseTitle
                }

                val media = MediaItem(
                    id = item.mediaId,
                    title = formattedMediaTitle,
                    posterUrl = item.posterUrl,
                    mediaType = try {
                        MediaType.valueOf(item.mediaType)
                    } catch (_: Exception) {
                        MediaType.MOVIE
                    }
                )

                ActivityHistoryCard(
                    item = item,
                    cleanTitle = cleanBaseTitle,
                    accentColor = accent,
                    onResume = {
                        onResumeMedia(media, item.seasonNumber, item.episodeNumber)
                    },
                    onDelete = { viewModel.removeHistoryItem(item.id) },
                    onOpenDetails = { onNavigateDetails?.invoke(media) }
                )
            }
        }
    }
}

@Composable
private fun ActivityHistoryCard(
    item: WatchHistoryEntity,
    cleanTitle: String,
    accentColor: Color,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetails: (() -> Unit)? = null
) {
    val isMovie = item.mediaType.equals("MOVIE", ignoreCase = true)
    val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f

    val badgeLabel = if (isMovie) "Movie" else "S${item.seasonNumber} • E${item.episodeNumber}"

    val timeStatusText = if (item.durationMs > 0) {
        val remainingSec = (item.durationMs - item.positionMs).coerceAtLeast(0) / 1000
        val remainingMin = remainingSec / 60
        val remainingHours = remainingMin / 60
        val remMinutes = remainingMin % 60
        if (item.isCompleted || progress >= 0.95f) {
            val totalMin = (item.durationMs / 1000) / 60
            val h = totalMin / 60
            val m = totalMin % 60
            if (h > 0) "Completed • ${h}h ${m}m" else "Completed • ${m}m"
        } else if (remainingHours > 0) {
            "${remainingHours}h ${remMinutes}m left • ${(progress * 100).toInt()}%"
        } else {
            "${remainingMin}m left • ${(progress * 100).toInt()}%"
        }
    } else {
        if (item.isCompleted) "Completed" else "In Progress"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(14.dp))
            .clickable { onResume() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(82.dp)
                    .height(116.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardElevated)
            ) {
                val displayImage = if (isMovie) {
                    item.backdropUrl?.takeIf { it.isNotBlank() } ?: item.posterUrl
                } else {
                    item.stillUrl?.takeIf { it.isNotBlank() }
                        ?: item.backdropUrl?.takeIf { it.isNotBlank() }
                        ?: item.posterUrl
                }

                if (!displayImage.isNullOrBlank()) {
                    AsyncImage(
                        model = displayImage,
                        contentDescription = cleanTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (item.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = accentColor,
                        trackColor = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove from history",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = cleanTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timeStatusText,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (item.isCompleted) Color(0xFF10B981) else accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (item.isCompleted) "Completed" else "Watching",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onResume,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (item.isCompleted) "Replay" else "Resume",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
