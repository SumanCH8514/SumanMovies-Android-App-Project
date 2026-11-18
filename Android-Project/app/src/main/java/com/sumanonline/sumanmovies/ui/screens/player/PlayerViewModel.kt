package com.sumanonline.sumanmovies.ui.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.SumanMoviesApp
import com.sumanonline.sumanmovies.data.models.MediaType
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
    val posterUrl: String? = null,
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
    val isSubtitlesModalOpen: Boolean = false
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

    private fun observeAudioTracks() {
        viewModelScope.launch {
            playerManager.audioTracks.collectLatest { tracks ->
                val selected = tracks.firstOrNull { it.isSelected }
                _uiState.value = _uiState.value.copy(
                    availableAudioTracks = tracks,
                    selectedAudioTrack = selected
                )
            }
        }
    }

    private fun observeSubtitleTracks() {
        viewModelScope.launch {
            playerManager.subtitleTracks.collectLatest { tracks ->
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
                val currentKey = current?.let { "${it.url}#${it.quality}#${it.format}" }

                if (error != null && (error != lastHandledError || currentKey != lastHandledStreamKey)) {
                    lastHandledError = error
                    lastHandledStreamKey = currentKey

                    val failed = if (currentKey != null) _uiState.value.failedStreamUrls + currentKey else _uiState.value.failedStreamUrls

                    val nextStream = _uiState.value.availableStreams.firstOrNull { candidate ->
                        val candidateKey = "${candidate.url}#${candidate.quality}#${candidate.format}"
                        !failed.contains(candidateKey) &&
                        (current == null || candidate.url != current.url || candidate.quality != current.quality)
                    } ?: _uiState.value.availableStreams.firstOrNull { candidate ->
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
        episodeNumber: Int = 1
    ) {
        _serverSubtitles.value = initialSubtitles
        val serverEnum = try {
            if (serverName != null) StreamingServer.valueOf(serverName)
            else repository.getSelectedServer(mediaId)
        } catch (_: Exception) {
            StreamingServer.MOVIEBOX
        }
        val resolvedServerDisplay = serverDisplayName ?: serverEnum.displayName

        _uiState.value = _uiState.value.copy(
            mediaId = mediaId,
            mediaTitle = title,
            mediaType = mediaType,
            posterUrl = posterUrl,
            serverName = serverEnum.name,
            serverDisplayName = resolvedServerDisplay,
            selectedServer = serverEnum,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )

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
                            playerManager.prepareStream(finalStream, _serverSubtitles.value)
                        }
                    } else {
                        if (!toPrepare.format.equals("EMBED", ignoreCase = true)) {
                            playerManager.prepareStream(toPrepare, _serverSubtitles.value)
                        }
                    }
                } else {
                    if (!toPrepare.format.equals("EMBED", ignoreCase = true)) {
                        playerManager.prepareStream(toPrepare, _serverSubtitles.value)
                    }
                }
            }

            _uiState.value = _uiState.value.copy(isLoadingStreams = true)
            val resolvedState = repository.resolveServer(serverEnum, title, seasonNumber, episodeNumber, mediaId)
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
                playerManager.prepareStream(first, _serverSubtitles.value)
            }
        }
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
            val resolvedState = repository.resolveServer(server, s.mediaTitle, s.seasonNumber, s.episodeNumber, s.mediaId)
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

    fun switchStream(stream: StreamSource) {
        val currentPos = playerManager.playbackState.value.currentPositionMs
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

    fun toggleStreamsDrawer() {
        _uiState.value = _uiState.value.copy(
            isStreamsDrawerOpen = !_uiState.value.isStreamsDrawerOpen,
            isAudioTracksModalOpen = false,
            isSubtitlesModalOpen = false
        )
    }

    fun toggleAudioTracksModal() {
        _uiState.value = _uiState.value.copy(
            isAudioTracksModalOpen = !_uiState.value.isAudioTracksModalOpen,
            isStreamsDrawerOpen = false,
            isSubtitlesModalOpen = false
        )
    }

    fun toggleSubtitlesModal() {
        _uiState.value = _uiState.value.copy(
            isSubtitlesModalOpen = !_uiState.value.isSubtitlesModalOpen,
            isStreamsDrawerOpen = false,
            isAudioTracksModalOpen = false
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

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
