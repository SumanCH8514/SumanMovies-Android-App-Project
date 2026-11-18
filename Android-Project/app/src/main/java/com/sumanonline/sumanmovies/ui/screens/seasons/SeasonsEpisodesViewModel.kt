package com.sumanonline.sumanmovies.ui.screens.seasons

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import com.sumanonline.sumanmovies.data.models.ServerState
import com.sumanonline.sumanmovies.data.models.StreamMirror
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeasonsEpisodesUiState(
    val mediaTitle: String = "",
    val mediaId: String = "",
    val posterUrl: String? = null,
    val releaseYear: String? = null,
    val selectedSeason: Int = 1,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = true,
    val showDownloadModal: Boolean = false,
    val selectedEpisodeForDownload: Episode? = null,
    val downloadModalServer: StreamingServer = StreamingServer.FOURKHDHUB,
    val serverStates: Map<StreamingServer, ServerState> = emptyMap(),
    val isDownloadModalLoading: Boolean = false,
    val resolvingStreamUrl: String? = null
)

class SeasonsEpisodesViewModel(
    private val repository: MediaRepository,
    private val mediaId: String,
    private val mediaTitle: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SeasonsEpisodesUiState(
            mediaTitle = mediaTitle,
            mediaId = mediaId
        )
    )
    val uiState: StateFlow<SeasonsEpisodesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val details = repository.getDetails(mediaId, MediaType.SERIES)
            val seasonsList = details?.seasons?.ifEmpty {
                listOf(
                    Season(1, "Season 1", 8),
                    Season(2, "Season 2", 8),
                    Season(3, "Season 3", 8)
                )
            } ?: listOf(
                Season(1, "Season 1", 8),
                Season(2, "Season 2", 8),
                Season(3, "Season 3", 8)
            )

            _uiState.value = _uiState.value.copy(
                seasons = seasonsList,
                posterUrl = details?.posterUrl,
                releaseYear = details?.releaseDate?.take(4) ?: details?.year
            )
            loadSeasonEpisodes(1)
        }
    }

    fun selectSeason(seasonNumber: Int) {
        if (_uiState.value.selectedSeason == seasonNumber) return
        _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber)
        loadSeasonEpisodes(seasonNumber)
    }

    fun openDownloadModal(episode: Episode) {
        _uiState.value = _uiState.value.copy(
            showDownloadModal = true,
            selectedEpisodeForDownload = episode,
            serverStates = emptyMap()
        )
        fetchDownloadQualities(_uiState.value.downloadModalServer, episode)
    }

    fun closeDownloadModal() {
        _uiState.value = _uiState.value.copy(
            showDownloadModal = false,
            selectedEpisodeForDownload = null,
            resolvingStreamUrl = null
        )
    }

    fun selectDownloadServer(server: StreamingServer) {
        _uiState.value = _uiState.value.copy(downloadModalServer = server)
        val episode = _uiState.value.selectedEpisodeForDownload ?: return
        val currentStreams = _uiState.value.serverStates[server]?.streams
        if (currentStreams.isNullOrEmpty()) {
            fetchDownloadQualities(server, episode)
        }
    }

    fun retryDownloadFetch() {
        val episode = _uiState.value.selectedEpisodeForDownload ?: return
        fetchDownloadQualities(_uiState.value.downloadModalServer, episode)
    }

    fun fetchDownloadQualities(server: StreamingServer, episode: Episode) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloadModalLoading = true)
            val resolved = repository.resolveServer(
                server = server,
                title = _uiState.value.mediaTitle,
                season = episode.seasonNumber,
                episode = episode.episodeNumber,
                providerId = _uiState.value.mediaId,
                releaseYear = _uiState.value.releaseYear
            )
            val updated = _uiState.value.serverStates.toMutableMap()
            updated[server] = resolved
            _uiState.value = _uiState.value.copy(
                serverStates = updated,
                isDownloadModalLoading = false
            )
        }
    }

    fun downloadStreamOption(
        context: Context,
        stream: StreamSource,
        mirror: StreamMirror? = null,
        onComplete: () -> Unit = {}
    ) {
        val episode = _uiState.value.selectedEpisodeForDownload ?: return
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
                    startDownloadInternal(context, finalStream, episode)
                    onComplete()
                } else {
                    Toast.makeText(context, "Could not resolve direct CDN download link. Try another mirror.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            startDownloadInternal(context, targetStream, episode)
            onComplete()
        }
    }

    private fun startDownloadInternal(
        context: Context,
        stream: StreamSource,
        episode: Episode
    ) {
        viewModelScope.launch {
            val epPart = " - S${episode.seasonNumber}E${episode.episodeNumber}"
            val epTitlePart = if (episode.title.isNotBlank()) " • ${episode.title}" else ""
            val item = MediaItem(
                id = _uiState.value.mediaId,
                title = "${_uiState.value.mediaTitle}$epPart$epTitlePart",
                posterUrl = episode.stillUrl ?: _uiState.value.posterUrl,
                mediaType = MediaType.SERIES
            )
            repository.startDownload(item, stream, episode)
            Toast.makeText(context, "Downloading S${episode.seasonNumber}E${episode.episodeNumber}", Toast.LENGTH_SHORT).show()
            _uiState.value = _uiState.value.copy(showDownloadModal = false, resolvingStreamUrl = null)
        }
    }

    private fun loadSeasonEpisodes(seasonNumber: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val tmdbId = mediaId.toIntOrNull()
            val episodes = if (tmdbId != null) {
                repository.getSeasonEpisodes(tmdbId, seasonNumber)
            } else {
                emptyList<Episode>()
            }

            val finalEpisodes = if (episodes.isEmpty()) {
                val dates = listOf("Sep 1, 2022", "Sep 1, 2022", "Sep 8, 2022", "Sep 15, 2022", "Sep 22, 2022", "Sep 29, 2022", "Oct 6, 2022", "Oct 13, 2022")
                val titles = listOf(
                    "A Shadow of the Past",
                    "Adrift",
                    "Adar",
                    "The Great Wave",
                    "Partings",
                    "Udûn",
                    "The Eye",
                    "Alloyed"
                )
                (1..8).map { epNum ->
                    Episode(
                        id = "$mediaId-$seasonNumber-$epNum",
                        episodeNumber = epNum,
                        seasonNumber = seasonNumber,
                        title = titles.getOrElse(epNum - 1) { "Episode $epNum" },
                        overview = "Description for episode $epNum",
                        stillUrl = null,
                        durationMinutes = 66 + (epNum * 2),
                        airDate = dates.getOrElse(epNum - 1) { "2022" }
                    )
                }
            } else {
                episodes
            }

            _uiState.value = _uiState.value.copy(
                episodes = finalEpisodes,
                isLoading = false
            )
        }
    }
}
