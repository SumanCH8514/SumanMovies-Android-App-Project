package com.sumanonline.sumanmovies.ui.screens.player

import android.content.Context
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.util.DeviceCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isBuffering: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val isEnded: Boolean = false,
    val errorMessage: String? = null
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
    private var exoPlayer: ExoPlayer? = null
    private var hasAttemptedAudioFallback: Boolean = false

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<PlayerAudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<PlayerAudioTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<PlayerSubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<PlayerSubtitleTrack>> = _subtitleTracks.asStateFlow()

    private val _isSubtitlesEnabled = MutableStateFlow(true)
    val isSubtitlesEnabled: StateFlow<Boolean> = _isSubtitlesEnabled.asStateFlow()

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val renderersFactory = DefaultRenderersFactory(context).apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                setEnableDecoderFallback(true)
            }

            val trackSelector = DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setExceedRendererCapabilitiesIfNecessary(false)
                        .setExceedVideoConstraintsIfNecessary(false)
                        .setExceedAudioConstraintsIfNecessary(false)
                        .setPreferredAudioMimeTypes(
                            MimeTypes.AUDIO_AAC,
                            MimeTypes.AUDIO_MPEG,
                            MimeTypes.AUDIO_OPUS,
                            MimeTypes.AUDIO_VORBIS,
                            MimeTypes.AUDIO_FLAC,
                            MimeTypes.AUDIO_E_AC3,
                            MimeTypes.AUDIO_AC3
                        )
                        .setViewportSizeToPhysicalDisplaySize(this@ExoPlayerManager.context, true)
                )
            }
            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setSeekForwardIncrementMs(10000)
                .setSeekBackIncrementMs(10000)
                .build().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val isBuffering = playbackState == Player.STATE_BUFFERING
                            val isEnded = playbackState == Player.STATE_ENDED
                            _playbackState.value = _playbackState.value.copy(
                                isBuffering = isBuffering,
                                isEnded = isEnded,
                                durationMs = duration.coerceAtLeast(0L),
                                errorMessage = if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) null else _playbackState.value.errorMessage
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

                            val userMsg = formatUserPlaybackError(error)
                            _playbackState.value = _playbackState.value.copy(
                                isBuffering = false,
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
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
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
                    result.add(
                        PlayerAudioTrack(
                            id = id,
                            language = format.language,
                            label = format.label ?: "",
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
                    result.add(
                        PlayerSubtitleTrack(
                            id = id,
                            language = format.language,
                            label = format.label ?: "",
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

        if (result.isNotEmpty() && !isTextDisabled) {
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
            _subtitleTracks.value = result
            if (isTextDisabled) {
                _isSubtitlesEnabled.value = false
            }
        }
    }

    private fun formatSubtitleTrackName(format: Format, index: Int): String {
        val langCode = format.language?.trim()?.lowercase()
        val localeLanguage = if (!langCode.isNullOrBlank() && langCode != "und") {
            try {
                val loc = java.util.Locale.forLanguageTag(langCode)
                val name = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
                if (name.isNotBlank()) name else java.util.Locale(langCode).getDisplayLanguage(java.util.Locale.ENGLISH)
            } catch (_: Exception) {
                langCode.uppercase()
            }
        } else null

        val rawLabel = format.label?.takeIf { it.isNotBlank() }
        val isCodeOnly = rawLabel != null && rawLabel.length <= 3 && localeLanguage != null
        val isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
        val isHearingImpaired = (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0 ||
                (format.roleFlags and C.ROLE_FLAG_CAPTION) != 0

        val extra = when {
            isForced && isHearingImpaired -> "Forced • CC"
            isForced -> "Forced"
            isHearingImpaired -> "CC"
            else -> ""
        }

        val primaryName = if (isCodeOnly) localeLanguage else (rawLabel ?: localeLanguage ?: "Subtitle ${index + 1}")
        return if (extra.isNotBlank() && !primaryName.contains(extra, ignoreCase = true)) {
            "$primaryName ($extra)"
        } else {
            primaryName
        }
    }

    private fun formatAudioTrackName(format: Format, index: Int, isSupported: Boolean = true): String {
        val langCode = format.language?.trim()?.lowercase()
        val localeLanguage = if (!langCode.isNullOrBlank() && langCode != "und") {
            try {
                val loc = java.util.Locale.forLanguageTag(langCode)
                val name = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
                if (name.isNotBlank()) name else java.util.Locale(langCode).getDisplayLanguage(java.util.Locale.ENGLISH)
            } catch (_: Exception) {
                langCode.uppercase()
            }
        } else null

        val rawLabel = format.label?.takeIf { it.isNotBlank() }
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
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_DTS -> "DTS"
            else -> ""
        }

        val primaryName = rawLabel ?: localeLanguage ?: "Audio Track ${index + 1}"
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

    fun selectAudioTrack(track: PlayerAudioTrack) {
        val player = getPlayer()
        val override = TrackSelectionOverride(track.mediaTrackGroup, track.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(override)
            .apply {
                if (!track.language.isNullOrBlank()) {
                    setPreferredAudioLanguage(track.language)
                }
            }
            .build()

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
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .apply {
                    if (!track.language.isNullOrBlank()) {
                        setPreferredTextLanguage(track.language)
                    }
                }
                .build()
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
        startPositionMs: Long = 0L
    ) {
        hasAttemptedAudioFallback = false
        val player = getPlayer()
        _audioTracks.value = emptyList()

        _playbackState.value = _playbackState.value.copy(
            errorMessage = null,
            isBuffering = true
        )

        if (subtitles.isNotEmpty()) {
            _subtitleTracks.value = subtitles.mapIndexed { idx, sub ->
                val langCode = sub.language.trim().lowercase()
                val localeName = try {
                    val loc = java.util.Locale.forLanguageTag(langCode)
                    val disp = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
                    if (disp.isNotBlank()) disp else java.util.Locale(langCode).getDisplayLanguage(java.util.Locale.ENGLISH)
                } catch (_: Exception) {
                    sub.label
                }
                val dispName = if (localeName.isNotBlank()) localeName else sub.label.ifBlank { "Subtitle ${idx + 1}" }
                PlayerSubtitleTrack(
                    id = sub.id.ifBlank { "server_sub_$idx" },
                    language = sub.language,
                    label = sub.label,
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
        player.trackSelectionParameters = player.trackSelectionParameters
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
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !_isSubtitlesEnabled.value)
            .build()

        val mediaSource = createMediaSource(stream, subtitles)
        player.setMediaSource(mediaSource)
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    private fun createMediaSource(
        stream: StreamSource,
        subtitles: List<SubtitleTrack> = emptyList()
    ): MediaSource {
        val defaultUa = stream.headers["User-Agent"]
            ?: if (stream.provider == "moviebox") {
                "com.community.oneroom/50020120 (Linux; U; Android 12; en_US; 22101316G)"
            } else {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(defaultUa)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(25000)

        val headers = stream.headers.toMutableMap()
        if (!headers.containsKey("User-Agent")) {
            headers["User-Agent"] = defaultUa
        }
        if (stream.provider == "moviebox" && !headers.containsKey("Referer")) {
            if (stream.format.equals("DASH", ignoreCase = true) || stream.url.contains(".mpd")) {
                headers["Referer"] = "https://sportslive.wine"
            }
        }
        val cookie = headers["Cookie"]
        val edgeCookie = headers["Edge-Cache-Cookie"]
        if (cookie != null && edgeCookie == null && cookie.contains("Edge-Cache-Cookie=")) {
            headers["Edge-Cache-Cookie"] = cookie.substringAfter("Edge-Cache-Cookie=").substringBefore(";")
        } else if (edgeCookie != null && cookie == null) {
            headers["Cookie"] = if (edgeCookie.startsWith("Edge-Cache-Cookie=")) edgeCookie else "Edge-Cache-Cookie=$edgeCookie"
        }
        httpDataSourceFactory.setDefaultRequestProperties(headers)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mimeType = when {
            stream.format.equals("DASH", ignoreCase = true) || stream.url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            stream.format.equals("HLS", ignoreCase = true) || stream.url.contains(".m3u8") || stream.url.contains("/range/") -> MimeTypes.APPLICATION_M3U8
            stream.format.equals("MKV", ignoreCase = true) || stream.url.contains(".mkv") -> MimeTypes.VIDEO_MATROSKA
            stream.format.equals("MP4", ignoreCase = true) || stream.url.contains(".mp4") -> MimeTypes.VIDEO_MP4
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

        val mediaItem = MediaItem.Builder()
            .setUri(stream.url)
            .apply {
                if (mimeType != null) setMimeType(mimeType)
                if (subtitleConfigs.isNotEmpty()) setSubtitleConfigurations(subtitleConfigs)
            }
            .build()

        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
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

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun skipForward(seconds: Int = 10) {
        exoPlayer?.let { p ->
            val target = p.currentPosition + (seconds * 1000L)
            p.seekTo(target.coerceAtMost(p.duration))
        }
    }

    fun skipBackward(seconds: Int = 10) {
        exoPlayer?.let { p ->
            val target = p.currentPosition - (seconds * 1000L)
            p.seekTo(target.coerceAtLeast(0L))
        }
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
