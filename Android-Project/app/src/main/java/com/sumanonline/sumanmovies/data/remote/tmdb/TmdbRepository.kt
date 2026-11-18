package com.sumanonline.sumanmovies.data.remote.tmdb

import com.sumanonline.sumanmovies.data.models.CastMember
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
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

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(apiBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(TmdbApi::class.java)

    fun updateApiKey(key: String) {
        this.apiKey = key
    }

    suspend fun getTrending(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            val resp = api.getTrending(page, lang)
            resp.results.map { dtoToMediaItem(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTrendingMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.getTrendingMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getIndianMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverMovies(
                page = page,
                languageFilter = "hi|te|ta|ml|kn|bn",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.getPopularMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getNowPlayingMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.getNowPlayingMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTopRatedMovies(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.getTopRatedMovies(page, lang).results.map { dtoToMediaItem(it, MediaType.MOVIE) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTrendingTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.getTrendingTv(page, lang).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.getPopularTv(page, lang).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getIndianTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "hi|te|ta|ml|kn|bn",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getHollywoodTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "en",
                originCountry = "US",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getKoreanTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "ko",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getChineseTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "zh",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTurkishTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "tr",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAiringTodayTv(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.getAiringTodayTv(page, lang).results.map { dtoToMediaItem(it, MediaType.SERIES) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTrendingAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getNewSeasonAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "first_air_date.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "vote_count.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getJapaneseAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "ja",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getChineseAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                withGenres = "16",
                languageFilter = "zh",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.ANIME) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getKDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "ko",
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getChineseDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "zh",
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getIndianDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "hi|te|ta|ml|kn|bn",
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getTurkishDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "tr",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPakistaniDrama(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                languageFilter = "ur",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularDramas(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.discoverTv(
                page = page,
                withGenres = "18",
                sortBy = "popularity.desc",
                language = lang
            ).results.map { dtoToMediaItem(it, MediaType.DRAMA) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAsianDramas(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return getKDrama(page, lang)
    }

    suspend fun getAnime(page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return getTrendingAnime(page, lang)
    }

    suspend fun search(query: String, page: Int = 1, lang: String = "en-US"): List<MediaItem> {
        return try {
            api.searchMulti(query, page, lang).results
                .filter { it.posterPath != null || it.backdropPath != null }
                .map { dtoToMediaItem(it) }
        } catch (_: Exception) {
            emptyList()
        }
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

    private fun dtoToMediaItem(dto: TmdbMovieDto, forcedType: MediaType? = null): MediaItem {
        val title = dto.title ?: dto.name ?: dto.originalTitle ?: "Untitled"
        val date = dto.releaseDate ?: dto.firstAirDate
        val year = date?.take(4)
        val type = forcedType ?: if (dto.title != null) MediaType.MOVIE else MediaType.SERIES

        return MediaItem(
            id = dto.id.toString(),
            tmdbId = dto.id,
            title = title,
            mediaType = type,
            year = year,
            rating = dto.voteAverage,
            posterUrl = dto.posterPath?.let { "$imageBaseUrl$it" },
            backdropUrl = dto.backdropPath?.let { "$backdropBaseUrl$it" },
            overview = dto.overview
        )
    }
}
