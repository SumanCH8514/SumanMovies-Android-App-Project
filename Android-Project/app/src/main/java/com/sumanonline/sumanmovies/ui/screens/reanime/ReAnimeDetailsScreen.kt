@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sumanonline.sumanmovies.ui.screens.reanime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
import com.sumanonline.sumanmovies.data.models.StreamSource
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
private val ScrubberBlue = Color(0xFF0284C7)
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
                        onBack = { viewModel.setFullscreen(false) },
                        onToggleFullscreen = { viewModel.setFullscreen(false) },
                        onSwitchServer = { viewModel.switchServer(it) },
                        onRetryPlayback = { viewModel.playEpisodeNumber(state.currentlyPlayingEpisode) },
                        onDownloadEpisode = { ep, st ->
                            viewModel.startDownload(details, ep, st)
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
                    BreadcrumbNavHeader(
                        animeTitle = details.displayTitle,
                        episodeNumber = state.currentlyPlayingEpisode,
                        onBack = onBack
                    )

                    EmbeddedAnimePlayer(
                        state = state,
                        details = details,
                        accent = accent,
                        isFullscreen = false,
                        onBack = onBack,
                        onToggleFullscreen = { viewModel.setFullscreen(true) },
                        onSwitchServer = { viewModel.switchServer(it) },
                        onRetryPlayback = { viewModel.playEpisodeNumber(state.currentlyPlayingEpisode) },
                        onDownloadEpisode = { ep, st ->
                            viewModel.startDownload(details, ep, st)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, Color(0xFF27272A)), RoundedCornerShape(14.dp))
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

@Composable
private fun BreadcrumbNavHeader(
    animeTitle: String,
    episodeNumber: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Home",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onBack() }
        )
        Text(
            text = "  ›  ",
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = animeTitle.uppercase(),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = "  ›  ",
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = "Episode $episodeNumber",
            color = LimeActive,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EmbeddedAnimePlayer(
    state: ReAnimeDetailsUiState,
    details: ReAnimeAnimeDetails,
    accent: Color,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSwitchServer: (ReAnimeServerOption) -> Unit,
    onRetryPlayback: () -> Unit,
    onDownloadEpisode: (ReAnimeEpisode, StreamSource) -> Unit,
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
    var isMuted by remember { mutableStateOf(false) }
    var isSubtitlesEnabled by remember { mutableStateOf(true) }
    var selectedSubtitleTrackId by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }

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

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isSubtitlesEnabled) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitlesEnabled)
            .build()
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
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
                    },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width * 0.45f) {
                            val target = maxOf(0L, exoPlayer.currentPosition - 10000L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        } else if (offset.x > size.width * 0.55f) {
                            val target = minOf(exoPlayer.duration, exoPlayer.currentPosition + 10000L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        }
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
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }

        if (state.isLoadingPlayback || (isBuffering && !state.isWebEmbedMode)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
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
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.playbackError,
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onRetryPlayback,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF27272A))
                    ) {
                        Text("Retry", color = LimeActive, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFullscreen) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = details.displayTitle,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Episode ${state.currentlyPlayingEpisode}",
                                    color = LimeActive,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { showSubtitleSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSubtitlesEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Subtitles",
                                tint = if (isSubtitlesEnabled) LimeActive else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { showDownloadSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { showSettingsSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                ) {
                    val activeMs = if (isDraggingSlider) sliderDragPosition else currentPositionMs
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
                            thumbColor = ScrubberBlue,
                            activeTrackColor = ScrubberBlue,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .padding(horizontal = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(23.dp)
                                )
                            }

                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            Text(
                                text = "${formatTime(activeMs)} / ${formatTime(durationMs)}",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val target = maxOf(0L, exoPlayer.currentPosition - 10000L)
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Replay 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val target = minOf(exoPlayer.duration, exoPlayer.currentPosition + 10000L)
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubtitleSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSubtitleSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141418),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3F3F46))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtitles",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showSubtitleSheet = false },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E24))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Display Subtitles",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isSubtitlesEnabled) "Captions visible" else "Captions hidden",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                    }

                    Switch(
                        checked = isSubtitlesEnabled,
                        onCheckedChange = { isSubtitlesEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = LimeActive,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF27272A)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AVAILABLE SUBTITLE TRACKS",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val textTrackOptions = remember(exoPlayer.currentTracks) {
                    val list = mutableListOf<Triple<Tracks.Group, Int, String>>()
                    val groups = exoPlayer.currentTracks.groups
                    for (g in groups) {
                        if (g.type == C.TRACK_TYPE_TEXT) {
                            for (i in 0 until g.length) {
                                val fmt = g.getTrackFormat(i)
                                val lang = fmt.language ?: ""
                                val label = fmt.label ?: if (lang.isNotBlank()) lang.uppercase() else "English Track ${list.size + 1}"
                                list.add(Triple(g, i, label))
                            }
                        }
                    }
                    list
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isSubtitlesEnabled) LimeActive.copy(alpha = 0.14f) else Color(0xFF1E1E24))
                        .border(
                            BorderStroke(1.dp, if (!isSubtitlesEnabled) LimeActive else Color(0xFF2A2A32)),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            isSubtitlesEnabled = false
                            selectedSubtitleTrackId = "off"
                            showSubtitleSheet = false
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Off",
                        color = if (!isSubtitlesEnabled) LimeActive else Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = if (!isSubtitlesEnabled) FontWeight.Bold else FontWeight.Medium
                    )
                    if (!isSubtitlesEnabled) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = LimeActive,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (textTrackOptions.isNotEmpty()) {
                    textTrackOptions.forEach { (group, trackIdx, trackName) ->
                        val isTrackActive = isSubtitlesEnabled && (selectedSubtitleTrackId == "$trackName-$trackIdx" || (selectedSubtitleTrackId == null && trackIdx == 0))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTrackActive) LimeActive.copy(alpha = 0.14f) else Color(0xFF1E1E24))
                                .border(
                                    BorderStroke(1.dp, if (isTrackActive) LimeActive else Color(0xFF2A2A32)),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    isSubtitlesEnabled = true
                                    selectedSubtitleTrackId = "$trackName-$trackIdx"
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setOverrideForType(
                                            TrackSelectionOverride(group.mediaTrackGroup, trackIdx)
                                        )
                                        .build()
                                    showSubtitleSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = trackName,
                                color = if (isTrackActive) LimeActive else Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = if (isTrackActive) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isTrackActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = LimeActive,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSubtitlesEnabled) LimeActive.copy(alpha = 0.14f) else Color(0xFF1E1E24))
                            .border(
                                BorderStroke(1.dp, if (isSubtitlesEnabled) LimeActive else Color(0xFF2A2A32)),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                isSubtitlesEnabled = true
                                showSubtitleSheet = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "English (Embedded Subtitle)",
                            color = if (isSubtitlesEnabled) LimeActive else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSubtitlesEnabled) FontWeight.Bold else FontWeight.Medium
                        )
                        if (isSubtitlesEnabled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = LimeActive,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141418),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3F3F46))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playback Settings",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showSettingsSheet = false },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AUDIO & STREAMING SERVER",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val srvList = state.playbackInfo?.servers ?: emptyList()
                if (srvList.isNotEmpty()) {
                    srvList.forEach { srv ->
                        val isSel = (srv.serverName == state.selectedServer?.serverName) &&
                                srv.dataType.equals(state.selectedServer?.dataType, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) LimeActive.copy(alpha = 0.14f) else Color(0xFF1E1E24))
                                .border(
                                    BorderStroke(1.dp, if (isSel) LimeActive else Color(0xFF2A2A32)),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onSwitchServer(srv)
                                    showSettingsSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = srv.serverName,
                                    color = if (isSel) LimeActive else Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "•",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (srv.dataType.equals("dub", ignoreCase = true)) "English Dub" else "Japanese Sub",
                                    color = if (isSel) LimeActive.copy(alpha = 0.9f) else TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (isSel) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = LimeActive,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else {
                    Text("No alternate servers available", color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "PLAYBACK SPEED",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    speeds.forEach { sp ->
                        val isSel = (playbackSpeed == sp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) LimeActive else Color(0xFF1E1E24))
                                .border(
                                    BorderStroke(1.dp, if (isSel) LimeActive else Color(0xFF2A2A32)),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    playbackSpeed = sp
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (sp == 1.0f) "1x" else "${sp}x",
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDownloadSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141418),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3F3F46))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Download Episode ${state.currentlyPlayingEpisode}",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { showDownloadSheet = false },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details.displayTitle,
                    color = TextMuted,
                    fontSize = 12.5.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                val currentEp = state.currentPlayingEpisodeObj ?: ReAnimeEpisode(
                    episodeNumber = state.currentlyPlayingEpisode,
                    title = "Episode ${state.currentlyPlayingEpisode}"
                )

                val availableStreams = state.playbackInfo?.directStreams ?: emptyList()

                if (availableStreams.isNotEmpty()) {
                    Text(
                        text = "SELECT DOWNLOAD QUALITY",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    availableStreams.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E24))
                                .border(BorderStroke(1.dp, Color(0xFF2A2A32)), RoundedCornerShape(10.dp))
                                .clickable {
                                    onDownloadEpisode(currentEp, st)
                                    Toast.makeText(context, "Added EP ${currentEp.episodeNumber} to Library Downloads", Toast.LENGTH_SHORT).show()
                                    showDownloadSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = st.label.ifBlank { "${st.quality} (${st.format})" },
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = st.audioLangs ?: "Lossless direct stream",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = LimeActive,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else if (state.selectedStream != null) {
                    val st = state.selectedStream
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E24))
                            .border(BorderStroke(1.dp, Color(0xFF2A2A32)), RoundedCornerShape(10.dp))
                            .clickable {
                                onDownloadEpisode(currentEp, st)
                                Toast.makeText(context, "Added EP ${currentEp.episodeNumber} to Library Downloads", Toast.LENGTH_SHORT).show()
                                showDownloadSheet = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${st.quality} (${st.format})",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Lossless direct stream",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = LimeActive,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Direct download stream not available for this episode.",
                            color = TextMuted,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Downloads can be tracked, paused, and played offline in Library > Downloads.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
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
