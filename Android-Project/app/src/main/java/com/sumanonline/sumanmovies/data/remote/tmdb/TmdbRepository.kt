package com.sumanonline.sumanmovies.data.remote.tmdb

import com.sumanonline.sumanmovies.data.models.CastMember
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
import android.util.LruCache
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class TmdbRepository(
    private var apiKey: String = "",
    baseUrl: String = "https://proxy.tmdb.sumanonline.com/"
) {
    private val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val apiBaseUrl = if (normalizedBaseUrl.endsWith("/3/")) normalizedBaseUrl else "${normalizedBaseUrl}3/"
    private val imageBaseUrl = "${normalizedBaseUrl}t/p/w500"
    private val backdropBaseUrl = "${normalizedBaseUrl}t/p/original"
    private val fastPosterBaseUrl = "${normalizedBaseUrl}t/p/w342"
    private val fastBackdropBaseUrl = "${normalizedBaseUrl}t/p/w780"

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val originalHttpUrl = original.url

        val url = if (apiKey.isNotBlank()) {
            originalHttpUrl.newBuilder()
                .addQueryParameter("api_key", apiKey)
                .build()
        } else {
            originalHttpUrl
        }

        val request = original.newBuilder().url(url).build()
        chain.proceed(request)
    }

    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 20
    }

    private val okHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()

    private val categoryCache = LruCache<String, Pair<Long, List<MediaItem>>>(120)
    private val categoryTtlMs = 15 * 60 * 1000L
    private val searchCache = LruCache<String, Pair<Long, List<MediaItem>>>(60)
    private val searchTtlMs = 5 * 60 * 1000L

    fun clearCache() {
        synchronized(categoryCache) { categoryCache.evictAll() }
        synchronized(searchCache) { searchCache.evictAll() }
    }

    private suspend inline fun fetchCategoryCached(
        key: String,
        page: Int,
        crossinline apiCall: suspend () -> List<MediaItem>
    ): List<MediaItem> {
        if (page == 1) {
            val cached = synchronized(categoryCache) { categoryCache.get(key) }
            if (cached != null && (System.currentTimeMillis() - cached.first) < categoryTtlMs && cached.second.isNotEmpty()) {
                return cached.second
            }
        }
        val list = try {
            apiCall()
        } catch (_: Exception) {
            emptyList()
        }
        if (page == 1 && list.isNotEmpty()) {
            synchronized(categoryCache) { categoryCache.put(key, Pair(System.currentTimeMillis(), list)) }
        }
        return list
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(apiBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(TmdbApi::class.java)

    fun updateApiKey(key: String) {
        this.apiKey = key
    }

    suspend fun getTrending(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTrending_${page}_${lang}", page) {
            val resp = api.getTrending(page, lang)
            resp.results.map { dtoToMediaItem(it) }
        }

    suspend fun getTrendingMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTrendingMovies_${page}_${lang}", page) {
            api.getTrendingMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getIndianMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getIndianMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                languageFilter = "hi|te|ta|ml|kn|bn",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getPopularMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getPopularMovies_${page}_${lang}", page) {
            api.getPopularMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getNowPlayingMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getNowPlayingMovies_${page}_${lang}", page) {
            api.getNowPlayingMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getTopRatedMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTopRatedMovies_${page}_${lang}", page) {
            api.getTopRatedMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getAnimeMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getAnimeMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getActionAdventureMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getActionAdventureMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                withGenres = "28|12",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getSciFiFantasyMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getSciFiFantasyMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                withGenres = "878|14",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getComedyMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getComedyMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                withGenres = "35",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getHorrorThrillerMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getHorrorThrillerMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                withGenres = "27|53",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getRomanceMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getRomanceMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                withGenres = "10749",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getCrimeMysteryMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getCrimeMysteryMovies_${page}_${lang}", page) {
            api.discoverMovies(
                page = page,
                withGenres = "80|9648",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        }

    suspend fun getTrendingTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTrendingTv_${page}_${lang}", page) {
            api.getTrendingTv(page, lang).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getPopularTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getPopularTv_${page}_${lang}", page) {
            api.getPopularTv(page, lang).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getIndianTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getIndianTv_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "hi|te|ta|ml|kn|bn",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getHollywoodTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getHollywoodTv_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "en",
                originCountry = "US",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getKoreanTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getKoreanTv_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "ko",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getChineseTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getChineseTv_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "zh",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getTurkishTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTurkishTv_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "tr",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getAiringTodayTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getAiringTodayTv_${page}_${lang}", page) {
            api.getAiringTodayTv(page, lang).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getTopRatedTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTopRatedTv_${page}_${lang}", page) {
            api.getTopRatedTv(page, lang).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getDramaSeries(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getDramaSeries_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getCrimeSeries(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getCrimeSeries_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "80",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getComedySeries(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getComedySeries_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "35",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getSciFiFantasySeries(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getSciFiFantasySeries_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "10765",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getActionAdventureSeries(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getActionAdventureSeries_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "10759",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getMysterySuspenseSeries(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getMysterySuspenseSeries_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "9648",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        }

    suspend fun getTrendingAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTrendingAnime_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        }

    suspend fun getNewSeasonAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getNewSeasonAnime_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "first_air_date.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        }

    suspend fun getPopularAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getPopularAnime_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "vote_count.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        }

    suspend fun getJapaneseAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getJapaneseAnime_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        }

    suspend fun getChineseAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getChineseAnime_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "zh",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        }

    suspend fun getKDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getKDrama_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "ko",
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        }

    suspend fun getChineseDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getChineseDrama_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "zh",
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        }

    suspend fun getIndianDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getIndianDrama_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "hi|te|ta|ml|kn|bn",
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        }

    suspend fun getTurkishDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getTurkishDrama_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "tr",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        }

    suspend fun getPakistaniDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getPakistaniDrama_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                languageFilter = "ur",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        }

    suspend fun getPopularDramas(page: Int = 1, lang: String = "en-US"): List<MediaItem> =
        fetchCategoryCached("getPopularDramas_${page}_${lang}", page) {
            api.discoverTv(
                page = page,
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        }

    suspend fun getAsianDramas(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return getKDrama(page, lang)
    }

    suspend fun getAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return getTrendingAnime(page, lang)
    }

    suspend fun getMediaByGenre(
        genreNameOrKey: String,
        page: Int = 1,
        lang: String = "en-US"
    ): List<MediaItem> = fetchCategoryCached("getMediaByGenre_${genreNameOrKey.lowercase()}_${page}_${lang}", page) {
        val clean = genreNameOrKey.lowercase().removePrefix("genre_").trim()
        val movieGenreId = when (clean) {
            "action" -> "28"
            "adventure" -> "12"
            "animation" -> "16"
            "comedy" -> "35"
            "crime" -> "80"
            "documentary" -> "99"
            "drama" -> "18"
            "fantasy" -> "14"
            "horror" -> "27"
            "mystery" -> "9648"
            "romance" -> "10749"
            "science_fiction", "science fiction", "scifi", "sci-fi" -> "878"
            "thriller" -> "53"
            "war" -> "10752"
            "western" -> "37"
            "family" -> "10751"
            "history" -> "36"
            "music" -> "10402"
            else -> null
        }
        val tvGenreId = when (clean) {
            "action", "adventure" -> "10759"
            "animation" -> "16"
            "comedy" -> "35"
            "crime" -> "80"
            "documentary" -> "99"
            "drama" -> "18"
            "fantasy" -> "10765"
            "horror" -> "10765|9648"
            "mystery" -> "9648"
            "romance" -> "18|10766"
            "science_fiction", "science fiction", "scifi", "sci-fi" -> "10765"
            "western" -> "37"
            "family" -> "10751"
            "war" -> "10768"
            else -> null
        }

        val movies = if (movieGenreId != null) {
            try {
                api.discoverMovies(page = page, withGenres = movieGenreId, sortBy = "popularity.desc", language = lang)
                    .results.map { dtoToMediaItem(it, MediaType.MOVIE) }
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        val tvShows = if (tvGenreId != null) {
            try {
                api.discoverTv(page = page, withGenres = tvGenreId, sortBy = "popularity.desc", language = lang)
                    .results.map { dtoToMediaItem(it, MediaType.SERIES) }
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        if (movies.isNotEmpty() && tvShows.isNotEmpty()) {
            val combined = mutableListOf<MediaItem>()
            val maxLen = maxOf(movies.size, tvShows.size)
            for (i in 0 until maxLen) {
                if (i < movies.size) combined.add(movies[i])
                if (i < tvShows.size) combined.add(tvShows[i])
            }
            combined.distinctBy { it.id }
        } else {
            movies.ifEmpty { tvShows }
        }
    }

    suspend fun search(query: String, page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        val cleanQuery = query.trim().lowercase(java.util.Locale.ROOT)
        if (cleanQuery.isBlank()) return emptyList()
        val cacheKey = "search_${cleanQuery}_${page}_${lang}"
        if (page == 1) {
            val cached = synchronized(searchCache) { searchCache.get(cacheKey) }
            if (cached != null && (System.currentTimeMillis() - cached.first) < searchTtlMs && cached.second.isNotEmpty()) {
                return cached.second
            }
        }
        val list = try {
            api.searchMulti(query, page, lang).results
                .filter { it.posterPath != null || it.backdropPath != null }
                .map { dtoToMediaItem(it) }
        } catch (_: Exception) {
            emptyList()
        }
        if (page == 1 && list.isNotEmpty()) {
            synchronized(searchCache) { searchCache.put(cacheKey, Pair(System.currentTimeMillis(), list)) }
        }
        return list
    }

    suspend fun fetchFastArtwork(
        title: String,
        year: String? = null,
        mediaType: MediaType? = null
    ): Pair<String?, String?> {
        val clean = title
            .replace(Regex("""\b(1080p|720p|480p|2160p|4k|hdr|web-dl|bluray|hevc|x265|x264|dual audio|hindi|english)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\.[a-zA-Z0-9]{3,4}$"""), "")
            .replace(Regex("""[🌐⭕💬]"""), "")
            .trim()
        if (clean.isBlank()) return Pair(null, null)

        val queries = mutableListOf(clean)
        val noPunct = clean.replace(Regex("""[:\-/]"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (noPunct != clean && noPunct.isNotBlank()) queries.add(noPunct)
        val base = clean.substringBefore(":").substringBefore("-").trim()
        if (base != clean && base != noPunct && base.length >= 3) queries.add(base)

        for (q in queries) {
            try {
                val response = api.searchMulti(q, 1, "en-US")
                val results = response.results.filter { !it.posterPath.isNullOrBlank() }
                if (results.isNotEmpty()) {
                    val targetYear = year?.take(4)
                    val matched = if (!targetYear.isNullOrBlank()) {
                        results.firstOrNull {
                            val y = (it.releaseDate ?: it.firstAirDate)?.take(4)
                            y == targetYear
                        } ?: results.first()
                    } else {
                        results.first()
                    }

                    val poster = matched.posterPath?.let { "$fastPosterBaseUrl$it" }
                    val backdrop = matched.backdropPath?.let { "$fastBackdropBaseUrl$it" }
                    return Pair(poster, backdrop)
                }
            } catch (_: Exception) {}
        }
        return Pair(null, null)
    }

    private fun extractLogoUrl(images: TmdbImagesDto?): String? {
        val logos = images?.logos ?: return null
        if (logos.isEmpty()) return null
        val enLogo = logos.firstOrNull { it.iso6391.equals("en", ignoreCase = true) && !it.filePath.isNullOrBlank() }
        val noLangLogo = logos.firstOrNull { it.iso6391.isNullOrBlank() && !it.filePath.isNullOrBlank() }
        val anyLogo = logos.firstOrNull { !it.filePath.isNullOrBlank() }
        val best = (enLogo ?: noLangLogo ?: anyLogo)?.filePath ?: return null
        return "$imageBaseUrl$best"
    }

    suspend fun getMovieDetails(movieId: Int, lang: String = "en-US"): MediaDetails? {
        return try {
            val dto = api.getMovieDetails(movieId, language = lang)
            var logoUrl = extractLogoUrl(dto.images)
            if (logoUrl == null) {
                try {
                    val imagesDto = api.getMovieImages(movieId)
                    logoUrl = extractLogoUrl(imagesDto)
                } catch (_: Exception) {}
            }
            val cast = dto.credits?.cast?.take(15)?.map {
                CastMember(
                    id = it.id,
                    name = it.name,
                    character = it.character,
                    profileUrl = it.profilePath?.let { p -> "$imageBaseUrl$p" }
                )
            } ?: emptyList()

            val trailers = dto.videos?.results?.filter { it.site.equals("YouTube", ignoreCase = true) }?.map {
                com.sumanonline.sumanmovies.data.models.TrailerItem(
                    id = it.id,
                    title = it.name,
                    key = it.key,
                    site = it.site,
                    thumbnailUrl = "https://img.youtube.com/vi/${it.key}/hqdefault.jpg"
                )
            } ?: emptyList()

            val similar = dto.similar?.results?.take(10)?.map { dtoToMediaItem(it, MediaType.MOVIE) } ?: emptyList()
            val recommendations = dto.recommendations?.results?.take(10)?.map { dtoToMediaItem(it, MediaType.MOVIE) } ?: emptyList()

            MediaDetails(
                id = dto.id.toString(),
                tmdbId = dto.id,
                title = dto.title ?: dto.originalTitle ?: "Unknown",
                logoUrl = logoUrl,
                tagline = dto.tagline,
                mediaType = MediaType.MOVIE,
                year = dto.releaseDate?.take(4),
                releaseDate = dto.releaseDate,
                rating = dto.voteAverage,
                ratingsCount = dto.voteCount ?: 1992,
                runtimeMinutes = dto.runtime,
                productionCompany = dto.productionCompanies?.firstOrNull()?.name ?: "Universal Pictures",
                totalSeasons = null,
                posterUrl = dto.posterPath?.let { "$imageBaseUrl$it" },
                backdropUrl = dto.backdropPath?.let { "$backdropBaseUrl$it" },
                overview = dto.overview,
                genres = dto.genres?.map { it.name } ?: emptyList(),
                cast = cast,
                seasons = emptyList(),
                trailers = trailers,
                recommendations = recommendations,
                similar = similar
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getTvDetails(tvId: Int, lang: String = "en-US"): MediaDetails? {
        return try {
            val dto = api.getTvDetails(tvId, language = lang)
            var logoUrl = extractLogoUrl(dto.images)
            if (logoUrl == null) {
                try {
                    val imagesDto = api.getTvImages(tvId)
                    logoUrl = extractLogoUrl(imagesDto)
                } catch (_: Exception) {}
            }
            val cast = dto.credits?.cast?.take(15)?.map {
                CastMember(
                    id = it.id,
                    name = it.name,
                    character = it.character,
                    profileUrl = it.profilePath?.let { p -> "$imageBaseUrl$p" }
                )
            } ?: emptyList()

            val seasons = dto.seasons?.filter { it.seasonNumber > 0 }?.map { s ->
                Season(
                    seasonNumber = s.seasonNumber,
                    name = s.name ?: "Season ${s.seasonNumber}",
                    episodeCount = s.episodeCount,
                    posterUrl = s.posterPath?.let { "$imageBaseUrl$it" }
                )
            } ?: emptyList()

            val trailers = dto.videos?.results?.filter { it.site.equals("YouTube", ignoreCase = true) }?.map {
                com.sumanonline.sumanmovies.data.models.TrailerItem(
                    id = it.id,
                    title = it.name,
                    key = it.key,
                    site = it.site,
                    thumbnailUrl = "https://img.youtube.com/vi/${it.key}/hqdefault.jpg"
                )
            } ?: emptyList()

            val similar = dto.similar?.results?.take(10)?.map { dtoToMediaItem(it, MediaType.SERIES) } ?: emptyList()
            val recommendations = dto.recommendations?.results?.take(10)?.map { dtoToMediaItem(it, MediaType.SERIES) } ?: emptyList()

            MediaDetails(
                id = dto.id.toString(),
                tmdbId = dto.id,
                title = dto.name ?: dto.title ?: "Unknown",
                logoUrl = logoUrl,
                tagline = dto.tagline,
                mediaType = MediaType.SERIES,
                year = dto.firstAirDate?.take(4),
                releaseDate = dto.firstAirDate,
                rating = dto.voteAverage,
                ratingsCount = dto.voteCount ?: 4090,
                runtimeMinutes = dto.episodeRunTime?.firstOrNull(),
                productionCompany = dto.productionCompanies?.firstOrNull()?.name ?: "Amazon Studios",
                totalSeasons = dto.numberOfSeasons ?: seasons.size,
                posterUrl = dto.posterPath?.let { "$imageBaseUrl$it" },
                backdropUrl = dto.backdropPath?.let { "$backdropBaseUrl$it" },
                overview = dto.overview,
                genres = dto.genres?.map { it.name } ?: emptyList(),
                cast = cast,
                seasons = seasons,
                trailers = trailers,
                recommendations = recommendations,
                similar = similar
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int, lang: String = "en-US"): List<Episode> {
        return try {
            val dto = api.getTvSeason(tvId, seasonNumber, lang)
            dto.episodes?.map { ep ->
                Episode(
                    id = ep.id.toString(),
                    episodeNumber = ep.episodeNumber,
                    seasonNumber = ep.seasonNumber,
                    title = ep.name ?: "Episode ${ep.episodeNumber}",
                    overview = ep.overview,
                    stillUrl = ep.stillPath?.let { "$imageBaseUrl$it" },
                    durationMinutes = ep.runtime,
                    airDate = ep.airDate
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun findTmdbIdByImdbId(imdbId: String, type: MediaType): Int? {
        return try {
            val resp = api.findByExternalId(imdbId, "imdb_id")
            if (type == MediaType.SERIES) {
                resp.tvResults.firstOrNull()?.id ?: resp.movieResults.firstOrNull()?.id
            } else {
                resp.movieResults.firstOrNull()?.id ?: resp.tvResults.firstOrNull()?.id
            }
        } catch (_: Exception) {
            null
        }
    }

    private val tmdbGenreMap = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
        80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
        14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
        10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News",
        10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap",
        10767 to "Talk", 10768 to "War & Politics"
    )

    private fun dtoToMediaItem(dto: TmdbMovieDto, forcedType: MediaType? = null): MediaItem {
        val title = dto.title ?: dto.name ?: dto.originalTitle ?: "Untitled"
        val date = dto.releaseDate ?: dto.firstAirDate
        val year = date?.take(4)
        val type = forcedType ?: if (dto.title != null) MediaType.MOVIE else MediaType.SERIES
        val mappedGenres = dto.genreIds?.mapNotNull { tmdbGenreMap[it] } ?: emptyList()

        return MediaItem(
            id = dto.id.toString(),
            tmdbId = dto.id,
            title = title,
            mediaType = type,
            year = year,
            rating = dto.voteAverage,
            posterUrl = dto.posterPath?.let { "$imageBaseUrl$it" },
            backdropUrl = dto.backdropPath?.let { "$backdropBaseUrl$it" },
            overview = dto.overview,
            genres = mappedGenres
        )
    }
}
