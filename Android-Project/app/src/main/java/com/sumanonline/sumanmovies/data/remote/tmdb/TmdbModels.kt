package com.sumanonline.sumanmovies.data.remote.tmdb

import com.google.gson.annotations.SerializedName

data class TmdbPagedResponse<T>(
    val page: Int,
    val results: List<T>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

data class TmdbMovieDto(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("original_title") val originalTitle: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?,
    @SerializedName("vote_count") val voteCount: Int?,
    @SerializedName("genre_ids") val genreIds: List<Int>?
)

data class TmdbGenreDto(
    val id: Int,
    val name: String
)

data class TmdbGenresResponse(
    val genres: List<TmdbGenreDto>
)

data class TmdbCompanyDto(
    val id: Int,
    val name: String
)

data class TmdbVideoDto(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String
)

data class TmdbVideosDto(
    val results: List<TmdbVideoDto>?
)

data class TmdbLogoDto(
    @SerializedName("file_path") val filePath: String?,
    @SerializedName("iso_639_1") val iso6391: String? = null,
    @SerializedName("aspect_ratio") val aspectRatio: Float? = null,
    @SerializedName("vote_average") val voteAverage: Float? = null,
    @SerializedName("vote_count") val voteCount: Int? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class TmdbImagesDto(
    val logos: List<TmdbLogoDto>? = null,
    val backdrops: List<TmdbLogoDto>? = null,
    val posters: List<TmdbLogoDto>? = null
)

data class TmdbMovieDetailsDto(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    val tagline: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?,
    @SerializedName("vote_count") val voteCount: Int?,
    val runtime: Int?,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    val genres: List<TmdbGenreDto>?,
    val seasons: List<TmdbSeasonDto>?,
    val credits: TmdbCreditsDto?,
    val similar: TmdbPagedResponse<TmdbMovieDto>?,
    val recommendations: TmdbPagedResponse<TmdbMovieDto>?,
    @SerializedName("production_companies") val productionCompanies: List<TmdbCompanyDto>?,
    val videos: TmdbVideosDto?,
    val images: TmdbImagesDto? = null
)

data class TmdbSeasonDto(
    val id: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    val name: String?,
    @SerializedName("episode_count") val episodeCount: Int,
    @SerializedName("poster_path") val posterPath: String?,
    val episodes: List<TmdbEpisodeDto>?
)

data class TmdbEpisodeDto(
    val id: Int,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    @SerializedName("still_path") val stillPath: String?,
    val runtime: Int?,
    @SerializedName("air_date") val airDate: String?
)

data class TmdbCreditsDto(
    val cast: List<TmdbCastDto>?
)

data class TmdbCastDto(
    val id: Int,
    val name: String,
    val character: String?,
    @SerializedName("profile_path") val profilePath: String?
)

data class TmdbFindResponse(
    @SerializedName("movie_results") val movieResults: List<TmdbMovieDto> = emptyList(),
    @SerializedName("tv_results") val tvResults: List<TmdbMovieDto> = emptyList()
)
