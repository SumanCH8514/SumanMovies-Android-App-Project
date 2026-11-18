package com.sumanonline.sumanmovies.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.getDisplayCategory
import com.sumanonline.sumanmovies.data.models.getDisplayQuality
import com.sumanonline.sumanmovies.data.models.getDisplayTitle
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreenModern(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onEnterPip: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onShare: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()
    val context = LocalContext.current

    val goldenAccent = Color(0xFFF59E0B)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeedDialogOpen by remember { mutableStateOf(false) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(resizeModeIndex) {
        playerViewRef?.resizeMode = resizeModes[resizeModeIndex % resizeModes.size]
    }

    LaunchedEffect(uiState.showControls, playbackState.isPlaying, uiState.isStreamsDrawerOpen, uiState.isAudioTracksModalOpen, uiState.isSubtitlesModalOpen, isSpeedDialogOpen) {
        if (uiState.showControls && playbackState.isPlaying &&
            !uiState.isStreamsDrawerOpen &&
            !uiState.isAudioTracksModalOpen &&
            !uiState.isSubtitlesModalOpen &&
            !isSpeedDialogOpen
        ) {
            delay(4500L)
            viewModel.setControlsVisibility(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                viewModel.toggleControls()
            }
    ) {
        val activeStream = uiState.currentStream
        val isEmbedStream = activeStream?.format.equals("EMBED", ignoreCase = true) ||
                activeStream?.url?.contains("playdrive.vip") == true

        if (isEmbedStream && activeStream != null) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = WebViewClient()
                        loadUrl(activeStream.url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.playerManager.getPlayer()
                        useController = false
                        resizeMode = resizeModes[resizeModeIndex % resizeModes.size]
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (playbackState.isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = goldenAccent)
            }
        }

        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = (statusBarInset + 8.dp).coerceAtLeast(16.dp))
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            val titleText = if (uiState.mediaType.name == "SERIES") {
                                "${uiState.mediaTitle} · S${String.format(java.util.Locale.US, "%02d", uiState.seasonNumber)} E${String.format(java.util.Locale.US, "%02d", uiState.episodeNumber)}"
                            } else {
                                uiState.mediaTitle.ifBlank { "Playing Video" }
                            }

                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            val yearText = uiState.releaseYear ?: "2026"
                            val detectedAudioLanguages = uiState.availableAudioTracks
                                .mapNotNull { it.displayName.ifBlank { null } ?: it.language?.uppercase()?.ifBlank { null } }
                                .distinct()

                            val languagesText = if (detectedAudioLanguages.isNotEmpty()) {
                                detectedAudioLanguages.take(4).joinToString(", ")
                            } else {
                                "English, Hindi, Multi-Audio"
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$yearText · ",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Text(
                                    text = languagesText,
                                    color = goldenAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), CircleShape)
                            .clickable {
                                onShare(uiState.mediaTitle, uiState.mediaId)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable {
                            viewModel.playerManager.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (navBarInset + 8.dp).coerceAtLeast(12.dp))
                        .padding(horizontal = 24.dp)
                ) {
                    val durationMs = playbackState.durationMs.coerceAtLeast(0L)
                    val currentPosMs = if (isDraggingSlider) {
                        (sliderPosition * durationMs).toLong()
                    } else {
                        playbackState.currentPositionMs.coerceAtLeast(0L)
                    }

                    val progressFraction = if (durationMs > 0L) {
                        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val hasHours = durationMs >= 3600_000L

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatModernTime(currentPosMs, showHours = hasHours),
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = if (isDraggingSlider) sliderPosition else progressFraction,
                            onValueChange = { newFrac ->
                                isDraggingSlider = true
                                sliderPosition = newFrac
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                val targetMs = (sliderPosition * durationMs).toLong()
                                viewModel.playerManager.seekTo(targetMs)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = goldenAccent,
                                activeTrackColor = goldenAccent,
                                inactiveTrackColor = Color(0xFF374151)
                            )
                        )

                        Text(
                            text = formatModernTime(durationMs, showHours = hasHours),
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.playerManager.togglePlayPause() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isMuted = viewModel.playerManager.toggleMute()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Volume",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            IconButton(
                                onClick = onEnterPip,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Picture in Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            val speedText = if (playbackState.playbackSpeed == 1.0f) {
                                "1x"
                            } else {
                                "${playbackState.playbackSpeed}x"
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { isSpeedDialogOpen = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = speedText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val audioLangCode = uiState.selectedAudioTrack?.language?.uppercase()?.take(3)
                                ?: uiState.selectedAudioTrack?.displayName?.take(3)?.uppercase()
                                ?: "HIN"

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.toggleAudioTracksModal() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Audio Track",
                                    tint = goldenAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = audioLangCode,
                                    color = goldenAccent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val subLangCode = if (!uiState.isSubtitlesEnabled || uiState.selectedSubtitleTrack == null) {
                                "OFF"
                            } else {
                                uiState.selectedSubtitleTrack?.language?.uppercase()?.take(3)
                                    ?: uiState.selectedSubtitleTrack?.displayName?.take(3)?.uppercase()
                                    ?: "ENG"
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.toggleSubtitlesModal() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = "Subtitles",
                                    tint = goldenAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = subLangCode,
                                    color = goldenAccent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val qualityText = uiState.currentStream?.quality?.let { q ->
                                if (q.isNotBlank()) "Auto · $q" else "Auto · 1080p"
                            } ?: "Auto · 1080p"

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.toggleStreamsDrawer() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = "Quality",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = qualityText,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isStreamsDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.toggleStreamsDrawer() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(360.dp)
                        .align(Alignment.CenterEnd)
                        .background(Color(0xFF141416))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quality & Servers",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.toggleStreamsDrawer() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "ACTIVE SERVER",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StreamingServer.entries.forEach { server ->
                                val isCurrent = (uiState.selectedServer == server)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) goldenAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isCurrent) goldenAccent else Color.White.copy(alpha = 0.1f)
                                            ),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.switchServerInPlayer(server) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = server.displayName,
                                        color = if (isCurrent) Color.White else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "AVAILABLE QUALITIES",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.availableStreams) { stream ->
                                val isSelected = uiState.currentStream?.url == stream.url
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) goldenAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) goldenAccent else Color.White.copy(alpha = 0.08f)
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            viewModel.switchStream(stream)
                                            viewModel.toggleStreamsDrawer()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = stream.quality.ifBlank { "Auto / Original" },
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${stream.format} • ${stream.provider}",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = goldenAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isAudioTracksModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.toggleAudioTracksModal() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(360.dp)
                        .align(Alignment.CenterEnd)
                        .background(Color(0xFF141416))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio Tracks",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.toggleAudioTracksModal() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (uiState.availableAudioTracks.isEmpty()) {
                            Text(
                                text = "Single standard audio track in stream.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.availableAudioTracks) { track ->
                                    val isSelected = uiState.selectedAudioTrack?.id == track.id || track.isSelected
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) goldenAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isSelected) goldenAccent else Color.White.copy(alpha = 0.08f)
                                                ),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                viewModel.selectAudioTrack(track)
                                                viewModel.toggleAudioTracksModal()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = track.displayName.ifBlank { track.label },
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (track.language != null) {
                                                Text(
                                                    text = track.language.uppercase(),
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = goldenAccent,
                                                modifier = Modifier.size(18.dp)
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

        if (uiState.isSubtitlesModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.toggleSubtitlesModal() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(360.dp)
                        .align(Alignment.CenterEnd)
                        .background(Color(0xFF141416))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subtitles",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.toggleSubtitlesModal() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!uiState.isSubtitlesEnabled) goldenAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (!uiState.isSubtitlesEnabled) goldenAccent else Color.White.copy(alpha = 0.08f)
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.disableSubtitles()
                                    viewModel.toggleSubtitlesModal()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Off (Subtitles Disabled)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!uiState.isSubtitlesEnabled) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = goldenAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.availableSubtitleTracks) { track ->
                                val isSelected = uiState.isSubtitlesEnabled && (uiState.selectedSubtitleTrack?.id == track.id || track.isSelected)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) goldenAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) goldenAccent else Color.White.copy(alpha = 0.08f)
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            viewModel.selectSubtitleTrack(track)
                                            viewModel.toggleSubtitlesModal()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = track.displayName.ifBlank { track.label },
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (track.language != null) {
                                            Text(
                                                text = track.language.uppercase(),
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = goldenAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isSpeedDialogOpen) {
            AlertDialog(
                onDismissRequest = { isSpeedDialogOpen = false },
                title = {
                    Text(
                        text = "Playback Speed",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val isSelected = playbackState.playbackSpeed == speed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) goldenAccent.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        viewModel.playerManager.setSpeed(speed)
                                        isSpeedDialogOpen = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (speed == 1.0f) "Normal (1x)" else "${speed}x",
                                    color = if (isSelected) goldenAccent else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = goldenAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isSpeedDialogOpen = false }) {
                        Text("Close", color = goldenAccent)
                    }
                },
                containerColor = Color(0xFF18181B),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

private fun formatModernTime(ms: Long, showHours: Boolean = false): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hr = totalSec / 3600
    return if (hr > 0 || showHours) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hr, min, sec)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", min, sec)
    }
}
