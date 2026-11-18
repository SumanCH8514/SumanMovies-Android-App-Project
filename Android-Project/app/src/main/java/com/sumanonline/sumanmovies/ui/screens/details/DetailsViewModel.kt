package com.sumanonline.sumanmovies.ui.screens.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import com.sumanonline.sumanmovies.data.models.ServerAvailability
import com.sumanonline.sumanmovies.data.models.ServerState
import com.sumanonline.sumanmovies.data.models.StreamMirror
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class QualityModalMode {
    STREAM,
    DOWNLOAD
}

data class DetailsUiState(
    val details: MediaDetails? = null,
    val selectedSeason: Int = 1,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val selectedServer: StreamingServer = StreamingServer.MOVIEBOX,
    val serverStates: Map<StreamingServer, ServerState> = StreamingServer.values().associateWith {
        ServerState(server = it, status = ServerAvailability.CHECKING)
    },
    val availableStreams: List<StreamSource> = emptyList(),
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val isStreamsLoading: Boolean = false,
    val isOverviewExpanded: Boolean = false,
    val serverErrorMessage: String? = null,
    val fallbackCandidates: List<StreamingServer> = emptyList(),
    val showQualityModal: Boolean = false,
    val qualityModalMode: QualityModalMode = QualityModalMode.STREAM,
    val qualityModalServer: StreamingServer? = null,
    val isQualityModalLoading: Boolean = false,
    val resolvingStreamUrl: String? = null
)

class DetailsViewModel(
    private val repository: MediaRepository,
    private val mediaId: String,
    private val mediaTypeStr: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val mediaType = try {
        MediaType.valueOf(mediaTypeStr.uppercase())
    } catch (_: Exception) {
        MediaType.MOVIE
    }

    init {
        val initialServer = repository.getSelectedServer(mediaId)
        _uiState.value = _uiState.value.copy(selectedServer = initialServer)
        loadDetails()
        observeFavorite()
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            repository.isFavoriteFlow(mediaId).collectLatest { fav ->
                _uiState.value = _uiState.value.copy(isFavorite = fav)
            }
        }
    }

    fun toggleFavorite() {
        val d = _uiState.value.details ?: return
        viewModelScope.launch {
            val item = MediaItem(
                id = d.id,
                title = d.title,
                mediaType = d.mediaType,
                year = d.year,
                rating = d.rating,
                posterUrl = d.posterUrl
            )
            repository.toggleFavorite(item)
        }
    }

    fun toggleOverviewExpanded() {
        _uiState.value = _uiState.value.copy(isOverviewExpanded = !_uiState.value.isOverviewExpanded)
    }

    fun selectServer(server: StreamingServer, openModalIfSupported: Boolean = true) {
        _uiState.value = _uiState.value.copy(selectedServer = server)
        repository.setSelectedServer(mediaId, server)

        val serverState = _uiState.value.serverStates[server]
        if (serverState != null && serverState.status == ServerAvailability.AVAILABLE) {
            _uiState.value = _uiState.value.copy(availableStreams = serverState.streams)
        } else if (serverState == null || serverState.status == ServerAvailability.UNAVAILABLE || serverState.status == ServerAvailability.ERROR) {
            retryServer(server)
        }

        if (openModalIfSupported && (server == StreamingServer.FOURKHDHUB || server == StreamingServer.HDHUB4U || server == StreamingServer.PLAY4U || server == StreamingServer.MOVIEBOX)) {
            openQualityModal(server)
        }
    }

    fun openStreamModal(server: StreamingServer = _uiState.value.selectedServer) {
        _uiState.value = _uiState.value.copy(
            showQualityModal = true,
            qualityModalMode = QualityModalMode.STREAM,
            qualityModalServer = server
        )
        val currentStreams = _uiState.value.serverStates[server]?.streams
        if (currentStreams.isNullOrEmpty()) {
            fetchRealtimeQualities(server)
        }
    }

    fun openDownloadModal(server: StreamingServer = _uiState.value.selectedServer) {
        _uiState.value = _uiState.value.copy(
            showQualityModal = true,
            qualityModalMode = QualityModalMode.DOWNLOAD,
            qualityModalServer = server
        )
        val currentStreams = _uiState.value.serverStates[server]?.streams
        if (currentStreams.isNullOrEmpty()) {
            fetchRealtimeQualities(server)
        }
    }

    fun selectDownloadServer(server: StreamingServer) {
        _uiState.value = _uiState.value.copy(
            qualityModalServer = server
        )
        val currentStreams = _uiState.value.serverStates[server]?.streams
        if (currentStreams.isNullOrEmpty()) {
            fetchRealtimeQualities(server)
        }
    }

    fun openQualityModal(
        server: StreamingServer = _uiState.value.selectedServer,
        mode: QualityModalMode = QualityModalMode.STREAM
    ) {
        _uiState.value = _uiState.value.copy(
            showQualityModal = true,
            qualityModalMode = mode,
            qualityModalServer = server
        )
        val currentStreams = _uiState.value.serverStates[server]?.streams
        if (currentStreams.isNullOrEmpty()) {
            fetchRealtimeQualities(server)
        }
    }

    fun closeQualityModal() {
        _uiState.value = _uiState.value.copy(showQualityModal = false)
    }

    fun fetchRealtimeQualities(server: StreamingServer) {
        val d = _uiState.value.details ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isQualityModalLoading = true)
            val isMovie = d.mediaType == MediaType.MOVIE
            val actualSeason = if (isMovie) 0 else _uiState.value.selectedSeason
            val actualEpisode = if (isMovie) 0 else 1

            val yr = d.year ?: d.releaseDate?.take(4)
            val resolved = repository.resolveServer(server, d.title, actualSeason, actualEpisode, mediaId, yr)
            val updated = _uiState.value.serverStates.toMutableMap()
            updated[server] = resolved

            _uiState.value = _uiState.value.copy(
                serverStates = updated,
                availableStreams = if (_uiState.value.selectedServer == server) resolved.streams else _uiState.value.availableStreams,
                isQualityModalLoading = false
            )
        }
    }

    fun playStreamOption(
        context: Context,
        stream: StreamSource,
        mirror: StreamMirror? = null,
        onLaunchPlayer: (MediaItem, StreamSource, StreamingServer) -> Unit
    ) {
        val d = _uiState.value.details ?: return
        val server = _uiState.value.qualityModalServer ?: _uiState.value.selectedServer
        val targetStream = if (mirror != null && mirror.url.isNotBlank()) {
            stream.copy(
                url = mirror.url,
                mirrorName = mirror.name,
                isLockerUrl = mirror.isLocker || repository.isLockerUrl(mirror.url)
            )
        } else {
            stream.copy(
                isLockerUrl = stream.isLockerUrl || repository.isLockerUrl(stream.url)
            )
        }

        val isLocker = targetStream.isLockerUrl || repository.isLockerUrl(targetStream.url)

        if (isLocker) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = targetStream.url)
                val resolved = repository.resolveLockerUrl(targetStream.url)
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = null)

                if (resolved != null && resolved.url.isNotBlank() && !repository.isLockerUrl(resolved.url)) {
                    val finalStream = targetStream.copy(
                        url = resolved.url,
                        headers = resolved.headers,
                        format = resolved.format,
                        codec = resolved.codec,
                        isLockerUrl = false
                    )
                    updateStreamInState(server, targetStream.url, finalStream)
                    _uiState.value = _uiState.value.copy(showQualityModal = false)
                    val isMovie = d.mediaType == MediaType.MOVIE
                    val displayTitle = if (isMovie) d.title else "${d.title} - S${_uiState.value.selectedSeason}E1"
                    val item = MediaItem(
                        id = d.id,
                        title = displayTitle,
                        posterUrl = d.posterUrl,
                        mediaType = d.mediaType
                    )
                    onLaunchPlayer(item, finalStream, server)
                } else {
                    Toast.makeText(context, "Could not resolve direct CDN link. Try another mirror.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(showQualityModal = false)
            val isMovie = d.mediaType == MediaType.MOVIE
            val displayTitle = if (isMovie) d.title else "${d.title} - S${_uiState.value.selectedSeason}E1"
            val item = MediaItem(
                id = d.id,
                title = displayTitle,
                posterUrl = d.posterUrl,
                mediaType = d.mediaType
            )
            onLaunchPlayer(item, targetStream, server)
        }
    }

    fun downloadStreamOption(
        context: Context,
        stream: StreamSource,
        mirror: StreamMirror? = null
    ) {
        val server = _uiState.value.qualityModalServer ?: _uiState.value.selectedServer
        val targetStream = if (mirror != null && mirror.url.isNotBlank()) {
            stream.copy(
                url = mirror.url,
                mirrorName = mirror.name,
                isLockerUrl = mirror.isLocker || repository.isLockerUrl(mirror.url)
            )
        } else {
            stream.copy(
                isLockerUrl = stream.isLockerUrl || repository.isLockerUrl(stream.url)
            )
        }

        val isLocker = targetStream.isLockerUrl || repository.isLockerUrl(targetStream.url)

        if (isLocker) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = targetStream.url)
                val resolved = repository.resolveLockerUrl(targetStream.url)
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = null)

                if (resolved != null && resolved.url.isNotBlank() && !repository.isLockerUrl(resolved.url)) {
                    val finalStream = targetStream.copy(
                        url = resolved.url,
                        headers = resolved.headers,
                        format = resolved.format,
                        codec = resolved.codec,
                        isLockerUrl = false
                    )
                    updateStreamInState(server, targetStream.url, finalStream)
                    _uiState.value = _uiState.value.copy(showQualityModal = false)
                    downloadStream(context, finalStream)
                } else {
                    Toast.makeText(context, "Could not resolve direct download link. Try another mirror.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(showQualityModal = false)
            downloadStream(context, targetStream)
        }
    }

    private fun updateStreamInState(server: StreamingServer, originalUrl: String, updatedStream: StreamSource) {
        val state = _uiState.value.serverStates[server] ?: return
        val newStreams = state.streams.map {
            if (it.url == originalUrl || it.mirrors.any { m -> m.url == originalUrl }) {
                updatedStream
            } else {
                it
            }
        }
        val updatedStates = _uiState.value.serverStates.toMutableMap()
        updatedStates[server] = state.copy(streams = newStreams)
        _uiState.value = _uiState.value.copy(
            serverStates = updatedStates,
            availableStreams = if (_uiState.value.selectedServer == server) newStreams else _uiState.value.availableStreams
        )
    }

    fun downloadStream(context: Context, stream: StreamSource) {
        val d = _uiState.value.details ?: return
        viewModelScope.launch {
            var finalStream = stream
            if (finalStream.isLockerUrl || repository.isLockerUrl(finalStream.url)) {
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = finalStream.url)
                val resolved = repository.resolveLockerUrl(finalStream.url)
                _uiState.value = _uiState.value.copy(resolvingStreamUrl = null)
                if (resolved != null && resolved.url.isNotBlank() && !repository.isLockerUrl(resolved.url)) {
                    finalStream = finalStream.copy(
                        url = resolved.url,
                        headers = resolved.headers,
                        format = resolved.format,
                        codec = resolved.codec,
                        isLockerUrl = false
                    )
                } else {
                    Toast.makeText(context, "Could not resolve direct download link. Try another mirror.", Toast.LENGTH_LONG).show()
                    return@launch
                }
            }

            val item = MediaItem(
                id = d.id,
                title = d.title,
                mediaType = d.mediaType,
                year = d.year,
                rating = d.rating,
                posterUrl = d.posterUrl
            )
            val currentEpisode = if (d.mediaType != MediaType.MOVIE) {
                _uiState.value.episodes.firstOrNull() ?: Episode(
                    id = "${d.id}-${_uiState.value.selectedSeason}-1",
                    episodeNumber = 1,
                    seasonNumber = _uiState.value.selectedSeason,
                    title = "Episode 1"
                )
            } else null

            repository.startDownload(item, finalStream, currentEpisode)
            Toast.makeText(
                context,
                "Starting download: ${item.title} (${finalStream.quality} ${finalStream.format}) via ${_uiState.value.selectedServer.displayName}...",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun retryServer(server: StreamingServer) {
        val d = _uiState.value.details ?: return
        viewModelScope.launch {
            val currentStates = _uiState.value.serverStates.toMutableMap()
            currentStates[server] = ServerState(server = server, status = ServerAvailability.CHECKING)
            _uiState.value = _uiState.value.copy(serverStates = currentStates)

            val isMovie = d.mediaType == MediaType.MOVIE
            val actualSeason = if (isMovie) 0 else _uiState.value.selectedSeason
            val actualEpisode = if (isMovie) 0 else 1

            val yr = d.year ?: d.releaseDate?.take(4)
            val resolved = repository.resolveServer(server, d.title, actualSeason, actualEpisode, mediaId, yr)
            val updated = _uiState.value.serverStates.toMutableMap()
            updated[server] = resolved
            _uiState.value = _uiState.value.copy(serverStates = updated)

            if (_uiState.value.selectedServer == server) {
                _uiState.value = _uiState.value.copy(availableStreams = resolved.streams)
            }
        }
    }

    fun dismissServerError() {
        _uiState.value = _uiState.value.copy(serverErrorMessage = null, fallbackCandidates = emptyList())
    }

    fun switchToFallbackServer(server: StreamingServer) {
        dismissServerError()
        selectServer(server)
    }

    fun startDownload(context: Context) {
        openDownloadModal(_uiState.value.selectedServer)
    }

    fun openExternalPlayer(context: Context) {
        val d = _uiState.value.details ?: return
        val currentServer = _uiState.value.selectedServer
        val state = _uiState.value.serverStates[currentServer]
        val stream = state?.streams?.firstOrNull() ?: _uiState.value.availableStreams.firstOrNull()

        if (stream != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val mime = if (stream.format.equals("MKV", ignoreCase = true) || stream.url.contains(".mkv")) {
                    "video/x-matroska"
                } else {
                    "video/mp4"
                }
                setDataAndType(Uri.parse(stream.url), mime)
                putExtra("title", d.title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                val chooser = Intent.createChooser(intent, "Play with (VLC / Nova / Video Player)").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(context, "No external video player found. Streaming in app player.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "No stream available on ${currentServer.displayName} to open externally.", Toast.LENGTH_SHORT).show()
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber)
        loadEpisodes(seasonNumber)
        val d = _uiState.value.details
        if (d != null) {
            checkServersAvailability(d.title, seasonNumber, 1)
        }
    }

    fun retry() {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val d = repository.getDetails(mediaId, mediaType)

            val seasonsList = if (d != null && d.mediaType != MediaType.MOVIE) {
                d.seasons
            } else emptyList()

            _uiState.value = _uiState.value.copy(
                details = d,
                seasons = seasonsList,
                isLoading = false
            )

            if (d != null) {
                if (seasonsList.isNotEmpty()) {
                    loadEpisodes(seasonsList.first().seasonNumber)
                }
                checkServersAvailability(d.title, 1, 1)
            }
        }
    }

    private fun loadEpisodes(seasonNumber: Int) {
        val tmdbId = _uiState.value.details?.tmdbId ?: mediaId.toIntOrNull()
        viewModelScope.launch {
            val eps = if (tmdbId != null) {
                repository.getSeasonEpisodes(tmdbId, seasonNumber)
            } else {
                emptyList()
            }

            _uiState.value = _uiState.value.copy(episodes = eps)
        }
    }

    fun checkServersAvailability(title: String, season: Int = 1, episode: Int = 1) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStreamsLoading = true)
            val isMovie = _uiState.value.details?.mediaType == MediaType.MOVIE
            val actualSeason = if (isMovie) 0 else season
            val actualEpisode = if (isMovie) 0 else episode
            val yr = _uiState.value.details?.year ?: _uiState.value.details?.releaseDate?.take(4)
            val serversMap = repository.resolveAllServers(title, actualSeason, actualEpisode, mediaId, yr)
            val selected = _uiState.value.selectedServer
            val activeStreams = serversMap[selected]?.streams ?: emptyList()

            _uiState.value = _uiState.value.copy(
                serverStates = serversMap,
                availableStreams = activeStreams,
                isStreamsLoading = false
            )
        }
    }

    private fun pickBestStream(streams: List<StreamSource>): StreamSource? {
        val supports4k = com.sumanonline.sumanmovies.util.DeviceCapabilities.supports4KHevc()
        return if (!supports4k) {
            streams.firstOrNull { it.quality.contains("1080") || it.category == "1080p" }
                ?: streams.firstOrNull { !it.quality.contains("2160") && !it.quality.contains("4k", ignoreCase = true) }
                ?: streams.firstOrNull()
        } else {
            streams.firstOrNull()
        }
    }

    fun onPlayClicked(
        onLaunchPlayer: (MediaItem, StreamSource, StreamingServer) -> Unit
    ) {
        val d = _uiState.value.details ?: return
        val currentServer = _uiState.value.selectedServer
        val state = _uiState.value.serverStates[currentServer]
        val allAvailable = state?.streams ?: _uiState.value.availableStreams
        val stream = pickBestStream(allAvailable)

        if (stream != null) {
            val item = MediaItem(
                id = d.id,
                title = d.title,
                posterUrl = d.posterUrl,
                mediaType = d.mediaType
            )
            if (stream.isLockerUrl) {
                viewModelScope.launch {
                    val resolved = repository.resolveLockerUrl(stream.url)
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
                    onLaunchPlayer(item, finalStream, currentServer)
                }
            } else {
                onLaunchPlayer(item, stream, currentServer)
            }
        } else {
            val availableAlternatives = _uiState.value.serverStates.filter {
                it.key != currentServer && it.value.status == ServerAvailability.AVAILABLE
            }.keys.toList()

            _uiState.value = _uiState.value.copy(
                serverErrorMessage = "${currentServer.displayName} is currently unavailable for this title.",
                fallbackCandidates = availableAlternatives
            )
        }
    }

    fun onEpisodeClicked(
        episode: Episode,
        onLaunchPlayer: (MediaItem, StreamSource, StreamingServer, List<StreamSource>, List<SubtitleTrack>) -> Unit
    ) {
        val d = _uiState.value.details ?: return
        val currentServer = _uiState.value.selectedServer
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStreamsLoading = true)
            val yr = d.year ?: d.releaseDate?.take(4)
            val resolvedState = repository.resolveServer(
                currentServer,
                d.title,
                episode.seasonNumber,
                episode.episodeNumber,
                d.id,
                yr
            )
            _uiState.value = _uiState.value.copy(isStreamsLoading = false)
            val stream = pickBestStream(resolvedState.streams)

            if (stream != null) {
                val item = MediaItem(
                    id = d.id,
                    title = "${d.title} - S${episode.seasonNumber}E${episode.episodeNumber}",
                    posterUrl = episode.stillUrl ?: d.posterUrl,
                    mediaType = d.mediaType
                )
                if (stream.isLockerUrl) {
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
                    onLaunchPlayer(item, finalStream, currentServer, resolvedState.streams, resolvedState.subtitles)
                } else {
                    onLaunchPlayer(item, stream, currentServer, resolvedState.streams, resolvedState.subtitles)
                }
            } else {
                val availableAlternatives = _uiState.value.serverStates.filter {
                    it.key != currentServer && it.value.status == ServerAvailability.AVAILABLE
                }.keys.toList()

                _uiState.value = _uiState.value.copy(
                    serverErrorMessage = "${currentServer.displayName} is currently unavailable for S${episode.seasonNumber}E${episode.episodeNumber}.",
                    fallbackCandidates = availableAlternatives
                )
            }
        }
    }
}
