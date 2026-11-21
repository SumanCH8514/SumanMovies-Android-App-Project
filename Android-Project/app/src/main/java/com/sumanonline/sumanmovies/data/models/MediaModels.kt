package com.sumanonline.sumanmovies.data.models

enum class MediaType {
    MOVIE,
    SERIES,
    ANIME,
    DRAMA,
    TV_CHANNEL
}

data class MediaItem(
    val id: String,
    val tmdbId: Int? = null,
    val title: String,
    val mediaType: MediaType = MediaType.MOVIE,
    val year: String? = null,
    val rating: Float? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val provider: String = "tmdb"
)

data class TrailerItem(
    val id: String,
    val title: String,
    val key: String,
    val site: String = "YouTube",
    val thumbnailUrl: String? = null
)

data class MediaDetails(
    val id: String,
    val tmdbId: Int? = null,
    val title: String,
    val logoUrl: String? = null,
    val tagline: String? = null,
    val mediaType: MediaType = MediaType.MOVIE,
    val year: String? = null,
    val releaseDate: String? = null,
    val rating: Float? = null,
    val ratingsCount: Int = 1992,
    val ratingBreakdown: List<Float> = listOf(0.68f, 0.22f, 0.06f, 0.03f, 0.01f),
    val runtimeMinutes: Int? = null,
    val productionCompany: String? = null,
    val totalSeasons: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val trailers: List<TrailerItem> = emptyList(),
    val recommendations: List<MediaItem> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
    val provider: String = "tmdb"
)

data class CastMember(
    val id: Int,
    val name: String,
    val character: String?,
    val profileUrl: String?
)

data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val posterUrl: String? = null,
    val episodes: List<Episode> = emptyList()
)

data class Episode(
    val id: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val title: String,
    val overview: String? = null,
    val stillUrl: String? = null,
    val durationMinutes: Int? = null,
    val airDate: String? = null
)

data class StreamMirror(
    val name: String,
    val url: String,
    val isLocker: Boolean = true,
    val isOnline: Boolean = true
)

data class StreamSource(
    val quality: String,
    val format: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val sizeBytes: Long? = null,
    val directFile: Boolean = true,
    val codec: String? = null,
    val label: String = "",
    val provider: String,
    val audioLangs: String? = null,
    val sizeFormatted: String? = null,
    val title: String? = null,
    val sourceQualityTag: String? = null,
    val mirrorName: String? = null,
    val isLockerUrl: Boolean = false,
    val category: String = "1080p",
    val mirrors: List<StreamMirror> = emptyList(),
    val isStreamable: Boolean = true,
    val isOnline: Boolean = true
) {
    fun canStream(): Boolean {
        if (!isStreamable || !isOnline) return false
        val lowUrl = url.lowercase()
        val lowFmt = format.lowercase()
        if (lowUrl.endsWith(".zip") || lowUrl.contains(".zip?") || lowUrl.contains(".mkv.zip") || lowUrl.contains(".rar") || lowFmt == "zip" || lowFmt == "rar") {
            return false
        }
        return true
    }
}

data class SubtitleTrack(
    val id: String,
    val language: String,
    val label: String,
    val url: String,
    val format: String = "vtt"
)

data class WatchProgress(
    val mediaId: String,
    val title: String,
    val posterUrl: String?,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCompleted: Boolean = false,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)

fun StreamSource.getDisplayQuality(server: StreamingServer? = null): String {
    val isMovieBox = server == StreamingServer.MOVIEBOX || provider.equals("moviebox", ignoreCase = true)
    if (isMovieBox) {
        val q = quality.lowercase()
        val c = category.lowercase()
        val t = (title ?: label).lowercase()
        return when {
            q.contains("1080") || c.contains("1080") || t.contains("1080") || q.contains("fhd") -> "FHD"
            q.contains("720") || c.contains("720") || t.contains("720") || q.contains("hd") -> "HD"
            q.contains("480") || q.contains("360") || c.contains("480") || c.contains("360") || q.contains("sd") -> "SD"
            q.contains("2160") || q.contains("4k") || c.contains("2160") || c.contains("4k") -> "FHD"
            else -> "HD"
        }
    }
    return quality
}

fun StreamSource.getDisplayCategory(server: StreamingServer? = null): String {
    val isMovieBox = server == StreamingServer.MOVIEBOX || provider.equals("moviebox", ignoreCase = true)
    if (isMovieBox) {
        val q = quality.lowercase()
        val c = category.lowercase()
        val t = (title ?: label).lowercase()
        return when {
            q.contains("1080") || c.contains("1080") || t.contains("1080") || q.contains("fhd") -> "FHD"
            q.contains("720") || c.contains("720") || t.contains("720") || q.contains("hd") -> "HD"
            q.contains("480") || q.contains("360") || c.contains("480") || c.contains("360") || q.contains("sd") -> "SD"
            q.contains("2160") || q.contains("4k") || c.contains("2160") || c.contains("4k") -> "FHD"
            else -> "HD"
        }
    }
    return when {
        category.isNotBlank() && category != "1080p" -> category
        quality.contains("4K", ignoreCase = true) || quality.contains("2160", ignoreCase = true) -> "2160p / 4K"
        quality.contains("1080", ignoreCase = true) -> "1080p"
        quality.contains("720", ignoreCase = true) -> "720p"
        quality.contains("480", ignoreCase = true) -> "480p"
        else -> "Other"
    }
}

fun StreamSource.getDisplayTitle(server: StreamingServer? = null): String {
    val raw = title ?: label
    val isMovieBox = server == StreamingServer.MOVIEBOX || provider.equals("moviebox", ignoreCase = true)
    if (!isMovieBox) return raw
    return raw
        .replace("1080p Full HD", "FHD", ignoreCase = true)
        .replace("1080p", "FHD", ignoreCase = true)
        .replace("720p HD", "HD", ignoreCase = true)
        .replace("720p", "HD", ignoreCase = true)
        .replace("480p", "SD", ignoreCase = true)
        .replace("360p", "SD", ignoreCase = true)
}
