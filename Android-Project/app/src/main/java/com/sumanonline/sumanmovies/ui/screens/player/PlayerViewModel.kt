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
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val mediaTitle: String = "",
    val mediaId: String = "",
    val mediaType: MediaType = MediaType.MOVIE,
    val releaseYear: String? = null,
    val posterUrl: String? = null,
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
    val pendingFallbackStream: StreamSource? = null,
    val resolvingStreamUrl: String? = null,
    val isLoadingStreams: Boolean = false,
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
    val dialogueBoostEnabled: Boolean = false
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val playerManager = ExoPlayerManager(application)
    private val app = application as SumanMoviesApp
    private val repository = app.mediaRepository

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _serverSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())

    init {
        startPositionLoop()
        observePlaybackErrors()
        observeAudioTracks()
        observeSubtitleTracks()
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

    private fun observeAudioTracks() {
        viewModelScope.launch {
            playerManager.audioTracks.collectLatest { tracks ->
                val preferredAudio = app.preferencesManager.preferredAudioLanguage.value
                val selected = tracks.firstOrNull { it.isSelected }

                if (tracks.isNotEmpty() && preferredAudio.isNotBlank() && !preferredAudio.equals("None", ignoreCase = true)) {
                    val matchingTrack = tracks.firstOrNull { t ->
                        t.isSupported && (
                            t.displayName.contains(preferredAudio, ignoreCase = true) ||
                            t.label?.contains(preferredAudio, ignoreCase = true) == true ||
                            matchesLanguageCode(preferredAudio, t.language)
                        )
                    }
                    if (matchingTrack != null && !matchingTrack.isSelected && (selected == null || !selected.displayName.contains(preferredAudio, ignoreCase = true))) {
                        playerManager.selectAudioTrack(matchingTrack)
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

                if (tracks.isNotEmpty() && subtitlesByDefault && preferredSub.isNotBlank() && !preferredSub.equals("None", ignoreCase = true)) {
                    val currentSelected = tracks.firstOrNull { it.isSelected }
                    val matchingTrack = tracks.firstOrNull { t ->
                        t.displayName.contains(preferredSub, ignoreCase = true) ||
                        t.label.contains(preferredSub, ignoreCase = true) ||
                        matchesLanguageCode(preferredSub, t.language)
                    }
                    if (matchingTrack != null && !matchingTrack.isSelected && (currentSelected == null || !currentSelected.displayName.contains(preferredSub, ignoreCase = true))) {
                        playerManager.selectSubtitleTrack(matchingTrack)
                    }
                } else if (!subtitlesByDefault && tracks.any { it.isSelected }) {
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
                val error = state.errorMessage
                val current = _uiState.value.currentStream

                if (error != null && current != null) {
                    val streamKey = "${current.url}#${current.quality}#${current.format}"
                    if (error == lastHandledError && streamKey == lastHandledStreamKey) {
                        return@collectLatest
                    }
                    lastHandledError = error
                    lastHandledStreamKey = streamKey

                    val failed = _uiState.value.failedStreamUrls.toMutableSet()
                    failed.add(streamKey)

                    val nextStream = _uiState.value.availableStreams.firstOrNull { candidate ->
                        val candidateKey = "${candidate.url}#${candidate.quality}#${candidate.format}"
                        !failed.contains(candidateKey)
                    }

                    _uiState.value = _uiState.value.copy(
                        failedStreamUrls = failed,
                        playbackErrorMessage = error,
                        pendingFallbackStream = nextStream
                    )
                } else if (error == null) {
                    lastHandledError = null
                    lastHandledStreamKey = null
                }
            }
        }
    }

    private fun startPositionLoop() {
        viewModelScope.launch {
            while (isActive) {
                playerManager.updatePosition()
                val current = playerManager.playbackState.value
                if (current.durationMs > 0 && current.currentPositionMs > 0) {
                    val s = _uiState.value
                    if (s.mediaId.isNotBlank()) {
                        repository.saveWatchProgress(
                            mediaId = s.mediaId,
                            title = s.mediaTitle,
                            mediaType = s.mediaType,
                            posterUrl = s.posterUrl,
                            season = s.seasonNumber,
                            episode = s.episodeNumber,
                            positionMs = current.currentPositionMs,
                            durationMs = current.durationMs
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    fun initPlayback(
        mediaId: String,
        title: String,
        mediaType: MediaType,
        posterUrl: String?,
        releaseYear: String? = null,
        initialStreamUrl: String?,
        initialFormat: String?,
        initialQuality: String? = null,
        initialProvider: String? = null,
        initialDirect: Boolean = false,
        initialHeaders: Map<String, String> = emptyMap(),
        serverName: String? = null,
        serverDisplayName: String? = null,
        initialStreams: List<StreamSource> = emptyList(),
        initialSubtitles: List<SubtitleTrack> = emptyList(),
        seasonNumber: Int = 1,
        episodeNumber: Int = 1,
        initialEpisodeTitle: String? = null
    ) {
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
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .trim()
            .ifBlank { title }
        val resolvedMediaTitle = if (isSeries) "$cleanBase - S${seasonNumber}E${episodeNumber}" else title

        _uiState.value = _uiState.value.copy(
            mediaId = mediaId,
            mediaTitle = resolvedMediaTitle,
            mediaType = mediaType,
            releaseYear = releaseYear,
            posterUrl = posterUrl,
            episodeTitle = initialEpisodeTitle,
            serverName = serverEnum.name,
            serverDisplayName = resolvedServerDisplay,
            selectedServer = serverEnum,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )

        if (mediaType == MediaType.SERIES) {
            loadSeriesMetadata(mediaId, title, seasonNumber, episodeNumber, initialEpisodeTitle)
        } else if (releaseYear.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    val details = repository.getDetails(mediaId, mediaType)
                    val yr = details?.year ?: details?.releaseDate?.take(4)
                    if (!yr.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(releaseYear = yr)
                    }
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
                        headers = initialHeaders
                    )
            } else null

            val mergedInitial = if (initialStream != null) {
                listOf(initialStream) + initialStreams.filter { it.url != initialStream.url }
            } else {
                initialStreams
            }

            val supports4k = com.sumanonline.sumanmovies.util.DeviceCapabilities.supports4KHevc()
            val candidateInitial = if (!supports4k && initialStream != null && (initialStream.quality.contains("2160") || initialStream.quality.contains("4k", ignoreCase = true))) {
                mergedInitial.firstOrNull { it.quality.contains("1080") || it.category == "1080p" }
                    ?: mergedInitial.firstOrNull { !it.quality.contains("2160") && !it.quality.contains("4k", ignoreCase = true) }
                    ?: initialStream
            } else {
                initialStream
            }

            val defaultStream = candidateInitial ?: if (!supports4k) {
                mergedInitial.firstOrNull { it.quality.contains("1080") || it.category == "1080p" }
                    ?: mergedInitial.firstOrNull { !it.quality.contains("2160") && !it.quality.contains("4k", ignoreCase = true) }
                    ?: mergedInitial.firstOrNull()
            } else {
                mergedInitial.firstOrNull()
            }

            _uiState.value = _uiState.value.copy(
                availableStreams = mergedInitial,
                currentStream = defaultStream
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
                if (toPrepare.isLockerUrl) {
                    _uiState.value = _uiState.value.copy(resolvingStreamUrl = toPrepare.url)
                    val resolved = repository.resolveLockerUrl(toPrepare.url)
                    _uiState.value = _uiState.value.copy(resolvingStreamUrl = null)
                    if (resolved != null && resolved.url.isNotBlank()) {
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
                        if (!toPrepare.format.equals("EMBED", ignoreCase = true)) {
                            playerManager.prepareStream(toPrepare, _serverSubtitles.value, resumePositionMs)
                        }
                    }
                } else {
                    if (!toPrepare.format.equals("EMBED", ignoreCase = true)) {
                        playerManager.prepareStream(toPrepare, _serverSubtitles.value, resumePositionMs)
                    }
                }
            }

            _uiState.value = _uiState.value.copy(isLoadingStreams = true)
            val resolvedState = repository.resolveServer(
                serverEnum,
                cleanBase,
                seasonNumber,
                episodeNumber,
                mediaId,
                _uiState.value.releaseYear ?: releaseYear
            )
            val currentList = _uiState.value.availableStreams.toMutableList()
            for (newS in resolvedState.streams) {
                if (currentList.none { it.url == newS.url }) {
                    currentList.add(newS)
                }
            }
            if (resolvedState.subtitles.isNotEmpty()) {
                _serverSubtitles.value = resolvedState.subtitles
                val cur = _uiState.value.currentStream
                if (cur != null) {
                    val pos = playerManager.playbackState.value.currentPositionMs
                    playerManager.prepareStream(cur, resolvedState.subtitles, pos)
                }
            }
            _uiState.value = _uiState.value.copy(
                availableStreams = currentList,
                isLoadingStreams = false
            )
            if (_uiState.value.currentStream == null && currentList.isNotEmpty()) {
                val first = currentList.first()
                _uiState.value = _uiState.value.copy(currentStream = first)
                playerManager.prepareStream(first, _serverSubtitles.value, resumePositionMs)
            }
        }
    }

    private fun loadSeriesMetadata(
        mediaId: String,
        title: String,
        seasonNum: Int,
        epNum: Int,
        initialEpTitle: String?
    ) {
        viewModelScope.launch {
            try {
                val details = repository.getDetails(mediaId, MediaType.SERIES)
                val yr = details?.year ?: details?.releaseDate?.take(4)
                if (!yr.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(releaseYear = yr)
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
                    episodeTitle = initialEpTitle
                )
                loadDrawerSeasonEpisodes(seasonNum, epNum)
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
                loadDrawerSeasonEpisodes(seasonNum, epNum)
            }
        }
    }

    fun loadDrawerSeasonEpisodes(seasonNum: Int, currentEpNum: Int? = null) {
        viewModelScope.launch {
            try {
                val tmdbId = _uiState.value.mediaId.toIntOrNull()
                val episodes = if (tmdbId != null) {
                    repository.getSeasonEpisodes(tmdbId, seasonNum)
                } else {
                    emptyList()
                }
                val finalEpisodes = episodes.ifEmpty {
                    val sampleTitles = listOf(
                        "A Shadow of the Past", "Adrift", "Adar", "The Great Wave",
                        "Partings", "Udûn", "The Eye", "Alloyed"
                    )
                    val sampleSynopses = listOf(
                        "Galadriel is disturbed by signs of an ancient evil return; Arondir makes a discovery.",
                        "Galadriel finds a new ally; Elrond faces a cold reception from an old friend.",
                        "Arondir finds himself a captive; Galadriel and Halbrand explore a legendary kingdom.",
                        "Queen Regent Miriel faith is tested; Isildur finds himself at a crossroads.",
                        "Nori questions her instincts; Elrond struggles to stay true to his oath.",
                        "Adar and his army march on Ostirith.",
                        "Survivors of a cataclysm try to find safety; The Harfoots confront evil.",
                        "New alliances are forged and ancient destinies begin to unfold."
                    )
                    (1..8).map { ep ->
                        Episode(
                            id = "${_uiState.value.mediaId}-$seasonNum-$ep",
                            episodeNumber = ep,
                            seasonNumber = seasonNum,
                            title = sampleTitles.getOrElse(ep - 1) { "Episode $ep" },
                            overview = sampleSynopses.getOrElse(ep - 1) { "Episode $ep synopsis." },
                            stillUrl = null,
                            durationMinutes = 64 + (ep * 2),
                            airDate = "2022"
                        )
                    }
                }
                val targetEp = currentEpNum ?: _uiState.value.episodeNumber
                val epTitle = finalEpisodes.firstOrNull { it.episodeNumber == targetEp }?.title
                val maxEp = finalEpisodes.maxOfOrNull { it.episodeNumber } ?: finalEpisodes.size
                val hasNext = targetEp < maxEp || _uiState.value.seasonsList.any { it.seasonNumber > seasonNum }
                val hasPrev = targetEp > 1 || seasonNum > 1

                _uiState.value = _uiState.value.copy(
                    currentSeasonEpisodes = finalEpisodes,
                    selectedDrawerSeason = seasonNum,
                    episodeTitle = epTitle ?: _uiState.value.episodeTitle,
                    hasNextEpisode = hasNext,
                    hasPreviousEpisode = hasPrev
                )
            } catch (_: Exception) {}
        }
    }

    fun playEpisode(seasonNum: Int, epNum: Int) {
        val cleanBase = _uiState.value.mediaTitle
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .trim()
            .ifBlank { _uiState.value.mediaTitle }
        val updatedTitle = "$cleanBase - S${seasonNum}E${epNum}"

        val epTitle = _uiState.value.currentSeasonEpisodes.firstOrNull { it.episodeNumber == epNum && it.seasonNumber == seasonNum }?.title
        _uiState.value = _uiState.value.copy(
            mediaTitle = updatedTitle,
            seasonNumber = seasonNum,
            episodeNumber = epNum,
            episodeTitle = epTitle,
            isEpisodesDrawerOpen = false,
            isLoadingStreams = true
        )
        viewModelScope.launch {
            val resolvedState = repository.resolveServer(
                _uiState.value.selectedServer,
                cleanBase,
                seasonNum,
                epNum,
                _uiState.value.mediaId,
                _uiState.value.releaseYear
            )
            _uiState.value = _uiState.value.copy(
                availableStreams = resolvedState.streams,
                isLoadingStreams = false
            )
            val first = resolvedState.streams.firstOrNull()
            if (first != null) {
                val targetHistoryId = "${_uiState.value.mediaId}:s${seasonNum}e${epNum}"
                val savedProgress = repository.getWatchProgress(targetHistoryId)
                val resumePos = if (savedProgress != null && !savedProgress.isCompleted && savedProgress.positionMs > 0) {
                    savedProgress.positionMs
                } else {
                    0L
                }
                switchStream(first, resumePos)
            }
        }
        loadDrawerSeasonEpisodes(seasonNum, epNum)
    }

    fun playNextEpisode() {
        val curSeason = _uiState.value.seasonNumber
        val curEp = _uiState.value.episodeNumber
        val maxEpInSeason = _uiState.value.currentSeasonEpisodes.maxOfOrNull { it.episodeNumber } ?: 8
        if (curEp < maxEpInSeason) {
            playEpisode(curSeason, curEp + 1)
        } else {
            val nextSeason = _uiState.value.seasonsList.firstOrNull { it.seasonNumber > curSeason }?.seasonNumber ?: (curSeason + 1)
            playEpisode(nextSeason, 1)
        }
    }

    fun playPreviousEpisode() {
        val curSeason = _uiState.value.seasonNumber
        val curEp = _uiState.value.episodeNumber
        if (curEp > 1) {
            playEpisode(curSeason, curEp - 1)
        } else if (curSeason > 1) {
            playEpisode(curSeason - 1, 1)
        }
    }

    fun toggleEpisodesDrawer() {
        val next = !_uiState.value.isEpisodesDrawerOpen
        _uiState.value = _uiState.value.copy(
            isEpisodesDrawerOpen = next,
            isStreamsDrawerOpen = false,
            isAudioTracksModalOpen = false,
            isSubtitlesModalOpen = false
        )
    }

    fun setEpisodesDrawerTab(tab: String) {
        _uiState.value = _uiState.value.copy(episodesDrawerTab = tab)
    }

    fun selectDrawerSeason(seasonNum: Int) {
        _uiState.value = _uiState.value.copy(
            selectedDrawerSeason = seasonNum,
            episodesDrawerTab = "EPISODES"
        )
        loadDrawerSeasonEpisodes(seasonNum)
    }

    fun toggleDialogueBoost() {
        _uiState.value = _uiState.value.copy(
            dialogueBoostEnabled = !_uiState.value.dialogueBoostEnabled
        )
    }

    fun switchServerInPlayer(server: StreamingServer) {
        val s = _uiState.value
        _uiState.value = s.copy(
            selectedServer = server,
            serverName = server.name,
            serverDisplayName = server.displayName,
            isLoadingStreams = true
        )
        viewModelScope.launch {
            val isSeries = s.mediaType == MediaType.SERIES || s.mediaType == MediaType.ANIME || s.mediaType == MediaType.DRAMA
            val targetTitle = if (isSeries) {
                s.mediaTitle
                    .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
                    .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
                    .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
                    .trim()
                    .ifBlank { s.mediaTitle }
            } else {
                s.mediaTitle
            }
            val resolvedState = repository.resolveServer(server, targetTitle, s.seasonNumber, s.episodeNumber, s.mediaId, s.releaseYear)
            _uiState.value = _uiState.value.copy(
                availableStreams = resolvedState.streams,
                isLoadingStreams = false
            )
            val first = resolvedState.streams.firstOrNull()
            if (first != null) {
                switchStream(first)
            }
        }
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
        val currentPos = overridePositionMs ?: playerManager.playbackState.value.currentPositionMs
        if (stream.isLockerUrl) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = stream.url)
                val resolved = repository.resolveLockerUrl(stream.url)
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = null)
                val finalStream = if (resolved != null && resolved.url.isNotBlank()) {
                    stream.copy(
                        url = resolved.url,
                        headers = resolved.headers,
                        format = resolved.format,
                        codec = resolved.codec,
                        isLockerUrl = false
                    )
                } else {
                    stream
                }
                val updated = _uiState.value.availableStreams.map {
                    if (it.url == stream.url) finalStream else it
                }
                _uiState.value = _uiState.value.copy(
                    availableStreams = updated,
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
            _uiState.value = _uiState.value.copy(
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

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    fun setControlsVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showControls = visible)
    }

    fun toggleStreamsDrawer() {
        _uiState.value = _uiState.value.copy(
            isStreamsDrawerOpen = !_uiState.value.isStreamsDrawerOpen,
            isAudioTracksModalOpen = false,
            isSubtitlesModalOpen = false,
            isEpisodesDrawerOpen = false
        )
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
        playerManager.selectAudioTrack(track)
        _uiState.value = _uiState.value.copy(
            selectedAudioTrack = track,
            isAudioTracksModalOpen = false
        )
    }

    fun selectSubtitleTrack(track: PlayerSubtitleTrack) {
        playerManager.selectSubtitleTrack(track)
        _uiState.value = _uiState.value.copy(
            selectedSubtitleTrack = track,
            isSubtitlesEnabled = true,
            isSubtitlesModalOpen = false
        )
    }

    fun disableSubtitles() {
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

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
