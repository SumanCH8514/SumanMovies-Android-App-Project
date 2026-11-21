package com.sumanonline.sumanmovies.ui.screens.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import com.sumanonline.sumanmovies.SumanMoviesApp
import com.sumanonline.sumanmovies.data.cache.PlayerCacheManager
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.util.DeviceCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.ArrayList

data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isBuffering: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val isEnded: Boolean = false,
    val errorMessage: String? = null,
    val recoveryMessage: String? = null,
    val isSeekable: Boolean = true,
    val isSeeking: Boolean = false
)

data class PlayerAudioTrack(
    val id: String,
    val language: String?,
    val label: String,
    val displayName: String,
    val channelCount: Int,
    val isSelected: Boolean,
    val isSupported: Boolean = true,
    val mimeType: String? = null,
    val mediaTrackGroup: TrackGroup,
    val trackIndex: Int
)

data class PlayerSubtitleTrack(
    val id: String,
    val language: String?,
    val label: String,
    val displayName: String,
    val isSelected: Boolean,
    val isForced: Boolean = false,
    val isHearingImpaired: Boolean = false,
    val mediaTrackGroup: TrackGroup? = null,
    val trackIndex: Int = 0,
    val externalUrl: String? = null
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoPlayerManager(private val context: Context) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exoPlayer: ExoPlayer? = null
    private var hasAttemptedAudioFallback: Boolean = false

    private val recoveryEngine = PlaybackRecoveryEngine(managerScope, maxRetryAttempts = 3)
    val recoveryStatus: StateFlow<RecoveryStatus> = recoveryEngine.status

    val diagnostics = PlaybackDiagnostics()
    val diagnosticsSummary: StateFlow<PlaybackDiagnosticsSummary> = diagnostics.summary

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<PlayerAudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<PlayerAudioTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<PlayerSubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<PlayerSubtitleTrack>> = _subtitleTracks.asStateFlow()

    private val _isSubtitlesEnabled = MutableStateFlow(true)
    val isSubtitlesEnabled: StateFlow<Boolean> = _isSubtitlesEnabled.asStateFlow()

    private var activeStream: StreamSource? = null
    private var activeSubtitles: List<SubtitleTrack> = emptyList()

    init {
        managerScope.launch {
            recoveryEngine.status.collectLatest { status ->
                when (status) {
                    is RecoveryStatus.Reconnecting -> {
                        _playbackState.value = _playbackState.value.copy(
                            isBuffering = true,
                            recoveryMessage = status.message
                        )
                    }
                    is RecoveryStatus.Idle -> {
                        if (_playbackState.value.recoveryMessage != null) {
                            _playbackState.value = _playbackState.value.copy(recoveryMessage = null)
                        }
                    }
                    is RecoveryStatus.Failed -> {
                        _playbackState.value = _playbackState.value.copy(
                            isBuffering = false,
                            recoveryMessage = null,
                            errorMessage = status.message
                        )
                    }
                }
            }
        }
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildVideoRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    eventHandler: Handler,
                    eventListener: VideoRendererEventListener,
                    allowedVideoJoiningTimeMs: Long,
                    out: ArrayList<Renderer>
                ) {
                    super.buildVideoRenderers(
                        context,
                        extensionRendererMode,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        allowedVideoJoiningTimeMs,
                        out
                    )
                    try {
                        if (FfmpegLibrary.isAvailable()) {
                            out.add(
                                ExperimentalFfmpegVideoRenderer(
                                    allowedVideoJoiningTimeMs,
                                    eventHandler,
                                    eventListener,
                                    50
                                )
                            )
                        }
                    } catch (_: Throwable) {}
                }
            }.apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setEnableDecoderFallback(true)
            }

            val trackSelector = DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setExceedRendererCapabilitiesIfNecessary(true)
                        .setExceedVideoConstraintsIfNecessary(true)
                        .setExceedAudioConstraintsIfNecessary(true)
                        .setPreferredAudioMimeTypes(
                            MimeTypes.AUDIO_E_AC3,
                            MimeTypes.AUDIO_AC3,
                            MimeTypes.AUDIO_DTS,
                            MimeTypes.AUDIO_TRUEHD,
                            MimeTypes.AUDIO_AAC,
                            MimeTypes.AUDIO_MPEG,
                            MimeTypes.AUDIO_OPUS,
                            MimeTypes.AUDIO_VORBIS,
                            MimeTypes.AUDIO_FLAC
                        )
                        .setViewportSizeToPhysicalDisplaySize(this@ExoPlayerManager.context, true)
                )
            }

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000,
                    60000,
                    1500,
                    3000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(30000, true)
                .build()

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setSeekForwardIncrementMs(10000)
                .setSeekBackIncrementMs(10000)
                .build().apply {
                    addAnalyticsListener(diagnostics)
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        }

                        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                            val seekable = isCurrentMediaItemSeekable && isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                            _playbackState.value = _playbackState.value.copy(
                                isSeekable = seekable,
                                durationMs = duration.coerceAtLeast(0L)
                            )
                        }

                        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                            val seekable = isCurrentMediaItemSeekable && availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                            _playbackState.value = _playbackState.value.copy(isSeekable = seekable)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val isBuffering = playbackState == Player.STATE_BUFFERING
                            val isEnded = playbackState == Player.STATE_ENDED
                            val isReady = playbackState == Player.STATE_READY
                            if (isReady) {
                                recoveryEngine.markSuccess()
                            }
                            val seekable = isCurrentMediaItemSeekable && isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                            _playbackState.value = _playbackState.value.copy(
                                isBuffering = isBuffering,
                                isEnded = isEnded,
                                isSeekable = seekable,
                                isSeeking = if (isReady) false else _playbackState.value.isSeeking,
                                durationMs = duration.coerceAtLeast(0L),
                                errorMessage = if (isReady || isBuffering) null else _playbackState.value.errorMessage,
                                recoveryMessage = if (isReady) null else _playbackState.value.recoveryMessage
                            )
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            val rawMsg = error.localizedMessage ?: ""
                            val causeMsg = error.cause?.localizedMessage ?: ""
                            val combined = "$rawMsg $causeMsg ${error.message ?: ""}"

                            val isAudioCodecError = combined.contains("MediaCodecAudioRenderer", ignoreCase = true) ||
                                combined.contains("audio/eac3", ignoreCase = true) ||
                                combined.contains("audio/ac3", ignoreCase = true) ||
                                combined.contains("audio/vnd.dts", ignoreCase = true) ||
                                (combined.contains("NO_UNSUPPORTED_TYPE", ignoreCase = true) && combined.contains("audio", ignoreCase = true))

                            if (isAudioCodecError && !hasAttemptedAudioFallback) {
                                hasAttemptedAudioFallback = true
                                val currentTracks = _audioTracks.value
                                val candidate = currentTracks.firstOrNull { it.isSupported && !it.isSelected }
                                    ?: run {
                                        val tracks = exoPlayer?.currentTracks
                                        if (tracks != null) extractSupportedAudioFallback(tracks) else null
                                    }

                                if (candidate != null) {
                                    val currentPos = currentPosition.coerceAtLeast(0L)
                                    selectAudioTrack(candidate)
                                    seekTo(currentPos)
                                    prepare()
                                    play()
                                    return
                                }
                            }

                            if (recoveryEngine.isRetryable(error) && activeStream != null) {
                                val currentPos = currentPosition.coerceAtLeast(0L)
                                val playWhenReadyState = playWhenReady
                                diagnostics.recordRetryAttempt()
                                recoveryEngine.scheduleRetry(
                                    error = error,
                                    currentPositionMs = currentPos,
                                    playWhenReady = playWhenReadyState,
                                    onExecuteRetry = { savedPos, shouldPlay ->
                                        activeStream?.let { stream ->
                                            prepareStream(stream, activeSubtitles, savedPos, shouldPlay)
                                        }
                                    },
                                    onExhausted = { finalReason ->
                                        val formatted = formatUserPlaybackError(error)
                                        _playbackState.value = _playbackState.value.copy(
                                            isBuffering = false,
                                            recoveryMessage = null,
                                            errorMessage = formatted.ifBlank { finalReason }
                                        )
                                    }
                                )
                                return
                            }

                            val userMsg = formatUserPlaybackError(error)
                            _playbackState.value = _playbackState.value.copy(
                                isBuffering = false,
                                recoveryMessage = null,
                                errorMessage = userMsg
                            )
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            extractAudioTracks(tracks)
                            extractSubtitleTracks(tracks)
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    private fun formatUserPlaybackError(error: PlaybackException): String {
        val rawMsg = error.localizedMessage ?: ""
        val causeMsg = error.cause?.localizedMessage ?: ""
        val combined = "$rawMsg $causeMsg ${error.message ?: ""}"

        return when {
            combined.contains("MediaCodecAudioRenderer", ignoreCase = true) ||
            combined.contains("audio/eac3", ignoreCase = true) ||
            combined.contains("audio/ac3", ignoreCase = true) ||
            combined.contains("audio/vnd.dts", ignoreCase = true) ||
            (combined.contains("NO_UNSUPPORTED_TYPE", ignoreCase = true) && combined.contains("audio", ignoreCase = true)) -> {
                "This stream's Dolby Digital (E-AC-3) audio format is not supported by your device's hardware audio decoder. Please switch to an alternative stream."
            }
            combined.contains("NO_EXCEEDS_CAPABILITIES", ignoreCase = true) ||
            combined.contains("DecoderInitializationException", ignoreCase = true) ||
            combined.contains("MediaCodecVideoRenderer", ignoreCase = true) -> {
                "This video resolution or codec exceeds your device's hardware decoder capabilities. Please switch to a compatible 1080p or 720p stream."
            }
            combined.contains("BehindLiveWindowException", ignoreCase = true) -> {
                "The live stream window has expired. Please retry or switch stream."
            }
            combined.contains("HttpDataSource", ignoreCase = true) ||
            combined.contains("403", ignoreCase = true) ||
            combined.contains("429", ignoreCase = true) -> {
                "Stream source rejected connection. Please try another stream."
            }
            rawMsg.isNotBlank() -> rawMsg
            else -> "Playback error occurred."
        }
    }

    private fun extractSupportedAudioFallback(tracks: Tracks): PlayerAudioTrack? {
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i) && !group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        return PlayerAudioTrack(
                            id = "audio_${groupIndex}_${i}_${format.id ?: ""}_${format.language ?: ""}",
                            language = format.language,
                            label = format.label ?: "",
                            displayName = formatAudioTrackName(format, 0, true),
                            channelCount = format.channelCount,
                            isSelected = false,
                            isSupported = true,
                            mimeType = format.sampleMimeType,
                            mediaTrackGroup = group.mediaTrackGroup,
                            trackIndex = i
                        )
                    }
                }
            }
        }
        return null
    }

    fun clearError() {
        recoveryEngine.cancel()
        _playbackState.value = _playbackState.value.copy(
            errorMessage = null,
            recoveryMessage = null
        )
    }

    private fun extractAudioTracks(tracks: Tracks) {
        val result = mutableListOf<PlayerAudioTrack>()
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val isSupported = group.isTrackSupported(i)
                    val id = "audio_${groupIndex}_${i}_${format.id ?: ""}_${format.language ?: ""}"
                    val displayName = formatAudioTrackName(format, result.size, isSupported)
                    val resolvedLang = resolveTrackLanguage(format, result.size, isAudio = true)
                    val resolvedLangCode = resolveAudioLanguageCode(format.language, format.label, resolvedLang)
                    val cleanLabel = if (isJunkOrPromotionalLabel(format.label)) "" else (format.label ?: "")
                    result.add(
                        PlayerAudioTrack(
                            id = id,
                            language = resolvedLangCode ?: format.language,
                            label = cleanLabel,
                            displayName = displayName,
                            channelCount = format.channelCount,
                            isSelected = isSelected,
                            isSupported = isSupported,
                            mimeType = format.sampleMimeType,
                            mediaTrackGroup = group.mediaTrackGroup,
                            trackIndex = i
                        )
                    )
                }
            }
        }
        _audioTracks.value = result
    }

    private fun extractSubtitleTracks(tracks: Tracks) {
        val player = exoPlayer
        val isTextDisabled = player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true
        val result = mutableListOf<PlayerSubtitleTrack>()
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = !isTextDisabled && group.isTrackSelected(i)
                    val id = "sub_${groupIndex}_${i}_${format.id ?: ""}_${format.language ?: ""}"
                    val displayName = formatSubtitleTrackName(format, result.size)
                    val isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
                    val isHearingImpaired = (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0 ||
                            (format.roleFlags and C.ROLE_FLAG_CAPTION) != 0
                    val resolvedLang = resolveTrackLanguage(format, result.size, isAudio = false)
                    val resolvedLangCode = resolveAudioLanguageCode(format.language, format.label, resolvedLang)
                    val cleanLabel = if (isJunkOrPromotionalLabel(format.label)) "" else (format.label ?: "")
                    result.add(
                        PlayerSubtitleTrack(
                            id = id,
                            language = resolvedLangCode ?: format.language,
                            label = cleanLabel,
                            displayName = displayName,
                            isSelected = isSelected,
                            isForced = isForced,
                            isHearingImpaired = isHearingImpaired,
                            mediaTrackGroup = group.mediaTrackGroup,
                            trackIndex = i
                        )
                    )
                }
            }
        }

        if (result.isNotEmpty()) {
            val hasSelected = result.any { it.isSelected }
            if (!hasSelected && _isSubtitlesEnabled.value && player != null) {
                val preferred = result.firstOrNull { it.language?.startsWith("en", ignoreCase = true) == true } ?: result.first()
                if (preferred.mediaTrackGroup != null) {
                    val override = TrackSelectionOverride(preferred.mediaTrackGroup, preferred.trackIndex)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(override)
                        .build()
                }
                val updated = result.map { it.copy(isSelected = it.id == preferred.id) }
                _subtitleTracks.value = updated
                _isSubtitlesEnabled.value = true
            } else {
                _subtitleTracks.value = result
                _isSubtitlesEnabled.value = hasSelected
            }
        } else {
            if (_subtitleTracks.value.isEmpty()) {
                _subtitleTracks.value = emptyList()
            }
            if (isTextDisabled) {
                _isSubtitlesEnabled.value = false
            }
        }
    }

    companion object {
        private val LANGUAGE_MAP = mapOf(
            "hin" to "Hindi", "hi" to "Hindi", "hindi" to "Hindi",
            "kor" to "Korean", "ko" to "Korean", "korean" to "Korean",
            "eng" to "English", "en" to "English", "english" to "English",
            "tam" to "Tamil", "ta" to "Tamil", "tamil" to "Tamil",
            "tel" to "Telugu", "te" to "Telugu", "telugu" to "Telugu",
            "mal" to "Malayalam", "ml" to "Malayalam", "malayalam" to "Malayalam",
            "kan" to "Kannada", "kn" to "Kannada", "kannada" to "Kannada",
            "ben" to "Bengali", "bn" to "Bengali", "bengali" to "Bengali",
            "mar" to "Marathi", "mr" to "Marathi", "marathi" to "Marathi",
            "pan" to "Punjabi", "pa" to "Punjabi", "punjabi" to "Punjabi",
            "guj" to "Gujarati", "gu" to "Gujarati", "gujarati" to "Gujarati",
            "urd" to "Urdu", "ur" to "Urdu", "urdu" to "Urdu",
            "spa" to "Spanish", "es" to "Spanish", "spanish" to "Spanish",
            "fra" to "French", "fre" to "French", "fr" to "French", "french" to "French",
            "ger" to "German", "deu" to "German", "de" to "German", "german" to "German",
            "jpn" to "Japanese", "ja" to "Japanese", "japanese" to "Japanese",
            "zho" to "Chinese", "chi" to "Chinese", "zh" to "Chinese", "chinese" to "Chinese",
            "ara" to "Arabic", "ar" to "Arabic", "arabic" to "Arabic",
            "rus" to "Russian", "ru" to "Russian", "russian" to "Russian",
            "ita" to "Italian", "it" to "Italian", "italian" to "Italian",
            "por" to "Portuguese", "pt" to "Portuguese", "portuguese" to "Portuguese",
            "tur" to "Turkish", "tr" to "Turkish", "turkish" to "Turkish",
            "tha" to "Thai", "th" to "Thai", "thai" to "Thai",
            "vie" to "Vietnamese", "vi" to "Vietnamese", "vietnamese" to "Vietnamese",
            "ind" to "Indonesian", "id" to "Indonesian", "indonesian" to "Indonesian",
            "pol" to "Polish", "pl" to "Polish", "polish" to "Polish",
            "ukr" to "Ukrainian", "uk" to "Ukrainian", "ukrainian" to "Ukrainian",
            "dut" to "Dutch", "nld" to "Dutch", "nl" to "Dutch", "dutch" to "Dutch",
            "swe" to "Swedish", "sv" to "Swedish", "swedish" to "Swedish",
            "nor" to "Norwegian", "no" to "Norwegian", "norwegian" to "Norwegian",
            "dan" to "Danish", "da" to "Danish", "danish" to "Danish",
            "fin" to "Finnish", "fi" to "Finnish", "finnish" to "Finnish",
            "fil" to "Filipino", "tl" to "Filipino", "tagalog" to "Tagalog",
            "nep" to "Nepali", "ne" to "Nepali", "nepali" to "Nepali",
            "sin" to "Sinhala", "si" to "Sinhala", "sinhala" to "Sinhala"
        )

        private val JUNK_KEYWORDS = listOf(
            "moviesmod", "vegamovies", "hdhub4u", "kmdl", "katmovie", "bollyflix",
            "filmyzilla", "worldfree4u", "1xbet", "telegram", "t.me", "join", "download",
            "channel", "visit", "www.", "official", "psa", "qxr", "yify", "yts", "galaxyrg",
            "rarbg", "pahe", "mkvcage", "torrent", ".com", ".plus", ".org", ".net", ".vip",
            ".in", ".link", ".cc", ".site", ".xyz", ".me", ".top", ".info", ".biz", ".co"
        )
    }

    private fun isJunkOrPromotionalLabel(label: String?): Boolean {
        if (label.isNullOrBlank()) return true
        val clean = label.trim().lowercase()
        if (JUNK_KEYWORDS.any { clean.contains(it) }) return true
        if (clean.matches(Regex(".*\\.[a-z]{2,6}(\\/.*)?$"))) return true
        return false
    }

    private fun extractLanguageFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        for ((code, name) in LANGUAGE_MAP) {
            if (name.length > 3 && lower.contains(name.lowercase())) {
                return name
            }
            if (code.length >= 2 && lower.matches(Regex(".*\\b${Regex.escape(code)}\\b.*"))) {
                return name
            }
        }
        return null
    }

    private fun resolveTrackLanguage(format: Format, index: Int, isAudio: Boolean): String {
        val langCode = format.language?.trim()?.lowercase()
        val mappedLang = if (!langCode.isNullOrBlank() && langCode != "und") {
            LANGUAGE_MAP[langCode] ?: try {
                val loc = java.util.Locale.forLanguageTag(langCode)
                val name = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
                if (name.isNotBlank() && !name.equals(langCode, ignoreCase = true)) {
                    name
                } else {
                    java.util.Locale(langCode).getDisplayLanguage(java.util.Locale.ENGLISH)
                }
            } catch (_: Exception) {
                null
            }
        } else null

        if (!mappedLang.isNullOrBlank() && mappedLang != "und" && !mappedLang.equals("undetermined", ignoreCase = true)) {
            return mappedLang
        }

        val rawLabel = format.label?.trim()
        val langFromLabel = extractLanguageFromText(rawLabel)
        if (langFromLabel != null) {
            return langFromLabel
        }

        if (isAudio) {
            val streamLangs = activeStream?.audioLangs
                ?.split(",", "/", "&", "+", "|")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
            if (streamLangs != null && index < streamLangs.size) {
                val candidate = streamLangs[index]
                val normalizedFallback = LANGUAGE_MAP[candidate.lowercase()] ?: extractLanguageFromText(candidate) ?: candidate
                if (!isJunkOrPromotionalLabel(normalizedFallback)) {
                    return normalizedFallback
                }
            }

            val titleLang = extractLanguageFromText(activeStream?.title)
            if (titleLang != null && index == 0) {
                return titleLang
            }
        }

        if (!rawLabel.isNullOrBlank() && !isJunkOrPromotionalLabel(rawLabel) && rawLabel.length in 2..30) {
            return rawLabel
        }

        if (!isAudio && index == 0) {
            return "English"
        }

        return if (isAudio) "Audio Track ${index + 1}" else "Subtitle ${index + 1}"
    }

    private fun formatSubtitleTrackName(format: Format, index: Int): String {
        val primaryName = resolveTrackLanguage(format, index, isAudio = false)
        val isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
        val isHearingImpaired = (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0 ||
                (format.roleFlags and C.ROLE_FLAG_CAPTION) != 0

        val extra = when {
            isForced && isHearingImpaired -> "Forced • CC"
            isForced -> "Forced"
            isHearingImpaired -> "CC"
            else -> ""
        }

        return if (extra.isNotBlank() && !primaryName.contains(extra, ignoreCase = true)) {
            "$primaryName ($extra)"
        } else {
            primaryName
        }
    }

    private fun formatAudioTrackName(format: Format, index: Int, isSupported: Boolean = true): String {
        val primaryName = resolveTrackLanguage(format, index, isAudio = true)
        val channelsStr = when (format.channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> if (format.channelCount > 0) "${format.channelCount} Channels" else ""
        }

        val codecStr = when (format.sampleMimeType) {
            MimeTypes.AUDIO_E_AC3 -> "DDP"
            MimeTypes.AUDIO_AC3 -> "DD"
            MimeTypes.AUDIO_AC4 -> "AC-4"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_DTS -> "DTS"
            MimeTypes.AUDIO_TRUEHD -> "TrueHD"
            else -> ""
        }

        val details = buildList {
            if (codecStr.isNotBlank() && !primaryName.contains(codecStr, ignoreCase = true)) add(codecStr)
            if (channelsStr.isNotBlank() && !primaryName.contains(channelsStr, ignoreCase = true)) add(channelsStr)
            if (!isSupported) add("Unsupported")
        }.joinToString(" • ")

        return if (details.isNotBlank()) {
            "$primaryName ($details)"
        } else {
            primaryName
        }
    }

    private fun resolveAudioLanguageCode(language: String?, label: String?, displayName: String): String? {
        val lang = language?.trim()?.lowercase()
        if (!lang.isNullOrBlank() && lang != "und") {
            return when (lang) {
                "hindi", "hin", "hi" -> "hi"
                "english", "eng", "en" -> "en"
                "tamil", "tam", "ta" -> "ta"
                "telugu", "tel", "te" -> "te"
                "malayalam", "mal", "ml" -> "ml"
                "kannada", "kan", "kn" -> "kn"
                "bengali", "ben", "bn" -> "bn"
                "marathi", "mar", "mr" -> "mr"
                "punjabi", "pan", "pa" -> "pa"
                "spanish", "spa", "es" -> "es"
                "french", "fra", "fre", "fr" -> "fr"
                "german", "ger", "deu", "de" -> "de"
                "japanese", "jpn", "ja" -> "ja"
                "korean", "kor", "ko" -> "ko"
                "chinese", "chi", "zho", "zh" -> "zh"
                "arabic", "ara", "ar" -> "ar"
                "russian", "rus", "ru" -> "ru"
                else -> lang
            }
        }
        val cleanLabel = if (isJunkOrPromotionalLabel(label)) "" else (label ?: "")
        val cleanDisplayName = if (isJunkOrPromotionalLabel(displayName)) "" else displayName
        val combined = "$cleanLabel $cleanDisplayName".lowercase()
        return when {
            combined.contains("hindi") -> "hi"
            combined.contains("english") -> "en"
            combined.contains("tamil") -> "ta"
            combined.contains("telugu") -> "te"
            combined.contains("malayalam") -> "ml"
            combined.contains("kannada") -> "kn"
            combined.contains("bengali") -> "bn"
            combined.contains("marathi") -> "mr"
            combined.contains("punjabi") -> "pa"
            combined.contains("spanish") -> "es"
            combined.contains("french") -> "fr"
            combined.contains("german") -> "de"
            combined.contains("japanese") -> "ja"
            combined.contains("korean") -> "ko"
            combined.contains("chinese") -> "zh"
            combined.contains("arabic") -> "ar"
            combined.contains("russian") -> "ru"
            else -> null
        }
    }

    fun selectAudioTrack(track: PlayerAudioTrack) {
        val player = getPlayer()
        val currentAudioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        var targetGroup = track.mediaTrackGroup
        var targetIndex = track.trackIndex

        val matched = currentAudioGroups.find { g ->
            g.mediaTrackGroup == track.mediaTrackGroup ||
            (g.length > track.trackIndex && g.getTrackFormat(track.trackIndex).id == track.id) ||
            (0 until g.length).any { i ->
                val f = g.getTrackFormat(i)
                (f.language != null && f.language.equals(track.language, ignoreCase = true)) ||
                (f.label != null && f.label.equals(track.label, ignoreCase = true))
            }
        }
        if (matched != null) {
            targetGroup = matched.mediaTrackGroup
            val idx = (0 until matched.length).firstOrNull { i ->
                val f = matched.getTrackFormat(i)
                f.id == track.id ||
                (f.language != null && f.language.equals(track.language, ignoreCase = true)) ||
                (f.label != null && f.label.equals(track.label, ignoreCase = true))
            } ?: track.trackIndex
            targetIndex = idx
        }

        val override = TrackSelectionOverride(targetGroup, targetIndex)
        val resolvedLang = resolveAudioLanguageCode(track.language, track.label, track.displayName)

        val paramsBuilder = (player.trackSelectionParameters as? DefaultTrackSelector.Parameters)?.buildUpon()
            ?.setExceedAudioConstraintsIfNecessary(true)
            ?: player.trackSelectionParameters.buildUpon()

        paramsBuilder
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(override)

        if (!resolvedLang.isNullOrBlank()) {
            paramsBuilder.setPreferredAudioLanguage(resolvedLang)
        }

        player.trackSelectionParameters = paramsBuilder.build()

        _audioTracks.value = _audioTracks.value.map {
            it.copy(isSelected = it.id == track.id)
        }
    }

    fun selectSubtitleTrack(track: PlayerSubtitleTrack) {
        val player = getPlayer()
        if (track.mediaTrackGroup != null) {
            val override = TrackSelectionOverride(track.mediaTrackGroup, track.trackIndex)
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .addOverride(override)
                .apply {
                    if (!track.language.isNullOrBlank()) {
                        setPreferredTextLanguage(track.language)
                    }
                }
                .build()
        } else {
            var matchedOverride: TrackSelectionOverride? = null
            for (group in player.currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val fmtLang = fmt.language?.lowercase()
                        val trackLang = track.language?.lowercase()
                        val fmtLabel = fmt.label?.lowercase()
                        val trackLabel = track.label.lowercase()
                        val trackDisp = track.displayName.lowercase()

                        val matches = (fmtLang != null && trackLang != null && (fmtLang == trackLang || fmtLang.startsWith(trackLang) || trackLang.startsWith(fmtLang))) ||
                                (!fmtLabel.isNullOrBlank() && (fmtLabel == trackLabel || fmtLabel == trackDisp))
                        if (matches) {
                            matchedOverride = TrackSelectionOverride(group.mediaTrackGroup, i)
                            break
                        }
                    }
                    if (matchedOverride != null) break
                }
            }

            val builder = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .apply {
                    if (!track.language.isNullOrBlank()) {
                        setPreferredTextLanguage(track.language)
                    }
                }

            if (matchedOverride != null) {
                builder.addOverride(matchedOverride)
            }
            player.trackSelectionParameters = builder.build()
        }

        _subtitleTracks.value = _subtitleTracks.value.map {
            it.copy(isSelected = it.id == track.id)
        }
        _isSubtitlesEnabled.value = true
    }

    fun disableSubtitles() {
        val player = getPlayer()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()

        _subtitleTracks.value = _subtitleTracks.value.map {
            it.copy(isSelected = false)
        }
        _isSubtitlesEnabled.value = false
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        if (!enabled) {
            disableSubtitles()
        } else {
            val currentTracks = _subtitleTracks.value
            val firstAvailable = currentTracks.firstOrNull()
            if (firstAvailable != null) {
                selectSubtitleTrack(firstAvailable)
            } else {
                val player = getPlayer()
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                _isSubtitlesEnabled.value = true
            }
        }
    }

    fun prepareStream(
        stream: StreamSource,
        subtitles: List<SubtitleTrack> = emptyList(),
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true
    ) {
        activeStream = stream
        activeSubtitles = subtitles
        hasAttemptedAudioFallback = false
        val player = getPlayer()
        _audioTracks.value = emptyList()

        diagnostics.resetSession()

        _playbackState.value = _playbackState.value.copy(
            errorMessage = null,
            recoveryMessage = null,
            isBuffering = true,
            currentPositionMs = startPositionMs.coerceAtLeast(0L),
            durationMs = 0L,
            bufferedPositionMs = 0L
        )

        if (subtitles.isNotEmpty()) {
            _subtitleTracks.value = subtitles.mapIndexed { idx, sub ->
                val langCode = sub.language.trim().lowercase()
                val mappedName = LANGUAGE_MAP[langCode] ?: try {
                    val loc = java.util.Locale.forLanguageTag(langCode)
                    val disp = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
                    if (disp.isNotBlank() && !disp.equals(langCode, ignoreCase = true)) disp else java.util.Locale(langCode).getDisplayLanguage(java.util.Locale.ENGLISH)
                } catch (_: Exception) {
                    null
                }
                val labelClean = if (isJunkOrPromotionalLabel(sub.label)) "" else sub.label
                val labelLang = extractLanguageFromText(sub.label)
                val dispName = mappedName ?: labelLang ?: labelClean.ifBlank { "Subtitle ${idx + 1}" }
                PlayerSubtitleTrack(
                    id = sub.id.ifBlank { "server_sub_$idx" },
                    language = sub.language,
                    label = labelClean,
                    displayName = dispName,
                    isSelected = idx == 0,
                    isForced = false,
                    isHearingImpaired = false,
                    mediaTrackGroup = null,
                    trackIndex = idx,
                    externalUrl = sub.url
                )
            }
        } else {
            _subtitleTracks.value = emptyList()
        }

        val supports4K = DeviceCapabilities.supports4KHevc()
        val (maxW, maxH) = when {
            (stream.quality.contains("2160") || stream.quality.contains("4k", ignoreCase = true)) && supports4K -> 3840 to 2160
            stream.quality.contains("2160") || stream.quality.contains("4k", ignoreCase = true) -> 1920 to 1080
            stream.quality.contains("1080") -> 1920 to 1080
            stream.quality.contains("720") -> 1280 to 720
            stream.quality.contains("480") -> 854 to 480
            stream.quality.contains("360") -> 640 to 360
            else -> if (supports4K) 3840 to 2160 else 1920 to 1080
        }
        val appPrefs = try {
            (context.applicationContext as? SumanMoviesApp)?.preferencesManager
        } catch (_: Exception) { null }

        val preferredAudio = appPrefs?.preferredAudioLanguage?.value
        val audioLangCode = when (preferredAudio?.lowercase()) {
            "english" -> "en"
            "hindi" -> "hi"
            "spanish" -> "es"
            "french" -> "fr"
            "arabic" -> "ar"
            "german" -> "de"
            "japanese" -> "ja"
            "korean" -> "ko"
            "chinese" -> "zh"
            "tamil" -> "ta"
            "telugu" -> "te"
            "bengali" -> "bn"
            else -> preferredAudio?.lowercase()
        }

        val preferredSub = appPrefs?.preferredSubtitleLanguage?.value
        val subLangCode = when (preferredSub?.lowercase()) {
            "english" -> "en"
            "hindi" -> "hi"
            "spanish" -> "es"
            "french" -> "fr"
            "arabic" -> "ar"
            "german" -> "de"
            "japanese" -> "ja"
            "korean" -> "ko"
            "chinese" -> "zh"
            "tamil" -> "ta"
            "telugu" -> "te"
            "bengali" -> "bn"
            else -> preferredSub?.lowercase()
        }

        val subtitlesByDefault = appPrefs?.subtitlesByDefault?.value ?: _isSubtitlesEnabled.value
        val defaultSpeed = appPrefs?.defaultPlaybackSpeed?.value ?: 1.0f

        val trackParamsBuilder = player.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(maxW, maxH)
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_AAC,
                MimeTypes.AUDIO_MPEG,
                MimeTypes.AUDIO_OPUS,
                MimeTypes.AUDIO_VORBIS,
                MimeTypes.AUDIO_FLAC,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3
            )
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesByDefault)

        if (!audioLangCode.isNullOrBlank() && audioLangCode != "none") {
            trackParamsBuilder.setPreferredAudioLanguage(audioLangCode)
        }
        if (subtitlesByDefault && !subLangCode.isNullOrBlank() && subLangCode != "none") {
            trackParamsBuilder.setPreferredTextLanguage(subLangCode)
        }

        player.trackSelectionParameters = trackParamsBuilder.build()
        if (defaultSpeed > 0f) {
            player.setPlaybackSpeed(defaultSpeed)
            _playbackState.value = _playbackState.value.copy(playbackSpeed = defaultSpeed)
        }

        val mediaSource = createMediaSource(stream, subtitles)
        player.setMediaSource(mediaSource, true)
        player.seekTo(startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private fun createMediaSource(
        stream: StreamSource,
        subtitles: List<SubtitleTrack> = emptyList()
    ): MediaSource {
        val httpDataSourceFactory = PlaybackNetworkConfig.buildHttpDataSourceFactory(stream)
        val cacheDataSourceFactory = PlayerCacheManager.buildCacheDataSourceFactory(context, httpDataSourceFactory)
        val dataSourceFactory = DefaultDataSource.Factory(context, cacheDataSourceFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mimeType = when {
            stream.format.equals("DASH", ignoreCase = true) || stream.url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            stream.format.equals("HLS", ignoreCase = true) || stream.url.contains(".m3u8") || stream.url.contains("/range/") -> MimeTypes.APPLICATION_M3U8
            else -> null
        }

        val subtitleConfigs = subtitles.map { sub ->
            val subMime = when {
                sub.format.equals("VTT", ignoreCase = true) || sub.url.contains(".vtt") -> MimeTypes.TEXT_VTT
                sub.format.equals("SRT", ignoreCase = true) || sub.url.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
                sub.format.equals("SSA", ignoreCase = true) || sub.url.contains(".ass") -> MimeTypes.TEXT_SSA
                sub.format.equals("TTML", ignoreCase = true) || sub.url.contains(".ttml") -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.TEXT_VTT
            }
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(subMime)
                .setLanguage(sub.language.lowercase())
                .setLabel(sub.label)
                .setSelectionFlags(if (sub.language.contains("en", ignoreCase = true)) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

        val uri = android.net.Uri.parse(stream.url)
        val customCacheKey = PlayerCacheManager.generateCacheKey(uri)

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setCustomCacheKey(customCacheKey)
            .apply {
                if (mimeType != null) setMimeType(mimeType)
                if (subtitleConfigs.isNotEmpty()) setSubtitleConfigurations(subtitleConfigs)
            }
            .build()

        return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem)
    }

    fun updatePosition() {
        exoPlayer?.let { p ->
            _playbackState.value = _playbackState.value.copy(
                currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = p.duration.coerceAtLeast(0L),
                bufferedPositionMs = p.bufferedPosition.coerceAtLeast(0L)
            )
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        if (!player.isCurrentMediaItemSeekable || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return
        }
        val duration = player.duration
        val target = if (duration > 0L) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        _playbackState.value = _playbackState.value.copy(
            isSeeking = true,
            isBuffering = true,
            currentPositionMs = target
        )
        player.seekTo(target)
    }

    fun skipForward(seconds: Int = 0) {
        val player = exoPlayer ?: return
        if (!player.isCurrentMediaItemSeekable || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return
        }
        val step = if (seconds > 0) seconds else try {
            (context.applicationContext as? SumanMoviesApp)?.preferencesManager?.doubleTapSeekDuration?.value ?: 10
        } catch (_: Exception) { 10 }
        val target = player.currentPosition + (step * 1000L)
        val dur = player.duration
        val maxPos = if (dur > 0L) dur else Long.MAX_VALUE
        val finalPos = target.coerceAtMost(maxPos)
        _playbackState.value = _playbackState.value.copy(
            isSeeking = true,
            isBuffering = true,
            currentPositionMs = finalPos
        )
        player.seekTo(finalPos)
    }

    fun skipBackward(seconds: Int = 0) {
        val player = exoPlayer ?: return
        if (!player.isCurrentMediaItemSeekable || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return
        }
        val step = if (seconds > 0) seconds else try {
            (context.applicationContext as? SumanMoviesApp)?.preferencesManager?.doubleTapSeekDuration?.value ?: 10
        } catch (_: Exception) { 10 }
        val target = player.currentPosition - (step * 1000L)
        val finalPos = target.coerceAtLeast(0L)
        _playbackState.value = _playbackState.value.copy(
            isSeeking = true,
            isBuffering = true,
            currentPositionMs = finalPos
        )
        player.seekTo(finalPos)
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    fun getVolume(): Float = exoPlayer?.volume ?: 1f

    fun toggleMute(): Boolean {
        val player = exoPlayer ?: return false
        val current = player.volume
        return if (current > 0f) {
            player.volume = 0f
            true
        } else {
            player.volume = 1f
            false
        }
    }

    fun resetPlayback() {
        activeStream = null
        recoveryEngine.cancel()
        exoPlayer?.pause()
        exoPlayer?.seekTo(0L)
        exoPlayer?.clearMediaItems()
        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = 0L,
            durationMs = 0L,
            bufferedPositionMs = 0L,
            isBuffering = true,
            isPlaying = false,
            errorMessage = null,
            recoveryMessage = null,
            isSeeking = false,
            isSeekable = true
        )
    }

    fun release() {
        recoveryEngine.cancel()
        managerScope.cancel()
        exoPlayer?.removeAnalyticsListener(diagnostics)
        exoPlayer?.release()
        exoPlayer = null
    }
}
