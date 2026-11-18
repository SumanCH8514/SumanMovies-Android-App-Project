package com.sumanonline.sumanmovies.data.repository

import com.sumanonline.sumanmovies.data.download.AppDownloadManager
import com.sumanonline.sumanmovies.data.local.AppDatabase
import com.sumanonline.sumanmovies.data.local.entities.DownloadEntity
import com.sumanonline.sumanmovies.data.local.entities.FavoriteEntity
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.ServerState
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.data.remote.backend.BackendRepository
import com.sumanonline.sumanmovies.data.remote.fourkhdhub.FourKHdHubMobileClient
import com.sumanonline.sumanmovies.data.remote.hdhub4u.HDHub4uMobileClient
import com.sumanonline.sumanmovies.data.remote.moviebox.MovieBoxMobileClient
import com.sumanonline.sumanmovies.data.remote.play4u.Play4UMobileClient
import com.sumanonline.sumanmovies.data.remote.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class MediaRepository(
    private val database: AppDatabase,
    private val tmdbRepo: TmdbRepository,
    private val backendRepo: BackendRepository,
    private val movieBoxClient: MovieBoxMobileClient,
    private val fourKHdHubClient: FourKHdHubMobileClient,
    private val hdHub4uClient: HDHub4uMobileClient,
    private val play4UClient: Play4UMobileClient,
    private val downloadManager: AppDownloadManager,
    private val serverSelectionManager: ServerSelectionManager,
    private val playbackResolver: PlaybackResolver,
    private val searchHistoryManager: SearchHistoryManager? = null
) {
    fun getSearchHistory(): List<String> = searchHistoryManager?.getSearchHistory() ?: emptyList()
    fun addSearchQuery(query: String) { searchHistoryManager?.addSearchQuery(query) }
    fun removeSearchQuery(query: String) { searchHistoryManager?.removeSearchQuery(query) }
    fun clearSearchHistory() { searchHistoryManager?.clearSearchHistory() }
    fun getContinueWatchingFlow(): Flow<List<WatchHistoryEntity>> {
        return database.watchHistoryDao().getContinueWatching()
    }

    fun getAllHistoryFlow(): Flow<List<WatchHistoryEntity>> {
        return database.watchHistoryDao().getAllHistory()
    }

    fun getCompletedHistoryFlow(): Flow<List<WatchHistoryEntity>> {
        return database.watchHistoryDao().getCompleted()
    }

    suspend fun saveWatchProgress(
        mediaId: String,
        title: String,
        mediaType: MediaType,
        posterUrl: String?,
        season: Int = 1,
        episode: Int = 1,
        positionMs: Long,
        durationMs: Long
    ) {
        val isCompleted = durationMs > 0 && positionMs >= (durationMs * 0.95)
        val entity = WatchHistoryEntity(
            id = "$mediaId:s${season}e${episode}",
            mediaId = mediaId,
            title = title,
            mediaType = mediaType.name,
            posterUrl = posterUrl,
            seasonNumber = season,
            episodeNumber = episode,
            positionMs = positionMs,
            durationMs = durationMs,
            isCompleted = isCompleted
        )
        database.watchHistoryDao().insertOrUpdate(entity)
    }

    suspend fun getWatchProgress(id: String): WatchHistoryEntity? {
        return database.watchHistoryDao().getProgress(id)
    }

    suspend fun resetWatchProgress(id: String) {
        val existing = database.watchHistoryDao().getProgress(id)
        if (existing != null) {
            database.watchHistoryDao().insertOrUpdate(existing.copy(positionMs = 0L, isCompleted = false))
        }
    }

    suspend fun markCompleted(id: String, completed: Boolean) {
        database.watchHistoryDao().markCompleted(id, completed)
    }

    suspend fun deleteWatchProgress(id: String) {
        database.watchHistoryDao().deleteById(id)
    }

    suspend fun removeHistory(id: String) {
        deleteWatchProgress(id)
    }

    suspend fun clearHistory() {
        database.watchHistoryDao().clearAll()
    }

    fun getFavoritesFlow(): Flow<List<FavoriteEntity>> {
        return database.favoriteDao().getAllFavorites()
    }

    fun isFavoriteFlow(id: String): Flow<Boolean> {
        return database.favoriteDao().isFavoriteFlow(id)
    }

    suspend fun toggleFavorite(item: MediaItem) {
        val exists = database.favoriteDao().getFavoriteById(item.id) != null
        if (exists) {
            database.favoriteDao().deleteById(item.id)
        } else {
            database.favoriteDao().insert(
                FavoriteEntity(
                    id = item.id,
                    title = item.title,
                    mediaType = item.mediaType.name,
                    posterUrl = item.posterUrl,
                    year = item.year,
                    rating = item.rating
                )
            )
        }
    }

    fun getDownloadsFlow(): Flow<List<DownloadEntity>> {
        return downloadManager.getDownloadsFlow()
    }

    fun getDownloadProgressFlow() = downloadManager.progressFlow

    fun getCompletedDownloadsCountFlow(): Flow<Int> = downloadManager.getCompletedDownloadsCountFlow()

    fun getTotalDownloadsCountFlow(): Flow<Int> = downloadManager.getTotalDownloadsCountFlow()

    suspend fun startDownload(
        item: MediaItem,
        stream: StreamSource,
        episode: Episode? = null
    ): Long {
        return downloadManager.enqueueDownload(item, stream, episode)
    }

    suspend fun pauseDownload(id: String) {
        downloadManager.pauseDownload(id)
    }

    suspend fun resumeDownload(id: String) {
        downloadManager.resumeDownload(id)
    }

    suspend fun removeDownload(downloadId: Long) {
        downloadManager.removeDownload(downloadId)
    }

    suspend fun deleteDownloadById(id: String) {
        downloadManager.deleteDownloadById(id)
    }

    suspend fun syncDownloads() {
        downloadManager.checkAndUpdateProgress()
    }

    fun getSelectedServer(mediaId: String): StreamingServer {
        return serverSelectionManager.getSelectedServer(mediaId)
    }

    fun setSelectedServer(mediaId: String, server: StreamingServer) {
        serverSelectionManager.setSelectedServer(mediaId, server)
    }

    suspend fun resolveServer(
        server: StreamingServer,
        title: String,
        season: Int = 1,
        episode: Int = 1,
        providerId: String? = null
    ): ServerState {
        return playbackResolver.resolveServer(server, title, season, episode, providerId)
    }

    suspend fun resolveLockerUrl(url: String): StreamSource? {
        return playbackResolver.resolveLockerUrl(url)
    }

    fun isLockerUrl(url: String): Boolean {
        return playbackResolver.isLockerUrl(url)
    }

    suspend fun resolveAllServers(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        providerId: String? = null
    ): Map<StreamingServer, ServerState> = coroutineScope {
        val mbDef = async { resolveServer(StreamingServer.MOVIEBOX, title, season, episode, providerId) }
        val fourkDef = async { resolveServer(StreamingServer.FOURKHDHUB, title, season, episode, providerId) }
        val hdhub4uDef = async { resolveServer(StreamingServer.HDHUB4U, title, season, episode, providerId) }
        val play4uDef = async { resolveServer(StreamingServer.PLAY4U, title, season, episode, providerId) }
        val dramaDef = async { resolveServer(StreamingServer.DRAMACHI, title, season, episode, providerId) }

        mapOf(
            StreamingServer.PLAY4U to play4uDef.await(),
            StreamingServer.MOVIEBOX to mbDef.await(),
            StreamingServer.HDHUB4U to hdhub4uDef.await(),
            StreamingServer.FOURKHDHUB to fourkDef.await(),
            StreamingServer.DRAMACHI to dramaDef.await()
        )
    }

    suspend fun getHomeHeroBanner(): MediaItem? {
        val trending = tmdbRepo.getTrending(1)
        return trending.firstOrNull()
    }

    suspend fun getTrending(): List<MediaItem> = tmdbRepo.getTrending(1)
    suspend fun getTrendingMovies(): List<MediaItem> = tmdbRepo.getTrendingMovies(1)
    suspend fun getIndianMovies(): List<MediaItem> = tmdbRepo.getIndianMovies(1)
    suspend fun getPopularMovies(): List<MediaItem> = tmdbRepo.getPopularMovies(1)
    suspend fun getNowPlayingMovies(): List<MediaItem> = tmdbRepo.getNowPlayingMovies(1)
    suspend fun getTopRatedMovies(): List<MediaItem> = tmdbRepo.getTopRatedMovies(1)

    suspend fun getTrendingTv(): List<MediaItem> = tmdbRepo.getTrendingTv(1)
    suspend fun getIndianTv(): List<MediaItem> = tmdbRepo.getIndianTv(1)
    suspend fun getHollywoodTv(): List<MediaItem> = tmdbRepo.getHollywoodTv(1)
    suspend fun getKoreanTv(): List<MediaItem> = tmdbRepo.getKoreanTv(1)
    suspend fun getChineseTv(): List<MediaItem> = tmdbRepo.getChineseTv(1)
    suspend fun getTurkishTv(): List<MediaItem> = tmdbRepo.getTurkishTv(1)
    suspend fun getPopularTv(): List<MediaItem> = tmdbRepo.getPopularTv(1)
    suspend fun getAiringTodayTv(): List<MediaItem> = tmdbRepo.getAiringTodayTv(1)

    suspend fun getTrendingAnime(): List<MediaItem> = tmdbRepo.getTrendingAnime(1)
    suspend fun getNewSeasonAnime(): List<MediaItem> = tmdbRepo.getNewSeasonAnime(1)
    suspend fun getPopularAnime(): List<MediaItem> = tmdbRepo.getPopularAnime(1)
    suspend fun getJapaneseAnime(): List<MediaItem> = tmdbRepo.getJapaneseAnime(1)
    suspend fun getChineseAnime(): List<MediaItem> = tmdbRepo.getChineseAnime(1)
    suspend fun getAnime(): List<MediaItem> = tmdbRepo.getAnime(1)

    suspend fun getKDrama(): List<MediaItem> = tmdbRepo.getKDrama(1)
    suspend fun getChineseDrama(): List<MediaItem> = tmdbRepo.getChineseDrama(1)
    suspend fun getIndianDrama(): List<MediaItem> = tmdbRepo.getIndianDrama(1)
    suspend fun getTurkishDrama(): List<MediaItem> = tmdbRepo.getTurkishDrama(1)
    suspend fun getPakistaniDrama(): List<MediaItem> = tmdbRepo.getPakistaniDrama(1)
    suspend fun getPopularDramas(): List<MediaItem> = tmdbRepo.getPopularDramas(1)
    suspend fun getAsianDramas(): List<MediaItem> = tmdbRepo.getAsianDramas(1)
    suspend fun getLiveTvChannels(): List<MediaItem> = backendRepo.getTvChannels()

    suspend fun searchAll(query: String): List<MediaItem> {
        val tmdbResults = tmdbRepo.search(query)
        val backendResults = backendRepo.search(query)
        val movieBoxResults = movieBoxClient.search(query)
        val fourKHdHubResults = fourKHdHubClient.search(query)
        val hdHub4uResults = hdHub4uClient.search(query)
        val play4uResults = play4UClient.search(query)

        val combined = mutableListOf<MediaItem>()
        combined.addAll(tmdbResults)
        combined.addAll(backendResults.filter { b -> tmdbResults.none { it.title.equals(b.title, true) } })
        combined.addAll(movieBoxResults.filter { mb -> combined.none { it.title.equals(mb.title, true) } })
        combined.addAll(fourKHdHubResults.filter { fk -> combined.none { it.title.equals(fk.title, true) } })
        combined.addAll(hdHub4uResults.filter { hd -> combined.none { it.title.equals(hd.title, true) } })
        combined.addAll(play4uResults.filter { p4 -> combined.none { it.title.equals(p4.title, true) } })
        return combined
    }

    suspend fun getPlay4UCatalog(
        type: String? = null,
        industry: String? = null,
        lang: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> {
        return play4UClient.getCatalog(type, industry, lang, genre, page)
    }

    suspend fun searchPlay4U(query: String, page: Int = 1): List<MediaItem> {
        return play4UClient.search(query, page)
    }

    suspend fun getMovieBoxCatalog(
        type: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> {
        return movieBoxClient.getCatalog(type, genre, page)
    }

    suspend fun searchMovieBox(query: String, page: Int = 1): List<MediaItem> {
        return movieBoxClient.search(query, page)
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): List<Episode> {
        return tmdbRepo.getSeasonEpisodes(tvId, seasonNumber)
    }

    suspend fun getCategoryMedia(categoryKey: String, page: Int = 1): Pair<List<MediaItem>, Int> {
        val list = when (categoryKey.lowercase()) {
            "trending_movies", "trending movies" -> tmdbRepo.getTrendingMovies(page)
            "indian_movies", "indian movies" -> tmdbRepo.getIndianMovies(page)
            "popular_movies", "popular movies" -> tmdbRepo.getPopularMovies(page)
            "now_playing", "now playing" -> tmdbRepo.getNowPlayingMovies(page)
            "top_rated", "top rated" -> tmdbRepo.getTopRatedMovies(page)
            "trending" -> tmdbRepo.getTrending(page)

            "trending_tv", "trending series" -> tmdbRepo.getTrendingTv(page)
            "indian_tv", "indian tv series" -> tmdbRepo.getIndianTv(page)
            "hollywood_tv", "hollywood tv series" -> tmdbRepo.getHollywoodTv(page)
            "korean_tv", "korean tv series" -> tmdbRepo.getKoreanTv(page)
            "chinese_tv", "chinese tv series" -> tmdbRepo.getChineseTv(page)
            "turkish_tv", "turkish tv series" -> tmdbRepo.getTurkishTv(page)
            "popular_tv", "popular tv" -> tmdbRepo.getPopularTv(page)
            "airing_today", "airing today" -> tmdbRepo.getAiringTodayTv(page)

            "trending_anime", "trending anime" -> tmdbRepo.getTrendingAnime(page)
            "new_season_anime", "new season anime" -> tmdbRepo.getNewSeasonAnime(page)
            "popular_anime", "popular anime" -> tmdbRepo.getPopularAnime(page)
            "japanese_anime", "japanese anime" -> tmdbRepo.getJapaneseAnime(page)
            "chinese_anime", "chinese anime" -> tmdbRepo.getChineseAnime(page)
            "anime" -> tmdbRepo.getAnime(page)

            "k_drama", "korean drama", "k-drama" -> tmdbRepo.getKDrama(page)
            "chinese_drama", "chinese drama" -> tmdbRepo.getChineseDrama(page)
            "indian_drama", "indian drama" -> tmdbRepo.getIndianDrama(page)
            "turkish_drama", "turkish drama" -> tmdbRepo.getTurkishDrama(page)
            "pakistani_drama", "pakistani drama" -> tmdbRepo.getPakistaniDrama(page)
            "popular_dramas", "popular dramas" -> tmdbRepo.getPopularDramas(page)
            "asian_dramas", "asian dramas" -> tmdbRepo.getAsianDramas(page)
            else -> tmdbRepo.getPopularMovies(page)
        }
        return Pair(list, 20001)
    }

    suspend fun getDetails(id: String, type: MediaType): MediaDetails? {
        val cleanId = id.removePrefix("play4u:").trim()
        val tmdbId = id.toIntOrNull() ?: cleanId.toIntOrNull() ?: run {
            if (cleanId.startsWith("tt")) {
                tmdbRepo.findTmdbIdByImdbId(cleanId, type)
            } else null
        }
        if (tmdbId != null) {
            return if (type == MediaType.MOVIE) {
                tmdbRepo.getMovieDetails(tmdbId)
            } else {
                tmdbRepo.getTvDetails(tmdbId)
            }
        }
        if (id.startsWith("moviebox:")) {
            return movieBoxClient.getDetails(id)
        }
        return null
    }

    suspend fun resolveAllStreams(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        providerId: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val servers = resolveAllServers(title, season, episode, providerId)
        val allStreams = servers.values.flatMap { it.streams }
        val allSubs = servers.values.flatMap { it.subtitles }
        return Pair(allStreams, allSubs)
    }
}
