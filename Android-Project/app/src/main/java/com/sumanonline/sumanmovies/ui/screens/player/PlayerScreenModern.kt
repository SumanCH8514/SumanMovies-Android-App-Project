package com.sumanonline.sumanmovies.ui.screens.player

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.getDisplayCategory
import com.sumanonline.sumanmovies.data.models.getDisplayQuality
import com.sumanonline.sumanmovies.data.models.getDisplayTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val goldenAccent = Color(0xFFFFAD32)
private val darkDrawerBg = Color(0xFF0D0F14)

private val COMMON_LANGUAGE_MAP = mapOf(
    "hin" to "Hindi", "hi" to "Hindi", "hindi" to "Hindi",
    "eng" to "English", "en" to "English", "english" to "English",
    "tam" to "Tamil", "ta" to "Tamil", "tamil" to "Tamil",
    "tel" to "Telugu", "te" to "Telugu", "telugu" to "Telugu",
    "mal" to "Malayalam", "ml" to "Malayalam", "malayalam" to "Malayalam",
    "kan" to "Kannada", "kn" to "Kannada", "kannada" to "Kannada",
    "ben" to "Bengali", "bn" to "Bengali", "bengali" to "Bengali",
    "mar" to "Marathi", "mr" to "Marathi", "marathi" to "Marathi",
    "guj" to "Gujarati", "gu" to "Gujarati", "gujarati" to "Gujarati",
    "pan" to "Punjabi", "pa" to "Punjabi", "punjabi" to "Punjabi",
    "urd" to "Urdu", "ur" to "Urdu", "urdu" to "Urdu",
    "kor" to "Korean", "ko" to "Korean", "korean" to "Korean",
    "jpn" to "Japanese", "ja" to "Japanese", "japanese" to "Japanese",
    "zho" to "Chinese", "chi" to "Chinese", "zh" to "Chinese", "chinese" to "Chinese",
    "mandarin" to "Mandarin", "cantonese" to "Cantonese",
    "spa" to "Spanish", "es" to "Spanish", "spanish" to "Spanish",
    "fra" to "French", "fre" to "French", "fr" to "French", "french" to "French",
    "deu" to "German", "ger" to "German", "de" to "German", "german" to "German",
    "ita" to "Italian", "it" to "Italian", "italian" to "Italian",
    "por" to "Portuguese", "pt" to "Portuguese", "portuguese" to "Portuguese",
    "rus" to "Russian", "ru" to "Russian", "russian" to "Russian",
    "tur" to "Turkish", "tr" to "Turkish", "turkish" to "Turkish",
    "ara" to "Arabic", "ar" to "Arabic", "arabic" to "Arabic",
    "tha" to "Thai", "th" to "Thai", "thai" to "Thai",
    "vie" to "Vietnamese", "vi" to "Vietnamese", "vietnamese" to "Vietnamese",
    "ind" to "Indonesian", "id" to "Indonesian", "indonesian" to "Indonesian",
    "bho" to "Bhojpuri", "bhojpuri" to "Bhojpuri",
    "nep" to "Nepali", "ne" to "Nepali", "nepali" to "Nepali",
    "sin" to "Sinhala", "si" to "Sinhala", "sinhala" to "Sinhala",
    "pol" to "Polish", "pl" to "Polish", "polish" to "Polish",
    "nld" to "Dutch", "dut" to "Dutch", "nl" to "Dutch", "dutch" to "Dutch",
    "swe" to "Swedish", "sv" to "Swedish", "swedish" to "Swedish"
)

private fun cleanAudioLanguageName(track: PlayerAudioTrack): String? {
    val code = track.language?.trim()?.lowercase()
    if (!code.isNullOrBlank() && code != "und" && code != "zxx" && code != "root" && code != "qaa") {
        COMMON_LANGUAGE_MAP[code]?.let { return it }
        val localeName = try {
            val loc = Locale.forLanguageTag(code)
            val name = loc.getDisplayLanguage(Locale.ENGLISH)
            if (name.isNotBlank()) name else Locale(code).getDisplayLanguage(Locale.ENGLISH)
        } catch (_: Exception) {
            null
        }
        if (!localeName.isNullOrBlank() && !localeName.equals(code, ignoreCase = true) && !localeName.equals("und", ignoreCase = true)) {
            return localeName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        }
    }

    val raw = (track.label.ifBlank { null } ?: track.displayName).ifBlank { return null }

    var cleaned = raw
        .replace(Regex("""\([^)]*\)"""), "")
        .replace(Regex("""\[[^\]]*\]"""), "")
        .replace(Regex("""\{[^}]*\}"""), "")

    cleaned = cleaned.replace(
        Regex("""(?i)\b(aac|ac3|eac3|ddp|dd\+|dd|dts|truehd|atmos|flac|mp3|opus|stereo|mono|surround|channels?|audio|track|original|clean|dubbed|dub|sdh|commentary|\d+\.\d+|\d+ch)\b"""),
        ""
    )

    cleaned = cleaned.replace(Regex("""[•·\-_|:,\/]+"""), " ").trim()

    if (cleaned.isNotBlank()) {
        val firstToken = cleaned.split(" ").firstOrNull { it.isNotBlank() }?.trim() ?: ""
        COMMON_LANGUAGE_MAP[firstToken.lowercase()]?.let { return it }
        COMMON_LANGUAGE_MAP[cleaned.lowercase()]?.let { return it }

        for ((_, name) in COMMON_LANGUAGE_MAP) {
            if (cleaned.contains(name, ignoreCase = true)) {
                return name
            }
        }

        if (firstToken.length >= 3 && firstToken.all { it.isLetter() }) {
            return firstToken.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
        }
    }

    for ((_, name) in COMMON_LANGUAGE_MAP) {
        if (raw.contains(name, ignoreCase = true)) {
            return name
        }
    }

    return null
}

private fun formatPlayerLanguagesSummary(tracks: List<PlayerAudioTrack>, streamAudioLangs: String?): String {
    val detected = tracks
        .mapNotNull { cleanAudioLanguageName(it) }
        .distinct()

    if (detected.isNotEmpty()) {
        return detected.joinToString(", ")
    }

    if (!streamAudioLangs.isNullOrBlank()) {
        val parsed = streamAudioLangs
            .split(Regex("""[,+/•|&]+"""))
            .mapNotNull { token ->
                val cleaned = token.replace(Regex("""\([^)]*\)"""), "").replace(Regex("""\[[^\]]*\]"""), "").trim().lowercase()
                COMMON_LANGUAGE_MAP[cleaned] ?: token.trim().takeIf { it.isNotBlank() }
            }
            .distinct()
        if (parsed.isNotEmpty()) {
            return parsed.joinToString(", ")
        }
    }

    return ""
}

private fun cleanSeriesBaseTitle(rawTitle: String): String {
    return rawTitle
        .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
        .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
        .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
        .trim()
        .ifBlank { rawTitle }
}

@Composable
private fun DoubleTapSeekOverlay(
    showForward: Boolean,
    forwardSeconds: Int,
    showRewind: Boolean,
    rewindSeconds: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showRewind,
            enter = fadeIn() + scaleIn(initialScale = 0.82f),
            exit = fadeOut() + scaleOut(targetScale = 0.82f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "-${rewindSeconds}s",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showForward,
            enter = fadeIn() + scaleIn(initialScale = 0.82f),
            exit = fadeOut() + scaleOut(targetScale = 0.82f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "+${forwardSeconds}s",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

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
    val isSeries = uiState.mediaType == MediaType.SERIES ||
            uiState.mediaType == MediaType.ANIME ||
            uiState.mediaType == MediaType.DRAMA

    if (isSeries) {
        SeriesPlayerScreen(
            viewModel = viewModel,
            onBack = onBack,
            onEnterPip = onEnterPip,
            onToggleFullscreen = onToggleFullscreen,
            onShare = onShare,
            modifier = modifier
        )
    } else {
        MoviePlayerScreen(
            viewModel = viewModel,
            onBack = onBack,
            onEnterPip = onEnterPip,
            onToggleFullscreen = onToggleFullscreen,
            onShare = onShare,
            modifier = modifier
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MoviePlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onEnterPip: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onShare: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()

    var showForwardIndicator by remember { mutableStateOf(false) }
    var forwardSeekSeconds by remember { mutableIntStateOf(0) }
    var showRewindIndicator by remember { mutableStateOf(false) }
    var rewindSeekSeconds by remember { mutableIntStateOf(0) }
    var lastForwardTime by remember { mutableStateOf(0L) }
    var lastRewindTime by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(
        uiState.showControls,
        playbackState.isPlaying,
        uiState.isStreamsDrawerOpen,
        uiState.isAudioTracksModalOpen,
        uiState.isSubtitlesModalOpen,
        isSpeedDialogOpen
    ) {
        if (
            uiState.showControls &&
            playbackState.isPlaying &&
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        viewModel.toggleControls()
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        val now = System.currentTimeMillis()
                        val step = viewModel.seekStepSeconds
                        if (offset.x > width * 0.55f) {
                            val accumulated = if (now - lastForwardTime < 850L) forwardSeekSeconds + step else step
                            forwardSeekSeconds = accumulated
                            lastForwardTime = now
                            showForwardIndicator = true
                            showRewindIndicator = false
                            viewModel.playerManager.skipForward(step)
                            coroutineScope.launch {
                                delay(750L)
                                if (System.currentTimeMillis() - lastForwardTime >= 700L) {
                                    showForwardIndicator = false
                                    forwardSeekSeconds = 0
                                }
                            }
                        } else if (offset.x < width * 0.45f) {
                            val accumulated = if (now - lastRewindTime < 850L) rewindSeekSeconds + step else step
                            rewindSeekSeconds = accumulated
                            lastRewindTime = now
                            showRewindIndicator = true
                            showForwardIndicator = false
                            viewModel.playerManager.skipBackward(step)
                            coroutineScope.launch {
                                delay(750L)
                                if (System.currentTimeMillis() - lastRewindTime >= 700L) {
                                    showRewindIndicator = false
                                    rewindSeekSeconds = 0
                                }
                            }
                        } else {
                            viewModel.playerManager.togglePlayPause()
                        }
                    }
                )
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

        val isBusy = playbackState.isBuffering || uiState.isLoadingStreams || uiState.resolvingStreamUrl != null

        if (isBusy) {
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
                                Color.Black.copy(alpha = 0.78f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f)
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
                                .background(Color.Black.copy(alpha = 0.45f))
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
                            val titleText = uiState.mediaTitle.ifBlank { "Playing Video" }

                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            val languagesText = formatPlayerLanguagesSummary(
                                tracks = uiState.availableAudioTracks,
                                streamAudioLangs = uiState.currentStream?.audioLangs
                            )

                            val yearText = uiState.releaseYear?.takeIf { it.isNotBlank() }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!yearText.isNullOrBlank()) {
                                    Text(
                                        text = if (languagesText.isNotBlank()) "$yearText · " else yearText,
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                                if (languagesText.isNotBlank()) {
                                    Text(
                                        text = languagesText,
                                        color = goldenAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }


                }

                if (!isBusy) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
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
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (navBarInset + 6.dp).coerceAtLeast(10.dp))
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
                                .height(20.dp)
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

                            val providerName = uiState.serverDisplayName?.takeIf { it.isNotBlank() }
                                ?: uiState.currentStream?.provider?.let { p ->
                                    StreamingServer.entries.firstOrNull { it.id.equals(p, ignoreCase = true) }?.displayName
                                        ?: p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                }
                                ?: uiState.selectedServer.displayName

                            PlayerProviderBadge(
                                providerName = providerName,
                                onClick = { viewModel.toggleStreamsDrawer() },
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.downloadCurrentPlayingStream(context) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Download",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = onEnterPip,
                                modifier = Modifier.size(32.dp)
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
                                modifier = Modifier.size(32.dp)
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

                            val audioLangCode = uiState.currentStream?.audioLangs?.take(3)?.uppercase()
                                ?: uiState.selectedAudioTrack?.language?.uppercase()?.take(3)
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

                            val isSubActive = uiState.isSubtitlesEnabled && uiState.selectedSubtitleTrack != null
                            val subLangCode = if (!isSubActive) {
                                "OFF"
                            } else {
                                uiState.selectedSubtitleTrack?.language?.uppercase()?.take(3)
                                    ?: uiState.selectedSubtitleTrack?.displayName?.take(3)?.uppercase()
                                    ?: "ENG"
                            }
                            val subColor = if (isSubActive) goldenAccent else Color.White

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
                                    tint = subColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = subLangCode,
                                    color = subColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSubActive) FontWeight.Bold else FontWeight.Medium
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
                                onClick = { viewModel.openExternalPlayer(context) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in External Player",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        DoubleTapSeekOverlay(
            showForward = showForwardIndicator,
            forwardSeconds = forwardSeekSeconds,
            showRewind = showRewindIndicator,
            rewindSeconds = rewindSeekSeconds
        )

        DrawersAndDialogs(viewModel, isSpeedDialogOpen, onDismissSpeedDialog = { isSpeedDialogOpen = false })
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SeriesPlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onEnterPip: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onShare: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()

    var showForwardIndicator by remember { mutableStateOf(false) }
    var forwardSeekSeconds by remember { mutableIntStateOf(0) }
    var showRewindIndicator by remember { mutableStateOf(false) }
    var rewindSeekSeconds by remember { mutableIntStateOf(0) }
    var lastForwardTime by remember { mutableStateOf(0L) }
    var lastRewindTime by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(
        uiState.showControls,
        playbackState.isPlaying,
        uiState.isStreamsDrawerOpen,
        uiState.isAudioTracksModalOpen,
        uiState.isSubtitlesModalOpen,
        uiState.isEpisodesDrawerOpen,
        isSpeedDialogOpen
    ) {
        if (
            uiState.showControls &&
            playbackState.isPlaying &&
            !uiState.isStreamsDrawerOpen &&
            !uiState.isAudioTracksModalOpen &&
            !uiState.isSubtitlesModalOpen &&
            !uiState.isEpisodesDrawerOpen &&
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        viewModel.toggleControls()
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        val now = System.currentTimeMillis()
                        val step = viewModel.seekStepSeconds
                        if (offset.x > width * 0.55f) {
                            val accumulated = if (now - lastForwardTime < 850L) forwardSeekSeconds + step else step
                            forwardSeekSeconds = accumulated
                            lastForwardTime = now
                            showForwardIndicator = true
                            showRewindIndicator = false
                            viewModel.playerManager.skipForward(step)
                            coroutineScope.launch {
                                delay(750L)
                                if (System.currentTimeMillis() - lastForwardTime >= 700L) {
                                    showForwardIndicator = false
                                    forwardSeekSeconds = 0
                                }
                            }
                        } else if (offset.x < width * 0.45f) {
                            val accumulated = if (now - lastRewindTime < 850L) rewindSeekSeconds + step else step
                            rewindSeekSeconds = accumulated
                            lastRewindTime = now
                            showRewindIndicator = true
                            showForwardIndicator = false
                            viewModel.playerManager.skipBackward(step)
                            coroutineScope.launch {
                                delay(750L)
                                if (System.currentTimeMillis() - lastRewindTime >= 700L) {
                                    showRewindIndicator = false
                                    rewindSeekSeconds = 0
                                }
                            }
                        } else {
                            viewModel.playerManager.togglePlayPause()
                        }
                    }
                )
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

        val isBusy = playbackState.isBuffering || uiState.isLoadingStreams || uiState.resolvingStreamUrl != null

        if (isBusy) {
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
                                Color.Black.copy(alpha = 0.78f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f)
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
                                .background(Color.Black.copy(alpha = 0.45f))
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
                            val sPadded = String.format(Locale.US, "%02d", uiState.seasonNumber)
                            val ePadded = String.format(Locale.US, "%02d", uiState.episodeNumber)
                            val epTitlePart = if (!uiState.episodeTitle.isNullOrBlank()) " ${uiState.episodeTitle}" else ""
                            val cleanBase = cleanSeriesBaseTitle(uiState.mediaTitle)
                            val titleText = "$cleanBase S$sPadded - EP$ePadded$epTitlePart"

                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            val languagesText = formatPlayerLanguagesSummary(
                                tracks = uiState.availableAudioTracks,
                                streamAudioLangs = uiState.currentStream?.audioLangs
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val prefix = "S${String.format(Locale.US, "%02d", uiState.seasonNumber)}E${String.format(Locale.US, "%02d", uiState.episodeNumber)}"
                                Text(
                                    text = if (languagesText.isNotBlank()) "$prefix · " else prefix,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                if (languagesText.isNotBlank()) {
                                    Text(
                                        text = languagesText,
                                        color = goldenAccent,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                                viewModel.toggleEpisodesDrawer()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Episodes",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!isBusy) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
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
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (navBarInset + 6.dp).coerceAtLeast(10.dp))
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val prevEnabled = uiState.hasPreviousEpisode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161922).copy(alpha = if (prevEnabled) 0.85f else 0.35f))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Color(0xFF2B303C).copy(alpha = if (prevEnabled) 1f else 0.35f)
                                    ),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = prevEnabled) {
                                    viewModel.playPreviousEpisode()
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "← Previous",
                                color = Color.White.copy(alpha = if (prevEnabled) 1f else 0.35f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        val nextEnabled = uiState.hasNextEpisode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF161922).copy(alpha = if (nextEnabled) 0.85f else 0.35f))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Color(0xFF2B303C).copy(alpha = if (nextEnabled) 1f else 0.35f)
                                    ),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = nextEnabled) {
                                    viewModel.playNextEpisode()
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Next Episode",
                                color = Color.White.copy(alpha = if (nextEnabled) 1f else 0.35f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

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
                                .height(20.dp)
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

                            val providerName = uiState.serverDisplayName?.takeIf { it.isNotBlank() }
                                ?: uiState.currentStream?.provider?.let { p ->
                                    StreamingServer.entries.firstOrNull { it.id.equals(p, ignoreCase = true) }?.displayName
                                        ?: p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                }
                                ?: uiState.selectedServer.displayName

                            PlayerProviderBadge(
                                providerName = providerName,
                                onClick = { viewModel.toggleStreamsDrawer() },
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.downloadCurrentPlayingStream(context) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Download",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = onEnterPip,
                                modifier = Modifier.size(32.dp)
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
                                modifier = Modifier.size(32.dp)
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.toggleEpisodesDrawer() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Episodes",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "EPISODES",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val audioLangCode = uiState.currentStream?.audioLangs?.take(3)?.uppercase()
                                ?: uiState.selectedAudioTrack?.language?.uppercase()?.take(3)
                                ?: uiState.selectedAudioTrack?.displayName?.take(3)?.uppercase()
                                ?: "ENG"

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

                            val isSubActive = uiState.isSubtitlesEnabled && uiState.selectedSubtitleTrack != null
                            val subLangCode = if (!isSubActive) {
                                "OFF"
                            } else {
                                uiState.selectedSubtitleTrack?.language?.uppercase()?.take(3)
                                    ?: uiState.selectedSubtitleTrack?.displayName?.take(3)?.uppercase()
                                    ?: "ENG"
                            }
                            val subColor = if (isSubActive) goldenAccent else Color.White

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
                                    tint = subColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = subLangCode,
                                    color = subColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSubActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }

                            val qualityText = uiState.currentStream?.quality?.let { q ->
                                if (q.isNotBlank()) "Auto · $q" else "Auto · 720p"
                            } ?: "Auto · 720p"

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
                                onClick = { viewModel.openExternalPlayer(context) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in External Player",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isEpisodesDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.toggleEpisodesDrawer() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(380.dp)
                        .align(Alignment.CenterEnd)
                        .background(darkDrawerBg)
                        .border(BorderStroke(1.dp, Color(0xFF1E222D)), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                val isEpisodesTab = uiState.episodesDrawerTab == "EPISODES"
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        viewModel.setEpisodesDrawerTab("EPISODES")
                                    }
                                ) {
                                    Text(
                                        text = "EPISODES",
                                        color = if (isEpisodesTab) Color.White else Color(0xFF9CA3AF),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(2.5.dp)
                                            .width(52.dp)
                                            .background(if (isEpisodesTab) goldenAccent else Color.Transparent)
                                    )
                                }

                                val isSeasonsTab = uiState.episodesDrawerTab == "SEASONS"
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        viewModel.setEpisodesDrawerTab("SEASONS")
                                    }
                                ) {
                                    Text(
                                        text = "SEASONS",
                                        color = if (isSeasonsTab) Color.White else Color(0xFF9CA3AF),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(2.5.dp)
                                            .width(52.dp)
                                            .background(if (isSeasonsTab) goldenAccent else Color.Transparent)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleEpisodesDrawer() },
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

                        if (uiState.episodesDrawerTab == "EPISODES") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val seasonsToDisplay = if (uiState.seasonsList.isNotEmpty()) {
                                    uiState.seasonsList
                                } else {
                                    listOf(
                                        Season(1, "Season 1", 8),
                                        Season(2, "Season 2", 8),
                                        Season(3, "Season 3", 6)
                                    )
                                }

                                seasonsToDisplay.forEach { s ->
                                    val isSelected = s.seasonNumber == uiState.selectedDrawerSeason
                                    val pillText = "S${String.format(Locale.US, "%02d", s.seasonNumber)}"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color.White else Color(0xFF1E222D))
                                            .clickable {
                                                viewModel.selectDrawerSeason(s.seasonNumber)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pillText,
                                            color = if (isSelected) Color.Black else Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val curSeason = uiState.seasonsList.firstOrNull { it.seasonNumber == uiState.selectedDrawerSeason }
                                ?: Season(uiState.selectedDrawerSeason, "Season ${uiState.selectedDrawerSeason}", uiState.currentSeasonEpisodes.size.coerceAtLeast(8))
                            val seasonYear = uiState.releaseYear ?: "2022"
                            val seasonMetaText = "S${String.format(Locale.US, "%02d", curSeason.seasonNumber)} - ${curSeason.name} · ${curSeason.episodeCount} eps · $seasonYear"

                            Text(
                                text = seasonMetaText,
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.currentSeasonEpisodes) { ep ->
                                    val isCurrentEp = ep.seasonNumber == uiState.seasonNumber && ep.episodeNumber == uiState.episodeNumber
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrentEp) goldenAccent.copy(alpha = 0.14f) else Color(0xFF141720))
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isCurrentEp) goldenAccent else Color.White.copy(alpha = 0.06f)
                                                ),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.playEpisode(ep.seasonNumber, ep.episodeNumber)
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(108.dp)
                                                .height(62.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF1F2430))
                                        ) {
                                            if (!ep.stillUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ep.stillUrl,
                                                    contentDescription = ep.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(Color(0xFF2A3040), Color(0xFF161A24))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            val durationText = "${ep.durationMinutes ?: 68}m"
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.75f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = durationText,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            val epNumPadded = String.format(Locale.US, "%02d", ep.episodeNumber)
                                            Text(
                                                text = "$epNumPadded ${ep.title}",
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = ep.overview ?: "No overview available for this episode.",
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val seasonsToDisplay = if (uiState.seasonsList.isNotEmpty()) {
                                uiState.seasonsList
                            } else {
                                listOf(
                                    Season(1, "Season 1", 8),
                                    Season(2, "Season 2", 8),
                                    Season(3, "Season 3", 6)
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(seasonsToDisplay) { s ->
                                    val isSelected = s.seasonNumber == uiState.selectedDrawerSeason
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) goldenAccent.copy(alpha = 0.12f) else Color(0xFF141720))
                                            .border(
                                                BorderStroke(
                                                    if (isSelected) 1.5.dp else 1.dp,
                                                    if (isSelected) goldenAccent else Color.White.copy(alpha = 0.06f)
                                                ),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                viewModel.selectDrawerSeason(s.seasonNumber)
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(68.dp)
                                                .height(96.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF1E222D))
                                        ) {
                                            if (!s.posterUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = s.posterUrl,
                                                    contentDescription = s.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else if (!uiState.posterUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = uiState.posterUrl,
                                                    contentDescription = s.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color(0xFF232938)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "S${s.seasonNumber}",
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            val sPadded = String.format(Locale.US, "%02d", s.seasonNumber)
                                            Text(
                                                text = "S$sPadded - ${s.name}",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val seasonDesc = when (s.seasonNumber) {
                                                1 -> "Beginning in a time of relative peace, the series follows an ensemble cast of characters."
                                                2 -> "Dark forces begin to gather and characters must confront the rising threats to their realm."
                                                else -> "The saga continues with epic confrontations and deep-rooted destinies."
                                            }
                                            Text(
                                                text = seasonDesc,
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "${s.episodeCount} episodes · ${uiState.releaseYear ?: "2022"}",
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
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

        DoubleTapSeekOverlay(
            showForward = showForwardIndicator,
            forwardSeconds = forwardSeekSeconds,
            showRewind = showRewindIndicator,
            rewindSeconds = rewindSeekSeconds
        )

        DrawersAndDialogs(viewModel, isSpeedDialogOpen, onDismissSpeedDialog = { isSpeedDialogOpen = false })
    }
}

@Composable
private fun DrawersAndDialogs(
    viewModel: PlayerViewModel,
    isSpeedDialogOpen: Boolean,
    onDismissSpeedDialog: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()

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
                    .width(340.dp)
                    .align(Alignment.CenterEnd)
                    .background(darkDrawerBg)
                    .border(BorderStroke(1.dp, Color(0xFF1E222D)), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AUDIO",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141720))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dialogue Boost",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (uiState.dialogueBoostEnabled) goldenAccent else Color(0xFF222634))
                                .clickable { viewModel.toggleDialogueBoost() }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (uiState.dialogueBoostEnabled) "ON" else "OFF",
                                color = if (uiState.dialogueBoostEnabled) Color.Black else Color(0xFF9CA3AF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "AUDIO TRACKS & LANGUAGES",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayAudioTracks = uiState.availableAudioTracks
                    val currentStreamLang = uiState.currentStream?.audioLangs
                    val alternateAudioStreams = uiState.availableStreams
                        .filter { !it.audioLangs.isNullOrBlank() && it.audioLangs != currentStreamLang }
                        .distinctBy { it.audioLangs }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (displayAudioTracks.isNotEmpty()) {
                            items(displayAudioTracks) { track ->
                                val isSelected = if (uiState.availableAudioTracks.isNotEmpty()) {
                                    track.id == uiState.selectedAudioTrack?.id
                                } else {
                                    track.isSelected
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) goldenAccent.copy(alpha = 0.12f) else Color(0xFF141720))
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSelected) goldenAccent else Color.White.copy(alpha = 0.06f)
                                            ),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.selectAudioTrack(track)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track.displayName,
                                        color = if (isSelected) goldenAccent else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val tagText = when {
                                            track.channelCount > 2 -> "${(track.language ?: "ENG").uppercase()} 5.1"
                                            track.channelCount == 2 -> "Stereo"
                                            else -> (track.language ?: "ENG").uppercase()
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF222634))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = tagText,
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = goldenAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (!currentStreamLang.isNullOrBlank()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(goldenAccent.copy(alpha = 0.12f))
                                        .border(
                                            BorderStroke(1.dp, goldenAccent),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentStreamLang,
                                        color = goldenAccent,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = goldenAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (alternateAudioStreams.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "PROVIDER AUDIO VERSIONS",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            items(alternateAudioStreams) { altStream ->
                                val altLang = altStream.audioLangs ?: "Alternate Audio"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF141720))
                                        .border(
                                            BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.switchStream(altStream)
                                            viewModel.toggleAudioTracksModal()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = altLang,
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${altStream.format} · ${altStream.getDisplayQuality()}",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 10.5.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF222634))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "SWITCH",
                                            color = goldenAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
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

    if (uiState.isStreamsDrawerOpen) {
        val currentServer = uiState.drawerSelectedServer
        val streamsForCurrentServer = uiState.serverStreamsMap[currentServer] ?: (if (uiState.selectedServer == currentServer) uiState.availableStreams else emptyList())
        val isCurrentServerLoading = uiState.loadingServers.contains(currentServer)
        val availableQualities = remember(streamsForCurrentServer) { getAvailableQualitiesForServer(streamsForCurrentServer) }
        val activeQuality = uiState.drawerSelectedQuality?.takeIf { availableQualities.contains(it) } ?: availableQualities.firstOrNull() ?: "1080p"

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { viewModel.toggleStreamsDrawer() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(480.dp)
                    .align(Alignment.CenterEnd)
                    .background(darkDrawerBg)
                    .border(BorderStroke(1.dp, Color(0xFF1E222D)), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Quality & Servers",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Select Server & Resolutions",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.5.sp
                            )
                        }
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "ACTIVE SERVER",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StreamingServer.entries.forEach { server ->
                            val isViewed = (uiState.drawerSelectedServer == server)
                            val isPlaying = (uiState.selectedServer == server && uiState.currentStream != null)
                            val isLoading = uiState.loadingServers.contains(server)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isViewed) goldenAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isViewed) goldenAccent else Color.White.copy(alpha = 0.1f)
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.selectDrawerServer(server) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isViewed) goldenAccent else Color(0xFF22C55E))
                                        )
                                    }
                                    Text(
                                        text = server.displayName,
                                        color = if (isViewed) Color.White else Color(0xFF9CA3AF),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isViewed) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            color = goldenAccent,
                                            strokeWidth = 1.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E222D)))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(125.dp)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "QUALITIES",
                                color = Color(0xFF9CA3AF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isCurrentServerLoading && streamsForCurrentServer.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color.White.copy(alpha = 0.04f))
                                        )
                                    }
                                }
                            } else if (availableQualities.isEmpty()) {
                                Text(
                                    text = "No qualities",
                                    color = Color(0xFF6B7280),
                                    fontSize = 11.sp
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableQualities) { quality ->
                                        val isQualitySelected = (activeQuality == quality)
                                        val linkCount = streamsForCurrentServer.count { normalizeQualityBucket(it) == quality }
                                        val badgeColor = when (quality) {
                                            "4K" -> Color(0xFFEAB308)
                                            "1080p" -> Color(0xFF3B82F6)
                                            "720p" -> Color(0xFF14B8A6)
                                            "480p", "360p" -> Color(0xFF8B5CF6)
                                            else -> Color(0xFF9CA3AF)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(
                                                    if (isQualitySelected) goldenAccent.copy(alpha = 0.18f)
                                                    else Color(0xFF141720)
                                                )
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        if (isQualitySelected) goldenAccent
                                                        else Color.White.copy(alpha = 0.08f)
                                                    ),
                                                    RoundedCornerShape(7.dp)
                                                )
                                                .clickable { viewModel.selectDrawerQuality(quality) }
                                                .padding(horizontal = 9.dp, vertical = 7.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = quality,
                                                        color = if (isQualitySelected) goldenAccent else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isQualitySelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "[$linkCount ${if (linkCount == 1) "link" else "links"}]",
                                                        color = if (isQualitySelected) goldenAccent.copy(alpha = 0.85f) else Color(0xFF9CA3AF),
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(badgeColor)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF1E222D)))
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (isCurrentServerLoading && streamsForCurrentServer.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = goldenAccent,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Loading ${currentServer.displayName} streams...",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else if (streamsForCurrentServer.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No streams found on ${currentServer.displayName}",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 11.5.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                val streamsForQuality = streamsForCurrentServer.filter { normalizeQualityBucket(it) == activeQuality }
                                Text(
                                    text = "AVAILABLE LINKS (${streamsForQuality.size})",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (streamsForQuality.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No $activeQuality links found",
                                            color = Color(0xFF6B7280),
                                            fontSize = 11.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(streamsForQuality) { stream ->
                                            val isSelected = uiState.currentStream?.url == stream.url
                                            val rawTitle = stream.getDisplayTitle(currentServer)
                                            val fmt = stream.format.uppercase().trim()
                                            val formattedTitle = if (rawTitle.startsWith(fmt, ignoreCase = true)) {
                                                rawTitle
                                            } else {
                                                "$fmt $rawTitle"
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) goldenAccent.copy(alpha = 0.15f) else Color(0xFF141720))
                                                    .border(
                                                        BorderStroke(
                                                            1.dp,
                                                            if (isSelected) goldenAccent else Color.White.copy(alpha = 0.08f)
                                                        ),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        viewModel.selectStreamFromDrawer(stream)
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text(
                                                        text = formattedTitle,
                                                        color = if (isSelected) goldenAccent else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (!stream.audioLangs.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = stream.audioLangs,
                                                            color = Color(0xFF9CA3AF),
                                                            fontSize = 9.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                if (isSelected) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "PLAYING",
                                                            color = goldenAccent,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Playing",
                                                            tint = goldenAccent,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Switch",
                                                        tint = Color(0xFF9CA3AF),
                                                        modifier = Modifier.size(16.dp)
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
                    .width(320.dp)
                    .align(Alignment.CenterEnd)
                    .background(darkDrawerBg)
                    .border(BorderStroke(1.dp, Color(0xFF1E222D)), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SUBTITLES",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!uiState.isSubtitlesEnabled) goldenAccent.copy(alpha = 0.15f) else Color(0xFF141720))
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (!uiState.isSubtitlesEnabled) goldenAccent else Color.White.copy(alpha = 0.08f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.disableSubtitles() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            color = if (!uiState.isSubtitlesEnabled) goldenAccent else Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = if (!uiState.isSubtitlesEnabled) FontWeight.Bold else FontWeight.Medium
                        )
                        if (!uiState.isSubtitlesEnabled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = goldenAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.availableSubtitleTracks) { track ->
                            val isSelected = uiState.isSubtitlesEnabled && uiState.selectedSubtitleTrack?.id == track.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) goldenAccent.copy(alpha = 0.15f) else Color(0xFF141720))
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) goldenAccent else Color.White.copy(alpha = 0.08f)
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.selectSubtitleTrack(track) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.displayName,
                                    color = if (isSelected) goldenAccent else Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                }
            }
        }
    }

    if (isSpeedDialogOpen) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = onDismissSpeedDialog,
            containerColor = Color(0xFF161922),
            title = {
                Text(text = "Playback Speed", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    speeds.forEach { spd ->
                        val isSelected = playbackState.playbackSpeed == spd
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) goldenAccent.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable {
                                viewModel.playerManager.setSpeed(spd)
                                onDismissSpeedDialog()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (spd == 1.0f) "Normal (1x)" else "${spd}x",
                                color = if (isSelected) goldenAccent else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
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
                TextButton(onClick = onDismissSpeedDialog) {
                    Text(text = "Close", color = goldenAccent)
                }
            }
        )
    }

    if (uiState.playbackErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPlaybackError() },
            containerColor = Color(0xFF161922),
            title = {
                Text(text = "Playback Error", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = uiState.playbackErrorMessage ?: "An error occurred during playback.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.5.sp
                    )
                    if (uiState.pendingFallbackStream != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "A fallback stream is available. Would you like to switch?",
                            color = goldenAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                if (uiState.pendingFallbackStream != null) {
                    TextButton(onClick = { viewModel.confirmFallback() }) {
                        Text(text = "Switch Stream", color = goldenAccent)
                    }
                } else {
                    TextButton(onClick = { viewModel.retryCurrentStream() }) {
                        Text(text = "Retry", color = goldenAccent)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPlaybackError() }) {
                    Text(text = "Dismiss", color = Color(0xFF9CA3AF))
                }
            }
        )
    }
}

private fun formatModernTime(ms: Long, showHours: Boolean = false): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (showHours || hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun PlayerProviderBadge(
    providerName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (providerName.isBlank()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
        )
        Text(
            text = providerName,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}
