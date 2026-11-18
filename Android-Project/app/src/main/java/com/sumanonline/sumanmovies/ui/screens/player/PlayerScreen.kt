package com.sumanonline.sumanmovies.ui.screens.player

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.getDisplayCategory
import com.sumanonline.sumanmovies.data.models.getDisplayQuality
import com.sumanonline.sumanmovies.data.models.getDisplayTitle
import com.sumanonline.sumanmovies.ui.screens.customization.PlayerUiStyle
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    playerUiStyle: PlayerUiStyle = PlayerUiStyle.MODERN,
    onEnterPip: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onShare: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (playerUiStyle == PlayerUiStyle.MODERN) {
        PlayerScreenModern(
            viewModel = viewModel,
            onBack = onBack,
            onEnterPip = onEnterPip,
            onToggleFullscreen = onToggleFullscreen,
            onShare = onShare,
            modifier = modifier
        )
    } else {
        PlayerScreenClassic(
            viewModel = viewModel,
            onBack = onBack,
            modifier = modifier
        )
    }
}

@Composable
fun PlayerScreenClassic(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()
    val accent = LocalAppAccent.current.color
    val context = LocalContext.current

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
                factory = { context ->
                    PlayerView(context).apply {
                        player = viewModel.playerManager.getPlayer()
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
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
                CircularProgressIndicator(color = accent)
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
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = uiState.mediaTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                uiState.serverDisplayName?.let { server ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accent.copy(alpha = 0.3f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Server: $server",
                                            color = accent,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                uiState.currentStream?.let { s ->
                                    val dispQuality = s.getDisplayQuality(uiState.selectedServer)
                                    Text(
                                        text = "$dispQuality • ${s.format}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val s = uiState.currentStream
                                if (s != null) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        val mime = if (s.format.equals("MKV", ignoreCase = true) || s.url.contains(".mkv")) {
                                            "video/x-matroska"
                                        } else {
                                            "video/mp4"
                                        }
                                        setDataAndType(Uri.parse(s.url), mime)
                                        putExtra("title", uiState.mediaTitle)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(intent, "Play with (VLC / Nova)").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(chooser)
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Launch,
                                contentDescription = "Play in VLC or Nova",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.toggleAudioTracksModal() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isAudioTracksModalOpen) accent.copy(alpha = 0.35f)
                                    else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "Audio Tracks",
                                tint = if (uiState.availableAudioTracks.size > 1 || uiState.isAudioTracksModalOpen) accent else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.toggleSubtitlesModal() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isSubtitlesModalOpen) accent.copy(alpha = 0.35f)
                                    else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Subtitles",
                                tint = if (uiState.isSubtitlesEnabled || uiState.isSubtitlesModalOpen) accent else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.toggleStreamsDrawer() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isStreamsDrawerOpen) accent.copy(alpha = 0.35f)
                                    else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoSettings,
                                contentDescription = "Quality",
                                tint = if (uiState.isStreamsDrawerOpen) accent else Color.White
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.playerManager.skipBackward(10) },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Skip -10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playerManager.togglePlayPause() },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(accent)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.playerManager.skipForward(10) },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Skip +10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    val pos = playbackState.currentPositionMs.toFloat()
                    val dur = playbackState.durationMs.coerceAtLeast(1L).toFloat()

                    Slider(
                        value = pos.coerceIn(0f, dur),
                        onValueChange = { newPos -> viewModel.playerManager.seekTo(newPos.toLong()) },
                        valueRange = 0f..dur,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(playbackState.currentPositionMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(playbackState.durationMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (uiState.isStreamsDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.toggleStreamsDrawer() },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 370.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF141720))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {  }
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Streaming Sources",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${uiState.availableStreams.size} qualities available",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleStreamsDrawer() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
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

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(StreamingServer.entries) { server ->
                                val isCurrent = (uiState.selectedServer == server)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            border = BorderStroke(
                                                width = if (isCurrent) 1.5.dp else 1.dp,
                                                color = if (isCurrent) accent else Color.White.copy(alpha = 0.1f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.switchServerInPlayer(server) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(accent)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                        Text(
                                            text = server.displayName,
                                            color = if (isCurrent) Color.White else TextSecondary,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.isLoadingStreams) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fetching qualities from ${uiState.selectedServer.displayName}...",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (uiState.availableStreams.isEmpty() && !uiState.isLoadingStreams) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No streams found for this server.\nTry switching to another server above.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val isMovieBoxServer = uiState.selectedServer == StreamingServer.MOVIEBOX
                            val qualityHierarchy = if (isMovieBoxServer) {
                                listOf("FHD", "HD", "SD", "Other")
                            } else {
                                listOf("2160p / 4K", "1080p", "720p", "480p", "Other")
                            }
                            val groupedStreams = remember(uiState.availableStreams, uiState.selectedServer) {
                                uiState.availableStreams.groupBy { stream ->
                                    stream.getDisplayCategory(uiState.selectedServer)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                qualityHierarchy.forEach { category ->
                                    val streamsInCategory = groupedStreams[category]
                                    if (!streamsInCategory.isNullOrEmpty()) {
                                        item(key = "header_$category") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                            ) {
                                                val badgeColor = when {
                                                    category == "FHD" || category.contains("1080") -> Color(0xFF2563EB)
                                                    category == "HD" || category.contains("720") -> Color(0xFF0D9488)
                                                    category == "SD" || category.contains("480") || category.contains("360") -> Color(0xFF64748B)
                                                    category.contains("4K") || category.contains("2160") -> Color(0xFFFFB300)
                                                    else -> Color(0xFF64748B)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(badgeColor)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = category,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${streamsInCategory.size})",
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        itemsIndexed(
                                            items = streamsInCategory,
                                            key = { index, s -> "${category}_${s.provider}_${s.format}_${s.quality}_${s.url}_$index" }
                                        ) { _, s ->
                                            val isSelected = uiState.currentStream?.url == s.url && uiState.currentStream?.quality == s.quality
                                            val isResolving = uiState.resolvingStreamUrl == s.url
                                            val streamKey = "${s.url}#${s.quality}#${s.format}"
                                            val isFailed = uiState.failedStreamUrls.contains(streamKey) || uiState.failedStreamUrls.contains(s.url)

                                            val cardBorderColor = when {
                                                isSelected -> accent
                                                isFailed -> Color(0xFFE53935).copy(alpha = 0.5f)
                                                else -> Color.White.copy(alpha = 0.08f)
                                            }
                                            val cardBgColor = when {
                                                isSelected -> accent.copy(alpha = 0.16f)
                                                isFailed -> Color(0xFFE53935).copy(alpha = 0.05f)
                                                else -> Color.White.copy(alpha = 0.03f)
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(cardBgColor)
                                                    .border(BorderStroke(if (isSelected) 1.5.dp else 1.dp, cardBorderColor), RoundedCornerShape(10.dp))
                                                    .clickable { viewModel.switchStream(s) }
                                                    .padding(12.dp)
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {

                                                        val dispQuality = s.getDisplayQuality(uiState.selectedServer)
                                                        val is4K = s.quality.contains("4K", ignoreCase = true) || s.quality.contains("2160", ignoreCase = true)
                                                        val isFHD = dispQuality == "FHD" || s.quality.contains("1080", ignoreCase = true)
                                                        val isHD = dispQuality == "HD" || s.quality.contains("720", ignoreCase = true)
                                                        val isSD = dispQuality == "SD" || s.quality.contains("480", ignoreCase = true) || s.quality.contains("360", ignoreCase = true)

                                                        val (pillBg, pillTextColor) = when {
                                                            is4K -> Pair(Color(0xFFFFB300), Color.Black)
                                                            isFHD -> Pair(Color(0xFF2563EB), Color.White)
                                                            isHD -> Pair(Color(0xFF0D9488), Color.White)
                                                            isSD -> Pair(Color(0xFF475569), Color.White)
                                                            else -> Pair(Color(0xFF475569), Color.White)
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(pillBg)
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = dispQuality,
                                                                color = pillTextColor,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        if (isSelected) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(6.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFF4CAF50))
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "PLAYING",
                                                                    color = accent,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.sp
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = "Playing",
                                                                    tint = accent,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                            }
                                                        } else if (isResolving) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                CircularProgressIndicator(
                                                                    color = accent,
                                                                    strokeWidth = 1.5.dp,
                                                                    modifier = Modifier.size(11.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "Connecting...",
                                                                    color = TextMuted,
                                                                    fontSize = 10.sp
                                                                )
                                                            }
                                                        } else if (isFailed) {
                                                            Text(
                                                                text = "FAILED",
                                                                color = Color(0xFFEF4444),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Text(
                                                        text = s.getDisplayTitle(uiState.selectedServer),
                                                        color = if (isSelected) Color.White else TextPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        fontSize = 12.5.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    val subParts = listOfNotNull(
                                                        s.format.takeIf { it.isNotBlank() },
                                                        s.codec?.takeIf { it.isNotBlank() },
                                                        s.sizeFormatted?.takeIf { it.isNotBlank() },
                                                        s.audioLangs?.takeIf { it.isNotBlank() },
                                                        s.mirrorName?.let { "Mirror: $it" }
                                                    )
                                                    if (subParts.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = subParts.joinToString(" • "),
                                                            color = TextMuted,
                                                            fontSize = 10.5.sp,
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
                    }
                }
            }
        }

        if (uiState.isAudioTracksModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.toggleAudioTracksModal() },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 360.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF141720))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {  }
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Audio Tracks",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.availableAudioTracks.isNotEmpty()) {
                                        "${uiState.availableAudioTracks.size} audio track(s) available"
                                    } else {
                                        "Scanning audio tracks..."
                                    },
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleAudioTracksModal() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.availableAudioTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Single audio stream detected.\nDefault audio is actively playing.",
                                        color = TextMuted,
                                        fontSize = 12.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(
                                    items = uiState.availableAudioTracks,
                                    key = { index, track -> "${track.id}_$index" }
                                ) { _, track ->
                                    val isSelected = track.isSelected
                                    val cardBorderColor = if (isSelected) accent else Color.White.copy(alpha = 0.08f)
                                    val cardBgColor = if (isSelected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cardBgColor)
                                            .border(BorderStroke(if (isSelected) 1.5.dp else 1.dp, cardBorderColor), RoundedCornerShape(10.dp))
                                            .clickable { viewModel.selectAudioTrack(track) }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Audiotrack,
                                                        contentDescription = null,
                                                        tint = if (isSelected) accent else Color.White.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column {
                                                    Text(
                                                        text = track.displayName,
                                                        color = if (isSelected) Color.White else TextPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        val langTag = track.language?.takeIf { it.isNotBlank() && it != "und" }?.uppercase() ?: "AUDIO"
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isSelected) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = langTag,
                                                                color = if (isSelected) accent else TextSecondary,
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        if (track.channelCount > 0) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = when (track.channelCount) {
                                                                    1 -> "Mono"
                                                                    2 -> "Stereo"
                                                                    6 -> "5.1 Surround"
                                                                    8 -> "7.1 Surround"
                                                                    else -> "${track.channelCount} Channels"
                                                                },
                                                                color = TextMuted,
                                                                fontSize = 10.5.sp
                                                            )
                                                        }
                                                        if (!track.isSupported) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFFFF5252).copy(alpha = 0.2f))
                                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = "UNSUPPORTED",
                                                                    color = Color(0xFFFF5252),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (isSelected) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF4CAF50))
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = accent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Active",
                                                        tint = accent,
                                                        modifier = Modifier.size(14.dp)
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

        if (uiState.isSubtitlesModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.toggleSubtitlesModal() },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 360.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF141720))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {  }
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Subtitles (CC)",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.availableSubtitleTracks.isNotEmpty()) {
                                        "${uiState.availableSubtitleTracks.size} subtitle track(s) available"
                                    } else {
                                        "No subtitle tracks available"
                                    },
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleSubtitlesModal() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Subtitles Display",
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (uiState.isSubtitlesEnabled) {
                                            "Subtitles are turned ON"
                                        } else {
                                            "Subtitles are turned OFF"
                                        },
                                        color = if (uiState.isSubtitlesEnabled) accent else TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = uiState.isSubtitlesEnabled,
                                    onCheckedChange = { viewModel.toggleSubtitlesEnabled() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accent,
                                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "AVAILABLE SUBTITLES",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.availableSubtitleTracks.isEmpty()) {
                                item(key = "no_subtitles") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No subtitle tracks available\nfor this stream.",
                                            color = TextMuted,
                                            fontSize = 11.5.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = uiState.availableSubtitleTracks,
                                    key = { index, track -> "${track.id}_$index" }
                                ) { _, track ->
                                    val isTrackSelected = uiState.isSubtitlesEnabled && (track.isSelected || uiState.selectedSubtitleTrack?.id == track.id)
                                    val cardBorder = if (isTrackSelected) accent else Color.White.copy(alpha = 0.08f)
                                    val cardBg = if (isTrackSelected) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(cardBg)
                                            .border(BorderStroke(if (isTrackSelected) 1.5.dp else 1.dp, cardBorder), RoundedCornerShape(10.dp))
                                            .clickable { viewModel.selectSubtitleTrack(track) }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isTrackSelected) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Subtitles,
                                                        contentDescription = null,
                                                        tint = if (isTrackSelected) accent else Color.White.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column {
                                                    Text(
                                                        text = track.displayName,
                                                        color = if (isTrackSelected) Color.White else TextPrimary,
                                                        fontWeight = if (isTrackSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        val langTag = track.language?.takeIf { it.isNotBlank() && it != "und" }?.uppercase() ?: "SUB"
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isTrackSelected) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = langTag,
                                                                color = if (isTrackSelected) accent else TextSecondary,
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        if (track.isHearingImpaired) {
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color.White.copy(alpha = 0.08f))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = "CC",
                                                                    color = TextMuted,
                                                                    fontSize = 9.5.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        if (track.isForced) {
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFFE53935).copy(alpha = 0.2f))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = "FORCED",
                                                                    color = Color(0xFFFF8A80),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (isTrackSelected) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF4CAF50))
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = accent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Active",
                                                        tint = accent,
                                                        modifier = Modifier.size(14.dp)
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

        if (uiState.playbackErrorMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPlaybackError() },
                title = {
                    Text(
                        text = "Playback Error",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = uiState.playbackErrorMessage ?: "An error occurred during playback.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        if (uiState.pendingFallbackStream != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Would you like to switch to alternative stream: ${uiState.pendingFallbackStream?.label}?",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    if (uiState.pendingFallbackStream != null) {
                        TextButton(
                            onClick = { viewModel.confirmFallback() }
                        ) {
                            Text(
                                text = "Switch Stream",
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.retryCurrentStream() }
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
                        onClick = {
                            viewModel.dismissPlaybackError()
                            onBack()
                        }
                    ) {
                        Text(text = "Close", color = TextMuted)
                    }
                },
                containerColor = CardElevated
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hr = totalSec / 3600
    return if (hr > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hr, min, sec)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", min, sec)
    }
}

