package com.sumanonline.sumanmovies.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/all/day")
    suspend fun getTrending(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("trending/movie/day")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("trending/tv/day")
    suspend fun getTrendingTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("tv/airing_today")
    suspend fun getAiringTodayTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("page") page: Int = 1,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") languageFilter: String? = null,
        @Query("with_origin_country") originCountry: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("page") page: Int = 1,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") languageFilter: String? = null,
        @Query("with_origin_country") originCountry: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbPagedResponse<TmdbMovieDto>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") append: String = "credits,similar,videos,recommendations,images",
        @Query("include_image_language") imageLanguages: String = "en,null",
        @Query("language") language: String = "en-US"
    ): TmdbMovieDetailsDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("append_to_response") append: String = "credits,similar,videos,recommendations,images",
        @Query("include_image_language") imageLanguages: String = "en,null",
        @Query("language") language: String = "en-US"
    ): TmdbMovieDetailsDto

    @GET("movie/{movie_id}/images")
    suspend fun getMovieImages(
        @Path("movie_id") movieId: Int,
        @Query("include_image_language") imageLanguages: String = "en,null"
    ): TmdbImagesDto

    @GET("tv/{tv_id}/images")
    suspend fun getTvImages(
        @Path("tv_id") tvId: Int,
        @Query("include_image_language") imageLanguages: String = "en,null"
    ): TmdbImagesDto

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String = "en-US"
    ): TmdbSeasonDto

    @GET("genre/movie/list")
    suspend fun getMovieGenres(
        @Query("language") language: String = "en-US"
    ): TmdbGenresResponse

    @GET("genre/tv/list")
    suspend fun getTvGenres(
        @Query("language") language: String = "en-US"
    ): TmdbGenresResponse

    @GET("find/{external_id}")
    suspend fun findByExternalId(
        @Path("external_id") externalId: String,
        @Query("external_source") externalSource: String = "imdb_id"
    ): TmdbFindResponse
}
