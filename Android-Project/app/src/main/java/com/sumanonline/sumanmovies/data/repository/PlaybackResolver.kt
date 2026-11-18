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
import com.sumanonline.sumanmovies.util.MediaMatcher
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
        providerId: String? = null,
        releaseYear: String? = null
    ): ServerState = withContext(Dispatchers.IO) {
        try {
            val (rawStreams, subtitles) = when (server) {
                StreamingServer.MOVIEBOX -> resolveMovieBox(title, season, episode, providerId, releaseYear)
                StreamingServer.FOURKHDHUB -> resolveFourKHdHub(title, season, episode, providerId, releaseYear)
                StreamingServer.HDHUB4U -> resolveHDHub4u(title, season, episode, providerId, releaseYear)
                StreamingServer.PLAY4U -> resolvePlay4U(title, season, episode, providerId, releaseYear)
                StreamingServer.DRAMACHI -> resolveDramachi(title, season, episode, providerId, releaseYear)
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

    private suspend fun resolveMovieBox(
        title: String,
        season: Int,
        episode: Int,
        providerId: String?,
        releaseYear: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        if (providerId?.startsWith("moviebox:") == true) {
            val cleanId = providerId.removePrefix("moviebox:")
            val (directStreams, directSubs) = movieBoxClient.resolveStreams(cleanId, season, episode)
            val backendRes = try {
                backendRepo.getStreams("moviebox", cleanId, season, episode)
            } catch (_: Exception) { Pair(emptyList(), emptyList()) }
            val mergedStreams = (directStreams + backendRes.first).distinctBy { it.url }
            val mergedSubs = (directSubs + backendRes.second).distinctBy { it.url }
            if (mergedStreams.isNotEmpty()) {
                return Pair(mergedStreams, mergedSubs)
            }
            return backendRes
        }

        val queries = MediaMatcher.buildSearchQueries(title)
        val localMatches = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val results = movieBoxClient.search(q)
            if (results.isNotEmpty()) {
                localMatches.addAll(results)
                break
            }
        }

        val topLocal = MediaMatcher.findBestMatch(
            localMatches,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

        if (topLocal != null) {
            val cleanId = topLocal.id.removePrefix("moviebox:")
            val (directStreams, directSubs) = movieBoxClient.resolveStreams(cleanId, season, episode)
            val backendRes = try {
                backendRepo.getStreams("moviebox", cleanId, season, episode)
            } catch (_: Exception) { Pair(emptyList(), emptyList()) }
            val mergedStreams = (directStreams + backendRes.first).distinctBy { it.url }
            val mergedSubs = (directSubs + backendRes.second).distinctBy { it.url }
            if (mergedStreams.isNotEmpty()) {
                return Pair(mergedStreams, mergedSubs)
            }
            if (backendRes.first.isNotEmpty()) {
                return backendRes
            }
        }

        val backendSearch = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val results = backendRepo.search(q, "moviebox")
            if (results.isNotEmpty()) {
                backendSearch.addAll(results)
                break
            }
        }

        val topBackend = MediaMatcher.findBestMatch(
            backendSearch,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

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
        providerId: String?,
        releaseYear: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        if (providerId?.startsWith("fourkhdhub:") == true || providerId?.startsWith("4khdhub:") == true) {
            val cleanId = providerId.substringAfter(":")
            val directStreams = fourKHdHubClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("fourkhdhub", cleanId, season, episode)
        }

        val queries = MediaMatcher.buildSearchQueries(title)
        val localResults = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val queryText = if (season > 0) "$q Season $season" else q
            val results = fourKHdHubClient.search(queryText).ifEmpty { fourKHdHubClient.search(q) }
            if (results.isNotEmpty()) {
                localResults.addAll(results)
                break
            }
        }

        val localMatch = MediaMatcher.findBestMatch(
            localResults,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

        if (localMatch != null) {
            val cleanId = localMatch.id.substringAfter(":")
            val directStreams = fourKHdHubClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
        }

        val backendResults = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val queryText = if (season > 0) "$q Season $season" else q
            val results = backendRepo.search(queryText, "fourkhdhub").ifEmpty { backendRepo.search(q, "fourkhdhub") }
            if (results.isNotEmpty()) {
                backendResults.addAll(results)
                break
            }
        }

        val match = MediaMatcher.findBestMatch(
            backendResults,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

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
        providerId: String?,
        releaseYear: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        if (providerId?.startsWith("hdhub4u:") == true) {
            val cleanId = providerId.substringAfter(":")
            val directStreams = hdHub4uClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("hdhub4u", cleanId, season, episode)
        }

        val queries = MediaMatcher.buildSearchQueries(title)
        val localResults = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val queryText = if (season > 0) "$q Season $season" else q
            val results = hdHub4uClient.search(queryText).ifEmpty { hdHub4uClient.search(q) }
            if (results.isNotEmpty()) {
                localResults.addAll(results)
                break
            }
        }

        val localMatch = MediaMatcher.findBestMatch(
            localResults,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

        if (localMatch != null) {
            val cleanId = localMatch.id.substringAfter(":")
            val directStreams = hdHub4uClient.resolveStreams(cleanId, season, episode)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
        }

        val backendResults = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val queryText = if (season > 0) "$q Season $season" else q
            val results = backendRepo.search(queryText, "hdhub4u").ifEmpty { backendRepo.search(q, "hdhub4u") }
            if (results.isNotEmpty()) {
                backendResults.addAll(results)
                break
            }
        }

        val match = MediaMatcher.findBestMatch(
            backendResults,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

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
        providerId: String?,
        releaseYear: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        if (providerId?.startsWith("play4u:") == true) {
            val cleanId = providerId.removePrefix("play4u:")
            val directStreams = play4UClient.resolveStreams(cleanId, season, episode, title, releaseYear)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
            return backendRepo.getStreams("play4u", cleanId, season, episode)
        }

        val queries = MediaMatcher.buildSearchQueries(title)
        val localResults = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val results = play4UClient.search(q)
            if (results.isNotEmpty()) {
                localResults.addAll(results)
                break
            }
        }

        val localMatch = MediaMatcher.findBestMatch(
            localResults,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

        if (localMatch != null) {
            val cleanId = localMatch.id.removePrefix("play4u:")
            val directStreams = play4UClient.resolveStreams(cleanId, season, episode, title, releaseYear)
            if (directStreams.first.isNotEmpty()) {
                return directStreams
            }
        }

        val backendResults = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val results = backendRepo.search(q, "play4u")
            if (results.isNotEmpty()) {
                backendResults.addAll(results)
                break
            }
        }

        val match = MediaMatcher.findBestMatch(
            backendResults,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

        if (match != null) {
            val cleanId = match.id.removePrefix("play4u:")
            val directStreams = play4UClient.resolveStreams(cleanId, season, episode, title, releaseYear)
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
        providerId: String?,
        releaseYear: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> {
        if (providerId?.startsWith("dramachi:") == true) {
            val cleanId = providerId.removePrefix("dramachi:")
            return backendRepo.getStreams("dramachi", cleanId, season, episode)
        }

        val queries = MediaMatcher.buildSearchQueries(title)
        val backendResults = mutableListOf<com.sumanonline.sumanmovies.data.models.MediaItem>()
        for (q in queries) {
            val results = backendRepo.search(q, "dramachi")
            if (results.isNotEmpty()) {
                backendResults.addAll(results)
                break
            }
        }

        val match = MediaMatcher.findBestMatch(
            backendResults,
            targetTitle = title,
            targetYear = releaseYear,
            season = season,
            isSeries = season > 0
        )

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
