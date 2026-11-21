package com.sumanonline.sumanmovies.ui.screens.details

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sumanonline.sumanmovies.data.models.StreamMirror
import com.sumanonline.sumanmovies.data.models.getDisplayCategory
import com.sumanonline.sumanmovies.data.models.getDisplayQuality
import com.sumanonline.sumanmovies.data.models.getDisplayTitle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.ServerAvailability
import com.sumanonline.sumanmovies.data.models.ServerState
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.data.models.TrailerItem
import com.sumanonline.sumanmovies.ui.components.VerticalPosterCard
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.BorderDark
import com.sumanonline.sumanmovies.ui.theme.CardDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onPlay: (MediaItem, StreamSource?, StreamingServer?, List<StreamSource>, List<SubtitleTrack>) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onShowAllEpisodesClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDownloads: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color
    val details = state.details
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                CircularProgressIndicator(color = accent)
            }
        } else if (details != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    ) {
                        val img = details.backdropUrl ?: details.posterUrl
                        if (!img.isNullOrBlank()) {
                            AsyncImage(
                                model = img,
                                contentDescription = details.title,
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
                                            BgDark.copy(alpha = 0.75f),
                                            BgDark
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "IMDb ",
                                    color = Color(0xFFF5C518),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format("%.1f", details.rating ?: 8.0f),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!details.logoUrl.isNullOrBlank()) {
                            var isLogoError by remember(details.logoUrl) { mutableStateOf(false) }
                            if (!isLogoError) {
                                AsyncImage(
                                    model = details.logoUrl,
                                    contentDescription = details.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp, max = 80.dp)
                                        .padding(horizontal = 24.dp, vertical = 6.dp),
                                    onError = { isLogoError = true }
                                )
                            } else {
                                Text(
                                    text = details.title,
                                    color = TextPrimary,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Text(
                                text = details.title,
                                color = TextPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val metaParts = mutableListOf<String>()
                        details.year?.let { metaParts.add(it) }
                        details.productionCompany?.let { metaParts.add(it) }

                        if (details.mediaType == MediaType.MOVIE) {
                            details.runtimeMinutes?.let { dur ->
                                val h = dur / 60
                                val m = dur % 60
                                metaParts.add(if (h > 0) "${h}h ${m}m" else "${m}m")
                            }
                        } else {
                            val seasonsCount = details.totalSeasons ?: details.seasons.size
                            if (seasonsCount > 0) {
                                metaParts.add(if (seasonsCount == 1) "1 Season" else "$seasonsCount Seasons")
                            }
                        }

                        if (details.genres.isNotEmpty()) {
                            metaParts.add(details.genres.take(2).joinToString(" & "))
                        }

                        Text(
                            text = metaParts.joinToString(" • "),
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val overviewText = details.overview ?: "No synopsis available."
                        Text(
                            text = overviewText,
                            color = TextSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            maxLines = if (state.isOverviewExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (overviewText.length > 70) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.isOverviewExpanded) "Read less" else "Read more",
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.toggleOverviewExpanded() }
                                    .padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                viewModel.playDirectOrOpenModal(context) { item, stream, srv, allStreams, subs ->
                                    onPlay(item, stream, srv, allStreams, subs)
                                }
                            },
                            enabled = !state.isDirectPlaying,
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (state.isDirectPlaying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Loading Stream...",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Play Now",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionIconButton(
                                icon = Icons.Default.FileDownload,
                                label = "Download",
                                onClick = { viewModel.openDownloadModal() }
                            )

                            ActionIconButton(
                                icon = if (state.isFavorite) Icons.Default.Check else Icons.Default.Add,
                                label = if (state.isFavorite) "Saved" else "Add to Library",
                                onClick = { viewModel.toggleFavorite() }
                            )

                            ActionIconButton(
                                icon = Icons.Default.Share,
                                label = "Share",
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Watch ${details.title} on SumanMovies!")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share via"))
                                }
                            )

                            ActionIconButton(
                                icon = Icons.Default.Movie,
                                label = "Trailer",
                                onClick = {
                                    val trailer = details.trailers.firstOrNull()
                                    if (trailer != null) {
                                        val item = MediaItem(
                                            id = "trailer-${trailer.id}",
                                            title = "${details.title} (Trailer)",
                                            posterUrl = trailer.thumbnailUrl ?: details.posterUrl,
                                            mediaType = MediaType.MOVIE
                                        )
                                        val trailerStream = StreamSource(
                                            quality = "HD",
                                            format = "MP4",
                                            url = "https://www.youtube.com/watch?v=${trailer.key}",
                                            label = "Trailer",
                                            provider = "YouTube"
                                        )
                                        onPlay(item, trailerStream, null, listOf(trailerStream), emptyList())
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Available Servers",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val isAnime = state.details?.mediaType == MediaType.ANIME ||
                            state.details?.genres?.any { it.contains("Anime", ignoreCase = true) || it.contains("Animation", ignoreCase = true) } == true ||
                            state.details?.id?.startsWith("reanime:") == true ||
                            com.sumanonline.sumanmovies.data.models.isAnimeCandidate(state.details?.title.orEmpty(), state.details?.mediaType, state.details?.genres, state.details?.id)
                        val availableServers = StreamingServer.getAvailableServers(isAnime)

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(availableServers) { server ->
                                val serverState = state.serverStates[server]
                                val isSelected = (state.selectedServer == server)
                                ServerCard(
                                    server = server,
                                    serverState = serverState,
                                    isSelected = isSelected,
                                    onSelect = {
                                        if ((server == StreamingServer.PLAY4U || server == StreamingServer.DRAMACHI) && isSelected) {
                                            viewModel.playDirect(context, server) { item, stream, srv, allStreams, subs ->
                                                onPlay(item, stream, srv, allStreams, subs)
                                            }
                                        } else {
                                            viewModel.selectServer(server)
                                        }
                                    },
                                    onRetry = { viewModel.retryServer(server) }
                                )
                            }
                        }
                    }
                }

                if (details.mediaType != MediaType.MOVIE && state.seasons.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(26.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Seasons & Episodes",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onShowAllEpisodesClick(details.id, details.title) }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = "Show All Episodes",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
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

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.seasons) { s ->
                                val isSelected = s.seasonNumber == state.selectedSeason
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) accent else CardElevated)
                                        .border(BorderStroke(1.dp, if (isSelected) accent else BorderDark), RoundedCornerShape(20.dp))
                                        .clickable { viewModel.selectSeason(s.seasonNumber) }
                                        .padding(horizontal = 18.dp, vertical = 9.dp)
                                ) {
                                    Text(
                                        text = s.name.ifBlank { "Season ${s.seasonNumber}" },
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.episodes) { ep ->
                                EpisodePreviewCard(
                                    episode = ep,
                                    onClick = {
                                        viewModel.onEpisodeClicked(ep) { item, stream, server, all, subs ->
                                            onPlay(item, stream, server, all, subs)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(26.dp))

                    Text(
                        text = "Trailers & Teasers",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val trailersList = if (details.trailers.isNotEmpty()) {
                        details.trailers
                    } else {
                        listOf(
                            TrailerItem("t1", "Official Trailer", "key1", "YouTube", details.backdropUrl),
                            TrailerItem("t2", "SDCC Teaser", "key2", "YouTube", details.posterUrl)
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(trailersList) { tr ->
                            Column(
                                modifier = Modifier
                                    .width(210.dp)
                                    .clickable { }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(210.dp)
                                        .height(118.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardDark)
                                ) {
                                    val thumb = tr.thumbnailUrl ?: details.backdropUrl
                                    if (!thumb.isNullOrBlank()) {
                                        AsyncImage(
                                            model = thumb,
                                            contentDescription = tr.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

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
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = tr.title,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(26.dp))

                    Text(
                        text = "Rating & Reviews",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardDark)
                            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(110.dp)
                            ) {
                                Text(
                                    text = String.format("%.1f", details.rating ?: 8.0f),
                                    color = TextPrimary,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val stars = ((details.rating ?: 8.0f) / 2.0f).toInt().coerceIn(1, 5)
                                    repeat(5) { i ->
                                        Icon(
                                            imageVector = if (i < stars) Icons.Default.Star else Icons.Outlined.Star,
                                            contentDescription = null,
                                            tint = if (i < stars) Color(0xFFF59E0B) else Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${String.format("%,d", details.ratingsCount)} ratings",
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val weights = details.ratingBreakdown
                                listOf(5, 4, 3, 2, 1).forEachIndexed { idx, starNum ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "$starNum",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.width(12.dp)
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        LinearProgressIndicator(
                                            progress = { weights.getOrElse(idx) { 0.1f } },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = Color(0xFFF59E0B),
                                            trackColor = CardElevated
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (details.cast.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(26.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cast & Crew",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "See All >",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(details.cast.chunked(2)) { pair ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { castMember ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.width(180.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(CardElevated)
                                            ) {
                                                if (!castMember.profileUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = castMember.profileUrl,
                                                        contentDescription = castMember.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = castMember.name,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Text(
                                                    text = castMember.character ?: "Actor",
                                                    color = TextMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val youMayLike = details.recommendations.ifEmpty { details.similar }
                if (youMayLike.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(26.dp))

                        Text(
                            text = "You May also Like",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(youMayLike) { item ->
                                VerticalPosterCard(
                                    item = item,
                                    onClick = { onMediaClick(item) }
                                )
                            }
                        }
                    }
                }

                if (details.similar.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(26.dp))

                        Text(
                            text = "Similar Titles",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(details.similar) { item ->
                                VerticalPosterCard(
                                    item = item,
                                    onClick = { onMediaClick(item) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Unable to load title details",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Check your connection or try again",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.retry() },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "Retry", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (state.serverErrorMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissServerError() },
                title = {
                    Text(
                        text = "Server Unavailable",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = state.serverErrorMessage ?: "The selected server could not resolve a stream.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        if (state.fallbackCandidates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Would you like to switch to ${state.fallbackCandidates.first().displayName} instead?",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    if (state.fallbackCandidates.isNotEmpty()) {
                        val fallback = state.fallbackCandidates.first()
                        TextButton(
                            onClick = { viewModel.switchToFallbackServer(fallback) }
                        ) {
                            Text(
                                text = "Switch to ${fallback.displayName}",
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.retryServer(state.selectedServer) }
                        ) {
                            Text(
                                text = "Retry",
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissServerError() }
                    ) {
                        Text(text = "Cancel", color = TextMuted)
                    }
                },
                containerColor = CardElevated
            )
        }

        val modalServer = state.qualityModalServer
        if (state.showQualityModal && modalServer != null && details != null) {
            val serverState = state.serverStates[modalServer]
            val liveStreams = serverState?.streams ?: emptyList()

            if (state.qualityModalMode == QualityModalMode.DOWNLOAD) {
                DownloadFullScreenModal(
                    server = modalServer,
                    details = details,
                    streams = liveStreams,
                    isLoading = state.isQualityModalLoading,
                    resolvingStreamUrl = state.resolvingStreamUrl,
                    onSelectServer = { srv -> viewModel.selectDownloadServer(srv) },
                    onDismiss = { viewModel.closeQualityModal() },
                    onRefresh = { viewModel.fetchRealtimeQualities(modalServer) },
                    onDownloadStream = { stream, mirror ->
                        viewModel.downloadStreamOption(context, stream, mirror)
                        viewModel.closeQualityModal()
                        onNavigateToDownloads()
                    }
                )
            } else {
                QualitySelectionBottomSheet(
                    server = modalServer,
                    details = details,
                    streams = liveStreams.filter { it.canStream() },
                    mode = state.qualityModalMode,
                    isLoading = state.isQualityModalLoading,
                    resolvingStreamUrl = state.resolvingStreamUrl,
                    onDismiss = { viewModel.closeQualityModal() },
                    onRefresh = { viewModel.fetchRealtimeQualities(modalServer) },
                    onPlayStream = { stream, mirror ->
                        viewModel.playStreamOption(context, stream, mirror) { item, s, srv ->
                            val all = state.serverStates[srv]?.streams ?: state.availableStreams
                            val subs = state.serverStates[srv]?.subtitles ?: emptyList()
                            onPlay(item, s, srv, all, subs)
                        }
                    },
                    onDownloadStream = { stream, mirror ->
                        viewModel.downloadStreamOption(context, stream, mirror)
                        viewModel.closeQualityModal()
                        onNavigateToDownloads()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(CardElevated)
                .border(1.dp, BorderDark, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EpisodePreviewCard(
    episode: Episode,
    onClick: () -> Unit
) {
    val accent = LocalAppAccent.current.color
    Row(
        modifier = Modifier
            .width(280.dp)
            .height(86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(70.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardElevated)
        ) {
            if (!episode.stillUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "EPISODE ${episode.episodeNumber} • 1h 6m",
                color = accent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = episode.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = episode.overview ?: "No episode description.",
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: StreamingServer,
    serverState: ServerState?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAppAccent.current.color
    val status = serverState?.status ?: ServerAvailability.CHECKING

    val borderColor = if (isSelected) accent else BorderDark
    val bgColor = if (isSelected) accent.copy(alpha = 0.16f) else CardElevated

    Box(
        modifier = modifier
            .widthIn(min = 120.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                shape = RoundedCornerShape(14.dp)
            )
            .background(bgColor)
            .clickable {
                if (status == ServerAvailability.ERROR || status == ServerAvailability.UNAVAILABLE) {
                    onRetry()
                }
                onSelect()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = server.displayName,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )

                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                when (status) {
                    ServerAvailability.CHECKING -> {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Checking...",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    ServerAvailability.AVAILABLE -> {
                        Box(
                            modifier = Modifier
                                .size(7.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Online",
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    ServerAvailability.UNAVAILABLE, ServerAvailability.ERROR -> {
                        Box(
                            modifier = Modifier
                                .size(7.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offline",
                            color = Color(0xFFE53935),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySelectionBottomSheet(
    server: StreamingServer,
    details: MediaDetails,
    streams: List<StreamSource>,
    mode: QualityModalMode,
    isLoading: Boolean,
    resolvingStreamUrl: String? = null,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPlayStream: (StreamSource, StreamMirror?) -> Unit,
    onDownloadStream: (StreamSource, StreamMirror?) -> Unit
) {
    val accent = LocalAppAccent.current.color
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isMovieBox = server == StreamingServer.MOVIEBOX
    val categoryOrder = if (isMovieBox) {
        listOf("FHD", "HD", "SD", "Other")
    } else {
        listOf("2160p / 4K", "1080p", "720p", "480p", "Other")
    }
    val groupedStreams = remember(streams, server) {
        streams
            .filterNot { s ->
                val title = s.title?.trim() ?: ""
                val url = s.url.trim().lowercase()
                ((title.equals("4K Movies", ignoreCase = true) || title.equals("4K Movie", ignoreCase = true)) &&
                    (url.contains("4khdhub") || s.provider.equals("hdhub4u", ignoreCase = true))) ||
                    (url.contains("4khdhub.one") && (title.equals("4K Movies", ignoreCase = true) || url.trimEnd('/') == "https://4khdhub.one"))
            }
            .groupBy { stream ->
                stream.getDisplayCategory(server)
            }.toList().sortedBy { (cat, _) ->
                val idx = categoryOrder.indexOf(cat)
                if (idx >= 0) idx else 99
            }
    }

    val expandedCategories = remember(groupedStreams) {
        mutableStateMapOf<String, Boolean>().apply {
            groupedStreams.forEach { (cat, _) ->
                put(cat, true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardElevated,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderDark)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            val isStreamMode = mode == QualityModalMode.STREAM
            val pillBadgeColor = if (isStreamMode) Color(0xFF4CAF50) else Color(0xFFF59E0B)
            val pillBadgeText = if (isStreamMode) "Live Streams" else "Direct Downloads"

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = server.displayName,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBadgeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(pillBadgeColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = pillBadgeText,
                            color = pillBadgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh qualities",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val titleText = if (isStreamMode) "Stream Links" else "Download Links"
            val titleColor = if (isStreamMode) accent else Color(0xFFF59E0B)
            val subtitleText = if (isStreamMode) {
                "${details.title} • Real-time streaming options from ${server.displayName}"
            } else {
                "${details.title} • Real-time direct options from ${server.displayName}"
            }

            Text(
                text = titleText,
                color = titleColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitleText,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isStreamMode) "Fetching all streaming options from ${server.displayName}..." else "Fetching all download options from ${server.displayName}...",
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Categorizing 4K, 1080p, 720p streams and mirrors",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            } else if (streams.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isStreamMode) "No real-time streaming options found on ${server.displayName}." else "No real-time download options found on ${server.displayName}.",
                        color = TextSecondary,
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Retry Live Fetch", color = accent, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    groupedStreams.forEach { (category, categoryItems) ->
                        val isExpanded = expandedCategories[category] ?: false
                        val topSourceTag = categoryItems.firstOrNull()?.sourceQualityTag ?: "BluRay"

                        item(key = "header_$category") {
                            CategorySectionHeader(
                                category = category,
                                sourceTag = topSourceTag,
                                optionsCount = categoryItems.size,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedCategories[category] = !isExpanded
                                }
                            )
                        }

                        if (isExpanded) {
                            itemsIndexed(
                                items = categoryItems,
                                key = { index, stream -> "${category}_${stream.provider}_${stream.format}_${stream.quality}_${stream.url}_$index" }
                            ) { _, stream ->
                                DownloadOptionCard(
                                    stream = stream,
                                    accent = accent,
                                    mode = mode,
                                    resolvingStreamUrl = resolvingStreamUrl,
                                    onPlay = { mirror -> onPlayStream(stream, mirror) },
                                    onDownload = { mirror -> onDownloadStream(stream, mirror) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(
    category: String,
    sourceTag: String,
    optionsCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotation")

    val (badgeBg, badgeTextColor) = when {
        category == "FHD" || category.contains("1080") ->
            Color(0xFF1E40AF) to Color.White
        category == "HD" || category.contains("720") ->
            Color(0xFF0D9488) to Color.White
        category == "SD" || category.contains("480") || category.contains("360") ->
            Color(0xFF475569) to Color.White
        category.contains("4K", ignoreCase = true) || category.contains("2160") ->
            Color(0xFFD97706) to Color.White
        else ->
            Color(0xFF374151) to Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = category,
                        color = badgeTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF15803D))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sourceTag,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$optionsCount options",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun DownloadOptionCard(
    stream: StreamSource,
    accent: Color,
    mode: QualityModalMode,
    resolvingStreamUrl: String?,
    onPlay: (StreamMirror?) -> Unit,
    onDownload: (StreamMirror?) -> Unit
) {
    val displayTitle = stream.getDisplayTitle()
    val isResolvingThisStream = resolvingStreamUrl != null &&
            (resolvingStreamUrl == stream.url || stream.mirrors.any { it.url == resolvingStreamUrl })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {

            Text(
                text = displayTitle,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (!stream.sizeFormatted.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFEA580C))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = stream.sizeFormatted ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (!stream.audioLangs.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0D9488))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = stream.audioLangs ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (!stream.sourceQualityTag.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF15803D))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = stream.sourceQualityTag ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (isResolvingThisStream) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unpacking direct CDN stream link...",
                        color = accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (stream.mirrors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stream.mirrors.forEach { mirror ->
                        val isMirrorResolving = resolvingStreamUrl == mirror.url

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mirror.name,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {

                                if (mode == QualityModalMode.STREAM) {
                                    Button(
                                        onClick = { onPlay(mirror) },
                                        enabled = !isMirrorResolving,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        if (isMirrorResolving) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 1.5.dp,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Play",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onDownload(mirror) },
                                        enabled = !isMirrorResolving,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF272F45),
                                            contentColor = Color.White
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Download",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${stream.format} • ${stream.codec ?: "H264"}",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (mode == QualityModalMode.STREAM) {
                            Button(
                                onClick = { onPlay(null) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Play",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { onDownload(null) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF272F45),
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Download",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QualityCategoryMeta(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultBadge: String,
    val badgeTextColor: Color,
    val badgeBgColor: Color
)

private fun getQualityBucket(stream: StreamSource): String {
    val q = stream.quality.lowercase()
    val c = stream.category.lowercase()
    val t = (stream.title ?: stream.label).lowercase()
    return when {
        q.contains("2160") || q.contains("4k") || c.contains("2160") || c.contains("4k") || t.contains("2160") || t.contains("4k") -> "4K"
        q.contains("1080") || q.contains("fhd") || c.contains("1080") || t.contains("1080") || t.contains("fhd") -> "1080p"
        q.contains("720") || q.contains("hd") || c.contains("720") || t.contains("720") -> "720p"
        q.contains("360") || q.contains("480") || q.contains("sd") || c.contains("360") || c.contains("480") || t.contains("360") || t.contains("480") -> "360p"
        else -> "1080p"
    }
}

@Composable
private fun DownloadFullScreenModal(
    server: StreamingServer,
    details: MediaDetails,
    streams: List<StreamSource>,
    isLoading: Boolean,
    resolvingStreamUrl: String? = null,
    onSelectServer: (StreamingServer) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadStream: (StreamSource, StreamMirror?) -> Unit
) {
    val standardCategories = remember {
        listOf(
            QualityCategoryMeta("4K", "4K", "Ultra HD (2160p)", "HDR10 / DV", Color(0xFFF59E0B), Color(0xFF78350F).copy(alpha = 0.5f)),
            QualityCategoryMeta("1080p", "1080p", "Full HD", "60FPS BluRay", Color(0xFF10B981), Color(0xFF064E3B).copy(alpha = 0.5f)),
            QualityCategoryMeta("720p", "720p", "HD Standard", "WEB-DL", Color(0xFF3B82F6), Color(0xFF1E3A8A).copy(alpha = 0.5f)),
            QualityCategoryMeta("360p", "360p", "Data Saver", "HEVC x265", Color(0xFFA855F7), Color(0xFF4C1D95).copy(alpha = 0.5f))
        )
    }

    val groupedStreams = remember(streams) {
        streams
            .filterNot { s ->
                val title = s.title?.trim() ?: ""
                val url = s.url.trim().lowercase()
                ((title.equals("4K Movies", ignoreCase = true) || title.equals("4K Movie", ignoreCase = true)) &&
                    (url.contains("4khdhub") || s.provider.equals("hdhub4u", ignoreCase = true))) ||
                    (url.contains("4khdhub.one") && (title.equals("4K Movies", ignoreCase = true) || url.trimEnd('/') == "https://4khdhub.one"))
            }
            .groupBy { getQualityBucket(it) }
    }

    val expandedCategories = remember(server, streams) {
        mutableStateMapOf<String, Boolean>().apply {
            val defaultCategory = when {
                groupedStreams.containsKey("720p") && groupedStreams["720p"]?.isNotEmpty() == true -> "720p"
                groupedStreams.containsKey("1080p") && groupedStreams["1080p"]?.isNotEmpty() == true -> "1080p"
                else -> standardCategories.firstOrNull {
                    groupedStreams.containsKey(it.id) && groupedStreams[it.id]?.isNotEmpty() == true
                }?.id
            }
            if (defaultCategory != null) {
                put(defaultCategory, true)
            }
        }
    }

    val isAnimeDownload = details.mediaType == MediaType.ANIME ||
        details.genres.any { it.equals("Anime", ignoreCase = true) || it.equals("Animation", ignoreCase = true) } ||
        details.id.startsWith("reanime:")

    val downloadProviders = remember(details) {
        if (isAnimeDownload) {
            listOf(
                StreamingServer.FOURKHDHUB,
                StreamingServer.HDHUB4U,
                StreamingServer.PLAY4U,
                StreamingServer.MOVIEBOX,
                StreamingServer.REANIME
            )
        } else {
            listOf(
                StreamingServer.FOURKHDHUB,
                StreamingServer.HDHUB4U,
                StreamingServer.PLAY4U,
                StreamingServer.MOVIEBOX
            )
        }
    }

    val releaseYear = details.releaseDate?.take(4) ?: details.year
    val yearStr = if (!releaseYear.isNullOrBlank()) " ($releaseYear)" else ""
    val activeProviderLabel = when (server) {
        StreamingServer.FOURKHDHUB -> "4KHub4U"
        else -> server.displayName
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF08080A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFFE50914)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Suman",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Movies",
                            color = Color(0xFFE50914),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                
                Text(
                    text = "${details.title}$yearStr Download Page",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT PROVIDER",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$activeProviderLabel Active",
                            color = Color(0xFF22C55E),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(downloadProviders) { srv ->
                        val isSelected = server == srv
                        val label = when (srv) {
                            StreamingServer.MOVIEBOX -> "MovieBox"
                            StreamingServer.PLAY4U -> "Play4U"
                            StreamingServer.HDHUB4U -> "HDHub4U"
                            StreamingServer.FOURKHDHUB -> "4KHub4U"
                            StreamingServer.DRAMACHI -> "Dramachi"
                            StreamingServer.REANIME -> "ReAnime"
                            StreamingServer.KMDL -> "KMDL"
                            StreamingServer.MOVIESLEECH -> "MoviesLeech"
                            StreamingServer.MOVIESMOD -> "MoviesMod"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFFE50914) else Color(0xFF16161D))
                                .clickable { onSelectServer(srv) }
                                .padding(horizontal = 20.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quality Categories",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Tap to expand / close",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                
                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFE50914),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Fetching all download options from $activeProviderLabel...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Categorizing 4K, 1080p, 720p, 360p download links",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                } else if (streams.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No download options found on $activeProviderLabel.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRefresh,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Retry Live Fetch", color = Color.White, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val activeCategories = standardCategories.filter {
                            groupedStreams[it.id]?.isNotEmpty() == true
                        }.ifEmpty {
                            groupedStreams.keys.map { key ->
                                QualityCategoryMeta(
                                    id = key,
                                    title = key,
                                    subtitle = "Available Options",
                                    defaultBadge = "Direct",
                                    badgeTextColor = Color(0xFF10B981),
                                    badgeBgColor = Color(0xFF064E3B).copy(alpha = 0.5f)
                                )
                            }
                        }

                        items(activeCategories, key = { it.id }) { cat ->
                            val categoryItems = groupedStreams[cat.id] ?: emptyList()
                            val isExpanded = expandedCategories[cat.id] == true
                            val rotation by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                label = "rotation"
                            )

                            val rawBadge = categoryItems.firstOrNull()?.sourceQualityTag
                                ?: cat.defaultBadge
                            val cleanBadge = when {
                                rawBadge.contains("IMAX", ignoreCase = true) -> "IMAX"
                                rawBadge.contains("BluRay", ignoreCase = true) -> "BluRay"
                                rawBadge.contains("HDR", ignoreCase = true) -> "HDR10 / DV"
                                rawBadge.contains("WEB-DL", ignoreCase = true) -> "WEB-DL"
                                else -> rawBadge
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF121217))
                                    .border(1.dp, Color(0xFF1F1F27), RoundedCornerShape(16.dp))
                            ) {
                                Column {
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedCategories[cat.id] = !isExpanded
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = cat.title,
                                                color = Color.White,
                                                fontSize = 16.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = cat.subtitle,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.5.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(cat.badgeBgColor)
                                                    .border(
                                                        0.5.dp,
                                                        cat.badgeTextColor.copy(alpha = 0.4f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = cleanBadge,
                                                    color = cat.badgeTextColor,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.06f))
                                                    .padding(horizontal = 9.dp, vertical = 3.5.dp)
                                            ) {
                                                val count = categoryItems.size
                                                Text(
                                                    text = "$count ${if (count == 1) "Link" else "Links"}",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.06f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .rotate(rotation)
                                                )
                                            }
                                        }
                                    }

                                    
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp)
                                                .padding(bottom = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            categoryItems.forEach { stream ->
                                                DownloadOptionCardV2(
                                                    stream = stream,
                                                    resolvingStreamUrl = resolvingStreamUrl,
                                                    onDownload = { mirror -> onDownloadStream(stream, mirror) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadOptionCardV2(
    stream: StreamSource,
    resolvingStreamUrl: String?,
    onDownload: (StreamMirror?) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val displayTitle = stream.getDisplayTitle()
    val isResolvingThisStream = resolvingStreamUrl != null &&
            (resolvingStreamUrl == stream.url || stream.mirrors.any { it.url == resolvingStreamUrl })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16161E))
            .border(1.dp, Color(0xFF1F1F27), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = displayTitle,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stream.quality.ifBlank { "HD" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }

                if (!stream.sizeFormatted.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stream.sizeFormatted ?: "",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                val formatCodec = "${stream.format}${stream.codec?.let { " • $it" } ?: ""}"
                Text(
                    text = "•  $formatCodec",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            if (isResolvingThisStream) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color(0xFFE50914),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "Unpacking direct CDN download link...",
                        color = Color(0xFFE50914),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (stream.mirrors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stream.mirrors.forEach { mirror ->
                        val isMirrorResolving = resolvingStreamUrl == mirror.url
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mirror.name,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onDownload(mirror) },
                                    enabled = !isMirrorResolving,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE50914),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    if (isMirrorResolving) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 1.5.dp,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Download",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(mirror.url))
                                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Link",
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Direct Server File",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onDownload(null) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Download",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(stream.url))
                                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Link",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
