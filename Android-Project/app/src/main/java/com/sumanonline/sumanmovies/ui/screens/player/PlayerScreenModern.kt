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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import com.sumanonline.sumanmovies.data.models.MediaSegment
import com.sumanonline.sumanmovies.data.models.MediaSegmentType
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import com.sumanonline.sumanmovies.data.models.StreamMirror
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.getDisplayCategory
import com.sumanonline.sumanmovies.data.models.getDisplayQuality
import com.sumanonline.sumanmovies.data.models.getDisplayTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val goldenAccent = Color(0xFFFFAD32)
private val darkDrawerBg = Color(0xFF121217)

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

    val junkKeywords = listOf("moviesmod", "vegamovies", "hdhub4u", "kmdl", ".plus", ".org", ".com", ".net", ".cc", ".site", ".xyz")
    val raw = (track.label.takeIf { it.isNotBlank() && junkKeywords.none { j -> it.contains(j, ignoreCase = true) } }
        ?: track.displayName.takeIf { junkKeywords.none { j -> it.contains(j, ignoreCase = true) } }
        ?: "").ifBlank { return null }

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
        .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
        .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
        .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
        .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*\(\d{4}\)"""), "")
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

        ModernPlayerLoader(
            uiState = uiState,
            playbackState = playbackState
        )

        ModernEmptyStreamsOverlay(
            uiState = uiState,
            onOpenServers = { viewModel.toggleStreamsDrawer() }
        )

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

                if (!playbackState.isBuffering) {
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

        ModernPlayerLoader(
            uiState = uiState,
            playbackState = playbackState
        )

        ModernEmptyStreamsOverlay(
            uiState = uiState,
            onOpenServers = { viewModel.toggleStreamsDrawer() }
        )

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

                if (!playbackState.isBuffering) {
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
                    if (uiState.hasPreviousEpisode || uiState.hasNextEpisode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.hasPreviousEpisode) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xD9121217))
                                        .border(
                                            BorderStroke(1.dp, Color(0xFF1F1F27)),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.playPreviousEpisode()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Episode",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Previous",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (uiState.hasNextEpisode) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xD9121217))
                                        .border(
                                            BorderStroke(1.dp, Color(0xFF1F1F27)),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.playNextEpisode()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Next Episode",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Episode",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
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

        val currentSeg = uiState.currentSkipSegment
        AnimatedVisibility(
            visible = currentSeg != null &&
                !uiState.isEpisodesDrawerOpen &&
                !uiState.isStreamsDrawerOpen &&
                !uiState.isAudioTracksModalOpen &&
                !uiState.isSubtitlesModalOpen &&
                !isSpeedDialogOpen,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 2 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 24.dp,
                    bottom = if (uiState.showControls) 88.dp else 28.dp
                )
        ) {
            if (currentSeg != null) {
                PlayerSkipButton(
                    segment = currentSeg,
                    hasNextEpisode = uiState.hasNextEpisode,
                    onSkip = { viewModel.skipCurrentSegment() },
                    onDismiss = { viewModel.dismissCurrentSkipSegment() }
                )
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
                        .border(BorderStroke(1.dp, Color(0xFF1F1F27)), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
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
                                            .background(if (isSelected) Color.White else Color(0xFF1F1F27))
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
                                            .background(if (isCurrentEp) goldenAccent.copy(alpha = 0.14f) else Color(0xFF16161D))
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
                                                .background(Color(0xFF181820))
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
                                                                listOf(Color(0xFF1F1F27), Color(0xFF121217))
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
                                            .background(if (isSelected) goldenAccent.copy(alpha = 0.12f) else Color(0xFF16161D))
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
                                                .background(Color(0xFF1F1F27))
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
                                                        .background(Color(0xFF1F1F27)),
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
                    .border(BorderStroke(1.dp, Color(0xFF1F1F27)), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
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
                            .background(Color(0xFF16161D))
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
                                .background(if (uiState.dialogueBoostEnabled) goldenAccent else Color(0xFF1F1F27))
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
                                        .background(if (isSelected) goldenAccent.copy(alpha = 0.12f) else Color(0xFF16161D))
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
                                    val trackText = run {
                                        val junk = listOf("moviesmod", "vegamovies", "hdhub4u", "kmdl", ".plus", ".org", ".com", ".net", ".cc", ".site", ".xyz")
                                        if (junk.any { track.displayName.contains(it, ignoreCase = true) }) {
                                            val lang = cleanAudioLanguageName(track) ?: "Audio Track"
                                            val details = track.displayName.substringAfter("(", "").substringBeforeLast(")")
                                            if (details.isNotBlank() && junk.none { details.contains(it, ignoreCase = true) }) "$lang ($details)" else lang
                                        } else {
                                            track.displayName
                                        }
                                    }
                                    Text(
                                        text = trackText,
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
                                                .background(Color(0xFF1F1F27))
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
                                        .background(Color(0xFF16161D))
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
                                            .background(Color(0xFF1F1F27))
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

    AnimatedVisibility(
        visible = uiState.isStreamsDrawerOpen,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        val currentServer = uiState.drawerSelectedServer
        val allStreamsForCurrentServer = uiState.serverStreamsMap[currentServer] ?: (if (uiState.selectedServer == currentServer) uiState.availableStreams else emptyList())
        val streamsForCurrentServer = remember(allStreamsForCurrentServer) { allStreamsForCurrentServer.filter { it.canStream() } }
        val isCurrentServerLoading = uiState.loadingServers.contains(currentServer)
        val availableQualities = remember(streamsForCurrentServer) { getAvailableQualitiesForServer(streamsForCurrentServer) }
        val activeQuality = uiState.drawerSelectedQuality?.takeIf { availableQualities.contains(it) } ?: availableQualities.firstOrNull() ?: "1080p"

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.toggleStreamsDrawer() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(500.dp)
                    .align(Alignment.CenterEnd)
                    .animateEnterExit(
                        enter = slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ),
                        exit = slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        )
                    )
                    .background(Color(0xFF121217))
                    .border(
                        BorderStroke(1.dp, Color(0xFF1F1F27)),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
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
                                text = "Quality & Servers",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select streaming server, resolution & source",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF181820))
                                .border(BorderStroke(1.dp, Color(0xFF22222B)), CircleShape)
                                .clickable { viewModel.toggleStreamsDrawer() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "STREAMING SERVER",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isAnime = uiState.mediaType == MediaType.ANIME ||
                            uiState.selectedServer == StreamingServer.REANIME ||
                            uiState.drawerSelectedServer == StreamingServer.REANIME ||
                            uiState.serverStreamsMap.containsKey(StreamingServer.REANIME) ||
                            uiState.mediaId.startsWith("reanime:") ||
                            com.sumanonline.sumanmovies.data.models.isAnimeCandidate(uiState.mediaTitle, uiState.mediaType, null, uiState.mediaId)

                        StreamingServer.getAvailableServers(isAnime, uiState.serverStreamsMap.containsKey(StreamingServer.REANIME)).forEach { server ->
                            val isViewed = (uiState.drawerSelectedServer == server)
                            val isPlaying = (uiState.selectedServer == server && uiState.currentStream != null)
                            val isLoading = uiState.loadingServers.contains(server)

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isViewed) goldenAccent.copy(alpha = 0.16f)
                                        else Color(0xFF16161D)
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isViewed) goldenAccent
                                            else Color(0xFF1F1F27)
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectDrawerServer(server) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
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
                                                .background(Color(0xFF22C55E))
                                        )
                                    } else if (isViewed) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(goldenAccent)
                                        )
                                    }
                                    Text(
                                        text = server.displayName,
                                        color = if (isViewed) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = if (isViewed) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(11.dp),
                                            color = goldenAccent,
                                            strokeWidth = 1.5.dp
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F1F27)))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "RESOLUTIONS",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isCurrentServerLoading && streamsForCurrentServer.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF16161D))
                                        )
                                    }
                                }
                            } else if (availableQualities.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No qualities",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.5.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableQualities) { quality ->
                                        val isQualitySelected = (activeQuality == quality)
                                        val linkCount = streamsForCurrentServer.count { normalizeQualityBucket(it) == quality }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isQualitySelected) goldenAccent.copy(alpha = 0.16f)
                                                    else Color(0xFF16161D)
                                                )
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        if (isQualitySelected) goldenAccent
                                                        else Color(0xFF1F1F27)
                                                    ),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { viewModel.selectDrawerQuality(quality) }
                                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = quality,
                                                        color = if (isQualitySelected) goldenAccent else Color.White,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isQualitySelected) FontWeight.Bold else FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "$linkCount ${if (linkCount == 1) "source" else "sources"}",
                                                        color = if (isQualitySelected) goldenAccent.copy(alpha = 0.85f) else Color(0xFF94A3B8),
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }
                                                if (isQualitySelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = goldenAccent,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF1F1F27)))
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            val streamsForQuality = streamsForCurrentServer.filter { normalizeQualityBucket(it) == activeQuality }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AVAILABLE SOURCES",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                if (streamsForQuality.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1F1F27))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${streamsForQuality.size}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

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
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.5.sp
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
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else if (streamsForQuality.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No $activeQuality streams found",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.5.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(streamsForQuality) { stream ->
                                        val isPlayingThis = (uiState.selectedServer == currentServer) && uiState.currentStream?.let { cur ->
                                            val sameUrl = cur.url == stream.url
                                            val sameLang = (cur.audioLangs.isNullOrBlank() && stream.audioLangs.isNullOrBlank()) || (cur.audioLangs == stream.audioLangs)
                                            val sameQuality = normalizeQualityBucket(cur) == normalizeQualityBucket(stream)
                                            val sameLabel = cur.label.isNotBlank() && stream.label.isNotBlank() && cur.label == stream.label
                                            (sameUrl && sameLang && sameQuality) || sameLabel
                                        } == true

                                        val formatBadge = stream.format.uppercase().trim().ifBlank { "MP4" }
                                        val qualityBadge = stream.quality.ifBlank { activeQuality }
                                        val audioText = stream.audioLangs?.trim()?.ifBlank { null }
                                        val isDub = audioText?.contains("dub", ignoreCase = true) == true
                                        val isSub = audioText?.contains("sub", ignoreCase = true) == true

                                        val displayTitle = when {
                                            !stream.title.isNullOrBlank() -> stream.title!!
                                            !stream.label.isBlank() -> stream.label
                                            else -> stream.getDisplayTitle(currentServer)
                                        }.removePrefix("$formatBadge ").trim()

                                        val isStreamOnline = stream.isOnline
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (!isStreamOnline) Color(0xFF141419)
                                                    else if (isPlayingThis) goldenAccent.copy(alpha = 0.12f)
                                                    else Color(0xFF16161D)
                                                )
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        if (!isStreamOnline) Color(0xFFEF4444).copy(alpha = 0.35f)
                                                        else if (isPlayingThis) goldenAccent
                                                        else Color(0xFF1F1F27)
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.selectStreamFromDrawer(stream)
                                                    },
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        if (!isStreamOnline) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                                                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "OFFLINE",
                                                                    color = Color(0xFFEF4444),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    letterSpacing = 0.5.sp
                                                                )
                                                            }
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF1F1F27))
                                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = formatBadge,
                                                                color = Color(0xFF93C5FD),
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF1F1F27))
                                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = qualityBadge,
                                                                color = Color(0xFFCBD5E1),
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        if (!stream.sizeFormatted.isNullOrBlank()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(goldenAccent.copy(alpha = 0.15f))
                                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = stream.sizeFormatted,
                                                                    color = goldenAccent,
                                                                    fontSize = 9.5.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }

                                                        if (!stream.codec.isNullOrBlank()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFF0F766E).copy(alpha = 0.25f))
                                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = stream.codec,
                                                                    color = Color(0xFF2DD4BF),
                                                                    fontSize = 9.5.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(5.dp))

                                                    Text(
                                                        text = displayTitle,
                                                        color = if (isPlayingThis) Color.White else Color(0xFFE2E8F0),
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isPlayingThis) FontWeight.SemiBold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (!audioText.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isDub) Icons.Default.Mic else Icons.Default.ClosedCaption,
                                                                contentDescription = null,
                                                                tint = if (isDub) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Text(
                                                                text = audioText,
                                                                color = if (isDub) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }

                                                if (isPlayingThis) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(goldenAccent.copy(alpha = 0.2f))
                                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    }
                                                } else if (!isStreamOnline) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF261418))
                                                            .clickable { viewModel.selectStreamFromDrawer(stream) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Stream offline",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF1F1F27))
                                                            .clickable { viewModel.selectStreamFromDrawer(stream) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Play stream",
                                                            tint = Color(0xFF94A3B8),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            if (stream.mirrors.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    stream.mirrors.forEachIndexed { mirrorIdx, mirror ->
                                                        val isMirrorOnline = mirror.isOnline
                                                        val isMirrorActive = isPlayingThis && (
                                                            (uiState.currentStream?.mirrorName != null && uiState.currentStream?.mirrorName == mirror.name) ||
                                                            (uiState.currentStream?.mirrorName == null && mirrorIdx == 0)
                                                        )
                                                        val isMirrorResolving = uiState.resolvingStreamUrl == mirror.url

                                                        val serverLabel = when {
                                                            mirror.name.contains("Server 1", ignoreCase = true) -> "Server 1"
                                                            mirror.name.contains("Server 2", ignoreCase = true) -> "Server 2"
                                                            mirror.name.contains("Server 3", ignoreCase = true) -> "Server 3"
                                                            else -> mirror.name
                                                        }
                                                        val serverSub = when {
                                                            mirror.name.contains("PixelServer", ignoreCase = true) || mirror.name.contains("PixelDrain", ignoreCase = true) -> "PixelServer"
                                                            mirror.name.contains("10Gbps", ignoreCase = true) -> "10Gbps Google"
                                                            mirror.name.contains("HubDrive", ignoreCase = true) -> "HubDrive Direct"
                                                            else -> ""
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(
                                                                    if (!isMirrorOnline) Color(0xFF1B1417)
                                                                    else if (isMirrorActive) goldenAccent.copy(alpha = 0.22f)
                                                                    else Color(0xFF16161D)
                                                                )
                                                                .border(
                                                                    BorderStroke(
                                                                        1.dp,
                                                                        if (!isMirrorOnline) Color(0xFFEF4444).copy(alpha = 0.35f)
                                                                        else if (isMirrorActive) goldenAccent
                                                                        else Color.White.copy(alpha = 0.1f)
                                                                    ),
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .clickable(enabled = !isMirrorResolving) {
                                                                    viewModel.selectStreamMirror(stream, mirror)
                                                                }
                                                                .padding(vertical = 5.dp, horizontal = 2.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isMirrorResolving) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(12.dp),
                                                                    strokeWidth = 1.5.dp,
                                                                    color = goldenAccent
                                                                )
                                                            } else {
                                                                Column(
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                    verticalArrangement = Arrangement.Center
                                                                ) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                                    ) {
                                                                        if (isMirrorActive) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Check,
                                                                                contentDescription = null,
                                                                                tint = goldenAccent,
                                                                                modifier = Modifier.size(9.dp)
                                                                            )
                                                                        }
                                                                        Text(
                                                                            text = serverLabel,
                                                                            color = if (isMirrorActive) goldenAccent else if (!isMirrorOnline) Color(0xFF94A3B8) else Color.White,
                                                                            fontSize = 9.5.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            maxLines = 1
                                                                        )
                                                                    }
                                                                    if (!isMirrorOnline) {
                                                                        Text(
                                                                            text = "OFFLINE",
                                                                            color = Color(0xFFEF4444),
                                                                            fontSize = 7.5.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            maxLines = 1
                                                                        )
                                                                    } else if (serverSub.isNotBlank()) {
                                                                        Text(
                                                                            text = serverSub,
                                                                            color = if (isMirrorActive) goldenAccent.copy(alpha = 0.85f) else Color(0xFF94A3B8),
                                                                            fontSize = 7.5.sp,
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
                    .border(BorderStroke(1.dp, Color(0xFF1F1F27)), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
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
                            .background(if (!uiState.isSubtitlesEnabled) goldenAccent.copy(alpha = 0.15f) else Color(0xFF16161D))
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
                                    .background(if (isSelected) goldenAccent.copy(alpha = 0.15f) else Color(0xFF16161D))
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
                                val subText = run {
                                    val junk = listOf("moviesmod", "vegamovies", "hdhub4u", "kmdl", ".plus", ".org", ".com", ".net", ".cc", ".site", ".xyz")
                                    if (junk.any { track.displayName.contains(it, ignoreCase = true) }) {
                                        val mapped = track.language?.let { COMMON_LANGUAGE_MAP[it.lowercase()] }
                                        mapped ?: "English"
                                    } else {
                                        track.displayName
                                    }
                                }
                                Text(
                                    text = subText,
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
            containerColor = Color(0xFF121217),
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
            containerColor = Color(0xFF121217),
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


@Composable
private fun ModernPlayerLoader(
    uiState: PlayerUiState,
    playbackState: PlayerPlaybackState,
    modifier: Modifier = Modifier
) {
    val hasActiveStream = uiState.currentStream != null
    val isActivelyPlaying = playbackState.isPlaying && !playbackState.isBuffering
    val isReadyOrPaused = !playbackState.isBuffering && (playbackState.durationMs > 0 || playbackState.currentPositionMs > 0)

    val isBusy = when {
        isActivelyPlaying -> false
        isReadyOrPaused && hasActiveStream -> false
        uiState.resolvingStreamUrl != null -> true
        !hasActiveStream -> uiState.isLoadingStreams || playbackState.isBuffering
        else -> playbackState.isBuffering
    }

    if (!isBusy) return

    var bufferingElapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isBusy) {
        bufferingElapsedSeconds = 0
        while (isBusy) {
            delay(1000L)
            bufferingElapsedSeconds++
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "playerLoaderPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val serverTitle = uiState.serverDisplayName ?: uiState.serverName ?: "Server"
    val isSeries = uiState.mediaType == MediaType.SERIES || uiState.mediaType == MediaType.ANIME || uiState.mediaType == MediaType.DRAMA

    val (primaryStatus, secondaryStatus) = when {
        uiState.resolvingStreamUrl != null -> {
            val mirrorTag = uiState.currentStream?.mirrorName ?: "Direct Mirror"
            Pair("Checking Host...", "Verifying $serverTitle • $mirrorTag")
        }
        !hasActiveStream && uiState.isLoadingStreams && isSeries && uiState.episodeNumber > 0 -> {
            Pair("Loading new Episode...", "Loading Episode ${uiState.episodeNumber} • $serverTitle")
        }
        !hasActiveStream && uiState.isLoadingStreams -> {
            Pair("Connecting to Server...", "Connecting to $serverTitle")
        }
        !uiState.loadingStageText.isNullOrBlank() -> {
            val sub = when {
                uiState.loadingStageText.contains("Episode", ignoreCase = true) -> {
                    "Loading Episode ${uiState.episodeNumber} • $serverTitle"
                }
                uiState.loadingStageText.contains("Host", ignoreCase = true) -> {
                    val mirrorTag = uiState.currentStream?.mirrorName ?: "Direct Mirror"
                    "Verifying $serverTitle • $mirrorTag"
                }
                uiState.loadingStageText.contains("Video", ignoreCase = true) -> {
                    val q = uiState.currentStream?.quality ?: "High-Speed Stream"
                    "Buffering $q • $serverTitle"
                }
                else -> "Connecting to $serverTitle"
            }
            Pair(uiState.loadingStageText, sub)
        }
        playbackState.isBuffering && playbackState.currentPositionMs <= 1500L -> {
            val q = uiState.currentStream?.quality ?: "High-Speed Stream"
            when {
                bufferingElapsedSeconds < 3 -> Pair("Loading Video...", "Preparing video player • $serverTitle")
                bufferingElapsedSeconds < 6 -> Pair("Buffering...", "Buffering $q • $serverTitle")
                else -> Pair("Optimizing Buffer...", "Connecting high-speed stream • $serverTitle")
            }
        }
        playbackState.isBuffering -> {
            val q = uiState.currentStream?.quality ?: "High-Speed Stream"
            when {
                bufferingElapsedSeconds < 3 -> Pair("Loading Video...", "Buffering $q • $serverTitle")
                else -> Pair("Buffering...", "Optimizing buffer with $serverTitle")
            }
        }
        else -> Pair("Loading...", "Please wait a moment")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE610131A))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)), RoundedCornerShape(18.dp))
                .padding(horizontal = 26.dp, vertical = 20.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(goldenAccent.copy(alpha = pulseAlpha))
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = goldenAccent.copy(alpha = 0.22f),
                    strokeWidth = 3.5.dp
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = goldenAccent,
                    strokeWidth = 3.5.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = primaryStatus,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = secondaryStatus,
                color = Color(0xFF9CA3AF),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayerSkipButton(
    segment: MediaSegment,
    hasNextEpisode: Boolean,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (segment.type) {
        MediaSegmentType.INTRO -> "Skip Intro"
        MediaSegmentType.RECAP -> "Skip Recap"
        MediaSegmentType.CREDITS -> if (hasNextEpisode && segment.endMs == null) "Next Episode" else "Skip Credits"
        MediaSegmentType.PREVIEW -> "Skip Preview"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE6141824))
            .border(
                BorderStroke(1.dp, Color(0xFFE5A00D).copy(alpha = 0.75f)),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onSkip)
            .padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = label,
                tint = Color(0xFFFFC043),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ModernEmptyStreamsOverlay(
    uiState: PlayerUiState,
    onOpenServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.currentStream != null || uiState.isLoadingStreams || uiState.resolvingStreamUrl != null || uiState.availableStreams.isNotEmpty()) {
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE610131A))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)), RoundedCornerShape(18.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Streams Found",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No stream sources found on ${uiState.selectedServer.displayName}",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(goldenAccent)
                    .clickable(onClick = onOpenServers)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Switch Server",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
