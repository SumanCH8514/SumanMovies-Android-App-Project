package com.sumanonline.sumanmovies.ui.screens.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeasonsEpisodesUiState(
    val mediaTitle: String = "",
    val mediaId: String = "",
    val selectedSeason: Int = 1,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = true
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

            _uiState.value = _uiState.value.copy(seasons = seasonsList)
            loadSeasonEpisodes(1)
        }
    }

    fun selectSeason(seasonNumber: Int) {
        if (_uiState.value.selectedSeason == seasonNumber) return
        _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber)
        loadSeasonEpisodes(seasonNumber)
    }

    fun downloadEpisode(context: android.content.Context, episode: Episode) {
        viewModelScope.launch {
            android.widget.Toast.makeText(context, "Resolving episode stream...", android.widget.Toast.LENGTH_SHORT).show()
            val (streams, _) = repository.resolveAllStreams(
                title = _uiState.value.mediaTitle,
                season = episode.seasonNumber,
                episode = episode.episodeNumber,
                providerId = _uiState.value.mediaId
            )
            var targetStream = streams.firstOrNull { it.directFile && !repository.isLockerUrl(it.url) } ?: streams.firstOrNull()
            if (targetStream != null && (targetStream.isLockerUrl || repository.isLockerUrl(targetStream.url))) {
                val resolved = repository.resolveLockerUrl(targetStream.url)
                if (resolved != null && resolved.url.isNotBlank() && !repository.isLockerUrl(resolved.url)) {
                    targetStream = targetStream.copy(
                        url = resolved.url,
                        headers = resolved.headers,
                        format = resolved.format,
                        codec = resolved.codec,
                        isLockerUrl = false
                    )
                }
            }
            if (targetStream != null && !repository.isLockerUrl(targetStream.url)) {
                val item = com.sumanonline.sumanmovies.data.models.MediaItem(
                    id = _uiState.value.mediaId,
                    title = _uiState.value.mediaTitle,
                    mediaType = MediaType.SERIES
                )
                repository.startDownload(item, targetStream, episode)
                android.widget.Toast.makeText(context, "Downloading S${episode.seasonNumber}E${episode.episodeNumber}", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "No stream available for download", android.widget.Toast.LENGTH_SHORT).show()
            }
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
