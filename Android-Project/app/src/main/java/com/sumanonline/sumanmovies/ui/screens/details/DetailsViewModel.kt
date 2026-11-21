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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val serverStates: Map<StreamingServer, ServerState> = StreamingServer.entries.associateWith {
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
    val resolvingStreamUrl: String? = null,
    val isDirectPlaying: Boolean = false
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

        if (openModalIfSupported && (server == StreamingServer.FOURKHDHUB || server == StreamingServer.HDHUB4U || server == StreamingServer.MOVIEBOX || server == StreamingServer.REANIME)) {
            openQualityModal(server)
        }
    }

    fun playDirectOrOpenModal(
        context: Context,
        onLaunchPlayer: (MediaItem, StreamSource, StreamingServer, List<StreamSource>, List<SubtitleTrack>) -> Unit
    ) {
        val currentServer = _uiState.value.selectedServer
        if (currentServer == StreamingServer.PLAY4U || currentServer == StreamingServer.DRAMACHI) {
            playDirect(context, currentServer, onLaunchPlayer)
        } else {
            openStreamModal(currentServer)
        }
    }

    private fun calculateAbsoluteEpisode(seasonNum: Int, epNum: Int): Int? {
        if (seasonNum <= 1) return epNum.takeIf { it > 0 }
        val seasons = _uiState.value.details?.seasons ?: _uiState.value.seasons
        if (seasons.isEmpty()) return epNum.takeIf { it > 0 }

        val currentEpisodes = _uiState.value.episodes
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

    fun playDirect(
        context: Context,
        server: StreamingServer = _uiState.value.selectedServer,
        onLaunchPlayer: (MediaItem, StreamSource, StreamingServer, List<StreamSource>, List<SubtitleTrack>) -> Unit
    ) {
        val d = _uiState.value.details ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDirectPlaying = true)
            val currentState = _uiState.value.serverStates[server]
            val isMovie = d.mediaType == MediaType.MOVIE
            val actualSeason = if (isMovie) 0 else _uiState.value.selectedSeason
            val actualEpisode = if (isMovie) 0 else _uiState.value.episodes.firstOrNull()?.episodeNumber ?: 1
            val yr = d.year ?: d.releaseDate?.take(4)
            val absEp = calculateAbsoluteEpisode(actualSeason, actualEpisode)

            val resolvedState = if (currentState != null && currentState.status == ServerAvailability.AVAILABLE && currentState.streams.isNotEmpty()) {
                currentState
            } else {
                repository.resolveServer(server, d.title, actualSeason, actualEpisode, mediaId, yr, false, absEp)
            }

            _uiState.value = _uiState.value.copy(isDirectPlaying = false)

            val stream = pickBestStream(resolvedState.streams)
            if (stream != null) {
                val displayTitle = if (isMovie) d.title else "${d.title} - S${actualSeason}E$actualEpisode"
                val item = MediaItem(
                    id = d.id,
                    title = displayTitle,
                    posterUrl = d.posterUrl,
                    backdropUrl = d.backdropUrl,
                    mediaType = d.mediaType
                )

                if (stream.isLockerUrl || repository.isLockerUrl(stream.url)) {
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
                    onLaunchPlayer(item, finalStream, server, resolvedState.streams, resolvedState.subtitles)
                } else {
                    onLaunchPlayer(item, stream, server, resolvedState.streams, resolvedState.subtitles)
                }
            } else {
                val availableAlternatives = _uiState.value.serverStates.filter {
                    it.key != server && it.value.status == ServerAvailability.AVAILABLE
                }.keys.toList()

                if (availableAlternatives.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        serverErrorMessage = "${server.displayName} is currently unavailable for this title. Try ${availableAlternatives.first().displayName}.",
                        fallbackCandidates = availableAlternatives
                    )
                } else {
                    Toast.makeText(
                        context,
                        "${server.displayName} is currently unavailable for this title.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
            val actualEpisode = if (isMovie) 0 else _uiState.value.episodes.firstOrNull()?.episodeNumber ?: 1

            val yr = d.year ?: d.releaseDate?.take(4)
            val absEp = calculateAbsoluteEpisode(actualSeason, actualEpisode)
            val resolved = repository.resolveServer(server, d.title, actualSeason, actualEpisode, mediaId, yr, false, absEp)
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
                        backdropUrl = d.backdropUrl,
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
                backdropUrl = d.backdropUrl,
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
            val actualEpisode = if (isMovie) 0 else _uiState.value.episodes.firstOrNull()?.episodeNumber ?: 1

            val yr = d.year ?: d.releaseDate?.take(4)
            val absEp = calculateAbsoluteEpisode(actualSeason, actualEpisode)
            val resolved = repository.resolveServer(server, d.title, actualSeason, actualEpisode, mediaId, yr, false, absEp)
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
                val matchedSeason = _uiState.value.details?.seasons?.firstOrNull { it.seasonNumber == seasonNumber }
                if (matchedSeason != null && matchedSeason.episodes.isNotEmpty()) {
                    matchedSeason.episodes
                } else if (matchedSeason != null && matchedSeason.episodeCount > 0) {
                    (1..matchedSeason.episodeCount).map { epIdx ->
                        Episode(
                            id = "${mediaId}:s${seasonNumber}e${epIdx}",
                            episodeNumber = epIdx,
                            seasonNumber = seasonNumber,
                            title = "Episode $epIdx"
                        )
                    }
                } else {
                    repository.getSeasonEpisodesForMedia(mediaId, seasonNumber)
                }
            }

            _uiState.value = _uiState.value.copy(episodes = eps)
            val d = _uiState.value.details
            if (d != null) {
                val firstEp = eps.firstOrNull()?.episodeNumber ?: 1
                checkServersAvailability(d.title, seasonNumber, firstEp)
            }
        }
    }

    private var checkServersJob: Job? = null

    fun checkServersAvailability(title: String, season: Int = 1, episode: Int = 1) {
        checkServersJob?.cancel()
        checkServersJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStreamsLoading = true)
            val isMovie = _uiState.value.details?.mediaType == MediaType.MOVIE
            val actualSeason = if (isMovie) 0 else season
            val actualEpisode = if (isMovie) 0 else episode
            val yr = _uiState.value.details?.year ?: _uiState.value.details?.releaseDate?.take(4)

            val currentServer = _uiState.value.selectedServer
            val details = _uiState.value.details
            val isAnime = details?.mediaType == MediaType.ANIME ||
                    mediaType == MediaType.ANIME ||
                    details?.genres?.any { it.contains("Anime", ignoreCase = true) || it.contains("Animation", ignoreCase = true) } == true ||
                    mediaId.startsWith("reanime:") ||
                    com.sumanonline.sumanmovies.data.models.isAnimeCandidate(title, mediaType, details?.genres, mediaId)
            val allServers = StreamingServer.getAvailableServers(isAnime)

            val initialStates = _uiState.value.serverStates.toMutableMap()
            allServers.forEach { srv ->
                val cached = repository.getCachedServerState(srv, title, actualSeason, actualEpisode, mediaId, yr)
                if (cached != null) {
                    initialStates[srv] = cached
                } else if (initialStates[srv]?.status != ServerAvailability.AVAILABLE) {
                    initialStates[srv] = ServerState(server = srv, status = ServerAvailability.CHECKING)
                }
            }

            val cachedSelectedStreams = initialStates[currentServer]?.takeIf { it.status == ServerAvailability.AVAILABLE }?.streams
            _uiState.value = _uiState.value.copy(
                serverStates = initialStates,
                availableStreams = cachedSelectedStreams ?: _uiState.value.availableStreams
            )

            val serversToCheck = listOf(currentServer) + allServers.filter { it != currentServer }

            serversToCheck.forEach { srv ->
                val cached = initialStates[srv]
                if (cached != null && cached.status == ServerAvailability.AVAILABLE) {
                    return@forEach
                }
                launch(Dispatchers.IO) {
                    val absEp = calculateAbsoluteEpisode(actualSeason, actualEpisode)
                    val resolved = repository.resolveServer(srv, title, actualSeason, actualEpisode, mediaId, yr, false, absEp)
                    val updated = _uiState.value.serverStates.toMutableMap()
                    updated[srv] = resolved
                    val isCurrent = (_uiState.value.selectedServer == srv)
                    val newAvailable = if (isCurrent && resolved.status == ServerAvailability.AVAILABLE) {
                        resolved.streams
                    } else {
                        _uiState.value.availableStreams
                    }
                    val stillChecking = updated.values.any { it.status == ServerAvailability.CHECKING }
                    _uiState.value = _uiState.value.copy(
                        serverStates = updated,
                        availableStreams = newAvailable,
                        isStreamsLoading = stillChecking
                    )
                }
            }
        }
    }

    private fun pickBestStream(streams: List<StreamSource>): StreamSource? {
        val supports4k = com.sumanonline.sumanmovies.util.DeviceCapabilities.supports4KHevc()
        val fullStreams = streams.filter { it.sourceQualityTag != "Preview" }
        val candidates = fullStreams.ifEmpty { streams }
        return if (!supports4k) {
            candidates.firstOrNull { it.quality.contains("1080") || it.category == "1080p" }
                ?: candidates.firstOrNull { !it.quality.contains("2160") && !it.quality.contains("4k", ignoreCase = true) }
                ?: candidates.firstOrNull()
        } else {
            candidates.firstOrNull()
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
                backdropUrl = d.backdropUrl,
                mediaType = d.mediaType
            )
            val isLocker = stream.isLockerUrl || repository.isLockerUrl(stream.url)
            if (isLocker) {
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
            val absEp = calculateAbsoluteEpisode(episode.seasonNumber, episode.episodeNumber)
            val resolvedState = repository.resolveServer(
                currentServer,
                d.title,
                episode.seasonNumber,
                episode.episodeNumber,
                d.id,
                yr,
                false,
                absEp
            )
            _uiState.value = _uiState.value.copy(isStreamsLoading = false)
            val stream = pickBestStream(resolvedState.streams)

            if (stream != null) {
                val item = MediaItem(
                    id = d.id,
                    title = "${d.title} - S${episode.seasonNumber}E${episode.episodeNumber}",
                    posterUrl = episode.stillUrl ?: d.posterUrl,
                    backdropUrl = d.backdropUrl,
                    mediaType = d.mediaType
                )
                val isLocker = stream.isLockerUrl || repository.isLockerUrl(stream.url)
                if (isLocker) {
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
