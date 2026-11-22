package com.sumanonline.sumanmovies.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.ui.theme.BorderDark
import com.sumanonline.sumanmovies.ui.theme.CardDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        if (onSeeAllClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSeeAllClick() }
                    .padding(4.dp)
            ) {
                Text(
                    text = "See All",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "See All",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun buildSafeImageModel(url: String?): Any? {
    if (url.isNullOrBlank()) return null
    val context = LocalContext.current
    return remember(url) {
        if (url.contains("animexin.dev")) {
            ImageRequest.Builder(context)
                .data(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://animexin.dev/")
                .crossfade(true)
                .build()
        } else {
            url
        }
    }
}

@Composable
fun VerticalPosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    accentColor: Color = LocalAppAccent.current.color,
    showProviderBadge: Boolean = true
) {
    val accent = accentColor
    Column(
        modifier = modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(115.dp)
                .height(165.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(
                    BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
                    RoundedCornerShape(12.dp)
                )
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = buildSafeImageModel(item.posterUrl),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(24.dp)
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
                    modifier = Modifier.size(14.dp)
                )
            }

            if (rank != null) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accent,
                                    accent.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val meta = buildString {
            if (!item.year.isNullOrBlank()) append("${item.year} • ")
            append(
                when (item.mediaType) {
                    MediaType.MOVIE -> "Movie"
                    MediaType.ANIME -> "Anime"
                    MediaType.DRAMA -> "Drama"
                    else -> "Series"
                }
            )
        }

        Text(
            text = meta,
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ContinueWatchingCard(
    item: WatchHistoryEntity,
    onResumeClick: () -> Unit,
    onInfoClick: () -> Unit,
    onMarkWatchedClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestartClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = LocalAppAccent.current.color
    var menuExpanded by remember { mutableStateOf(false) }

    val progress = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    val remainingMs = (item.durationMs - item.positionMs).coerceAtLeast(0L)
    val remMin = (remainingMs / 1000) / 60
    val remHours = remMin / 60
    val remainingFormatted = if (item.durationMs > 0) {
        if (remHours > 0) "${remHours}h ${remMin % 60}m left" else "${remMin}m left"
    } else {
        val pct = (progress * 100).toInt()
        if (pct > 0) "$pct% watched" else "In progress"
    }

    val isSeries = item.mediaType.equals("SERIES", ignoreCase = true) ||
            item.mediaType.equals("ANIME", ignoreCase = true) ||
            item.mediaType.equals("DRAMA", ignoreCase = true)

    val cleanBaseTitle = item.title
        .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
        .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
        .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
        .trim()
        .ifBlank { item.title }

    val displayTitle = if (isSeries) {
        "$cleanBaseTitle - S${item.seasonNumber}E${item.episodeNumber}"
    } else {
        item.title
    }

    val subtitleText = if (isSeries) {
        "S${item.seasonNumber} • E${item.episodeNumber} • $remainingFormatted"
    } else {
        "Movie • $remainingFormatted"
    }

    Column(
        modifier = modifier
            .width(225.dp)
            .clickable { onResumeClick() }
    ) {
        Box(
            modifier = Modifier
                .width(225.dp)
                .height(128.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(
                    BorderStroke(1.dp, BorderDark),
                    RoundedCornerShape(12.dp)
                )
        ) {
            val displayImage = if (isSeries) {
                item.stillUrl?.takeIf { it.isNotBlank() }
                    ?: item.backdropUrl?.takeIf { it.isNotBlank() }
                    ?: item.posterUrl
            } else {
                item.backdropUrl?.takeIf { it.isNotBlank() }
                    ?: item.posterUrl
            }

            if (!displayImage.isNullOrBlank()) {
                AsyncImage(
                    model = buildSafeImageModel(displayImage),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(CardElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Resume",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onResumeClick()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Play from Beginning",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRestartClick()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Details & Info",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onInfoClick()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Mark as Watched",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onMarkWatchedClick()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Remove from List",
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }

            if (item.durationMs > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = remainingFormatted,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .align(Alignment.BottomCenter),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: WatchHistoryEntity,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ContinueWatchingCard(
        item = item,
        onResumeClick = onResumeClick,
        onInfoClick = onResumeClick,
        onMarkWatchedClick = {},
        onDeleteClick = {},
        modifier = modifier
    )
}

@Composable
fun LandscapeCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(
                    BorderStroke(1.dp, BorderDark.copy(alpha = 0.5f)),
                    RoundedCornerShape(12.dp)
                )
        ) {
            val img = item.backdropUrl ?: item.posterUrl
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = buildSafeImageModel(img),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val meta = buildString {
            if (!item.year.isNullOrBlank()) append("${item.year} • ")
            append(
                when (item.mediaType) {
                    MediaType.MOVIE -> "Movie"
                    MediaType.ANIME -> "Anime"
                    MediaType.DRAMA -> "Drama"
                    else -> "Series"
                }
            )
        }

        Text(
            text = meta,
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
fun GenreCard(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardElevated)
            .border(
                BorderStroke(1.dp, BorderDark),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

