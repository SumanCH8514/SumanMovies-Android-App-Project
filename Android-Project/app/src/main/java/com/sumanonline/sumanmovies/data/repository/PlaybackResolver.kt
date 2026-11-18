package com.sumanonline.sumanmovies.data.repository

import com.sumanonline.sumanmovies.data.models.ServerAvailability
import com.sumanonline.sumanmovies.data.models.ServerState
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import com.sumanonline.sumanmovies.data.remote.backend.BackendRepository
import com.sumanonline.sumanmovies.data.remote.fourkhdhub.FourKHdHubMobileClient
import com.sumanonline.sumanmovies.data.remote.hdhub4u.HDHub4uMobileClient
import com.sumanonline.sumanmovies.data.remote.moviebox.MovieBoxMobileClient
import com.sumanonline.sumanmovies.data.remote.play4u.Play4UMobileClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaybackResolver(
    private val backendRepo: BackendRepository,
    private val movieBoxClient: MovieBoxMobileClient,
    private val fourKHdHubClient: FourKHdHubMobileClient = FourKHdHubMobileClient(),
    private val hdHub4uClient: HDHub4uMobileClient = HDHub4uMobileClient(),
    private val play4UClient: Play4UMobileClient = Play4UMobileClient()
) {
    suspend fun resolveServer(
        server: StreamingServer,
        title: String,
        season: Int = 1,
        episode: Int = 1,
        providerId: String? = null
    ): ServerState = withContext(Dispatchers.IO) {
        try {
            val (rawStreams, subtitles) = when (server) {
                StreamingServer.MOVIEBOX -> resolveMovieBox(title, season, episode, providerId)
                StreamingServer.FOURKHDHUB -> resolveFourKHdHub(title, season, episode, providerId)
                StreamingServer.HDHUB4U -> resolveHDHub4u(title, season, episode, providerId)
                StreamingServer.PLAY4U -> resolvePlay4U(title, season, episode, providerId)
                StreamingServer.DRAMACHI -> resolveDramachi(title, season, episode, providerId)
            }

            val validStreams = rawStreams.filter { validateStreamSource(it) }

            if (validStreams.isNotEmpty()) {
                val best = determineBestQuality(validStreams)
                ServerState(
                    server = server,
                    status = ServerAvailability.AVAILABLE,
                    streams = validStreams,
                    subtitles = subtitles,
                    bestQuality = best
                )
            } else {
                ServerState(
                    server = server,
                    status = ServerAvailability.UNAVAILABLE,
                    streams = emptyList(),
                    subtitles = emptyList(),
                    errorMessage = "No streaming links found on ${server.displayName}"
                )
            }
        } catch (e: Exception) {
            ServerState(
                server = server,
                status = ServerAvailability.ERROR,
                streams = emptyList(),
                subtitles = emptyList(),
                errorMessage = e.message ?: "Connection error to ${server.displayName}"
            )
        }
    }

    private fun cleanSearchQuery(title: String): String {
        return title
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .replace(Regex("""[:\-/].*"""), "")
            .trim()
    }

    private suspend fun resolveMovieBox(
        title: String,
        season: Int,
        episode: Int,
        providerId: String?
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val cleanTitle = cleanSearchQuery(title)

        if (providerId?.startsWith("moviebox:") == true) {
            val cleanId = providerId.removePrefix("moviebox:")
            val (directStreams, directSubs) = movieBoxClient.resolveStreams(cleanId, season, episode)
            if (directStreams.isNotEmpty()) {
                return Pair(directStreams, directSubs)
            }
            return backendRepo.getStreams("moviebox", cleanId, season, episode)
        }

        val localMatches = movieBoxClient.search(cleanTitle).ifEmpty {
            if (cleanTitle != title) movieBoxClient.search(title) else emptyList()
        }
        val topLocal = localMatches.firstOrNull {
            it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)
        } ?: localMatches.firstOrNull()

        if (topLocal != null) {
            val cleanId = topLocal.id.removePrefix("moviebox:")
            val (directStreams, directSubs) = movieBoxClient.resolveStreams(cleanId, season, episode)
            if (directStreams.isNotEmpty()) {
                return Pair(directStreams, directSubs)
            }
            val backendRes = backendRepo.getStreams("moviebox", cleanId, season, episode)
            if (backendRes.first.isNotEmpty()) {
                return backendRes
            }
        }

        val backendSearch = backendRepo.search(cleanTitle, "moviebox").ifEmpty {
            if (cleanTitle != title) backendRepo.search(title, "moviebox") else emptyList()
        }
        val topBackend = backendSearch.firstOrNull {
            it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)
        } ?: backendSearch.firstOrNull()

        if (topBackend != null) {
            val cleanId = topBackend.id.removePrefix("moviebox:")
            return backendRepo.getStreams("moviebox", cleanId, season, episode)
        }

        return Pair(emptyList(), emptyList())
    }

    private suspend fun resolveFourKHdHub(
        title: String,
        season: Int,
        episode: Int,
        providerId: String?
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val cleanTitle = cleanSearchQuery(title)

        if (providerId?.startsWith("fourkhdhub:") == true || providerId?.startsWith("4khdhub:") == true) {
            val cleanId = providerId.substringAfter(":")
            val directStreams = fourKHdHubClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("fourkhdhub", cleanId, season, episode)
        }

        val localResults = fourKHdHubClient.search(cleanTitle).ifEmpty {
            if (cleanTitle != title) fourKHdHubClient.search(title) else emptyList()
        }
        val seasonRegex = Regex("""\b(?:Season\s*0?|S0?)$season\b""", RegexOption.IGNORE_CASE)
        val localMatch = if (season > 0) {
            localResults.firstOrNull { seasonRegex.containsMatchIn(it.title) }
                ?: localResults.firstOrNull { it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.SERIES && (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)) }
                ?: localResults.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
                ?: localResults.firstOrNull()
        } else {
            localResults.firstOrNull {
                it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.MOVIE && (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true))
            } ?: localResults.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
              ?: localResults.firstOrNull()
        }

        if (localMatch != null) {
            val cleanId = localMatch.id.substringAfter(":")
            val directStreams = fourKHdHubClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
        }

        val results = backendRepo.search(cleanTitle, "fourkhdhub").ifEmpty {
            if (cleanTitle != title) backendRepo.search(title, "fourkhdhub") else emptyList()
        }
        val match = if (season > 0) {
            results.firstOrNull { seasonRegex.containsMatchIn(it.title) }
                ?: results.firstOrNull { it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.SERIES }
                ?: results.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
                ?: results.firstOrNull()
        } else {
            results.firstOrNull {
                it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.MOVIE && (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true))
            } ?: results.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
              ?: results.firstOrNull()
        }

        if (match != null) {
            val cleanId = match.id.substringAfter(":")
            val directStreams = fourKHdHubClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("fourkhdhub", cleanId, season, episode)
        }

        return Pair(emptyList(), emptyList())
    }

    private suspend fun resolveHDHub4u(
        title: String,
        season: Int,
        episode: Int,
        providerId: String?
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val cleanTitle = cleanSearchQuery(title)

        if (providerId?.startsWith("hdhub4u:") == true) {
            val cleanId = providerId.substringAfter(":")
            val directStreams = hdHub4uClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("hdhub4u", cleanId, season, episode)
        }

        val query = if (season > 0) "$cleanTitle Season $season" else cleanTitle
        val localResults = hdHub4uClient.search(query).ifEmpty {
            hdHub4uClient.search(cleanTitle).ifEmpty {
                if (cleanTitle != title) hdHub4uClient.search(title) else emptyList()
            }
        }
        val seasonRegex = Regex("""\b(?:Season\s*0?|S0?)$season\b""", RegexOption.IGNORE_CASE)
        val localMatch = if (season > 0) {
            localResults.firstOrNull { seasonRegex.containsMatchIn(it.title) }
                ?: localResults.firstOrNull { it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.SERIES && (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)) }
                ?: localResults.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
                ?: localResults.firstOrNull()
        } else {
            localResults.firstOrNull {
                it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.MOVIE && (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true))
            } ?: localResults.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
              ?: localResults.firstOrNull()
        }

        if (localMatch != null) {
            val cleanId = localMatch.id.substringAfter(":")
            val directStreams = hdHub4uClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
        }

        val results = backendRepo.search(query, "hdhub4u").ifEmpty {
            backendRepo.search(cleanTitle, "hdhub4u").ifEmpty {
                if (cleanTitle != title) backendRepo.search(title, "hdhub4u") else emptyList()
            }
        }
        val match = if (season > 0) {
            results.firstOrNull { seasonRegex.containsMatchIn(it.title) }
                ?: results.firstOrNull { it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.SERIES }
                ?: results.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
                ?: results.firstOrNull()
        } else {
            results.firstOrNull {
                it.mediaType == com.sumanonline.sumanmovies.data.models.MediaType.MOVIE && (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true))
            } ?: results.firstOrNull { it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true) }
              ?: results.firstOrNull()
        }

        if (match != null) {
            val cleanId = match.id.substringAfter(":")
            val directStreams = hdHub4uClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("hdhub4u", cleanId, season, episode)
        }

        return Pair(emptyList(), emptyList())
    }

    private suspend fun resolvePlay4U(
        title: String,
        season: Int,
        episode: Int,
        providerId: String?
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val cleanTitle = cleanSearchQuery(title)

        if (providerId?.startsWith("play4u:") == true) {
            val cleanId = providerId.removePrefix("play4u:")
            val directStreams = play4UClient.resolveStreams(cleanId, season, episode, cleanTitle)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("play4u", cleanId, season, episode)
        }

        val localResults = play4UClient.search(cleanTitle).ifEmpty {
            if (cleanTitle != title) play4UClient.search(title) else emptyList()
        }
        val localMatch = localResults.firstOrNull {
            it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)
        } ?: localResults.firstOrNull()

        if (localMatch != null) {
            val cleanId = localMatch.id.removePrefix("play4u:")
            val directStreams = play4UClient.resolveStreams(cleanId, season, episode, cleanTitle)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
        }

        val directFallback = play4UClient.resolveStreams(cleanTitle, season, episode, cleanTitle)
        if (directFallback.first.isNotEmpty()) {
            return directFallback
        }

        val results = backendRepo.search(cleanTitle, "play4u").ifEmpty {
            if (cleanTitle != title) backendRepo.search(title, "play4u") else emptyList()
        }
        val match = results.firstOrNull {
            it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)
        } ?: results.firstOrNull()

        if (match != null) {
            val cleanId = match.id.removePrefix("play4u:")
            val directStreams = play4UClient.resolveStreams(cleanId, season, episode, cleanTitle)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("play4u", cleanId, season, episode)
        }

        return Pair(emptyList(), emptyList())
    }

    private suspend fun resolveDramachi(
        title: String,
        season: Int,
        episode: Int,
        providerId: String?
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val cleanTitle = cleanSearchQuery(title)

        if (providerId?.startsWith("dramachi:") == true) {
            val cleanId = providerId.removePrefix("dramachi:")
            return backendRepo.getStreams("dramachi", cleanId, season, episode)
        }

        val results = backendRepo.search(cleanTitle, "dramachi").ifEmpty {
            if (cleanTitle != title) backendRepo.search(title, "dramachi") else emptyList()
        }
        val match = results.firstOrNull {
            it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)
        } ?: results.firstOrNull()

        if (match != null) {
            val cleanId = match.id.removePrefix("dramachi:")
            return backendRepo.getStreams("dramachi", cleanId, season, episode)
        }

        return Pair(emptyList(), emptyList())
    }

    private fun validateStreamSource(stream: StreamSource): Boolean {
        if (stream.url.isBlank()) return false
        val lower = stream.url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return true
    }

    fun isLockerUrl(url: String): Boolean {
        val lower = url.lowercase().trim()
        if (lower.isBlank()) return false
        return lower.contains("greenmotors") ||
                lower.contains("greenmountmotors") ||
                lower.contains("hubcdn") ||
                lower.contains("hubcloud") ||
                lower.contains("hubdrive") ||
                lower.contains("gamerxyt") ||
                lower.contains("hblinks") ||
                lower.contains("inventoryidea") ||
                lower.contains("hbplay") ||
                lower.contains("gadgetsweb") ||
                lower.contains("hubcloud.php")
    }

    suspend fun resolveLockerUrl(url: String): StreamSource? {
        val cleanUrl = url.trim()
        val fourk = fourKHdHubClient.resolveLockerUrl(cleanUrl)
        if (fourk != null && fourk.directFile && fourk.url.isNotBlank() && fourk.url != cleanUrl && !isLockerUrl(fourk.url)) {
            val sanitized = fourk.url.replace("&amp;", "&")
            return fourk.copy(url = sanitized, isLockerUrl = false)
        }
        val hdhub = hdHub4uClient.resolveLockerUrl(cleanUrl)
        if (hdhub != null && hdhub.directFile && hdhub.url.isNotBlank() && hdhub.url != cleanUrl && !isLockerUrl(hdhub.url)) {
            val sanitized = hdhub.url.replace("&amp;", "&")
            return hdhub.copy(url = sanitized, isLockerUrl = false)
        }
        if (fourk != null && !isLockerUrl(fourk.url) && fourk.url.isNotBlank() && (fourk.url.startsWith("http://") || fourk.url.startsWith("https://"))) {
            return fourk.copy(url = fourk.url.replace("&amp;", "&"), isLockerUrl = false)
        }
        if (hdhub != null && !isLockerUrl(hdhub.url) && hdhub.url.isNotBlank() && (hdhub.url.startsWith("http://") || hdhub.url.startsWith("https://"))) {
            return hdhub.copy(url = hdhub.url.replace("&amp;", "&"), isLockerUrl = false)
        }
        return null
    }

    private fun determineBestQuality(streams: List<StreamSource>): String {
        for (q in listOf("4K", "2160p", "1080p", "720p", "480p")) {
            if (streams.any { it.quality.contains(q, ignoreCase = true) }) {
                return q
            }
        }
        return streams.firstOrNull()?.quality ?: "HD"
    }
}
