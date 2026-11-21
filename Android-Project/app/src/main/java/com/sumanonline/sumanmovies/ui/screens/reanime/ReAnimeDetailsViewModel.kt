package com.sumanonline.sumanmovies.ui.screens.reanime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.ReAnimeAnimeDetails
import com.sumanonline.sumanmovies.data.models.ReAnimeEpisode
import com.sumanonline.sumanmovies.data.models.ReAnimePlaybackInfo
import com.sumanonline.sumanmovies.data.models.ReAnimeSeasonRelation
import com.sumanonline.sumanmovies.data.models.ReAnimeServerOption
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReAnimeEpisodeFilter(val label: String) {
    ALL("ALL"),
    SUB("SUB"),
    DUB("DUB")
}

data class ReAnimeDetailsUiState(
    val isLoading: Boolean = true,
    val details: ReAnimeAnimeDetails? = null,
    val errorMessage: String? = null,
    val selectedSeasonId: String? = null,
    val selectedChunkIndex: Int = 0,
    val filterType: ReAnimeEpisodeFilter = ReAnimeEpisodeFilter.ALL,
    val searchQuery: String = "",
    val isGridView: Boolean = true,
    val showFillerOnly: Boolean = false,
    val currentlyPlayingEpisode: Int = 1,
    val playbackInfo: ReAnimePlaybackInfo? = null,
    val selectedServer: ReAnimeServerOption? = null,
    val selectedStream: StreamSource? = null,
    val isLoadingPlayback: Boolean = false,
    val playbackError: String? = null,
    val isWebEmbedMode: Boolean = false,
    val isFullscreen: Boolean = false
) {
    val totalEpisodesCount: Int
        get() = details?.episodes?.size ?: details?.episodesTotal ?: 0

    val chunkOptions: List<Pair<Int, String>>
        get() {
            val total = totalEpisodesCount
            if (total <= 0) return listOf(0 to "1-1")
            val chunks = mutableListOf<Pair<Int, String>>()
            var start = 1
            var idx = 0
            while (start <= total) {
                val end = minOf(start + 99, total)
                chunks.add(idx to "$start-$end")
                start += 100
                idx++
            }
            return chunks
        }

    val currentChunkLabel: String
        get() {
            val chunks = chunkOptions
            return chunks.firstOrNull { it.first == selectedChunkIndex }?.second ?: chunks.firstOrNull()?.second ?: "1-100"
        }

    val currentPlayingEpisodeObj: ReAnimeEpisode?
        get() = details?.episodes?.firstOrNull { it.episodeNumber == currentlyPlayingEpisode }

    val displayedEpisodes: List<ReAnimeEpisode>
        get() {
            val all = details?.episodes ?: emptyList()
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                return all.filter { ep ->
                    ep.episodeNumber.toString() == q ||
                    ep.title.lowercase().contains(q) ||
                    "episode ${ep.episodeNumber}".contains(q) ||
                    "ep ${ep.episodeNumber}".contains(q)
                }
            }

            var filtered = when (filterType) {
                ReAnimeEpisodeFilter.ALL -> all
                ReAnimeEpisodeFilter.SUB -> all.filter { it.isSubbed }
                ReAnimeEpisodeFilter.DUB -> all.filter { it.isDubbed }
            }

            if (showFillerOnly) {
                filtered = filtered.filter { it.isFiller }
            }

            if (all.size <= 100) {
                return filtered
            }

            val startNum = selectedChunkIndex * 100 + 1
            val endNum = startNum + 99
            return filtered.filter { it.episodeNumber in startNum..endNum }
        }
}

class ReAnimeDetailsViewModel(
    private val repository: MediaRepository,
    private val initialMediaId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReAnimeDetailsUiState())
    val uiState: StateFlow<ReAnimeDetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetails(initialMediaId)
    }

    fun loadDetails(mediaId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                selectedSeasonId = mediaId
            )
            try {
                val result = repository.getReAnimeAnimeDetails(mediaId)
                if (result != null) {
                    val defaultEp = 1
                    val chunkIdx = (defaultEp - 1) / 100
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        details = result,
                        selectedChunkIndex = chunkIdx,
                        currentlyPlayingEpisode = defaultEp,
                        errorMessage = null
                    )
                    playEpisodeNumber(defaultEp, result)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Unable to load anime details from ReAnime or AniList"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load anime details"
                )
            }
        }
    }

    fun playEpisode(episode: ReAnimeEpisode) {
        playEpisodeNumber(episode.episodeNumber, _uiState.value.details)
    }

    fun playEpisodeNumber(episodeNumber: Int, currentDetails: ReAnimeAnimeDetails? = _uiState.value.details) {
        val details = currentDetails ?: _uiState.value.details
        _uiState.value = _uiState.value.copy(
            currentlyPlayingEpisode = episodeNumber,
            isLoadingPlayback = true,
            playbackError = null
        )

        viewModelScope.launch {
            try {
                val anilistId = details?.anilistId
                val slug = details?.slug ?: initialMediaId
                val info = repository.resolveReAnimePlayback(anilistId, slug, episodeNumber)

                val selectedServer = info.selectedServer
                val selectedStream = info.selectedStream

                val effectiveWebEmbed = if (info.directStreams.isEmpty() && !info.embedUrl.isNullOrBlank()) {
                    true
                } else {
                    _uiState.value.isWebEmbedMode
                }

                _uiState.value = _uiState.value.copy(
                    playbackInfo = info,
                    selectedServer = selectedServer,
                    selectedStream = selectedStream,
                    isWebEmbedMode = effectiveWebEmbed,
                    isLoadingPlayback = false,
                    playbackError = if (info.servers.isEmpty() && info.directStreams.isEmpty() && info.embedUrl.isNullOrBlank()) {
                        "No streaming source found for Episode $episodeNumber"
                    } else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPlayback = false,
                    playbackError = e.message ?: "Failed to resolve stream for Episode $episodeNumber"
                )
            }
        }
    }

    fun switchServer(server: ReAnimeServerOption) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedServer = server,
                isLoadingPlayback = true,
                playbackError = null
            )
            try {
                val direct = repository.resolveReAnimeDirectStreams(server)
                val currentInfo = _uiState.value.playbackInfo
                val updatedInfo = currentInfo?.copy(
                    selectedServer = server,
                    embedUrl = server.embedUrl,
                    directStreams = direct,
                    selectedStream = direct.firstOrNull()
                )
                _uiState.value = _uiState.value.copy(
                    playbackInfo = updatedInfo,
                    selectedStream = direct.firstOrNull(),
                    isLoadingPlayback = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPlayback = false,
                    playbackError = e.message ?: "Failed to switch server"
                )
            }
        }
    }

    fun selectStream(stream: StreamSource) {
        _uiState.value = _uiState.value.copy(selectedStream = stream)
    }

    fun togglePlayerMode() {
        _uiState.value = _uiState.value.copy(isWebEmbedMode = !_uiState.value.isWebEmbedMode)
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = fullscreen)
    }

    fun selectSeason(relation: ReAnimeSeasonRelation) {
        loadDetails(relation.id)
    }

    fun selectChunk(chunkIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedChunkIndex = chunkIndex)
    }

    fun setFilter(filter: ReAnimeEpisodeFilter) {
        _uiState.value = _uiState.value.copy(filterType = filter)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleViewMode() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
    }

    fun toggleFillerOnly() {
        _uiState.value = _uiState.value.copy(showFillerOnly = !_uiState.value.showFillerOnly)
    }

    fun startDownload(
        details: ReAnimeAnimeDetails,
        episode: ReAnimeEpisode,
        stream: StreamSource,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val mediaItem = MediaItem(
                    id = "reanime:${details.slug.ifBlank { details.id }}",
                    title = details.displayTitle,
                    mediaType = MediaType.SERIES,
                    posterUrl = details.posterUrl,
                    backdropUrl = details.bannerUrl,
                    year = details.year,
                    provider = "reanime"
                )
                val ep = Episode(
                    id = "${mediaItem.id}-S1E${episode.episodeNumber}",
                    seasonNumber = 1,
                    episodeNumber = episode.episodeNumber,
                    title = episode.title,
                    overview = "",
                    stillUrl = episode.thumbnailUrl,
                    airDate = episode.airedDate
                )
                repository.cacheItem(mediaItem)
                repository.startDownload(mediaItem, stream, ep)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to start download")
            }
        }
    }

    fun refresh() {
        val currentId = _uiState.value.selectedSeasonId ?: initialMediaId
        loadDetails(currentId)
    }
}
