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
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    private val searchCache = LruCache<String, Pair<Long, List<MediaItem>>>(60)
    private val searchTtlMs = 5 * 60 * 1000L
    private val play4uSearchCache = LruCache<String, Pair<Long, List<MediaItem>>>(50)
    private val movieBoxSearchCache = LruCache<String, Pair<Long, List<MediaItem>>>(50)

    fun clearSearchCache() {
        synchronized(searchCache) { searchCache.evictAll() }
        synchronized(play4uSearchCache) { play4uSearchCache.evictAll() }
        synchronized(movieBoxSearchCache) { movieBoxSearchCache.evictAll() }
    }
    fun getSearchHistory(): List<String> = searchHistoryManager?.getSearchHistory() ?: emptyList()
    fun addSearchQuery(query: String) { searchHistoryManager?.addSearchQuery(query) }
    fun removeSearchQuery(query: String) { searchHistoryManager?.removeSearchQuery(query) }
    fun clearSearchHistory() { searchHistoryManager?.clearSearchHistory() }
    fun getContinueWatchingFlow(): Flow<List<WatchHistoryEntity>> {
        return database.watchHistoryDao().getContinueWatching().map { list ->
            list.distinctBy { it.mediaId }
        }
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
        val cleanBase = title
            .replace(Regex("""(?i)\s*-\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*s\d+e\d+.*$"""), "")
            .replace(Regex("""(?i)\s*-\s*season\s*\d+.*$"""), "")
            .trim()
            .ifBlank { title }
        val isSeries = mediaType == MediaType.SERIES || mediaType == MediaType.ANIME || mediaType == MediaType.DRAMA
        val resolvedTitle = if (isSeries) "$cleanBase - S${season}E${episode}" else title
        val entity = WatchHistoryEntity(
            id = "$mediaId:s${season}e${episode}",
            mediaId = mediaId,
            title = resolvedTitle,
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
        providerId: String? = null,
        releaseYear: String? = null
    ): ServerState {
        return playbackResolver.resolveServer(server, title, season, episode, providerId, releaseYear)
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
        providerId: String? = null,
        releaseYear: String? = null
    ): Map<StreamingServer, ServerState> = coroutineScope {
        val mbDef = async { resolveServer(StreamingServer.MOVIEBOX, title, season, episode, providerId, releaseYear) }
        val fourkDef = async { resolveServer(StreamingServer.FOURKHDHUB, title, season, episode, providerId, releaseYear) }
        val hdhub4uDef = async { resolveServer(StreamingServer.HDHUB4U, title, season, episode, providerId, releaseYear) }
        val play4uDef = async { resolveServer(StreamingServer.PLAY4U, title, season, episode, providerId, releaseYear) }
        val dramaDef = async { resolveServer(StreamingServer.DRAMACHI, title, season, episode, providerId, releaseYear) }

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
    suspend fun getAnimeMovies(): List<MediaItem> = tmdbRepo.getAnimeMovies(1)
    suspend fun getActionAdventureMovies(): List<MediaItem> = tmdbRepo.getActionAdventureMovies(1)
    suspend fun getSciFiFantasyMovies(): List<MediaItem> = tmdbRepo.getSciFiFantasyMovies(1)
    suspend fun getComedyMovies(): List<MediaItem> = tmdbRepo.getComedyMovies(1)
    suspend fun getHorrorThrillerMovies(): List<MediaItem> = tmdbRepo.getHorrorThrillerMovies(1)
    suspend fun getRomanceMovies(): List<MediaItem> = tmdbRepo.getRomanceMovies(1)
    suspend fun getCrimeMysteryMovies(): List<MediaItem> = tmdbRepo.getCrimeMysteryMovies(1)

    suspend fun getTrendingTv(): List<MediaItem> = tmdbRepo.getTrendingTv(1)
    suspend fun getIndianTv(): List<MediaItem> = tmdbRepo.getIndianTv(1)
    suspend fun getHollywoodTv(): List<MediaItem> = tmdbRepo.getHollywoodTv(1)
    suspend fun getKoreanTv(): List<MediaItem> = tmdbRepo.getKoreanTv(1)
    suspend fun getChineseTv(): List<MediaItem> = tmdbRepo.getChineseTv(1)
    suspend fun getTurkishTv(): List<MediaItem> = tmdbRepo.getTurkishTv(1)
    suspend fun getPopularTv(): List<MediaItem> = tmdbRepo.getPopularTv(1)
    suspend fun getAiringTodayTv(): List<MediaItem> = tmdbRepo.getAiringTodayTv(1)
    suspend fun getTopRatedTv(): List<MediaItem> = tmdbRepo.getTopRatedTv(1)
    suspend fun getDramaSeries(): List<MediaItem> = tmdbRepo.getDramaSeries(1)
    suspend fun getCrimeSeries(): List<MediaItem> = tmdbRepo.getCrimeSeries(1)
    suspend fun getComedySeries(): List<MediaItem> = tmdbRepo.getComedySeries(1)
    suspend fun getSciFiFantasySeries(): List<MediaItem> = tmdbRepo.getSciFiFantasySeries(1)
    suspend fun getActionAdventureSeries(): List<MediaItem> = tmdbRepo.getActionAdventureSeries(1)
    suspend fun getMysterySuspenseSeries(): List<MediaItem> = tmdbRepo.getMysterySuspenseSeries(1)

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

    suspend fun searchAll(query: String, bypassCache: Boolean = false): List<MediaItem> {
        val cleanQuery = query.trim().lowercase(java.util.Locale.ROOT)
        if (cleanQuery.isBlank()) return emptyList()

        if (!bypassCache) {
            val cached = synchronized(searchCache) { searchCache.get(cleanQuery) }
            if (cached != null && (System.currentTimeMillis() - cached.first) < searchTtlMs && cached.second.isNotEmpty()) {
                return cached.second
            }
        }

        val results = try {
            tmdbRepo.search(query)
        } catch (_: Exception) {
            emptyList()
        }

        if (results.isNotEmpty()) {
            synchronized(searchCache) {
                searchCache.put(cleanQuery, Pair(System.currentTimeMillis(), results))
            }
        }
        return results
    }

    private val itemCache = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()

    fun cacheItem(item: MediaItem) {
        itemCache[item.id] = item
        item.tmdbId?.let { tmdbId ->
            itemCache[tmdbId.toString()] = item
        }
    }

    fun getCachedItem(id: String): MediaItem? = itemCache[id]

    suspend fun getPlay4UCatalog(
        type: String? = null,
        industry: String? = null,
        lang: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> {
        val res = play4UClient.getCatalog(type, industry, lang, genre, page)
        res.first.forEach { cacheItem(it) }
        return res
    }

    suspend fun searchPlay4U(query: String, page: Int = 1): List<MediaItem> {
        val cleanQuery = query.trim().lowercase(java.util.Locale.ROOT)
        val cacheKey = "play4u_${cleanQuery}_$page"
        if (page == 1) {
            val cached = synchronized(play4uSearchCache) { play4uSearchCache.get(cacheKey) }
            if (cached != null && (System.currentTimeMillis() - cached.first) < searchTtlMs && cached.second.isNotEmpty()) {
                cached.second.forEach { cacheItem(it) }
                return cached.second
            }
        }
        val results = play4UClient.search(query, page)
        results.forEach { cacheItem(it) }
        if (page == 1 && results.isNotEmpty()) {
            synchronized(play4uSearchCache) {
                play4uSearchCache.put(cacheKey, Pair(System.currentTimeMillis(), results))
            }
        }
        return results
    }

    suspend fun getMovieBoxCatalog(
        type: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> {
        val res = movieBoxClient.getCatalog(type, genre, page)
        res.first.forEach { cacheItem(it) }
        return res
    }

    suspend fun searchMovieBox(query: String, page: Int = 1): List<MediaItem> {
        val cleanQuery = query.trim().lowercase(java.util.Locale.ROOT)
        val cacheKey = "moviebox_${cleanQuery}_$page"
        if (page == 1) {
            val cached = synchronized(movieBoxSearchCache) { movieBoxSearchCache.get(cacheKey) }
            if (cached != null && (System.currentTimeMillis() - cached.first) < searchTtlMs && cached.second.isNotEmpty()) {
                cached.second.forEach { cacheItem(it) }
                return cached.second
            }
        }
        val results = movieBoxClient.search(query, page)
        results.forEach { cacheItem(it) }
        if (page == 1 && results.isNotEmpty()) {
            synchronized(movieBoxSearchCache) {
                movieBoxSearchCache.put(cacheKey, Pair(System.currentTimeMillis(), results))
            }
        }
        return results
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): List<Episode> {
        return tmdbRepo.getSeasonEpisodes(tvId, seasonNumber)
    }

    suspend fun getCategoryMedia(categoryKey: String, page: Int = 1): Pair<List<MediaItem>, Int> {
        val lowerKey = categoryKey.lowercase().trim()
        val genreClean = lowerKey.removePrefix("genre_").trim()
        val isGenre = lowerKey.startsWith("genre_") || genreClean in setOf(
            "action", "adventure", "animation", "comedy", "crime", "documentary",
            "drama", "fantasy", "horror", "mystery", "romance", "science_fiction",
            "science fiction", "scifi", "sci-fi", "thriller", "war", "western", "family", "history", "music"
        )
        if (isGenre) {
            val list = tmdbRepo.getMediaByGenre(genreClean, page)
            return Pair(list, 20001)
        }

        val list = when (lowerKey) {
            "top_10_trending_movies", "top 10 trending movies", "trending_movies", "trending movies" -> tmdbRepo.getTrendingMovies(page)
            "indian_cinema", "indian cinema", "indian_movies", "indian movies" -> tmdbRepo.getIndianMovies(page)
            "anime_movies", "anime movies" -> tmdbRepo.getAnimeMovies(page)
            "popular_movies", "popular movies" -> tmdbRepo.getPopularMovies(page)
            "action_adventure_movies", "action & adventure", "action & adventure movies" -> tmdbRepo.getActionAdventureMovies(page)
            "scifi_fantasy_movies", "sci-fi & fantasy", "sci-fi & fantasy movies" -> tmdbRepo.getSciFiFantasyMovies(page)
            "comedy_movies", "comedy movies" -> tmdbRepo.getComedyMovies(page)
            "horror_thriller_movies", "horror & thriller", "horror & thriller movies" -> tmdbRepo.getHorrorThrillerMovies(page)
            "romance_movies", "romance movies" -> tmdbRepo.getRomanceMovies(page)
            "crime_mystery_movies", "crime & mystery", "crime & mystery movies" -> tmdbRepo.getCrimeMysteryMovies(page)
            "top_rated", "top rated", "top_rated_movies", "top rated movies" -> tmdbRepo.getTopRatedMovies(page)
            "now_playing", "now playing" -> tmdbRepo.getNowPlayingMovies(page)
            "trending" -> tmdbRepo.getTrending(page)

            "top_10_trending_series", "top 10 trending series", "trending_tv", "trending series" -> tmdbRepo.getTrendingTv(page)
            "indian_webseries", "indian webseries", "indian_tv", "indian tv series" -> tmdbRepo.getIndianTv(page)
            "popular_tv", "popular tv", "popular tv shows" -> tmdbRepo.getPopularTv(page)
            "drama_series", "drama series" -> tmdbRepo.getDramaSeries(page)
            "crime_stories", "crime stories", "crime series" -> tmdbRepo.getCrimeSeries(page)
            "comedy_series", "comedy series" -> tmdbRepo.getComedySeries(page)
            "scifi_fantasy_series", "sci-fi & fantasy series" -> tmdbRepo.getSciFiFantasySeries(page)
            "action_adventure_series", "action & adventure series" -> tmdbRepo.getActionAdventureSeries(page)
            "mystery_suspense_series", "mystery & suspense", "mystery & suspense series" -> tmdbRepo.getMysterySuspenseSeries(page)
            "kdrama_sensations", "k-drama sensations", "k_drama", "korean drama", "k-drama" -> tmdbRepo.getKDrama(page)
            "top_rated_tv", "top rated tv series", "top rated tv" -> tmdbRepo.getTopRatedTv(page)
            "hollywood_tv", "hollywood tv series" -> tmdbRepo.getHollywoodTv(page)
            "korean_tv", "korean tv series" -> tmdbRepo.getKoreanTv(page)
            "chinese_tv", "chinese tv series" -> tmdbRepo.getChineseTv(page)
            "turkish_tv", "turkish tv series" -> tmdbRepo.getTurkishTv(page)
            "airing_today", "airing today" -> tmdbRepo.getAiringTodayTv(page)

            "trending_anime", "trending anime" -> tmdbRepo.getTrendingAnime(page)
            "new_season_anime", "new season anime" -> tmdbRepo.getNewSeasonAnime(page)
            "popular_anime", "popular anime" -> tmdbRepo.getPopularAnime(page)
            "japanese_anime", "japanese anime" -> tmdbRepo.getJapaneseAnime(page)
            "chinese_anime", "chinese anime" -> tmdbRepo.getChineseAnime(page)
            "anime" -> tmdbRepo.getAnime(page)

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
        val cached = itemCache[id]
        val cleanId = id.removePrefix("play4u:").removePrefix("moviebox:").trim()
        val tmdbId = id.toIntOrNull() ?: cleanId.toIntOrNull() ?: cached?.tmdbId ?: run {
            if (cleanId.startsWith("tt")) {
                tmdbRepo.findTmdbIdByImdbId(cleanId, type)
            } else null
        }
        if (tmdbId != null) {
            val tmdbDetails = if (type == MediaType.MOVIE) {
                tmdbRepo.getMovieDetails(tmdbId)
            } else {
                tmdbRepo.getTvDetails(tmdbId)
            }
            if (tmdbDetails != null) return tmdbDetails
        }

        if (id.startsWith("moviebox:")) {
            val mbDetails = movieBoxClient.getDetails(id)
            if (mbDetails != null) {
                return mbDetails
            }
        }

        val candidateTitle = cached?.title ?: run {
            val withoutYear = cleanId.replace(Regex("""[-_]?(19\d{2}|20\d{2}).*"""), "")
            withoutYear.replace('-', ' ').replace('_', ' ').trim()
        }
        val candidateYear = cached?.year ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(cleanId)?.value

        if (candidateTitle.isNotBlank()) {
            val searchResults = tmdbRepo.search(candidateTitle)
            val matched = if (!candidateYear.isNullOrBlank()) {
                searchResults.firstOrNull { it.year == candidateYear && it.mediaType == type }
                    ?: searchResults.firstOrNull { it.year == candidateYear }
                    ?: searchResults.firstOrNull { it.mediaType == type }
                    ?: searchResults.firstOrNull()
            } else {
                searchResults.firstOrNull { it.mediaType == type } ?: searchResults.firstOrNull()
            }

            if (matched?.tmdbId != null) {
                val fullDetails = if (matched.mediaType == MediaType.MOVIE) {
                    tmdbRepo.getMovieDetails(matched.tmdbId)
                } else {
                    tmdbRepo.getTvDetails(matched.tmdbId)
                }
                if (fullDetails != null) return fullDetails
            }
        }

        val fallbackTitle = cached?.title ?: candidateTitle.ifBlank { "Untitled" }
        return MediaDetails(
            id = id,
            title = fallbackTitle,
            mediaType = cached?.mediaType ?: type,
            year = cached?.year ?: candidateYear,
            rating = cached?.rating,
            posterUrl = cached?.posterUrl,
            backdropUrl = cached?.backdropUrl ?: cached?.posterUrl,
            overview = cached?.overview ?: "Curated title available for streaming and downloading.",
            genres = cached?.genres ?: emptyList(),
            seasons = if ((cached?.mediaType ?: type) == MediaType.SERIES) {
                listOf(
                    com.sumanonline.sumanmovies.data.models.Season(
                        seasonNumber = 1,
                        name = "Season 1",
                        episodeCount = 10,
                        posterUrl = cached?.posterUrl
                    )
                )
            } else emptyList()
        )
    }

    suspend fun resolveAllStreams(
        title: String,
        season: Int = 1,
        episode: Int = 1,
        providerId: String? = null,
        releaseYear: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val servers = resolveAllServers(title, season, episode, providerId, releaseYear)
        val allStreams = servers.values.flatMap { it.streams }
        val allSubs = servers.values.flatMap { it.subtitles }
        return Pair(allStreams, allSubs)
    }
}
