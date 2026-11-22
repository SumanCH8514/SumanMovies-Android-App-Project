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
import com.sumanonline.sumanmovies.data.models.ReAnimeAnimeDetails
import com.sumanonline.sumanmovies.data.models.ReAnimePlaybackInfo
import com.sumanonline.sumanmovies.data.models.ReAnimeServerOption
import com.sumanonline.sumanmovies.data.models.AnimeXinGenre
import com.sumanonline.sumanmovies.data.models.MoviesLeechGenre
import com.sumanonline.sumanmovies.data.models.ServerState
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.data.models.TvChannel
import com.sumanonline.sumanmovies.data.remote.animexin.AnimeXinMobileClient
import com.sumanonline.sumanmovies.data.remote.backend.BackendRepository
import com.sumanonline.sumanmovies.data.remote.fourkhdhub.FourKHdHubMobileClient
import com.sumanonline.sumanmovies.data.remote.hdhub4u.HDHub4uMobileClient
import com.sumanonline.sumanmovies.data.models.MediaSegment
import com.sumanonline.sumanmovies.data.remote.kmdl.KMDLMobileClient
import com.sumanonline.sumanmovies.data.remote.moviebox.MovieBoxMobileClient
import com.sumanonline.sumanmovies.data.remote.moviesleech.MoviesLeechMobileClient
import com.sumanonline.sumanmovies.data.remote.moviesmod.MoviesModMobileClient
import com.sumanonline.sumanmovies.data.remote.play4u.Play4UMobileClient
import com.sumanonline.sumanmovies.data.remote.reanime.ReAnimeMobileClient
import com.sumanonline.sumanmovies.data.remote.theintrodb.TheIntroDbClient
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
    private val searchHistoryManager: SearchHistoryManager? = null,
    private val kmdlClient: KMDLMobileClient = KMDLMobileClient(),
    private val moviesLeechClient: MoviesLeechMobileClient = MoviesLeechMobileClient(),
    private val moviesModClient: MoviesModMobileClient = MoviesModMobileClient(),
    private val animeXinClient: AnimeXinMobileClient = AnimeXinMobileClient(),
    private val reAnimeClient: ReAnimeMobileClient = ReAnimeMobileClient(),
    private val theIntroDbClient: TheIntroDbClient = TheIntroDbClient()
) {
    private val searchCache = LruCache<String, Pair<Long, List<MediaItem>>>(60)
    private val searchTtlMs = 5 * 60 * 1000L
    private val play4uSearchCache = LruCache<String, Pair<Long, List<MediaItem>>>(50)
    private val movieBoxSearchCache = LruCache<String, Pair<Long, List<MediaItem>>>(50)
    private val tmdbArtworkCache = LruCache<String, Pair<String?, String?>>(1500)

    fun clearSearchCache() {
        synchronized(searchCache) { searchCache.evictAll() }
        synchronized(play4uSearchCache) { play4uSearchCache.evictAll() }
        synchronized(movieBoxSearchCache) { movieBoxSearchCache.evictAll() }
        synchronized(tmdbArtworkCache) { tmdbArtworkCache.evictAll() }
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
        durationMs: Long,
        backdropUrl: String? = null,
        stillUrl: String? = null
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
        val existing = database.watchHistoryDao().getProgress("$mediaId:s${season}e${episode}")
        val finalBackdrop = backdropUrl ?: existing?.backdropUrl
        val finalStill = stillUrl ?: existing?.stillUrl
        val entity = WatchHistoryEntity(
            id = "$mediaId:s${season}e${episode}",
            mediaId = mediaId,
            title = resolvedTitle,
            mediaType = mediaType.name,
            posterUrl = posterUrl ?: existing?.posterUrl,
            backdropUrl = finalBackdrop,
            stillUrl = finalStill,
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

    suspend fun clearContinueWatching() {
        database.watchHistoryDao().clearContinueWatching()
    }

    suspend fun removeContinueWatchingItem(id: String, mediaId: String = "") {
        val cleanMediaId = mediaId.ifBlank { id.substringBefore(":") }
        database.watchHistoryDao().deleteContinueWatchingByMedia(id, cleanMediaId)
    }

    suspend fun markContinueWatchingCompleted(id: String, mediaId: String = "") {
        val cleanMediaId = mediaId.ifBlank { id.substringBefore(":") }
        database.watchHistoryDao().markMediaCompleted(id, cleanMediaId)
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
        val resolvedYear = if (!item.year.isNullOrBlank()) {
            item.year
        } else {
            itemCache[item.id]?.year
        }
        val enrichedItem = if (resolvedYear != null && item.year != resolvedYear) {
            item.copy(year = resolvedYear)
        } else {
            item
        }
        return downloadManager.enqueueDownload(enrichedItem, stream, episode)
    }

    suspend fun fetchHlsVariants(url: String, headers: Map<String, String>) =
        downloadManager.fetchHlsVariants(url, headers)

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

    fun getCachedServerState(
        server: StreamingServer,
        title: String,
        season: Int = 1,
        episode: Int = 1,
        providerId: String? = null,
        releaseYear: String? = null
    ): ServerState? {
        return playbackResolver.getCachedServer(server, title, season, episode, providerId, releaseYear)
    }

    suspend fun resolveServer(
        server: StreamingServer,
        title: String,
        season: Int = 1,
        episode: Int = 1,
        providerId: String? = null,
        releaseYear: String? = null,
        forceRefresh: Boolean = false,
        absoluteEpisode: Int? = null
    ): ServerState {
        return playbackResolver.resolveServer(server, title, season, episode, providerId, releaseYear, forceRefresh, absoluteEpisode)
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
        releaseYear: String? = null,
        isAnime: Boolean = false
    ): Map<StreamingServer, ServerState> = coroutineScope {
        val effectiveIsAnime = isAnime || com.sumanonline.sumanmovies.data.models.isAnimeCandidate(title, null, null, providerId)
        val servers = StreamingServer.getAvailableServers(effectiveIsAnime)
        val deferreds = servers.associateWith { server ->
            async { resolveServer(server, title, season, episode, providerId, releaseYear) }
        }
        deferreds.mapValues { it.value.await() }
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
    suspend fun getLiveTvChannels(): List<MediaItem> = getLiveChannels().map { it.toMediaItem() }

    private var cachedTvChannels: List<TvChannel> = emptyList()
    private var tvChannelsLastFetched: Long = 0L

    suspend fun getLiveChannels(category: String? = null, forceRefresh: Boolean = false): List<TvChannel> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedTvChannels.isNotEmpty() && (now - tvChannelsLastFetched) < 15 * 60 * 1000L) {
            return if (category.isNullOrBlank()) {
                cachedTvChannels
            } else {
                cachedTvChannels.filter { it.groupTitle?.contains(category, ignoreCase = true) == true }
            }
        }

        val remoteChannels = try {
            backendRepo.fetchTvChannels(category)
        } catch (_: Exception) {
            emptyList()
        }

        val channels = if (remoteChannels.isNotEmpty()) {
            remoteChannels
        } else if (cachedTvChannels.isNotEmpty()) {
            cachedTvChannels
        } else {
            getCuratedTvChannels()
        }

        if (channels.isNotEmpty()) {
            cachedTvChannels = channels
            tvChannelsLastFetched = now
            channels.forEach { cacheItem(it.toMediaItem()) }
        }

        return if (category.isNullOrBlank()) {
            channels
        } else {
            channels.filter { it.groupTitle?.contains(category, ignoreCase = true) == true }
        }
    }

    private fun getCuratedTvChannels(): List<TvChannel> = listOf(
        TvChannel("aajtak", "Aaj Tak HD", "https://live-aajtak.akamaized.net/hls/live/2016312/aajtak/aajtakmaster.m3u8", "https://upload.wikimedia.org/wikipedia/commons/2/28/Aaj_Tak_logo.png", "News", "India", "Hindi"),
        TvChannel("indiatoday", "India Today HD", "https://indiatodaylive.akamaized.net/hls/live/2014320/indiatoday/indiatodaymaster.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/India_Today_logo.svg/320px-India_Today_logo.svg.png", "News", "India", "English"),
        TvChannel("ndtv247", "NDTV 24x7", "https://ndtv24x7elemarchana.akamaized.net/hls/live/2003678/ndtv24x7/master.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/NDTV_24x7_logo.svg/320px-NDTV_24x7_logo.svg.png", "News", "India", "English"),
        TvChannel("abpnews", "ABP News HD", "https://abp-i.akamaihd.net/hls/live/765529/abpnews/master.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/ABP_News_Logo.png/320px-ABP_News_Logo.png", "News", "India", "Hindi"),
        TvChannel("ddsports", "DD Sports HD", "https://d360.live.mxtv.in/live/ddsports/live.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5b/DD_Sports_logo_2022.png/320px-DD_Sports_logo_2022.png", "Sports", "India", "Hindi"),
        TvChannel("ddnational", "DD National HD", "https://d360.live.mxtv.in/live/ddnational/live.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/DD_National_logo_2022.png/320px-DD_National_logo_2022.png", "Entertainment", "India", "Hindi"),
        TvChannel("skynews", "Sky News UK", "https://skynews.akamaized.net/hls/live/2026362/skynews/master.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Sky_News_logo_2015.svg/320px-Sky_News_logo_2015.svg.png", "News", "UK", "English"),
        TvChannel("redbulltv", "Red Bull TV HD", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/9/90/Red_Bull_TV_logo.svg/320px-Red_Bull_TV_logo.svg.png", "Sports", "Global", "English"),
        TvChannel("aljazeera", "Al Jazeera English HD", "https://live-hls-web-aje.getaj.net/AJE/01.m3u8", "https://upload.wikimedia.org/wikipedia/en/thumb/f/f2/Aljazeera_eng.svg/320px-Aljazeera_eng.svg.png", "News", "Qatar", "English"),
        TvChannel("france24", "France 24 English HD", "https://f24hls-i.akamaihd.net/hls/live/221193/F24_EN_LO_HLS/master.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/France_24_logo.svg/320px-France_24_logo.svg.png", "News", "France", "English"),
        TvChannel("dwenglish", "DW English HD", "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Deutsche_Welle_symbol_2012.svg/320px-Deutsche_Welle_symbol_2012.svg.png", "Documentary", "Germany", "English"),
        TvChannel("nasatv", "NASA TV HD", "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/NASA_logo.svg/320px-NASA_logo.svg.png", "Documentary", "USA", "English"),
        TvChannel("bloomberg", "Bloomberg Quicktake HD", "https://bloomberg.com/media-manifest/streams/us.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/56/Bloomberg_Television_logo.svg/320px-Bloomberg_Television_logo.svg.png", "News", "USA", "English"),
        TvChannel("euronews", "Euronews HD", "https://euronews-euronews-world-1-eu.plex.wurl.tv/playlist.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/Euronews_2016_logo.svg/320px-Euronews_2016_logo.svg.png", "News", "Europe", "English")
    )

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
        val results = try {
            val backendResults = withTimeoutOrNull(2500L) {
                backendRepo.search(query, "moviebox")
            }
            if (!backendResults.isNullOrEmpty()) {
                backendResults.distinctBy { it.id }
            } else {
                movieBoxClient.search(query, page).distinctBy { it.id }
            }
        } catch (_: Exception) {
            movieBoxClient.search(query, page).distinctBy { it.id }
        }.distinctBy { it.id }
        results.forEach { cacheItem(it) }
        if (page == 1 && results.isNotEmpty()) {
            synchronized(movieBoxSearchCache) {
                movieBoxSearchCache.put(cacheKey, Pair(System.currentTimeMillis(), results))
            }
        }
        return results
    }

    suspend fun getArtworkForTitle(
        title: String,
        year: String? = null,
        mediaType: MediaType? = null
    ): Pair<String?, String?> {
        val cacheKey = "${title.trim().lowercase()}_${year?.take(4) ?: ""}"
        val cached = synchronized(tmdbArtworkCache) { tmdbArtworkCache.get(cacheKey) }
        if (cached != null) return cached

        val fetched = tmdbRepo.fetchFastArtwork(title, year, mediaType)
        if (!fetched.first.isNullOrBlank()) {
            synchronized(tmdbArtworkCache) { tmdbArtworkCache.put(cacheKey, fetched) }
        }
        return fetched
    }

    suspend fun getKMDLCatalog(driveIndex: Int = 1, page: Int = 1): Pair<List<MediaItem>, Int> {
        val res = kmdlClient.getCatalog(driveIndex, page)
        val mapped = res.first.map { item ->
            val cacheKey = "${item.title.trim().lowercase()}_${item.year?.take(4) ?: ""}"
            val artwork = synchronized(tmdbArtworkCache) { tmdbArtworkCache.get(cacheKey) }
            val cached = getCachedItem(item.id)
            if (artwork != null && !artwork.first.isNullOrBlank()) {
                item.copy(posterUrl = artwork.first, backdropUrl = artwork.second ?: item.backdropUrl)
            } else if (cached != null && !cached.posterUrl.isNullOrBlank()) {
                item.copy(posterUrl = cached.posterUrl, backdropUrl = cached.backdropUrl ?: item.backdropUrl)
            } else {
                item
            }
        }
        mapped.forEach { cacheItem(it) }
        return Pair(mapped, res.second)
    }

    suspend fun searchKMDL(query: String, driveIndex: Int? = null): List<MediaItem> {
        val results = kmdlClient.search(query, driveIndex)
        val mapped = results.map { item ->
            val cacheKey = "${item.title.trim().lowercase()}_${item.year?.take(4) ?: ""}"
            val artwork = synchronized(tmdbArtworkCache) { tmdbArtworkCache.get(cacheKey) }
            val cached = getCachedItem(item.id)
            if (artwork != null && !artwork.first.isNullOrBlank()) {
                item.copy(posterUrl = artwork.first, backdropUrl = artwork.second ?: item.backdropUrl)
            } else if (cached != null && !cached.posterUrl.isNullOrBlank()) {
                item.copy(posterUrl = cached.posterUrl, backdropUrl = cached.backdropUrl ?: item.backdropUrl)
            } else {
                item
            }
        }
        mapped.forEach { cacheItem(it) }
        return mapped
    }

    suspend fun getMoviesLeechCatalog(categoryPath: String? = null, page: Int = 1): Pair<List<MediaItem>, Int> {
        val res = moviesLeechClient.getCatalog(categoryPath, page)
        res.first.forEach { cacheItem(it) }
        return res
    }

    suspend fun getMoviesLeechGenres(): List<MoviesLeechGenre> {
        return moviesLeechClient.getGenres()
    }

    suspend fun searchMoviesLeech(query: String, page: Int = 1): List<MediaItem> {
        val results = moviesLeechClient.search(query, page)
        results.forEach { cacheItem(it) }
        return results
    }

    suspend fun getMoviesModCatalog(categoryPath: String? = null, page: Int = 1): Pair<List<MediaItem>, Int> {
        val res = moviesModClient.getCatalog(categoryPath, page)
        res.first.forEach { cacheItem(it) }
        return res
    }

    suspend fun searchMoviesMod(query: String, page: Int = 1): List<MediaItem> {
        val results = moviesModClient.search(query, page)
        results.forEach { cacheItem(it) }
        return results
    }

    suspend fun getAnimeXinCatalog(categoryPath: String? = null, page: Int = 1): Pair<List<MediaItem>, Int> {
        val res = animeXinClient.getCatalog(categoryPath, page)
        res.first.forEach { cacheItem(it) }
        return res
    }

    suspend fun getAnimeXinGenres(): List<AnimeXinGenre> {
        return animeXinClient.getGenres()
    }

    suspend fun searchAnimeXin(query: String, page: Int = 1): List<MediaItem> {
        val results = animeXinClient.search(query, page)
        results.forEach { cacheItem(it) }
        return results
    }

    private val reAnimeDetailsCache = java.util.concurrent.ConcurrentHashMap<String, ReAnimeAnimeDetails>()

    suspend fun getReAnimeCatalog(category: String? = null, page: Int = 1): Pair<List<MediaItem>, Int> {
        val res = reAnimeClient.getCatalog(category, page)
        res.first.forEach { cacheItem(it) }
        return res
    }

    suspend fun searchReAnime(query: String): List<MediaItem> {
        val results = reAnimeClient.search(query)
        results.forEach { cacheItem(it) }
        return results
    }

    suspend fun getReAnimeAnimeDetails(mediaId: String): ReAnimeAnimeDetails? {
        val cached = reAnimeDetailsCache[mediaId]
        if (cached != null) return cached
        val fetched = reAnimeClient.fetchAnimeDetails(mediaId)
        if (fetched != null) {
            reAnimeDetailsCache[mediaId] = fetched
            if (fetched.anilistId != null) {
                reAnimeDetailsCache["reanime:${fetched.anilistId}"] = fetched
            }
            if (fetched.slug.isNotBlank()) {
                reAnimeDetailsCache["reanime:${fetched.slug}"] = fetched
            }
        }
        return fetched
    }

    suspend fun resolveReAnimePlayback(
        anilistId: Int?,
        slug: String?,
        episodeNumber: Int
    ): ReAnimePlaybackInfo {
        return reAnimeClient.resolveEpisodePlayback(anilistId, slug, episodeNumber)
    }

    suspend fun resolveReAnimeDirectStreams(server: ReAnimeServerOption): List<StreamSource> {
        return reAnimeClient.resolveDirectStreamsForServer(server)
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): List<Episode> {
        return tmdbRepo.getSeasonEpisodes(tvId, seasonNumber)
    }

    suspend fun getSeasonEpisodesForMedia(mediaId: String, seasonNumber: Int): List<Episode> {
        if (mediaId.startsWith("moviebox:")) {
            val cleanId = mediaId.removePrefix("moviebox:")
            val seasons = movieBoxClient.getSeasonInfo(cleanId)
            val match = seasons.firstOrNull { it.seasonNumber == seasonNumber }
            if (match != null && match.episodes.isNotEmpty()) {
                return match.episodes
            }
        }

        val cleanId = mediaId.removePrefix("play4u:")
            .removePrefix("moviebox:")
            .removePrefix("kmdl:")
            .removePrefix("moviesmod:")
            .removePrefix("moviesleech:")
            .removePrefix("animexin:")
            .removePrefix("fourkhdhub:")
            .removePrefix("hdhub4u:")
            .trim()

        val cachedItem = itemCache[mediaId] ?: itemCache[cleanId]
        var resolvedTmdbId: Int? = mediaId.toIntOrNull()
            ?: cleanId.toIntOrNull()
            ?: cachedItem?.tmdbId

        if (resolvedTmdbId == null && (cleanId.startsWith("tt") || mediaId.startsWith("tt"))) {
            val imdb = if (cleanId.startsWith("tt")) cleanId else mediaId
            resolvedTmdbId = try {
                tmdbRepo.findTmdbIdByImdbId(imdb, MediaType.SERIES)
            } catch (_: Exception) {
                null
            }
        }

        if (resolvedTmdbId == null) {
            val candidateTitle = cachedItem?.title ?: cleanId
            val cleanTitle = candidateTitle
                .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
                .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
                .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
                .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*\(\d{4}\)"""), "")
                .trim()
            if (cleanTitle.isNotBlank()) {
                val searchResults = try {
                    tmdbRepo.search(cleanTitle)
                } catch (_: Exception) {
                    emptyList()
                }
                resolvedTmdbId = searchResults.firstOrNull { it.mediaType == MediaType.SERIES }?.tmdbId
                    ?: searchResults.firstOrNull { it.mediaType == MediaType.SERIES }?.id?.toIntOrNull()
                    ?: searchResults.firstOrNull()?.tmdbId
            }
        }

        return if (resolvedTmdbId != null) {
            getSeasonEpisodes(resolvedTmdbId, seasonNumber)
        } else {
            emptyList()
        }
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

        if (id.startsWith("reanime:")) {
            val reDetails = reAnimeClient.getDetails(id)
            if (reDetails != null) {
                return reDetails
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

    suspend fun getSkipSegments(
        mediaId: String,
        title: String,
        type: MediaType,
        year: String? = null,
        seasonNumber: Int = 1,
        episodeNumber: Int = 1
    ): List<MediaSegment> {
        val cleanId = mediaId.removePrefix("play4u:")
            .removePrefix("moviebox:")
            .removePrefix("kmdl:")
            .removePrefix("moviesmod:")
            .removePrefix("moviesleech:")
            .removePrefix("animexin:")
            .removePrefix("fourkhdhub:")
            .removePrefix("hdhub4u:")
            .trim()

        val cachedItem = itemCache[mediaId] ?: itemCache[cleanId]
        var resolvedTmdbId: Int? = mediaId.toIntOrNull()
            ?: cleanId.toIntOrNull()
            ?: cachedItem?.tmdbId

        var resolvedImdbId: String? = if (cleanId.startsWith("tt", ignoreCase = true)) {
            cleanId
        } else if (mediaId.startsWith("tt", ignoreCase = true)) {
            mediaId
        } else null

        if (resolvedTmdbId == null && resolvedImdbId == null) {
            val cleanTitle = title
                .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
                .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
                .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
                .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*\(\d{4}\)"""), "")
                .trim()

            if (cleanTitle.isNotBlank()) {
                val searchResults = try {
                    tmdbRepo.search(cleanTitle)
                } catch (_: Exception) {
                    emptyList()
                }
                val matched = if (!year.isNullOrBlank()) {
                    searchResults.firstOrNull { it.year == year && it.mediaType == type }
                        ?: searchResults.firstOrNull { it.year == year }
                        ?: searchResults.firstOrNull { it.mediaType == type }
                        ?: searchResults.firstOrNull()
                } else {
                    searchResults.firstOrNull { it.mediaType == type }
                        ?: searchResults.firstOrNull()
                }
                resolvedTmdbId = matched?.tmdbId ?: matched?.id?.toIntOrNull()
            }
        }

        if (resolvedTmdbId == null && resolvedImdbId != null) {
            resolvedTmdbId = try {
                tmdbRepo.findTmdbIdByImdbId(resolvedImdbId, type)
            } catch (_: Exception) {
                null
            }
        }

        val isSeries = type == MediaType.SERIES || type == MediaType.ANIME || type == MediaType.DRAMA
        return theIntroDbClient.getSegments(
            tmdbId = resolvedTmdbId,
            imdbId = resolvedImdbId,
            season = if (isSeries) seasonNumber else null,
            episode = if (isSeries) episodeNumber else null,
            isSeries = isSeries
        )
    }
}
