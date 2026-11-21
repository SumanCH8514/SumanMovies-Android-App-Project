package com.sumanonline.sumanmovies.data.models

data class ReAnimeEpisode(
    val episodeNumber: Int,
    val title: String,
    val thumbnailUrl: String? = null,
    val isFiller: Boolean = false,
    val isSubbed: Boolean = true,
    val isDubbed: Boolean = false,
    val airedDate: String? = null
)

data class ReAnimeSeasonRelation(
    val id: String,
    val anilistId: Int? = null,
    val slug: String? = null,
    val title: String,
    val relationType: String,
    val format: String? = null,
    val year: String? = null,
    val coverUrl: String? = null,
    val episodesCount: Int? = null
)

data class ReAnimeServerOption(
    val serverName: String,
    val dataType: String,
    val embedUrl: String,
    val accessId: String
)

data class ReAnimePlaybackInfo(
    val episodeNumber: Int,
    val servers: List<ReAnimeServerOption> = emptyList(),
    val selectedServer: ReAnimeServerOption? = null,
    val embedUrl: String? = null,
    val directStreams: List<StreamSource> = emptyList(),
    val selectedStream: StreamSource? = null
)

data class ReAnimeAnimeDetails(
    val id: String,
    val anilistId: Int? = null,
    val slug: String = "",
    val titleEnglish: String = "",
    val titleRomaji: String = "",
    val titleNative: String = "",
    val bannerUrl: String? = null,
    val posterUrl: String? = null,
    val format: String = "TV",
    val status: String = "Finished",
    val year: String? = null,
    val season: String? = null,
    val episodesTotal: Int = 1,
    val duration: String? = null,
    val score: Float? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val studios: String? = null,
    val episodes: List<ReAnimeEpisode> = emptyList(),
    val seasons: List<ReAnimeSeasonRelation> = emptyList()
) {
    val displayTitle: String
        get() = titleEnglish.ifBlank {
            titleRomaji.ifBlank {
                titleNative.ifBlank {
                    slug.replace(Regex("""-[a-z0-9]{5,8}$"""), "")
                        .replace("-", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                }
            }
        }

    val displaySubtitle: String
        get() = if (titleRomaji.isNotBlank() && titleRomaji != titleEnglish) titleRomaji else titleNative
}
