package com.sumanonline.sumanmovies.ui.screens.player

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.SumanMoviesApp
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaSegment
import com.sumanonline.sumanmovies.data.models.MediaSegmentType
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.StreamMirror
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.ui.screens.player.thumbnail.ThumbnailFrame
import com.sumanonline.sumanmovies.ui.screens.player.thumbnail.ThumbnailPreviewManager
import com.sumanonline.sumanmovies.ui.screens.player.thumbnail.ThumbnailRepository
import com.sumanonline.sumanmovies.util.StreamHealthValidator
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun normalizeQualityBucket(stream: StreamSource): String {
    val qRaw = stream.quality.lowercase().trim()
    when {
        qRaw.contains("2160") || qRaw.contains("4k") -> return "4K"
        qRaw.contains("1080") -> return "1080p"
        qRaw.contains("720") -> return "720p"
        qRaw.contains("480") -> return "480p"
        qRaw.contains("360") -> return "360p"
    }
    val q = "${stream.quality} ${stream.category} ${stream.title ?: ""} ${stream.label}".lowercase()
    return when {
        q.contains("2160") || q.contains("4k") || q.contains("uhd") -> "4K"
        q.contains("1080") || q.contains("fhd") || q.contains("full hd") -> "1080p"
        q.contains("480") || q.contains("sd") -> "480p"
        q.contains("360") -> "360p"
        q.contains("720") || q.contains("hd") -> "720p"
        else -> "Auto"
    }
}

fun getAvailableQualitiesForServer(streams: List<StreamSource>): List<String> {
    if (streams.isEmpty()) return emptyList()
    val present = streams.map { normalizeQualityBucket(it) }.toSet()
    val standardOrder = listOf("4K", "1080p", "720p", "480p", "360p", "Auto")
    val sorted = standardOrder.filter { present.contains(it) }
    val extra = present.filter { !standardOrder.contains(it) }
    return sorted + extra
}

data class PlayerUiState(
    val mediaTitle: String = "",
    val mediaId: String = "",
    val tmdbId: Int? = null,
    val mediaType: MediaType = MediaType.MOVIE,
    val releaseYear: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val episodeStillUrl: String? = null,
    val episodeTitle: String? = null,
    val serverName: String? = null,
    val serverDisplayName: String? = null,
    val selectedServer: StreamingServer = StreamingServer.MOVIEBOX,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val availableStreams: List<StreamSource> = emptyList(),
    val currentStream: StreamSource? = null,
    val showControls: Boolean = true,
    val isStreamsDrawerOpen: Boolean = false,
    val failedStreamUrls: Set<String> = emptySet(),
    val playbackErrorMessage: String? = null,
    val recoveryMessage: String? = null,
    val pendingFallbackStream: StreamSource? = null,
    val resolvingStreamUrl: String? = null,
    val isLoadingStreams: Boolean = false,
    val drawerSelectedServer: StreamingServer = StreamingServer.MOVIEBOX,
    val drawerSelectedQuality: String? = null,
    val serverStreamsMap: Map<StreamingServer, List<StreamSource>> = emptyMap(),
    val loadingServers: Set<StreamingServer> = emptySet(),
    val availableAudioTracks: List<PlayerAudioTrack> = emptyList(),
    val selectedAudioTrack: PlayerAudioTrack? = null,
    val isAudioTracksModalOpen: Boolean = false,
    val availableSubtitleTracks: List<PlayerSubtitleTrack> = emptyList(),
    val selectedSubtitleTrack: PlayerSubtitleTrack? = null,
    val isSubtitlesEnabled: Boolean = true,
    val isSubtitlesModalOpen: Boolean = false,
    val isEpisodesDrawerOpen: Boolean = false,
    val episodesDrawerTab: String = "EPISODES",
    val selectedDrawerSeason: Int = 1,
    val seasonsList: List<Season> = emptyList(),
    val currentSeasonEpisodes: List<Episode> = emptyList(),
    val hasNextEpisode: Boolean = false,
    val hasPreviousEpisode: Boolean = false,
    val dialogueBoostEnabled: Boolean = false,
    val loadingStageText: String? = null,
    val skipSegments: List<MediaSegment> = emptyList(),
    val currentSkipSegment: MediaSegment? = null,
    val autoSkipIntro: Boolean = false,
    val autoSkipCredits: Boolean = false
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val playerManager = ExoPlayerManager(application)
    private val app = application as SumanMoviesApp
    private val repository = app.mediaRepository
    private val connectivityObserver = NetworkConnectivityObserver(application)
    val thumbnailRepository = ThumbnailRepository(application)
    val thumbnailPreviewManager = ThumbnailPreviewManager(viewModelScope, thumbnailRepository)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _serverSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    private val _serverStreamsCache = ConcurrentHashMap<StreamingServer, List<StreamSource>>()
    private val _serverSubtitlesCache = ConcurrentHashMap<StreamingServer, List<SubtitleTrack>>()
    private val _seasonEpisodesCache = ConcurrentHashMap<Int, List<Episode>>()
    private var prefetchJob: Job? = null
    private var skipSegmentsJob: Job? = null
    private val autoSkippedSegmentTypes = mutableSetOf<MediaSegmentType>()

    private fun calculateAbsoluteEpisode(seasonNum: Int, epNum: Int): Int? {
        if (seasonNum <= 1) return epNum.takeIf { it > 0 }
        val seasons = _uiState.value.seasonsList
        if (seasons.isEmpty()) return epNum.takeIf { it > 0 }

        val currentEpisodes = _uiState.value.currentSeasonEpisodes
        val firstEpInSeason = currentEpisodes.firstOrNull { it.seasonNumber == seasonNum }?.episodeNumber
            ?: currentEpisodes.firstOrNull()?.episodeNumber

        val currentSeason = seasons.firstOrNull { it.seasonNumber == seasonNum }
        val currentSeasonCount = currentSeason?.episodeCount ?: 0

        val prevCount = seasons
            .filter { it.seasonNumber in 1 until seasonNum }
            .sumOf { it.episodeCount }

        val isAlreadyAbsolute = (firstEpInSeason != null && firstEpInSeason > 1) ||
                (prevCount > 0 && epNum > prevCount) ||
                (currentSeasonCount in 1..50 && epNum > currentSeasonCount) ||
                (seasonNum > 1 && epNum > 50)

        return if (isAlreadyAbsolute) {
            epNum
        } else if (prevCount > 0) {
            prevCount + epNum
        } else {
            epNum
        }
    }

    private fun prefetchOtherServers(
        title: String,
        seasonNum: Int,
        epNum: Int,
        mediaId: String,
        releaseYear: String?,
        currentServer: StreamingServer
    ) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val isAnime = _uiState.value.mediaType == MediaType.ANIME ||
                _uiState.value.mediaId.startsWith("reanime:") ||
                _uiState.value.serverStreamsMap.containsKey(StreamingServer.REANIME) ||
                com.sumanonline.sumanmovies.data.models.isAnimeCandidate(title, _uiState.value.mediaType, null, mediaId)
            val targets = StreamingServer.getAvailableServers(isAnime, _uiState.value.serverStreamsMap.containsKey(StreamingServer.REANIME)).filter { it != currentServer }
            val absEp = calculateAbsoluteEpisode(seasonNum, epNum)
            targets.forEach { srv ->
                launch(Dispatchers.IO) {
                    if (_serverStreamsCache.containsKey(srv)) return@launch
                    try {
                        val resolved = repository.resolveServer(srv, title, seasonNum, epNum, mediaId, releaseYear, false, absEp)
                        if (resolved.streams.isNotEmpty()) {
                            _serverStreamsCache[srv] = resolved.streams
                            validateStreamsHealthAsync(srv, resolved.streams)
                            if (resolved.subtitles.isNotEmpty()) {
                                _serverSubtitlesCache[srv] = resolved.subtitles
                            }
                            synchronized(_serverStreamsCache) {
                                val cur = _uiState.value.serverStreamsMap.toMutableMap()
                                cur[srv] = resolved.streams
                                val loading = _uiState.value.loadingServers - srv
                                val activeDrawerServer = _uiState.value.drawerSelectedServer
                                val autoQuality = if (activeDrawerServer == srv && _uiState.value.drawerSelectedQuality == null) {
                                    val qList = getAvailableQualitiesForServer(resolved.streams)
                                    qList.firstOrNull { it == "1080p" } ?: qList.firstOrNull()
                                } else {
                                    _uiState.value.drawerSelectedQuality
                                }
                                _uiState.value = _uiState.value.copy(
                                    serverStreamsMap = cur,
                                    loadingServers = loading,
                                    drawerSelectedQuality = autoQuality
                                )
                            }
                        } else {
                            val loading = _uiState.value.loadingServers - srv
                            _uiState.value = _uiState.value.copy(loadingServers = loading)
                        }
                    } catch (_: Exception) {
                        val loading = _uiState.value.loadingServers - srv
                        _uiState.value = _uiState.value.copy(loadingServers = loading)
                    }
                }
            }
        }
    }

    init {
        startPositionLoop()
        observePlaybackErrors()
        observeAudioTracks()
        observeSubtitleTracks()
        observeSkipPreferences()
        observeConnectivity()
    }

    private fun observeSkipPreferences() {
        viewModelScope.launch {
            app.preferencesManager.autoSkipIntro.collectLatest { autoIntro ->
                _uiState.value = _uiState.value.copy(autoSkipIntro = autoIntro)
            }
        }
        viewModelScope.launch {
            app.preferencesManager.autoSkipCredits.collectLatest { autoCredits ->
                _uiState.value = _uiState.value.copy(autoSkipCredits = autoCredits)
            }
        }
    }

    private fun observeConnectivity() {
        connectivityObserver.startObserving()
        viewModelScope.launch {
            var hadNetworkLoss = false
            connectivityObserver.status.collectLatest { netStatus ->
                when (netStatus) {
                    NetworkStatus.LOST, NetworkStatus.UNAVAILABLE -> {
                        hadNetworkLoss = true
                    }
                    NetworkStatus.AVAILABLE -> {
                        if (hadNetworkLoss) {
                            hadNetworkLoss = false
                            val pState = playerManager.playbackState.value
                            val curStream = _uiState.value.currentStream
                            if (curStream != null && (pState.errorMessage != null || (pState.isBuffering && !pState.isPlaying))) {
                                val resumePos = pState.currentPositionMs
                                playerManager.clearError()
                                playerManager.prepareStream(curStream, _serverSubtitles.value, resumePos, pState.isPlaying)
                            }
                        }
                    }
                }
            }
        }
    }

    val seekStepSeconds: Int
        get() = app.preferencesManager.doubleTapSeekDuration.value

    private fun matchesLanguageCode(pref: String, code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        val cleanCode = code.trim().lowercase()
        val p = pref.trim().lowercase()
        return when (p) {
            "english" -> cleanCode.startsWith("en")
            "hindi" -> cleanCode.startsWith("hi")
            "spanish" -> cleanCode.startsWith("es")
            "french" -> cleanCode.startsWith("fr")
            "arabic" -> cleanCode.startsWith("ar")
            "german" -> cleanCode.startsWith("de")
            "japanese" -> cleanCode.startsWith("ja")
            "korean" -> cleanCode.startsWith("ko")
            "chinese" -> cleanCode.startsWith("zh")
            "tamil" -> cleanCode.startsWith("ta")
            "telugu" -> cleanCode.startsWith("te")
            "bengali" -> cleanCode.startsWith("bn")
            "russian" -> cleanCode.startsWith("ru")
            "portuguese" -> cleanCode.startsWith("pt")
            "italian" -> cleanCode.startsWith("it")
            else -> cleanCode.contains(p) || p.contains(cleanCode)
        }
    }

    private var hasUserSelectedAudio = false
    private var hasAttemptedInitialAudio = false
    private var hasUserSelectedSubtitle = false
    private var hasAttemptedInitialSubtitle = false

    private fun observeAudioTracks() {
        viewModelScope.launch {
            playerManager.audioTracks.collectLatest { tracks ->
                val preferredAudio = app.preferencesManager.preferredAudioLanguage.value
                val selected = tracks.firstOrNull { it.isSelected }

                if (!hasUserSelectedAudio && !hasAttemptedInitialAudio && tracks.isNotEmpty() && preferredAudio.isNotBlank() && !preferredAudio.equals("None", ignoreCase = true)) {
                    val matchingTrack = tracks.firstOrNull { t ->
                        t.isSupported && (
                            t.displayName.contains(preferredAudio, ignoreCase = true) ||
                            t.label?.contains(preferredAudio, ignoreCase = true) == true ||
                            matchesLanguageCode(preferredAudio, t.language)
                        )
                    }
                    if (matchingTrack != null && !matchingTrack.isSelected && (selected == null || !selected.displayName.contains(preferredAudio, ignoreCase = true))) {
                        hasAttemptedInitialAudio = true
                        playerManager.selectAudioTrack(matchingTrack)
                    } else if (matchingTrack != null) {
                        hasAttemptedInitialAudio = true
                    }
                }

                val currentSelected = tracks.firstOrNull { it.isSelected } ?: selected
                _uiState.value = _uiState.value.copy(
                    availableAudioTracks = tracks,
                    selectedAudioTrack = currentSelected
                )
            }
        }
    }

    private fun observeSubtitleTracks() {
        viewModelScope.launch {
            playerManager.subtitleTracks.collectLatest { tracks ->
                val preferredSub = app.preferencesManager.preferredSubtitleLanguage.value
                val subtitlesByDefault = app.preferencesManager.subtitlesByDefault.value

                if (!hasUserSelectedSubtitle && !hasAttemptedInitialSubtitle && tracks.isNotEmpty() && subtitlesByDefault && preferredSub.isNotBlank() && !preferredSub.equals("None", ignoreCase = true)) {
                    val currentSelected = tracks.firstOrNull { it.isSelected }
                    val matchingTrack = tracks.firstOrNull { t ->
                        t.displayName.contains(preferredSub, ignoreCase = true) ||
                        t.label.contains(preferredSub, ignoreCase = true) ||
                        matchesLanguageCode(preferredSub, t.language)
                    }
                    if (matchingTrack != null && !matchingTrack.isSelected && (currentSelected == null || !currentSelected.displayName.contains(preferredSub, ignoreCase = true))) {
                        hasAttemptedInitialSubtitle = true
                        playerManager.selectSubtitleTrack(matchingTrack)
                    } else if (matchingTrack != null) {
                        hasAttemptedInitialSubtitle = true
                    }
                } else if (!hasUserSelectedSubtitle && !hasAttemptedInitialSubtitle && !subtitlesByDefault && tracks.any { it.isSelected }) {
                    hasAttemptedInitialSubtitle = true
                    playerManager.disableSubtitles()
                }

                val selected = tracks.firstOrNull { it.isSelected }
                val isEnabled = playerManager.isSubtitlesEnabled.value
                _uiState.value = _uiState.value.copy(
                    availableSubtitleTracks = tracks,
                    selectedSubtitleTrack = selected,
                    isSubtitlesEnabled = isEnabled
                )
            }
        }
        viewModelScope.launch {
            playerManager.isSubtitlesEnabled.collectLatest { isEnabled ->
                val selected = if (isEnabled) _uiState.value.availableSubtitleTracks.firstOrNull { it.isSelected } else null
                _uiState.value = _uiState.value.copy(
                    isSubtitlesEnabled = isEnabled,
                    selectedSubtitleTrack = selected
                )
            }
        }
    }

    private fun observePlaybackErrors() {
        viewModelScope.launch {
            var lastHandledError: String? = null
            var lastHandledStreamKey: String? = null

            playerManager.playbackState.collectLatest { state ->
                if ((state.isPlaying || state.durationMs > 0) && !state.isBuffering && _uiState.value.loadingStageText != null) {
                    _uiState.value = _uiState.value.copy(loadingStageText = null)
                }
                if (_uiState.value.recoveryMessage != state.recoveryMessage) {
                    _uiState.value = _uiState.value.copy(recoveryMessage = state.recoveryMessage)
                }
                val error = state.errorMessage
                val current = _uiState.value.currentStream

                if (error != null && current != null && state.recoveryMessage == null) {
                    val streamKey = "${current.url}#${current.quality}#${current.format}"
                    if (error == lastHandledError && streamKey == lastHandledStreamKey) {
                        return@collectLatest
                    }
                    lastHandledError = error
                    lastHandledStreamKey = streamKey

                    triggerAutoFallback(current, error)
                } else if (error == null) {
                    lastHandledError = null
                    lastHandledStreamKey = null
                }
            }
        }
    }

    private fun triggerAutoFallback(failedStream: StreamSource, reason: String) {
        val s = _uiState.value
        val streamKey = "${failedStream.url}#${failedStream.quality}#${failedStream.format}"
        val failed = s.failedStreamUrls.toMutableSet()
        failed.add(streamKey)
        failed.add(failedStream.url)

        val server = s.selectedServer
        val serverStreams = s.serverStreamsMap[server] ?: s.availableStreams
        val currentBucket = normalizeQualityBucket(failedStream)

        val sameQualityCandidate = serverStreams.firstOrNull { candidate ->
            val key = "${candidate.url}#${candidate.quality}#${candidate.format}"
            candidate.isOnline && !failed.contains(key) && !failed.contains(candidate.url) && normalizeQualityBucket(candidate) == currentBucket
        }

        val fallbackOrder = when (currentBucket) {
            "4K" -> listOf("1080p", "720p", "480p", "360p", "Auto")
            "1080p" -> listOf("720p", "480p", "360p", "4K", "Auto")
            "720p" -> listOf("480p", "360p", "1080p", "4K", "Auto")
            "480p" -> listOf("360p", "720p", "1080p", "4K", "Auto")
            else -> listOf("1080p", "720p", "480p", "360p", "Auto")
        }

        var nextStream: StreamSource? = sameQualityCandidate
        var nextQualityBucket: String? = currentBucket

        if (nextStream == null) {
            for (bucket in fallbackOrder) {
                val candidate = serverStreams.firstOrNull { c ->
                    val key = "${c.url}#${c.quality}#${c.format}"
                    c.isOnline && !failed.contains(key) && !failed.contains(c.url) && normalizeQualityBucket(c) == bucket
                }
                if (candidate != null) {
                    nextStream = candidate
                    nextQualityBucket = bucket
                    break
                }
            }
        }

        if (nextStream != null) {
            val currentPos = playerManager.playbackState.value.currentPositionMs
            _uiState.value = s.copy(
                failedStreamUrls = failed,
                drawerSelectedQuality = nextQualityBucket,
                playbackErrorMessage = null,
                pendingFallbackStream = null
            )
            playerManager.clearError()
            switchStream(nextStream, currentPos)
        } else {
            val isAnime = s.mediaType == MediaType.ANIME ||
                s.mediaId.startsWith("reanime:") ||
                s.serverStreamsMap.containsKey(StreamingServer.REANIME) ||
                com.sumanonline.sumanmovies.data.models.isAnimeCandidate(s.mediaTitle, s.mediaType, null, s.mediaId)
            val otherServers = StreamingServer.getAvailableServers(isAnime, s.serverStreamsMap.containsKey(StreamingServer.REANIME)).filter { it != server }
            var altServerStream: Pair<StreamingServer, StreamSource>? = null
            for (altSrv in otherServers) {
                val altStreams = s.serverStreamsMap[altSrv]
                if (!altStreams.isNullOrEmpty()) {
                    val candidate = altStreams.firstOrNull { c ->
                        val key = "${c.url}#${c.quality}#${c.format}"
                        c.isOnline && !failed.contains(key) && !failed.contains(c.url) && normalizeQualityBucket(c) == "1080p"
                    } ?: altStreams.firstOrNull { c ->
                        val key = "${c.url}#${c.quality}#${c.format}"
                        c.isOnline && !failed.contains(key) && !failed.contains(c.url)
                    }
                    if (candidate != null) {
                        altServerStream = altSrv to candidate
                        break
                    }
                }
            }

            if (altServerStream != null) {
                val (altSrv, candidate) = altServerStream
                val currentPos = playerManager.playbackState.value.currentPositionMs
                val serverSubs = _serverSubtitlesCache[altSrv] ?: _serverSubtitles.value
                _serverSubtitles.value = serverSubs
                val streamsForServer = s.serverStreamsMap[altSrv] ?: listOf(candidate)
                _uiState.value = s.copy(
                    failedStreamUrls = failed,
                    selectedServer = altSrv,
                    serverName = altSrv.name,
                    serverDisplayName = altSrv.displayName,
                    drawerSelectedServer = altSrv,
                    drawerSelectedQuality = normalizeQualityBucket(candidate),
                    availableStreams = streamsForServer,
                    playbackErrorMessage = null,
                    pendingFallbackStream = null
                )
                playerManager.clearError()
                switchStream(candidate, currentPos)
            } else {
                _uiState.value = s.copy(
                    failedStreamUrls = failed,
                    playbackErrorMessage = reason,
                    pendingFallbackStream = null
                )
            }
        }
    }

    private fun startPositionLoop() {
        viewModelScope.launch {
            var bufferingStallTicks = 0
            while (isActive) {
                val s = _uiState.value
                if (!s.isLoadingStreams && s.resolvingStreamUrl == null && s.pendingFallbackStream == null) {
                    playerManager.updatePosition()
                    val current = playerManager.playbackState.value
                    if (current.isBuffering && current.currentPositionMs <= 500L && s.currentStream != null && s.playbackErrorMessage == null && !current.isSeeking) {
                        bufferingStallTicks++
                        if (bufferingStallTicks >= 16) {
                            bufferingStallTicks = 0
                            triggerAutoFallback(s.currentStream, "Stream buffering timed out (stalled)")
                        }
                    } else if (!current.isBuffering || current.currentPositionMs > 500L || current.isSeeking) {
                        bufferingStallTicks = 0
                    }
                    if (current.durationMs > 0 && current.currentPositionMs > 0) {
                        if (s.mediaId.isNotBlank()) {
                            val curEp = s.currentSeasonEpisodes.firstOrNull {
                                it.seasonNumber == s.seasonNumber && it.episodeNumber == s.episodeNumber
                            }
                            val still = curEp?.stillUrl ?: s.episodeStillUrl
                            repository.saveWatchProgress(
                                mediaId = s.mediaId,
                                title = s.mediaTitle,
                                mediaType = s.mediaType,
                                posterUrl = s.posterUrl,
                                season = s.seasonNumber,
                                episode = s.episodeNumber,
                                positionMs = current.currentPositionMs,
                                durationMs = current.durationMs,
                                backdropUrl = s.backdropUrl,
                                stillUrl = still
                            )
                        }

                        val curStream = s.currentStream
                        if (curStream != null) {
                            thumbnailPreviewManager.startPrewarming(
                                mediaId = s.mediaId,
                                streamUrl = curStream.url,
                                headers = curStream.headers,
                                currentPosMs = current.currentPositionMs,
                                durationMs = current.durationMs,
                                posterUrl = s.backdropUrl ?: s.posterUrl
                            )
                        }

                        val pos = current.currentPositionMs
                        val segments = s.skipSegments
                        val activeSegment = if (pos > 0L && segments.isNotEmpty()) {
                            segments.firstOrNull { it.contains(pos) }
                        } else null

                        if (activeSegment != s.currentSkipSegment) {
                            _uiState.value = _uiState.value.copy(currentSkipSegment = activeSegment)
                        }

                        if (activeSegment != null) {
                            val autoIntro = s.autoSkipIntro
                            val autoCredits = s.autoSkipCredits
                            val shouldAutoSkip = when (activeSegment.type) {
                                MediaSegmentType.INTRO, MediaSegmentType.RECAP -> autoIntro && !autoSkippedSegmentTypes.contains(activeSegment.type)
                                MediaSegmentType.CREDITS -> autoCredits && !autoSkippedSegmentTypes.contains(activeSegment.type)
                                MediaSegmentType.PREVIEW -> false
                            }
                            if (shouldAutoSkip) {
                                autoSkippedSegmentTypes.add(activeSegment.type)
                                skipCurrentSegment()
                            }
                        } else {
                            segments.forEach { seg ->
                                if (pos < seg.startMs - 2000L) {
                                    autoSkippedSegmentTypes.remove(seg.type)
                                }
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun initPlayback(
        mediaId: String,
        title: String,
        mediaType: MediaType,
        posterUrl: String?,
        backdropUrl: String? = null,
        releaseYear: String? = null,
        initialStreamUrl: String?,
        initialFormat: String?,
        initialQuality: String? = null,
        initialProvider: String? = null,
        initialDirect: Boolean = false,
        initialIsLocker: Boolean = false,
        initialHeaders: Map<String, String> = emptyMap(),
        serverName: String? = null,
        serverDisplayName: String? = null,
        initialStreams: List<StreamSource> = emptyList(),
        initialSubtitles: List<SubtitleTrack> = emptyList(),
        seasonNumber: Int = 1,
        episodeNumber: Int = 1,
        initialEpisodeTitle: String? = null
    ) {
        hasUserSelectedAudio = false
        hasAttemptedInitialAudio = false
        hasUserSelectedSubtitle = false
        hasAttemptedInitialSubtitle = false
        playerManager.resetPlayback()
        _serverSubtitles.value = initialSubtitles
        val serverEnum = try {
            if (serverName != null) StreamingServer.valueOf(serverName)
            else repository.getSelectedServer(mediaId)
        } catch (_: Exception) {
            StreamingServer.MOVIEBOX
        }
        val resolvedServerDisplay = serverDisplayName ?: serverEnum.displayName

        val isSeries = mediaType == MediaType.SERIES || mediaType == MediaType.ANIME || mediaType == MediaType.DRAMA
        val cleanBase = title
            .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
            .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .trim()
            .ifBlank { title }
        val resolvedMediaTitle = if (isSeries) "$cleanBase - S${seasonNumber}E${episodeNumber}" else title
        val hasPrevInit = isSeries && (episodeNumber > 1 || seasonNumber > 1)
        val hasNextInit = isSeries

        autoSkippedSegmentTypes.clear()
        _seasonEpisodesCache.clear()
        val initialTmdbId = mediaId.toIntOrNull() ?: repository.getCachedItem(mediaId)?.tmdbId
        _uiState.value = _uiState.value.copy(
            mediaId = mediaId,
            tmdbId = initialTmdbId,
            mediaTitle = resolvedMediaTitle,
            mediaType = mediaType,
            releaseYear = releaseYear,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            episodeTitle = initialEpisodeTitle,
            serverName = serverEnum.name,
            serverDisplayName = resolvedServerDisplay,
            selectedServer = serverEnum,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            hasNextEpisode = hasNextInit,
            hasPreviousEpisode = hasPrevInit,
            skipSegments = emptyList(),
            currentSkipSegment = null
        )
        loadSkipSegments(mediaId, title, mediaType, releaseYear, seasonNumber, episodeNumber)

        if (isSeries) {
            loadSeriesMetadata(mediaId, title, mediaType, seasonNumber, episodeNumber, initialEpisodeTitle)
        } else if (mediaType != MediaType.TV_CHANNEL) {
            viewModelScope.launch {
                try {
                    val details = repository.getDetails(mediaId, mediaType)
                    val yr = details?.year ?: details?.releaseDate?.take(4)
                    val bd = details?.backdropUrl
                    val resolvedTmdb = details?.tmdbId
                    _uiState.value = _uiState.value.copy(
                        releaseYear = if (releaseYear.isNullOrBlank() && !yr.isNullOrBlank()) yr else _uiState.value.releaseYear,
                        backdropUrl = if (_uiState.value.backdropUrl.isNullOrBlank() && !bd.isNullOrBlank()) bd else _uiState.value.backdropUrl,
                        tmdbId = resolvedTmdb ?: _uiState.value.tmdbId
                    )
                } catch (_: Exception) {}
            }
        }

        viewModelScope.launch {
            val initialStream = if (!initialStreamUrl.isNullOrBlank()) {
                initialStreams.firstOrNull { it.url == initialStreamUrl }
                    ?: StreamSource(
                        quality = initialQuality ?: "HD",
                        format = initialFormat ?: "MP4",
                        url = initialStreamUrl,
                        label = "$resolvedServerDisplay - ${initialQuality ?: "HD"}",
                        provider = initialProvider ?: serverEnum.id,
                        directFile = initialDirect,
                        headers = initialHeaders,
                        isLockerUrl = initialIsLocker || repository.isLockerUrl(initialStreamUrl)
                    )
            } else null

            val mergedInitial = (if (initialStream != null) {
                listOf(initialStream) + initialStreams.filter { it.url != initialStream.url }
            } else {
                initialStreams
            }).map { s ->
                if (!s.isLockerUrl && repository.isLockerUrl(s.url)) {
                    s.copy(isLockerUrl = true)
                } else s
            }

            val supports4k = com.sumanonline.sumanmovies.util.DeviceCapabilities.supports4KHevc()
            val streamableInitial = mergedInitial.filter { it.canStream() }
            val fullMerged = streamableInitial.filter { it.sourceQualityTag != "Preview" }.ifEmpty { streamableInitial }
            val candidateInitial = if (!supports4k && initialStream != null && initialStream.canStream() && (initialStream.quality.contains("2160") || initialStream.quality.contains("4k", ignoreCase = true))) {
                fullMerged.firstOrNull { it.quality.contains("1080") || it.category == "1080p" }
                    ?: fullMerged.firstOrNull { !it.quality.contains("2160") && !it.quality.contains("4k", ignoreCase = true) }
                    ?: initialStream
            } else if (initialStream != null && initialStream.canStream() && initialStream.sourceQualityTag != "Preview") {
                initialStream
            } else {
                fullMerged.firstOrNull { it.quality.contains("1080") || it.category == "1080p" }
                    ?: fullMerged.firstOrNull()
            }

            val defaultStream = candidateInitial ?: if (!supports4k) {
                fullMerged.firstOrNull { it.quality.contains("1080") || it.category == "1080p" }
                    ?: fullMerged.firstOrNull { !it.quality.contains("2160") && !it.quality.contains("4k", ignoreCase = true) }
                    ?: fullMerged.firstOrNull()
            } else {
                fullMerged.firstOrNull()
            }

            val initialMap = if (mergedInitial.isNotEmpty()) {
                mapOf(serverEnum to mergedInitial)
            } else {
                emptyMap()
            }
            _serverStreamsCache.clear()
            _serverSubtitlesCache.clear()
            if (mergedInitial.isNotEmpty()) {
                _serverStreamsCache[serverEnum] = mergedInitial
                validateStreamsHealthAsync(serverEnum, mergedInitial)
            }
            if (initialSubtitles.isNotEmpty()) {
                _serverSubtitlesCache[serverEnum] = initialSubtitles
            }

            val initialQualities = getAvailableQualitiesForServer(mergedInitial)
            val initialQualityBucket = defaultStream?.let { normalizeQualityBucket(it) }
                ?: initialQualities.firstOrNull { it == "1080p" }
                ?: initialQualities.firstOrNull()

            _uiState.value = _uiState.value.copy(
                availableStreams = mergedInitial,
                currentStream = defaultStream,
                drawerSelectedServer = serverEnum,
                drawerSelectedQuality = initialQualityBucket,
                serverStreamsMap = initialMap
            )

            val episodeHistoryId = if (isSeries) "$mediaId:s${seasonNumber}e${episodeNumber}" else "$mediaId:s1e1"
            val savedProgress = repository.getWatchProgress(episodeHistoryId) ?: repository.getWatchProgress(mediaId)
            val resumePositionMs = if (savedProgress != null && !savedProgress.isCompleted && savedProgress.positionMs > 0) {
                savedProgress.positionMs
            } else {
                0L
            }

            val toPrepare = defaultStream
            if (toPrepare != null) {
                val isLocker = toPrepare.isLockerUrl || repository.isLockerUrl(toPrepare.url)
                if (isLocker) {
                    _uiState.value = _uiState.value.copy(resolvingStreamUrl = toPrepare.url)
                    val resolved = repository.resolveLockerUrl(toPrepare.url)
                    _uiState.value = _uiState.value.copy(resolvingStreamUrl = null)
                    if (resolved != null && resolved.url.isNotBlank() && !repository.isLockerUrl(resolved.url)) {
                        val finalStream = toPrepare.copy(
                            url = resolved.url,
                            headers = resolved.headers,
                            format = resolved.format,
                            codec = resolved.codec,
                            isLockerUrl = false
                        )
                        _uiState.value = _uiState.value.copy(
                            currentStream = finalStream,
                            availableStreams = _uiState.value.availableStreams.map {
                                if (it.url == toPrepare.url) finalStream else it
                            }
                        )
                        if (!finalStream.format.equals("EMBED", ignoreCase = true)) {
                            playerManager.prepareStream(finalStream, _serverSubtitles.value, resumePositionMs)
                        }
                    } else {
                        triggerAutoFallback(toPrepare, "Direct playback link unavailable")
                    }
                } else {
                    if (!toPrepare.format.equals("EMBED", ignoreCase = true)) {
                        playerManager.prepareStream(toPrepare, _serverSubtitles.value, resumePositionMs)
                    }
                }
            }

            val actualSeason = if (isSeries) seasonNumber.coerceAtLeast(1) else 0
            val actualEpisode = if (isSeries) episodeNumber.coerceAtLeast(1) else 0
            val absEp = calculateAbsoluteEpisode(actualSeason, actualEpisode)

            _uiState.value = _uiState.value.copy(isLoadingStreams = true)
            val resolvedState = repository.resolveServer(
                serverEnum,
                cleanBase,
                actualSeason,
                actualEpisode,
                mediaId,
                _uiState.value.releaseYear ?: releaseYear,
                false,
                absEp
            )
            val currentList = _uiState.value.availableStreams.toMutableList()
            for (newS in resolvedState.streams) {
                val existingIndex = currentList.indexOfFirst {
                    it.url == newS.url || (it.title != null && it.title == newS.title)
                }
                if (existingIndex >= 0) {
                    val existing = currentList[existingIndex]
                    if (!existing.isLockerUrl && newS.isLockerUrl) {
                        currentList[existingIndex] = existing.copy(
                            mirrors = if (existing.mirrors.isEmpty()) newS.mirrors else existing.mirrors,
                            sizeFormatted = existing.sizeFormatted ?: newS.sizeFormatted,
                            audioLangs = existing.audioLangs ?: newS.audioLangs,
                            codec = existing.codec ?: newS.codec
                        )
                    }
                } else {
                    currentList.add(newS)
                }
            }
            if (resolvedState.subtitles.isNotEmpty()) {
                _serverSubtitles.value = resolvedState.subtitles
                _serverSubtitlesCache[serverEnum] = resolvedState.subtitles
                val cur = _uiState.value.currentStream
                val currentPlayback = playerManager.playbackState.value
                val isActivelyPlaying = currentPlayback.isPlaying || currentPlayback.currentPositionMs > 1000L || currentPlayback.isSeeking
                if (cur != null && !isActivelyPlaying && initialSubtitles.isEmpty()) {
                    val pos = currentPlayback.currentPositionMs
                    playerManager.prepareStream(cur, resolvedState.subtitles, pos)
                }
            }
            _serverStreamsCache[serverEnum] = currentList
            validateStreamsHealthAsync(serverEnum, currentList)
            val curMap = _uiState.value.serverStreamsMap.toMutableMap()
            curMap[serverEnum] = currentList
            val updatedQualities = getAvailableQualitiesForServer(currentList)
            val autoQ = _uiState.value.drawerSelectedQuality ?: (updatedQualities.firstOrNull { it == "1080p" } ?: updatedQualities.firstOrNull())

            _uiState.value = _uiState.value.copy(
                availableStreams = currentList,
                serverStreamsMap = curMap,
                drawerSelectedServer = serverEnum,
                drawerSelectedQuality = autoQ,
                isLoadingStreams = false
            )
            val streamableCurrent = currentList.filter { it.canStream() }
            if (_uiState.value.currentStream == null && streamableCurrent.isNotEmpty()) {
                val first = streamableCurrent.first()
                switchStream(first, resumePositionMs)
            }
            prefetchOtherServers(
                title = cleanBase,
                seasonNum = actualSeason,
                epNum = actualEpisode,
                mediaId = mediaId,
                releaseYear = _uiState.value.releaseYear ?: releaseYear,
                currentServer = serverEnum
            )
        }
    }

    private fun loadSeriesMetadata(
        mediaId: String,
        title: String,
        mediaType: MediaType,
        seasonNum: Int,
        epNum: Int,
        initialEpTitle: String?
    ) {
        viewModelScope.launch {
            try {
                val details = repository.getDetails(mediaId, mediaType)
                val yr = details?.year ?: details?.releaseDate?.take(4)
                val resolvedTmdbId = details?.tmdbId
                if (!yr.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(releaseYear = yr)
                }
                if (!details?.backdropUrl.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(backdropUrl = details?.backdropUrl)
                }
                if (resolvedTmdbId != null) {
                    _uiState.value = _uiState.value.copy(tmdbId = resolvedTmdbId)
                }
                val loadedSeasons = details?.seasons?.filter { it.seasonNumber > 0 }?.ifEmpty { null }
                    ?: listOf(
                        Season(1, "Season 1", 8),
                        Season(2, "Season 2", 8),
                        Season(3, "Season 3", 6)
                    )
                _uiState.value = _uiState.value.copy(
                    seasonsList = loadedSeasons,
                    selectedDrawerSeason = seasonNum,
                    episodeTitle = initialEpTitle,
                    tmdbId = resolvedTmdbId ?: _uiState.value.tmdbId
                )
                loadDrawerSeasonEpisodes(seasonNum, epNum, resolvedTmdbId)
            } catch (_: Exception) {
                val defaultSeasons = listOf(
                    Season(1, "Season 1", 8),
                    Season(2, "Season 2", 8),
                    Season(3, "Season 3", 6)
                )
                _uiState.value = _uiState.value.copy(
                    seasonsList = defaultSeasons,
                    selectedDrawerSeason = seasonNum,
                    episodeTitle = initialEpTitle
                )
                loadDrawerSeasonEpisodes(seasonNum, epNum, _uiState.value.tmdbId)
            }
        }
    }

    fun loadDrawerSeasonEpisodes(seasonNum: Int, currentEpNum: Int? = null, tmdbIdOverride: Int? = null) {
        viewModelScope.launch {
            try {
                val effectiveTmdbId = tmdbIdOverride
                    ?: _uiState.value.tmdbId
                    ?: _uiState.value.mediaId.toIntOrNull()
                    ?: repository.getCachedItem(_uiState.value.mediaId)?.tmdbId

                if (effectiveTmdbId != null && effectiveTmdbId != _uiState.value.tmdbId) {
                    _uiState.value = _uiState.value.copy(tmdbId = effectiveTmdbId)
                }

                val cached = _seasonEpisodesCache[seasonNum]
                val episodes = if (!cached.isNullOrEmpty()) {
                    cached
                } else {
                    val fetched = if (effectiveTmdbId != null) {
                        repository.getSeasonEpisodes(effectiveTmdbId, seasonNum)
                    } else {
                        repository.getSeasonEpisodesForMedia(_uiState.value.mediaId, seasonNum)
                    }
                    if (fetched.isNotEmpty()) {
                        _seasonEpisodesCache[seasonNum] = fetched
                        fetched
                    } else {
                        emptyList()
                    }
                }

                val seasonObj = _uiState.value.seasonsList.firstOrNull { it.seasonNumber == seasonNum }
                val expectedEpCount = seasonObj?.episodeCount?.takeIf { it > 0 } ?: 10

                val finalEpisodes = episodes.ifEmpty {
                    (1..expectedEpCount).map { ep ->
                        Episode(
                            id = "${_uiState.value.mediaId}-$seasonNum-$ep",
                            episodeNumber = ep,
                            seasonNumber = seasonNum,
                            title = "Episode $ep",
                            overview = null,
                            stillUrl = null,
                            durationMinutes = null,
                            airDate = null
                        )
                    }
                }

                val targetEp = currentEpNum ?: _uiState.value.episodeNumber
                val epObj = finalEpisodes.firstOrNull { it.episodeNumber == targetEp }
                val epTitle = epObj?.title
                val epStill = epObj?.stillUrl
                val maxEp = finalEpisodes.maxOfOrNull { it.episodeNumber } ?: finalEpisodes.size
                val hasNext = targetEp < maxEp || _uiState.value.seasonsList.any { it.seasonNumber > seasonNum }
                val hasPrev = targetEp > 1 || seasonNum > 1

                _uiState.value = _uiState.value.copy(
                    currentSeasonEpisodes = finalEpisodes,
                    selectedDrawerSeason = seasonNum,
                    episodeTitle = epTitle ?: _uiState.value.episodeTitle,
                    episodeStillUrl = epStill ?: _uiState.value.episodeStillUrl,
                    hasNextEpisode = hasNext,
                    hasPreviousEpisode = hasPrev
                )
            } catch (_: Exception) {}
        }
    }

    fun playEpisode(seasonNum: Int, epNum: Int) {
        hasUserSelectedAudio = false
        hasAttemptedInitialAudio = false
        hasUserSelectedSubtitle = false
        hasAttemptedInitialSubtitle = false

        val oldState = _uiState.value
        val oldPos = playerManager.playbackState.value.currentPositionMs
        val oldDur = playerManager.playbackState.value.durationMs
        if (oldState.mediaId.isNotBlank() && oldDur > 0L && oldPos > 0L) {
            val oldEp = oldState.currentSeasonEpisodes.firstOrNull {
                it.seasonNumber == oldState.seasonNumber && it.episodeNumber == oldState.episodeNumber
            }
            val still = oldEp?.stillUrl ?: oldState.episodeStillUrl
            viewModelScope.launch {
                repository.saveWatchProgress(
                    mediaId = oldState.mediaId,
                    title = oldState.mediaTitle,
                    mediaType = oldState.mediaType,
                    posterUrl = oldState.posterUrl,
                    season = oldState.seasonNumber,
                    episode = oldState.episodeNumber,
                    positionMs = oldPos,
                    durationMs = oldDur,
                    backdropUrl = oldState.backdropUrl,
                    stillUrl = still
                )
            }
        }
        playerManager.resetPlayback()

        val cleanBase = _uiState.value.mediaTitle
            .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
            .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .trim()
            .ifBlank { _uiState.value.mediaTitle }
        val updatedTitle = "$cleanBase - S${seasonNum}E${epNum}"

        val isSeries = _uiState.value.mediaType == MediaType.SERIES || _uiState.value.mediaType == MediaType.ANIME || _uiState.value.mediaType == MediaType.DRAMA
        val curMaxEp = _uiState.value.currentSeasonEpisodes.maxOfOrNull { it.episodeNumber }
            ?: (_uiState.value.seasonsList.firstOrNull { it.seasonNumber == seasonNum }?.episodeCount?.takeIf { it > 0 })
            ?: (epNum + 1)
        val hasNextImmediate = isSeries && (epNum < curMaxEp || _uiState.value.seasonsList.any { it.seasonNumber > seasonNum })
        val hasPrevImmediate = isSeries && (epNum > 1 || seasonNum > 1)

        val cachedSeasonEps = _seasonEpisodesCache[seasonNum] ?: _uiState.value.currentSeasonEpisodes
        val matchedEp = cachedSeasonEps.firstOrNull { it.episodeNumber == epNum && it.seasonNumber == seasonNum }
        val epTitle = matchedEp?.title
        val epStill = matchedEp?.stillUrl
        autoSkippedSegmentTypes.clear()
        _uiState.value = _uiState.value.copy(
            mediaTitle = updatedTitle,
            seasonNumber = seasonNum,
            episodeNumber = epNum,
            episodeTitle = epTitle,
            episodeStillUrl = epStill,
            hasNextEpisode = hasNextImmediate,
            hasPreviousEpisode = hasPrevImmediate,
            currentStream = null,
            availableStreams = emptyList(),
            isEpisodesDrawerOpen = false,
            isLoadingStreams = true,
            loadingStageText = "Loading new Episode...",
            skipSegments = emptyList(),
            currentSkipSegment = null
        )
        loadSkipSegments(
            mediaId = _uiState.value.mediaId,
            title = cleanBase,
            type = _uiState.value.mediaType,
            year = _uiState.value.releaseYear,
            seasonNumber = seasonNum,
            episodeNumber = epNum
        )
        viewModelScope.launch {
            _serverStreamsCache.clear()
            _serverSubtitlesCache.clear()
            val absEp = calculateAbsoluteEpisode(seasonNum, epNum)
            val resolvedState = repository.resolveServer(
                _uiState.value.selectedServer,
                cleanBase,
                seasonNum,
                epNum,
                _uiState.value.mediaId,
                _uiState.value.releaseYear,
                false,
                absEp
            )
            _serverStreamsCache[_uiState.value.selectedServer] = resolvedState.streams
            if (resolvedState.subtitles.isNotEmpty()) {
                _serverSubtitles.value = resolvedState.subtitles
                _serverSubtitlesCache[_uiState.value.selectedServer] = resolvedState.subtitles
            }
            val curMap = mapOf(_uiState.value.selectedServer to resolvedState.streams)
            val qList = getAvailableQualitiesForServer(resolvedState.streams)
            val defaultQ = qList.firstOrNull { it == "1080p" } ?: qList.firstOrNull()

            _uiState.value = _uiState.value.copy(
                availableStreams = resolvedState.streams,
                serverStreamsMap = curMap,
                drawerSelectedServer = _uiState.value.selectedServer,
                drawerSelectedQuality = defaultQ,
                isLoadingStreams = false
            )
            val first = resolvedState.streams.firstOrNull { it.sourceQualityTag != "Preview" }
                ?: resolvedState.streams.firstOrNull()
            if (first != null) {
                val targetHistoryId = "${_uiState.value.mediaId}:s${seasonNum}e${epNum}"
                val savedProgress = repository.getWatchProgress(targetHistoryId)
                val resumePos = if (savedProgress != null && !savedProgress.isCompleted && savedProgress.positionMs > 10000L && (savedProgress.durationMs <= 0L || savedProgress.positionMs < (savedProgress.durationMs * 0.95))) {
                    savedProgress.positionMs
                } else {
                    0L
                }
                switchStream(first, resumePos)
            }
            prefetchOtherServers(
                title = cleanBase,
                seasonNum = seasonNum,
                epNum = epNum,
                mediaId = _uiState.value.mediaId,
                releaseYear = _uiState.value.releaseYear,
                currentServer = _uiState.value.selectedServer
            )
        }
        loadDrawerSeasonEpisodes(seasonNum, epNum, _uiState.value.tmdbId)
    }

    fun playNextEpisode() {
        val curSeason = _uiState.value.seasonNumber
        val curEp = _uiState.value.episodeNumber
        val maxEpInSeason = _uiState.value.currentSeasonEpisodes.maxOfOrNull { it.episodeNumber } ?: 8
        if (curEp < maxEpInSeason) {
            playEpisode(curSeason, curEp + 1)
        } else {
            val nextSeason = _uiState.value.seasonsList.firstOrNull { it.seasonNumber > curSeason }?.seasonNumber ?: (curSeason + 1)
            val isContinuous = curEp > 50 || curEp > (_uiState.value.seasonsList.firstOrNull { it.seasonNumber == curSeason }?.episodeCount ?: 0)
            val targetEp = if (isContinuous) curEp + 1 else 1
            playEpisode(nextSeason, targetEp)
        }
    }

    fun playPreviousEpisode() {
        val curSeason = _uiState.value.seasonNumber
        val curEp = _uiState.value.episodeNumber
        val minEpInSeason = _uiState.value.currentSeasonEpisodes.minOfOrNull { it.episodeNumber } ?: 1
        if (curEp > minEpInSeason) {
            playEpisode(curSeason, curEp - 1)
        } else if (curSeason > 1) {
            val prevSeason = _uiState.value.seasonsList.lastOrNull { it.seasonNumber < curSeason }?.seasonNumber ?: (curSeason - 1)
            val prevSeasonEpCount = _seasonEpisodesCache[prevSeason]?.maxOfOrNull { it.episodeNumber }
                ?: _uiState.value.seasonsList.firstOrNull { it.seasonNumber == prevSeason }?.episodeCount
                ?: 1
            playEpisode(prevSeason, prevSeasonEpCount)
        }
    }

    fun toggleEpisodesDrawer() {
        val next = !_uiState.value.isEpisodesDrawerOpen
        val currentSeason = _uiState.value.seasonNumber
        _uiState.value = _uiState.value.copy(
            isEpisodesDrawerOpen = next,
            selectedDrawerSeason = if (next) currentSeason else _uiState.value.selectedDrawerSeason,
            episodesDrawerTab = if (next) "EPISODES" else _uiState.value.episodesDrawerTab,
            isStreamsDrawerOpen = false,
            isAudioTracksModalOpen = false,
            isSubtitlesModalOpen = false
        )
        if (next && _uiState.value.currentSeasonEpisodes.none { it.seasonNumber == currentSeason }) {
            loadDrawerSeasonEpisodes(currentSeason, null, _uiState.value.tmdbId)
        }
    }

    fun setEpisodesDrawerTab(tab: String) {
        _uiState.value = _uiState.value.copy(episodesDrawerTab = tab)
    }

    fun selectDrawerSeason(seasonNum: Int) {
        _uiState.value = _uiState.value.copy(
            selectedDrawerSeason = seasonNum,
            episodesDrawerTab = "EPISODES"
        )
        loadDrawerSeasonEpisodes(seasonNum, null, _uiState.value.tmdbId)
    }

    fun toggleDialogueBoost() {
        _uiState.value = _uiState.value.copy(
            dialogueBoostEnabled = !_uiState.value.dialogueBoostEnabled
        )
    }

    fun selectDrawerServer(server: StreamingServer, forceReload: Boolean = false) {
        val s = _uiState.value
        if (!forceReload && s.drawerSelectedServer == server && s.serverStreamsMap.containsKey(server)) return

        val cached = if (forceReload) null else _serverStreamsCache[server]
        if (cached != null && cached.isNotEmpty()) {
            val qualities = getAvailableQualitiesForServer(cached)
            val defaultQuality = qualities.firstOrNull { it == "1080p" } ?: qualities.firstOrNull()
            _uiState.value = s.copy(
                drawerSelectedServer = server,
                drawerSelectedQuality = defaultQuality
            )
        } else {
            if (forceReload) {
                _serverStreamsCache.remove(server)
            }
            val curMap = s.serverStreamsMap.toMutableMap()
            if (forceReload) {
                curMap.remove(server)
            }
            _uiState.value = s.copy(
                serverStreamsMap = curMap,
                drawerSelectedServer = server,
                drawerSelectedQuality = null,
                loadingServers = s.loadingServers + server
            )
            viewModelScope.launch(Dispatchers.IO) {
                val isSeries = s.mediaType == MediaType.SERIES || s.mediaType == MediaType.ANIME || s.mediaType == MediaType.DRAMA
                val cleanTitle = if (isSeries) {
                    s.mediaTitle
                        .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
                        .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
                        .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
                        .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""\s*\(\d{4}\)"""), "")
                        .trim()
                        .ifBlank { s.mediaTitle }
                } else {
                    s.mediaTitle
                }
                val actualSeason = if (isSeries) s.seasonNumber.coerceAtLeast(1) else 0
                val actualEpisode = if (isSeries) s.episodeNumber.coerceAtLeast(1) else 0
                val absEp = calculateAbsoluteEpisode(actualSeason, actualEpisode)
                try {
                    val resolvedState = repository.resolveServer(
                        server,
                        cleanTitle,
                        actualSeason,
                        actualEpisode,
                        s.mediaId,
                        s.releaseYear,
                        false,
                        absEp
                    )
                    _serverStreamsCache[server] = resolvedState.streams
                    validateStreamsHealthAsync(server, resolvedState.streams)
                    if (resolvedState.subtitles.isNotEmpty()) {
                        _serverSubtitlesCache[server] = resolvedState.subtitles
                    }
                    val cur = _uiState.value.serverStreamsMap.toMutableMap()
                    cur[server] = resolvedState.streams
                    val loading = _uiState.value.loadingServers - server
                    val qualities = getAvailableQualitiesForServer(resolvedState.streams)
                    val defaultQuality = qualities.firstOrNull { it == "1080p" } ?: qualities.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        serverStreamsMap = cur,
                        loadingServers = loading,
                        drawerSelectedQuality = if (_uiState.value.drawerSelectedServer == server) defaultQuality else _uiState.value.drawerSelectedQuality
                    )
                } catch (_: Exception) {
                    val loading = _uiState.value.loadingServers - server
                    val cur = _uiState.value.serverStreamsMap.toMutableMap()
                    cur[server] = emptyList()
                    _uiState.value = _uiState.value.copy(serverStreamsMap = cur, loadingServers = loading)
                }
            }
        }
    }

    fun retryFetchServerStreams(server: StreamingServer) {
        selectDrawerServer(server, forceReload = true)
    }

    fun selectDrawerQuality(quality: String) {
        _uiState.value = _uiState.value.copy(drawerSelectedQuality = quality)
    }

    private fun validateStreamsHealthAsync(server: StreamingServer, streams: List<StreamSource>) {
        viewModelScope.launch(Dispatchers.IO) {
            var changed = false
            val checkedStreams = streams.map { stream ->
                var s = stream
                val isPd = s.url.contains("pixeldrain.com") || s.url.contains("pixeldrain.dev")
                if (isPd) {
                    val norm = StreamHealthValidator.normalizeDirectUrl(s.url)
                    val online = StreamHealthValidator.isStreamableMediaUrl(norm)
                    if (s.isOnline != online || s.url != norm) {
                        s = s.copy(url = norm, isOnline = online)
                        changed = true
                    }
                }
                if (s.mirrors.isNotEmpty()) {
                    var mChanged = false
                    val checkedMirrors = s.mirrors.map { mirror ->
                        val mIsPd = mirror.url.contains("pixeldrain.com") || mirror.url.contains("pixeldrain.dev")
                        if (mIsPd) {
                            val norm = StreamHealthValidator.normalizeDirectUrl(mirror.url)
                            val online = StreamHealthValidator.isStreamableMediaUrl(norm)
                            if (mirror.isOnline != online || mirror.url != norm) {
                                mChanged = true
                                mirror.copy(url = norm, isOnline = online)
                            } else mirror
                        } else mirror
                    }
                    if (mChanged) {
                        s = s.copy(mirrors = checkedMirrors)
                        changed = true
                    }
                }
                s
            }
            if (changed) {
                _serverStreamsCache[server] = checkedStreams
                synchronized(_serverStreamsCache) {
                    val cur = _uiState.value.serverStreamsMap.toMutableMap()
                    cur[server] = checkedStreams
                    val curAvailable = if (_uiState.value.selectedServer == server) checkedStreams else _uiState.value.availableStreams
                    _uiState.value = _uiState.value.copy(
                        serverStreamsMap = cur,
                        availableStreams = curAvailable
                    )
                }
            }
        }
    }

    fun selectStreamFromDrawer(stream: StreamSource) {
        if (!stream.isOnline) {
            Toast.makeText(getApplication(), "This source is currently offline (link expired or bandwidth exceeded)", Toast.LENGTH_SHORT).show()
            return
        }
        val s = _uiState.value
        val targetServer = s.drawerSelectedServer
        val serverSubs = _serverSubtitlesCache[targetServer] ?: _serverSubtitles.value
        _serverSubtitles.value = serverSubs
        val streamsForServer = s.serverStreamsMap[targetServer] ?: listOf(stream)
        _uiState.value = s.copy(
            selectedServer = targetServer,
            serverName = targetServer.name,
            serverDisplayName = targetServer.displayName,
            availableStreams = streamsForServer
        )
        switchStream(stream)
    }

    fun selectStreamMirror(stream: StreamSource, mirror: StreamMirror) {
        if (!mirror.isOnline) {
            Toast.makeText(getApplication(), "This mirror is currently offline (link expired or bandwidth exceeded)", Toast.LENGTH_SHORT).show()
            return
        }
        val targetStream = stream.copy(
            url = mirror.url,
            mirrorName = mirror.name,
            isLockerUrl = mirror.isLocker || repository.isLockerUrl(mirror.url),
            isOnline = mirror.isOnline
        )
        val s = _uiState.value
        val targetServer = s.drawerSelectedServer
        val serverSubs = _serverSubtitlesCache[targetServer] ?: _serverSubtitles.value
        _serverSubtitles.value = serverSubs
        val streamsForServer = s.serverStreamsMap[targetServer] ?: listOf(targetStream)
        _uiState.value = s.copy(
            selectedServer = targetServer,
            serverName = targetServer.name,
            serverDisplayName = targetServer.displayName,
            availableStreams = streamsForServer,
            loadingStageText = "Connecting to Server..."
        )
        switchStream(targetStream)
    }

    fun switchServerInPlayer(server: StreamingServer) {
        selectDrawerServer(server)
    }

    fun confirmFallback() {
        val next = _uiState.value.pendingFallbackStream
        _uiState.value = _uiState.value.copy(
            playbackErrorMessage = null,
            pendingFallbackStream = null
        )
        playerManager.clearError()
        if (next != null) {
            switchStream(next)
        }
    }

    fun skipCurrentSegment() {
        val seg = _uiState.value.currentSkipSegment ?: return
        val currentPlayback = playerManager.playbackState.value
        val duration = currentPlayback.durationMs
        if (seg.type == MediaSegmentType.CREDITS && _uiState.value.hasNextEpisode && seg.endMs == null) {
            playNextEpisode()
        } else {
            val targetMs = seg.targetPosition(duration)
            playerManager.seekTo(targetMs)
            _uiState.value = _uiState.value.copy(currentSkipSegment = null)
        }
    }

    fun dismissCurrentSkipSegment() {
        val seg = _uiState.value.currentSkipSegment ?: return
        autoSkippedSegmentTypes.add(seg.type)
        _uiState.value = _uiState.value.copy(currentSkipSegment = null)
    }

    private fun loadSkipSegments(
        mediaId: String,
        title: String,
        type: MediaType,
        year: String?,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        skipSegmentsJob?.cancel()
        skipSegmentsJob = viewModelScope.launch(Dispatchers.IO) {
            val segments = try {
                repository.getSkipSegments(
                    mediaId = mediaId,
                    title = title,
                    type = type,
                    year = year,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber
                )
            } catch (_: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                skipSegments = segments,
                currentSkipSegment = null
            )
        }
    }

    fun retryCurrentStream() {
        _uiState.value = _uiState.value.copy(
            playbackErrorMessage = null,
            pendingFallbackStream = null
        )
        playerManager.clearError()
        val cur = _uiState.value.currentStream
        if (cur != null) {
            val pos = playerManager.playbackState.value.currentPositionMs
            playerManager.prepareStream(cur, _serverSubtitles.value, pos)
        }
    }

    fun dismissPlaybackError() {
        _uiState.value = _uiState.value.copy(
            playbackErrorMessage = null,
            pendingFallbackStream = null
        )
        playerManager.clearError()
    }

    fun switchStream(stream: StreamSource, overridePositionMs: Long? = null) {
        thumbnailRepository.releaseSession()
        val currentPos = overridePositionMs ?: playerManager.playbackState.value.currentPositionMs
        val isLocker = stream.isLockerUrl || repository.isLockerUrl(stream.url)
        if (isLocker) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    resolvingStreamUrl = stream.url,
                    loadingStageText = "Checking Host..."
                )
                val resolved = repository.resolveLockerUrl(stream.url)
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = null)
                val finalStream = if (resolved != null && resolved.url.isNotBlank() && !repository.isLockerUrl(resolved.url)) {
                    stream.copy(
                        url = resolved.url,
                        headers = resolved.headers,
                        format = resolved.format,
                        codec = resolved.codec,
                        isLockerUrl = false
                    )
                } else {
                    null
                }
                if (finalStream == null) {
                    triggerAutoFallback(stream, "Direct playback link unavailable")
                    return@launch
                }
                val updated = _uiState.value.availableStreams.map {
                    if (it.url == stream.url || (it.title != null && it.title == stream.title)) finalStream else it
                }
                val currentMap = _uiState.value.serverStreamsMap.toMutableMap()
                val currentServerList = currentMap[_uiState.value.selectedServer]?.map {
                    if (it.url == stream.url || (it.title != null && it.title == stream.title)) finalStream else it
                }
                if (currentServerList != null) {
                    currentMap[_uiState.value.selectedServer] = currentServerList
                }
                _uiState.value = _uiState.value.copy(
                    availableStreams = updated,
                    serverStreamsMap = currentMap,
                    currentStream = finalStream,
                    isStreamsDrawerOpen = false,
                    playbackErrorMessage = null,
                    pendingFallbackStream = null
                )
                if (!finalStream.format.equals("EMBED", ignoreCase = true)) {
                    playerManager.prepareStream(finalStream, _serverSubtitles.value, currentPos)
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(loadingStageText = "Loading Video...")
            val updated = _uiState.value.availableStreams.map {
                if (it.url == stream.url || (it.title != null && it.title == stream.title)) stream else it
            }
            val currentMap = _uiState.value.serverStreamsMap.toMutableMap()
            val currentServerList = currentMap[_uiState.value.selectedServer]?.map {
                if (it.url == stream.url || (it.title != null && it.title == stream.title)) stream else it
            }
            if (currentServerList != null) {
                currentMap[_uiState.value.selectedServer] = currentServerList
            }
            _uiState.value = _uiState.value.copy(
                availableStreams = updated,
                serverStreamsMap = currentMap,
                currentStream = stream,
                isStreamsDrawerOpen = false,
                playbackErrorMessage = null,
                pendingFallbackStream = null
            )
            if (!stream.format.equals("EMBED", ignoreCase = true)) {
                playerManager.prepareStream(stream, _serverSubtitles.value, currentPos)
            }
        }
    }

    fun downloadCurrentPlayingStream(context: Context) {
        val s = _uiState.value
        val curStream = s.currentStream
        if (curStream == null) {
            Toast.makeText(context, "No stream active to download", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val isSeries = s.mediaType == MediaType.SERIES || s.mediaType == MediaType.ANIME || s.mediaType == MediaType.DRAMA
                val cleanTitle = if (isSeries) {
                    s.mediaTitle
                        .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
                        .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
                        .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
                        .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""\s*\(\d{4}\)"""), "")
                        .trim()
                        .ifBlank { s.mediaTitle }
                } else {
                    s.mediaTitle
                }

                val mediaItem = MediaItem(
                    id = s.mediaId,
                    title = cleanTitle,
                    posterUrl = s.posterUrl,
                    backdropUrl = s.posterUrl,
                    overview = "",
                    year = s.releaseYear,
                    mediaType = s.mediaType
                )

                val ep = if (isSeries) {
                    Episode(
                        id = "${s.mediaId}-${s.seasonNumber}-${s.episodeNumber}",
                        episodeNumber = s.episodeNumber,
                        seasonNumber = s.seasonNumber,
                        title = s.episodeTitle ?: "Episode ${s.episodeNumber}",
                        overview = "",
                        stillUrl = s.posterUrl,
                        durationMinutes = 0,
                        airDate = ""
                    )
                } else null

                val isLocker = curStream.isLockerUrl || repository.isLockerUrl(curStream.url)
                val streamToDownload = if (isLocker) {
                    val resolved = repository.resolveLockerUrl(curStream.url)
                    if (resolved != null && resolved.url.isNotBlank() && !repository.isLockerUrl(resolved.url)) {
                        curStream.copy(
                            url = resolved.url,
                            headers = resolved.headers,
                            format = resolved.format,
                            codec = resolved.codec,
                            isLockerUrl = false
                        )
                    } else curStream
                } else curStream

                val downloadId = repository.startDownload(mediaItem, streamToDownload, ep)
                if (downloadId > 0) {
                    val epText = if (ep != null) " (S${ep.seasonNumber}E${ep.episodeNumber})" else ""
                    Toast.makeText(context, "Downloading $cleanTitle$epText in ${streamToDownload.quality}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Download failed to start", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    fun setControlsVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showControls = visible)
    }

    fun toggleStreamsDrawer() {
        val opening = !_uiState.value.isStreamsDrawerOpen
        if (opening) {
            val curServer = _uiState.value.selectedServer
            val curStreams = _uiState.value.serverStreamsMap[curServer] ?: _uiState.value.availableStreams
            val qualities = getAvailableQualitiesForServer(curStreams)
            val selectedQ = _uiState.value.currentStream?.let { normalizeQualityBucket(it) }
                ?: qualities.firstOrNull { it == "1080p" }
                ?: qualities.firstOrNull()
            _uiState.value = _uiState.value.copy(
                isStreamsDrawerOpen = true,
                drawerSelectedServer = curServer,
                drawerSelectedQuality = selectedQ,
                isAudioTracksModalOpen = false,
                isSubtitlesModalOpen = false,
                isEpisodesDrawerOpen = false
            )
        } else {
            _uiState.value = _uiState.value.copy(isStreamsDrawerOpen = false)
        }
    }

    fun toggleAudioTracksModal() {
        _uiState.value = _uiState.value.copy(
            isAudioTracksModalOpen = !_uiState.value.isAudioTracksModalOpen,
            isStreamsDrawerOpen = false,
            isSubtitlesModalOpen = false,
            isEpisodesDrawerOpen = false
        )
    }

    fun toggleSubtitlesModal() {
        _uiState.value = _uiState.value.copy(
            isSubtitlesModalOpen = !_uiState.value.isSubtitlesModalOpen,
            isStreamsDrawerOpen = false,
            isAudioTracksModalOpen = false,
            isEpisodesDrawerOpen = false
        )
    }

    fun toggleSubtitlesEnabled() {
        val next = !_uiState.value.isSubtitlesEnabled
        playerManager.setSubtitlesEnabled(next)
        _uiState.value = _uiState.value.copy(isSubtitlesEnabled = next)
    }

    fun selectAudioTrack(track: PlayerAudioTrack) {
        hasUserSelectedAudio = true
        playerManager.selectAudioTrack(track)
        _uiState.value = _uiState.value.copy(
            selectedAudioTrack = track,
            isAudioTracksModalOpen = false
        )
    }

    fun selectSubtitleTrack(track: PlayerSubtitleTrack) {
        hasUserSelectedSubtitle = true
        playerManager.selectSubtitleTrack(track)
        _uiState.value = _uiState.value.copy(
            selectedSubtitleTrack = track,
            isSubtitlesEnabled = true,
            isSubtitlesModalOpen = false
        )
    }

    fun disableSubtitles() {
        hasUserSelectedSubtitle = true
        playerManager.disableSubtitles()
        _uiState.value = _uiState.value.copy(
            selectedSubtitleTrack = null,
            isSubtitlesEnabled = false,
            isSubtitlesModalOpen = false
        )
    }

    fun openExternalPlayer(context: Context) {
        val s = _uiState.value.currentStream
        if (s == null || s.url.isBlank()) {
            Toast.makeText(context, "No stream URL available to play externally", Toast.LENGTH_SHORT).show()
            return
        }

        playerManager.pause()

        try {
            val uri = Uri.parse(s.url)
            val mime = when {
                s.format.equals("MKV", ignoreCase = true) || s.url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
                s.format.equals("HLS", ignoreCase = true) || s.url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                s.format.equals("DASH", ignoreCase = true) || s.url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
                s.format.equals("MP4", ignoreCase = true) || s.url.contains(".mp4", ignoreCase = true) -> "video/mp4"
                s.format.equals("WEBM", ignoreCase = true) || s.url.contains(".webm", ignoreCase = true) -> "video/webm"
                else -> "video/*"
            }

            val title = if (!_uiState.value.episodeTitle.isNullOrBlank()) {
                "${_uiState.value.mediaTitle} - ${_uiState.value.episodeTitle}"
            } else {
                _uiState.value.mediaTitle
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra("title", title)
                putExtra("display_name", title)

                if (s.headers.isNotEmpty()) {
                    val headersList = mutableListOf<String>()
                    s.headers.forEach { (k, v) ->
                        headersList.add(k)
                        headersList.add(v)
                    }
                    putExtra("headers", headersList.toTypedArray())

                    s.headers["User-Agent"]?.let { putExtra("http-user-agent", it) }
                    s.headers["Referer"]?.let { putExtra("http-referer", it) }
                    s.headers["Cookie"]?.let { putExtra("http-cookie", it) }
                }

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Play with external player (VLC / MX / Nova / Browser)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "No external video player or browser found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestSeekPreview(targetMs: Long) {
        val s = _uiState.value
        val cur = s.currentStream
        thumbnailPreviewManager.requestPreview(
            timestampMs = targetMs,
            mediaId = s.mediaId,
            streamUrl = cur?.url,
            headers = cur?.headers ?: emptyMap(),
            posterUrl = s.episodeStillUrl ?: s.backdropUrl ?: s.posterUrl
        )
    }

    fun clearSeekPreview(immediate: Boolean = false) {
        thumbnailPreviewManager.clearPreview(immediate)
    }

    override fun onCleared() {
        super.onCleared()
        thumbnailPreviewManager.clearPreview(true)
        thumbnailRepository.releaseSession()
        connectivityObserver.stopObserving()
        playerManager.release()
    }
}
