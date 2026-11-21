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
    DRAMACHI("dramachi", "Dramachi", "Dramas & Series"),
    REANIME("reanime", "ReAnime", "Anime HD"),
    KMDL("kmdl", "KMDL", "Cloud Drive"),
    MOVIESLEECH("moviesleech", "MoviesLeech", "1080p / 4K"),
    MOVIESMOD("moviesmod", "MoviesMod", "Dual Audio");

    companion object {
        fun fromId(id: String?): StreamingServer {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MOVIEBOX
        }

        fun getAvailableServers(isAnime: Boolean = false, hasReAnime: Boolean = false): List<StreamingServer> {
            return if (isAnime || hasReAnime) entries else entries.filter { it != REANIME }
        }
    }
}

fun isAnimeCandidate(
    title: String,
    mediaType: MediaType? = null,
    genres: List<String>? = null,
    id: String? = null
): Boolean {
    if (mediaType == MediaType.ANIME) return true
    if (id?.startsWith("reanime:", ignoreCase = true) == true) return true
    if (genres?.any { it.contains("anime", ignoreCase = true) || it.contains("animation", ignoreCase = true) } == true) return true
    val lower = title.lowercase().trim()
    val commonAnime = listOf(
        "pokemon", "pokémon", "pocket monster", "naruto", "one piece", "bleach", "dragon ball",
        "attack on titan", "shingeki", "demon slayer", "kimetsu", "jujutsu", "my hero academia",
        "boku no hero", "solo leveling", "hunter x hunter", "death note", "fullmetal alchemist",
        "chainsaw man", "tokyo ghoul", "sword art online", "black clover", "haikyuu", "dr. stone",
        "vinland saga", "fairy tail", "gintama", "boruto", "spy x family", "overlord", "fate/",
        "mob psycho", "code geass", "steins;gate", "neon genesis evangelion", "death parade",
        "blue lock", "kaiju no. 8", "frieren", "dandadan", "mashle", "wind breaker", "hell's paradise",
        "classroom of the elite", "tokyo revengers", "shield hero", "re:zero", "konosuba"
    )
    return commonAnime.any { lower.contains(it) }
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

data class MoviesLeechGenre(
    val name: String,
    val path: String
)
