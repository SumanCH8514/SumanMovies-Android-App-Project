package com.sumanonline.sumanmovies.ui.screens.reanime

import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sumanonline.sumanmovies.data.models.ReAnimeAnimeDetails
import com.sumanonline.sumanmovies.data.models.ReAnimeEpisode
import com.sumanonline.sumanmovies.data.models.ReAnimeSeasonRelation
import com.sumanonline.sumanmovies.data.models.ReAnimeServerOption
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.CardDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary
import kotlinx.coroutines.delay

class ChamferedCornerShape(private val cornerSize: Float = 14f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cut = minOf(cornerSize, size.width * 0.45f, size.height * 0.45f)
        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(0f, cut)
            close()
        }
        return Outline.Generic(path)
    }
}

private val TileChamferShape = ChamferedCornerShape(12f)
private val LimeActive = Color(0xFFA3E635)
private val AmberFiller = Color(0xFFF59E0B)
private val DarkTileBg = Color(0xFF161618)
private val DarkTileBorder = Color(0xFF27272A)

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun ReAnimeDetailsScreen(
    viewModel: ReAnimeDetailsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color
    val context = LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BackHandler(enabled = state.isFullscreen) {
        viewModel.setFullscreen(false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (state.isLoading && state.details == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = LimeActive,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else if (state.errorMessage != null && state.details == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.errorMessage ?: "Unknown error",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("Retry", color = LimeActive, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val details = state.details ?: return@Box

            if (state.isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    EmbeddedAnimePlayer(
                        state = state,
                        details = details,
                        accent = accent,
                        isFullscreen = true,
                        topInset = topInset,
                        onBack = { viewModel.setFullscreen(false) },
                        onToggleFullscreen = { viewModel.setFullscreen(false) },
                        onSwitchServer = { viewModel.switchServer(it) },
                        onToggleMode = { viewModel.togglePlayerMode() },
                        onRetryPlayback = { viewModel.playEpisodeNumber(state.currentlyPlayingEpisode) },
                        onSelectNextEpisode = {
                            val next = state.currentlyPlayingEpisode + 1
                            if (next <= state.totalEpisodesCount) {
                                viewModel.playEpisodeNumber(next)
                            }
                        },
                        onSelectPrevEpisode = {
                            val prev = state.currentlyPlayingEpisode - 1
                            if (prev >= 1) {
                                viewModel.playEpisodeNumber(prev)
                            }
                        },
                        onShare = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Watch ${details.displayTitle} on SumanMovies!")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Anime"))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset)
                ) {
                    EmbeddedAnimePlayer(
                        state = state,
                        details = details,
                        accent = accent,
                        isFullscreen = false,
                        topInset = 0.dp,
                        onBack = onBack,
                        onToggleFullscreen = { viewModel.setFullscreen(true) },
                        onSwitchServer = { viewModel.switchServer(it) },
                        onToggleMode = { viewModel.togglePlayerMode() },
                        onRetryPlayback = { viewModel.playEpisodeNumber(state.currentlyPlayingEpisode) },
                        onSelectNextEpisode = {
                            val next = state.currentlyPlayingEpisode + 1
                            if (next <= state.totalEpisodesCount) {
                                viewModel.playEpisodeNumber(next)
                            }
                        },
                        onSelectPrevEpisode = {
                            val prev = state.currentlyPlayingEpisode - 1
                            if (prev >= 1) {
                                viewModel.playEpisodeNumber(prev)
                            }
                        },
                        onShare = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Watch ${details.displayTitle} on SumanMovies!")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Anime"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 60.dp)
                    ) {
                        item {
                            AnimeDetailsHeader(details = details, accent = accent)
                        }

                        if (details.seasons.isNotEmpty()) {
                            item {
                                AnimeSeasonsRow(
                                    seasons = details.seasons,
                                    currentId = state.selectedSeasonId ?: details.id,
                                    accent = LimeActive,
                                    onSeasonSelect = { rel ->
                                        viewModel.selectSeason(rel)
                                    }
                                )
                            }
                        }

                        item {
                            EpisodesHeaderAndControls(
                                state = state,
                                accent = LimeActive,
                                onChunkSelect = { viewModel.selectChunk(it) },
                                onFilterChange = { viewModel.setFilter(it) },
                                onToggleView = { viewModel.toggleViewMode() },
                                onToggleFiller = { viewModel.toggleFillerOnly() }
                            )
                        }

                        item {
                            EpisodeSearchBar(
                                query = state.searchQuery,
                                accent = LimeActive,
                                onQueryChange = { viewModel.setSearchQuery(it) }
                            )
                        }

                        val eps = state.displayedEpisodes
                        if (eps.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (state.searchQuery.isNotBlank()) "No episodes match \"${state.searchQuery}\"" else "No episodes found for this filter",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else if (state.isGridView) {
                            item {
                                EpisodesGridSection(
                                    episodes = eps,
                                    currentPlayingEp = state.currentlyPlayingEpisode,
                                    onEpisodeClick = { ep ->
                                        viewModel.playEpisode(ep)
                                    }
                                )
                            }
                        } else {
                            items(eps, key = { it.episodeNumber }) { ep ->
                                EpisodeListItem(
                                    episode = ep,
                                    isPlaying = state.currentlyPlayingEpisode == ep.episodeNumber,
                                    accent = LimeActive,
                                    onClick = {
                                        viewModel.playEpisode(ep)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EmbeddedAnimePlayer(
    state: ReAnimeDetailsUiState,
    details: ReAnimeAnimeDetails,
    accent: Color,
    isFullscreen: Boolean,
    topInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSwitchServer: (ReAnimeServerOption) -> Unit,
    onToggleMode: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSelectNextEpisode: () -> Unit,
    onSelectPrevEpisode: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mobileUserAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(mobileUserAgent)
            .setDefaultRequestProperties(mapOf("Referer" to "https://flixcloud.cc/"))
    }
    val mediaSourceFactory = remember {
        DefaultMediaSourceFactory(httpDataSourceFactory)
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableLongStateOf(0L) }
    var serverMenuExpanded by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                durationMs = maxOf(0L, exoPlayer.duration)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val streamUrl = state.selectedStream?.url
    LaunchedEffect(streamUrl, state.isWebEmbedMode) {
        if (!state.isWebEmbedMode && !streamUrl.isNullOrBlank()) {
            val mediaItem = MediaItem.fromUri(streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isPlaying, isDraggingSlider) {
        while (isPlaying && !isDraggingSlider) {
            currentPositionMs = maxOf(0L, exoPlayer.currentPosition)
            durationMs = maxOf(0L, exoPlayer.duration)
            bufferedPositionMs = maxOf(0L, exoPlayer.bufferedPosition)
            delay(250L)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3800L)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
    ) {
        if (state.isWebEmbedMode) {
            val embedUrl = state.playbackInfo?.embedUrl
            if (!embedUrl.isNullOrBlank()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = mobileUserAgent
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val u = request?.url?.toString() ?: ""
                                    if (u.startsWith("https://flixcloud.cc") || u.startsWith("https://reanime.wtf")) {
                                        return false
                                    }
                                    return true
                                }
                            }
                            loadUrl(embedUrl, mapOf("Referer" to "https://reanime.wtf/"))
                        }
                    },
                    update = { wv ->
                        if (wv.url != embedUrl) {
                            wv.loadUrl(embedUrl, mapOf("Referer" to "https://reanime.wtf/"))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No embed source available", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isPlaying && currentPositionMs == 0L && !state.isLoadingPlayback && state.playbackError == null && !state.isWebEmbedMode) {
            val previewPoster = state.currentPlayingEpisodeObj?.thumbnailUrl ?: details.bannerUrl ?: details.posterUrl
            if (!previewPoster.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(previewPoster)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        }

        if (state.isLoadingPlayback || (isBuffering && !state.isWebEmbedMode)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = LimeActive,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loading Episode ${state.currentlyPlayingEpisode}...",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (state.playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = AmberFiller,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.playbackError,
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onRetryPlayback,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF27272A))
                        ) {
                            Text("Retry", color = LimeActive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (!state.isWebEmbedMode && !state.playbackInfo?.embedUrl.isNullOrBlank()) {
                            TextButton(
                                onClick = onToggleMode,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(LimeActive.copy(alpha = 0.2f))
                            ) {
                                Text("Try Web Embed", color = LimeActive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topInset + 6.dp, start = 8.dp, end = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            val epObj = state.currentPlayingEpisodeObj
                            Text(
                                text = "EP ${state.currentlyPlayingEpisode} • ${epObj?.title ?: details.displayTitle}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = details.displayTitle,
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box {
                            val curServer = state.selectedServer?.serverName ?: "HD-1"
                            val curType = state.selectedServer?.dataType?.uppercase() ?: "SUB"
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .border(BorderStroke(1.dp, Color(0xFF3F3F46)), RoundedCornerShape(6.dp))
                                    .clickable { serverMenuExpanded = true }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$curServer [$curType]",
                                    color = LimeActive,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = LimeActive,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = serverMenuExpanded,
                                onDismissRequest = { serverMenuExpanded = false },
                                modifier = Modifier.background(CardDark)
                            ) {
                                val srvList = state.playbackInfo?.servers ?: emptyList()
                                if (srvList.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No other servers", color = TextMuted, fontSize = 12.sp) },
                                        onClick = { serverMenuExpanded = false }
                                    )
                                } else {
                                    srvList.forEach { srv ->
                                        val isSel = srv.accessId == state.selectedServer?.accessId && srv.dataType == state.selectedServer?.dataType
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${srv.serverName} • ${srv.dataType.uppercase()}",
                                                    color = if (isSel) LimeActive else TextPrimary,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            onClick = {
                                                onSwitchServer(srv)
                                                serverMenuExpanded = false
                                            },
                                            colors = MenuDefaults.itemColors()
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = onToggleMode,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (state.isWebEmbedMode) Color(0xFF3F3F46) else Color.Black.copy(alpha = 0.6f))
                                .border(BorderStroke(1.dp, Color(0xFF3F3F46)), RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = if (state.isWebEmbedMode) Icons.Default.Public else Icons.Default.Bolt,
                                contentDescription = "Toggle Player Mode",
                                tint = if (state.isWebEmbedMode) LimeActive else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (!isFullscreen) {
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                if (!state.isWebEmbedMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = maxOf(0L, exoPlayer.currentPosition - 10000L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0L)
                                    }
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(LimeActive)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val target = minOf(exoPlayer.duration, exoPlayer.currentPosition + 10000L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (!state.isWebEmbedMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeMs = if (isDraggingSlider) sliderDragPosition else currentPositionMs
                            Text(
                                text = formatTime(activeMs),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            val progressFraction = if (durationMs > 0L) {
                                (activeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Slider(
                                value = progressFraction,
                                onValueChange = { frac ->
                                    isDraggingSlider = true
                                    sliderDragPosition = (frac * durationMs).toLong()
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(sliderDragPosition)
                                    currentPositionMs = sliderDragPosition
                                    isDraggingSlider = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = LimeActive,
                                    activeTrackColor = LimeActive,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            )

                            Text(
                                text = formatTime(durationMs),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onSelectPrevEpisode,
                                enabled = state.currentlyPlayingEpisode > 1,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Episode",
                                    tint = if (state.currentlyPlayingEpisode > 1) Color.White else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onSelectNextEpisode,
                                enabled = state.currentlyPlayingEpisode < state.totalEpisodesCount,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Episode",
                                    tint = if (state.currentlyPlayingEpisode < state.totalEpisodesCount) Color.White else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = "EP ${state.currentlyPlayingEpisode} / ${state.totalEpisodesCount}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeDetailsHeader(
    details: ReAnimeAnimeDetails,
    accent: Color
) {
    var expandedOverview by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (!details.posterUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .width(78.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, CardElevated), RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(details.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = details.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details.displayTitle,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (details.displaySubtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = details.displaySubtitle,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (details.score != null && details.score > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(LimeActive.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "★ ${String.format(java.util.Locale.US, "%.1f", details.score)}",
                                color = LimeActive,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!details.year.isNullOrBlank()) {
                        Text(
                            text = details.year,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                    }

                    Text(
                        text = details.format,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(text = "•", color = TextMuted, fontSize = 10.sp)

                    Text(
                        text = "${details.episodesTotal} Eps",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (details.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        items(details.genres.take(5)) { g ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CardDark)
                                    .border(BorderStroke(1.dp, CardElevated), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = g,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!details.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = details.description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = if (expandedOverview) 20 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expandedOverview = !expandedOverview }
            )
        }
    }
}

@Composable
private fun AnimeSeasonsRow(
    seasons: List<ReAnimeSeasonRelation>,
    currentId: String,
    accent: Color,
    onSeasonSelect: (ReAnimeSeasonRelation) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "Seasons & Relations",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(seasons) { rel ->
                val isSelected = rel.id == currentId || (rel.anilistId != null && currentId.contains(rel.anilistId.toString()))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.22f) else CardDark)
                        .border(
                            BorderStroke(1.dp, if (isSelected) accent else CardElevated),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSeasonSelect(rel) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = rel.title,
                            color = if (isSelected) accent else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rel.relationType.replace("_", " "),
                                color = if (isSelected) accent.copy(alpha = 0.8f) else TextMuted,
                                fontSize = 10.sp
                            )
                            if (!rel.format.isNullOrBlank()) {
                                Text(
                                    text = "• ${rel.format}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            if (rel.episodesCount != null && rel.episodesCount > 0) {
                                Text(
                                    text = "• ${rel.episodesCount} eps",
                                    color = TextMuted,
                                    fontSize = 10.sp
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
private fun EpisodesHeaderAndControls(
    state: ReAnimeDetailsUiState,
    accent: Color,
    onChunkSelect: (Int) -> Unit,
    onFilterChange: (ReAnimeEpisodeFilter) -> Unit,
    onToggleView: () -> Unit,
    onToggleFiller: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

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
                text = "Episodes",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF27272A))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = state.totalEpisodesCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.chunkOptions.size > 1) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF27272A))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.currentChunkLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select range",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(CardDark)
                    ) {
                        state.chunkOptions.forEach { (idx, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (idx == state.selectedChunkIndex) accent else TextPrimary,
                                        fontWeight = if (idx == state.selectedChunkIndex) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    onChunkSelect(idx)
                                    dropdownExpanded = false
                                },
                                colors = MenuDefaults.itemColors()
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.filterType == ReAnimeEpisodeFilter.ALL) Color(0xFF3F3F46) else Color.Transparent)
                    .clickable { onFilterChange(ReAnimeEpisodeFilter.ALL) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ALL",
                    color = if (state.filterType == ReAnimeEpisodeFilter.ALL) Color.White else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = { onFilterChange(if (state.filterType == ReAnimeEpisodeFilter.SUB) ReAnimeEpisodeFilter.ALL else ReAnimeEpisodeFilter.SUB) },
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.filterType == ReAnimeEpisodeFilter.SUB) Color(0xFF3F3F46) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = "Subtitles",
                    tint = if (state.filterType == ReAnimeEpisodeFilter.SUB) accent else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { onFilterChange(if (state.filterType == ReAnimeEpisodeFilter.DUB) ReAnimeEpisodeFilter.ALL else ReAnimeEpisodeFilter.DUB) },
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.filterType == ReAnimeEpisodeFilter.DUB) Color(0xFF3F3F46) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Dubbed",
                    tint = if (state.filterType == ReAnimeEpisodeFilter.DUB) accent else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onToggleView,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (state.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onToggleFiller,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.showFillerOnly) AmberFiller.copy(alpha = 0.2f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = if (state.showFillerOnly) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Filler Only",
                    tint = if (state.showFillerOnly) AmberFiller else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EpisodeSearchBar(
    query: String,
    accent: Color,
    onQueryChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141416))
            .border(BorderStroke(1.dp, Color(0xFF27272A)), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                maxLines = 1,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search episodes...",
                            color = TextMuted,
                            fontSize = 12.5.sp
                        )
                    }
                    inner()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EpisodesGridSection(
    episodes: List<ReAnimeEpisode>,
    currentPlayingEp: Int?,
    onEpisodeClick: (ReAnimeEpisode) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 36.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(((episodes.size / 10 + 2) * 44).coerceAtMost(520).coerceAtLeast(180).dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(episodes, key = { it.episodeNumber }) { ep ->
            val isPlaying = currentPlayingEp == ep.episodeNumber
            val isFiller = ep.isFiller

            val bgColor = when {
                isPlaying -> LimeActive
                isFiller -> DarkTileBg
                else -> DarkTileBg
            }

            val borderColor = when {
                isPlaying -> LimeActive
                isFiller -> AmberFiller
                else -> DarkTileBorder
            }

            val textColor = when {
                isPlaying -> Color.Black
                isFiller -> AmberFiller
                else -> Color(0xFFD4D4D8)
            }

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(TileChamferShape)
                    .background(bgColor)
                    .border(BorderStroke(1.dp, borderColor), TileChamferShape)
                    .clickable { onEpisodeClick(ep) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ep.episodeNumber.toString(),
                    color = textColor,
                    fontSize = 11.5.sp,
                    fontWeight = if (isPlaying || isFiller) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: ReAnimeEpisode,
    isPlaying: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPlaying) accent.copy(alpha = 0.12f) else CardDark)
            .border(
                BorderStroke(1.dp, if (isPlaying) accent else CardElevated),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(90.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
        ) {
            if (!episode.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(episode.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (isPlaying) LimeActive else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "EP ${episode.episodeNumber}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNumber}. ${episode.title}",
                color = if (isPlaying) accent else TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (episode.isFiller) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(AmberFiller.copy(alpha = 0.2f))
                            .border(BorderStroke(0.5.dp, AmberFiller), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "FILLER",
                            color = AmberFiller,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (episode.isSubbed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF27272A))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "SUB",
                            color = TextMuted,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (episode.isDubbed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF27272A))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "DUB",
                            color = TextMuted,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (!episode.airedDate.isNullOrBlank()) {
                    Text(
                        text = episode.airedDate.take(10),
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}
