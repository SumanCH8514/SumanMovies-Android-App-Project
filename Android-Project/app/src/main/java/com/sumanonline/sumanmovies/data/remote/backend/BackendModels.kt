package com.sumanonline.sumanmovies.data.remote.backend

import com.google.gson.annotations.SerializedName

data class BackendSearchResponse(
    val provider: String,
    val count: Int,
    val data: List<BackendCatalogDto>
)

data class BackendCatalogDto(
    val id: String,
    val provider: String,
    val title: String,
    val mediaType: String,
    val year: String?,
    val posterUrl: String?
)

data class BackendMediaDetailsResponse(
    val data: BackendMediaDetailsDto?
)

data class BackendMediaDetailsDto(
    val id: String,
    val provider: String,
    val title: String,
    val mediaType: String,
    val year: String?,
    val description: String?,
    val posterUrl: String?,
    val genres: List<String>?,
    val seasons: List<BackendSeasonDto>?
)

data class BackendSeasonDto(
    val seasonNumber: Int,
    val episodeCount: Int
)

data class BackendStreamsResponse(
    val data: BackendStreamsDataDto?
)

data class BackendStreamsDataDto(
    val provider: String,
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val streams: List<BackendReleaseStreamDto>?,
    val subtitles: List<BackendSubtitleDto>?
)

data class BackendReleaseStreamDto(
    val quality: String,
    val format: String,
    val url: String,
    val headers: Map<String, String>?,
    val sizeBytes: Long?,
    val directFile: Boolean,
    val codec: String?,
    val label: String
)

data class BackendSubtitleDto(
    val id: String,
    val language: String,
    val label: String,
    val url: String,
    val format: String
)

data class BackendTvResponse(
    val count: Int,
    val data: List<BackendTvChannelDto>
)

data class BackendTvChannelDto(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?
)

data class BackendSignRequest(
    val method: String = "GET",
    val url: String,
    val body: String? = null,
    val token: String? = null
)

data class BackendSignResponse(
    val headers: Map<String, String>
)
