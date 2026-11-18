package com.sumanonline.sumanmovies.data.models

enum class StreamingServer(
    val id: String,
    val displayName: String,
    val tag: String
) {
    PLAY4U("play4u", "Play4U", "1080p HLS"),
    MOVIEBOX("moviebox", "MovieBox", "Default"),
    HDHUB4U("hdhub4u", "HDHub4u", "1080p / 4K"),
    FOURKHDHUB("fourkhdhub", "4KHDHub", "4K / 1080p"),
    DRAMACHI("dramachi", "Dramachi", "Dramas & Series");

    companion object {
        fun fromId(id: String?): StreamingServer {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MOVIEBOX
        }
    }
}

enum class ServerAvailability {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    ERROR
}

data class ServerState(
    val server: StreamingServer,
    val status: ServerAvailability = ServerAvailability.CHECKING,
    val streams: List<StreamSource> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val bestQuality: String? = null,
    val errorMessage: String? = null
)
