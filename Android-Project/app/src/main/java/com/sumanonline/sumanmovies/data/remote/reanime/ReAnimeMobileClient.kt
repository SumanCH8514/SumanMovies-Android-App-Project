package com.sumanonline.sumanmovies.data.remote.reanime

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ReAnimeMobileClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val redirectClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val gson = Gson()
    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val mirrors = listOf("https://reanime.wtf", "https://reindex.to", "https://reanime.cz")

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val decomposed = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
        val stripped = Regex("\\p{InCombiningDiacriticalMarks}+").replace(decomposed, "")
        val trimmed = stripped.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val anilistResults = searchAniList(trimmed)
        if (anilistResults.isNotEmpty()) {
            return@withContext anilistResults
        }

        searchReAnimeMirrors(trimmed)
    }

    private fun searchAniList(query: String): List<MediaItem> {
        return try {
            val q = """
                query (${'$'}search: String) {
                  Page (page: 1, perPage: 25) {
                    media (search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                      id
                      idMal
                      title { english romaji userPreferred }
                      coverImage { extraLarge large medium }
                      bannerImage
                      seasonYear
                      description(asHtml: false)
                      genres
                      episodes
                      format
                      averageScore
                    }
                  }
                }
            """.trimIndent()

            val payload = JsonObject().apply {
                addProperty("query", q)
                val vars = JsonObject().apply {
                    addProperty("search", query)
                }
                add("variables", vars)
            }.toString()

            val body = payload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(body)
                .header("User-Agent", mobileUserAgent)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val str = resp.body?.string() ?: return emptyList()
                val root = JsonParser.parseString(str).asJsonObject
                val data = root.getAsJsonObject("data") ?: return emptyList()
                val page = data.getAsJsonObject("Page") ?: return emptyList()
                val mediaArr = page.getAsJsonArray("media") ?: return emptyList()

                val items = mutableListOf<MediaItem>()
                for (elem in mediaArr) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asInt ?: continue
                    val titleObj = obj.getAsJsonObject("title")
                    val title = titleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
                        ?: titleObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
                        ?: titleObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                        ?: "Anime #$id"

                    val coverObj = obj.getAsJsonObject("coverImage")
                    val poster = coverObj?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                        ?: coverObj?.get("large")?.takeIf { !it.isJsonNull }?.asString
                        ?: coverObj?.get("medium")?.takeIf { !it.isJsonNull }?.asString

                    val banner = obj.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
                    val year = obj.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt?.toString()
                    val desc = obj.get("description")?.takeIf { !it.isJsonNull }?.asString
                    val score = obj.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble?.let { (it / 10.0) }
                    val format = obj.get("format")?.takeIf { !it.isJsonNull }?.asString
                    val isMovieItem = format.equals("MOVIE", ignoreCase = true)

                    val genres = mutableListOf<String>()
                    obj.getAsJsonArray("genres")?.forEach { g ->
                        if (!g.isJsonNull && g.isJsonPrimitive) genres.add(g.asString)
                    }

                    items.add(
                        MediaItem(
                            id = "reanime:$id",
                            title = title,
                            mediaType = if (isMovieItem) MediaType.MOVIE else MediaType.SERIES,
                            year = year,
                            posterUrl = poster,
                            backdropUrl = banner,
                            overview = desc,
                            genres = genres,
                            rating = score?.toFloat(),
                            provider = "reanime"
                        )
                    )
                }
                items
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchReAnimeMirrors(query: String): List<MediaItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        for (mirror in mirrors) {
            try {
                val url = "$mirror/api/search?q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", "$mirror/")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val str = resp.body?.string() ?: return@use
                    val arr = JsonParser.parseString(str).takeIf { it.isJsonArray }?.asJsonArray ?: return@use
                    val items = mutableListOf<MediaItem>()
                    for (elem in arr) {
                        if (!elem.isJsonObject) continue
                        val obj = elem.asJsonObject
                        val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("anime_id")?.takeIf { !it.isJsonNull }?.asString ?: continue
                        val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: id
                        val poster = obj.get("cover")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("poster")?.takeIf { !it.isJsonNull }?.asString

                        items.add(
                            MediaItem(
                                id = "reanime:$id",
                                title = title,
                                mediaType = MediaType.SERIES,
                                posterUrl = poster,
                                provider = "reanime"
                            )
                        )
                    }
                    if (items.isNotEmpty()) return items
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    suspend fun resolveAniListId(
        title: String,
        releaseYear: String? = null,
        season: Int = 1,
        isMovie: Boolean = false
    ): Int? = withContext(Dispatchers.IO) {
        val decomposed = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        val stripped = Regex("\\p{InCombiningDiacriticalMarks}+").replace(decomposed, "")
        val clean = stripped
            .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
            .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .trim()

        if (clean.isBlank()) return@withContext null

        if (season > 1 && !isMovie) {
            val seasonVariants = listOf(
                "$clean Season $season",
                "$clean $season",
                "$clean S$season"
            )
            for (v in seasonVariants) {
                val candidateId = queryAniListSingle(v, releaseYear, isSeasonQuery = true, isMovie = false)
                if (candidateId != null) {
                    return@withContext candidateId
                }
            }
        }

        queryAniListSingle(clean, releaseYear, isSeasonQuery = false, isMovie = isMovie)
            ?: queryAniListSingle(clean, null, isSeasonQuery = false, isMovie = isMovie)
    }

    private fun queryAniListSingle(
        query: String,
        releaseYear: String? = null,
        isSeasonQuery: Boolean = false,
        isMovie: Boolean = false
    ): Int? {
        return try {
            val formats = if (isMovie) "[MOVIE]" else "[TV, TV_SHORT, ONA]"
            val q = """
                query (${'$'}search: String) {
                  Media (search: ${'$'}search, type: ANIME, format_in: $formats) {
                    id
                    seasonYear
                    format
                    title { english romaji userPreferred }
                  }
                }
            """.trimIndent()

            val payload = JsonObject().apply {
                addProperty("query", q)
                val vars = JsonObject().apply {
                    addProperty("search", query)
                }
                add("variables", vars)
            }.toString()

            val body = payload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(body)
                .header("User-Agent", mobileUserAgent)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val str = resp.body?.string() ?: return null
                val root = JsonParser.parseString(str).asJsonObject
                val data = root.getAsJsonObject("data") ?: return null
                val media = data.getAsJsonObject("Media") ?: return null
                val format = media.get("format")?.takeIf { !it.isJsonNull }?.asString
                if (!isMovie && format.equals("MOVIE", ignoreCase = true)) {
                    return null
                }
                val targetYear = releaseYear?.toIntOrNull()
                val matchedYear = media.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt
                if (targetYear != null && matchedYear != null && kotlin.math.abs(matchedYear - targetYear) > 4) {
                    return null
                }
                val titleObj = media.getAsJsonObject("title")
                val candTitle = titleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
                    ?: titleObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
                    ?: titleObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                    ?: ""
                val genericWords = setOf("season", "s", "part", "pt", "2nd", "3rd", "4th", "5th", "ii", "iii", "iv", "v", "the", "a", "an", "tv", "cour", "act")
                val qTokens = Regex("""\b[a-z0-9]+\b""").findAll(query.lowercase()).map { it.value }.filter { it !in genericWords && !it.all { ch -> ch.isDigit() } }.toSet()
                val cTokens = Regex("""\b[a-z0-9]+\b""").findAll(candTitle.lowercase()).map { it.value }.filter { it !in genericWords && !it.all { ch -> ch.isDigit() } }.toSet()
                val extraWords = cTokens - qTokens
                if (!isMovie && extraWords.any { it in setOf("movie", "prison", "blood", "movie5", "film", "special") }) {
                    return null
                }
                if (isSeasonQuery && extraWords.isNotEmpty()) {
                    return null
                }
                media.get("id")?.asInt
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveStreams(
        idOrSlug: String,
        season: Int = 1,
        episode: Int = 1,
        title: String? = null,
        releaseYear: String? = null,
        absoluteEpisode: Int? = null,
        isMovie: Boolean = false
    ): Pair<List<StreamSource>, List<SubtitleTrack>> = withContext(Dispatchers.IO) {
        val cleanId = idOrSlug.removePrefix("reanime:").trim()
        val decomposed = java.text.Normalizer.normalize(title ?: "", java.text.Normalizer.Form.NFD)
        val stripped = Regex("\\p{InCombiningDiacriticalMarks}+").replace(decomposed, "")
        val cleanTitle = stripped
            .replace(Regex("""(?i)\s*[-–]?\s*s\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*season\s*\d+.*$"""), "")
            .replace(Regex("""(?i)\s*[-–]?\s*ep\s*\d+.*$"""), "")
            .replace(Regex("""\s*\((?:Season|S)\s*\d+\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(\d{4}\)"""), "")
            .trim()

        var serverItems = emptyList<FlixServerItem>()
        var targetAnilistId: Int? = null

        val baseId = cleanId.toIntOrNull()
            ?: if (!cleanTitle.isNullOrBlank()) resolveAniListId(cleanTitle, releaseYear, 1, isMovie) else null
            ?: if (cleanId.isNotBlank()) resolveAniListId(cleanId, releaseYear, 1, isMovie) else null

        if (baseId != null && absoluteEpisode != null && absoluteEpisode > 0) {
            val absServers = fetchFlixServers(baseId, absoluteEpisode)
            if (absServers.isNotEmpty()) {
                targetAnilistId = baseId
                serverItems = absServers
            }
        }

        if (serverItems.isEmpty() && season > 1 && !cleanTitle.isNullOrBlank() && !isMovie) {
            val seasonalId = resolveAniListId(cleanTitle, releaseYear, season, isMovie = false)
            if (seasonalId != null && seasonalId != baseId) {
                val seasonalServers = fetchFlixServers(seasonalId, maxOf(1, episode))
                if (seasonalServers.isNotEmpty()) {
                    targetAnilistId = seasonalId
                    serverItems = seasonalServers
                }
            }
        }

        if (serverItems.isEmpty() && baseId != null) {
            val candidateEp = maxOf(1, episode)
            val baseServers = fetchFlixServers(baseId, candidateEp)
            if (baseServers.isNotEmpty()) {
                targetAnilistId = baseId
                serverItems = baseServers
            }
        }

        if (serverItems.isEmpty() || targetAnilistId == null) {
            return@withContext Pair(emptyList(), emptyList())
        }

        val streams = mutableListOf<StreamSource>()
        val seenUrls = mutableSetOf<String>()
        val seenAccessKeys = mutableSetOf<String>()

        val dubServers = serverItems.filter { (it.dataType ?: "").equals("dub", ignoreCase = true) }
        val subServers = serverItems.filter { !(it.dataType ?: "").equals("dub", ignoreCase = true) }
        val prioritized = dubServers + subServers

        for (srv in prioritized) {
            val link = srv.dataLink ?: continue
            val accessId = if (link.contains("/e/")) {
                link.substringAfter("/e/").substringBefore("?")
            } else {
                link.substringBefore("?")
            }
            if (accessId.isBlank()) continue

            val isDub = (srv.dataType ?: "").equals("dub", ignoreCase = true)
            val accessKey = "$accessId:$isDub"
            if (!seenAccessKeys.add(accessKey)) continue

            val directStreams = resolveFlixDirectMkv(accessId, isDub)
            for (st in directStreams) {
                val dedupeKey = "${st.url}#${st.quality}#${st.audioLangs}"
                if (seenUrls.add(dedupeKey)) {
                    streams.add(st)
                }
            }
        }

        Pair(streams, emptyList())
    }

    private data class FlixServerItem(
        val serverName: String?,
        val dataType: String?,
        val hardsub: Boolean?,
        val dataLink: String?
    )

    private fun fetchFlixServers(anilistId: Int, episode: Int): List<FlixServerItem> {
        for (mirror in mirrors) {
            try {
                val url = "$mirror/api/flix/$anilistId/$episode"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", "$mirror/")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val str = resp.body?.string() ?: return@use
                    val root = JsonParser.parseString(str).takeIf { it.isJsonObject }?.asJsonObject ?: return@use
                    val arr = root.getAsJsonArray("servers") ?: return@use
                    val list = mutableListOf<FlixServerItem>()
                    for (elem in arr) {
                        if (!elem.isJsonObject) continue
                        val obj = elem.asJsonObject
                        list.add(
                            FlixServerItem(
                                serverName = obj.get("serverName")?.takeIf { !it.isJsonNull }?.asString,
                                dataType = obj.get("dataType")?.takeIf { !it.isJsonNull }?.asString,
                                hardsub = obj.get("hardsub")?.takeIf { !it.isJsonNull }?.asBoolean,
                                dataLink = obj.get("dataLink")?.takeIf { !it.isJsonNull }?.asString
                            )
                        )
                    }
                    if (list.isNotEmpty()) return list
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun resolveFlixDirectMkv(accessId: String, isDub: Boolean): List<StreamSource> {
        val metaUrl = "https://flixcloud.cc/d/$accessId/__data.json"
        val request = Request.Builder()
            .url(metaUrl)
            .header("Referer", "https://flixcloud.cc/d/$accessId")
            .header("User-Agent", mobileUserAgent)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val str = resp.body?.string() ?: return emptyList()
                val root = JsonParser.parseString(str).asJsonObject
                val nodes = root.getAsJsonArray("nodes") ?: return emptyList()
                if (nodes.size() < 2) return emptyList()
                val node1 = nodes[1].takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
                val dataArr = node1.getAsJsonArray("data") ?: return emptyList()

                var baseHost: String? = null
                var token: String? = null
                var fileId: String? = null
                var filename: String? = null
                var resolution = "1080p"
                var sizeHuman = ""

                for (elem in dataArr) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        if (obj.has("base") && obj.has("token")) {
                            val baseIdx = obj.get("base").asInt
                            val tokenIdx = obj.get("token").asInt
                            if (baseIdx < dataArr.size() && tokenIdx < dataArr.size()) {
                                baseHost = dataArr[baseIdx].takeIf { it.isJsonPrimitive }?.asString
                                token = dataArr[tokenIdx].takeIf { it.isJsonPrimitive }?.asString
                            }
                        }
                        if (obj.has("fileId") && obj.has("filename")) {
                            val fileIdIdx = obj.get("fileId").asInt
                            val fnIdx = obj.get("filename").asInt
                            if (fileIdIdx < dataArr.size() && fnIdx < dataArr.size()) {
                                fileId = dataArr[fileIdIdx].takeIf { it.isJsonPrimitive }?.asString
                                filename = dataArr[fnIdx].takeIf { it.isJsonPrimitive }?.asString
                            }
                            if (obj.has("resolution")) {
                                val resIdx = obj.get("resolution").asInt
                                if (resIdx < dataArr.size()) {
                                    resolution = dataArr[resIdx].takeIf { it.isJsonPrimitive }?.asString ?: "1080p"
                                }
                            }
                            if (obj.has("sizeHuman")) {
                                val sizeIdx = obj.get("sizeHuman").asInt
                                if (sizeIdx < dataArr.size()) {
                                    sizeHuman = dataArr[sizeIdx].takeIf { it.isJsonPrimitive }?.asString ?: ""
                                }
                            }
                        }
                    }
                }

                if (baseHost == null || token == null || fileId == null) {
                    for (elem in dataArr) {
                        if (!elem.isJsonPrimitive) continue
                        val v = elem.asString
                        if (baseHost == null && v.startsWith("https://") && !v.contains("/thumbnail") && !v.contains("/e/")) {
                            baseHost = v.trimEnd('/')
                        }
                        if (token == null && v.startsWith("eyJ") && v.contains(".")) {
                            token = v
                        }
                        if (fileId == null && v.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                            fileId = v
                        }
                        if (filename == null && (v.endsWith(".mkv", ignoreCase = true) || v.endsWith(".mp4", ignoreCase = true))) {
                            filename = v
                        }
                    }
                }

                if (baseHost.isNullOrBlank() || token.isNullOrBlank() || fileId.isNullOrBlank()) {
                    return emptyList()
                }

                val downloadUrl = "$baseHost/download/$fileId?token=${URLEncoder.encode(token, "UTF-8")}"
                val dlRequest = Request.Builder()
                    .url(downloadUrl)
                    .header("Referer", "https://flixcloud.cc/d/$accessId")
                    .header("User-Agent", mobileUserAgent)
                    .build()

                val directCdnUrl = redirectClient.newCall(dlRequest).execute().use { dlResp ->
                    if (dlResp.code in 300..399) {
                        dlResp.header("Location")
                    } else if (dlResp.code == 200) {
                        downloadUrl
                    } else null
                } ?: client.newCall(dlRequest).execute().use { dlResp ->
                    if (dlResp.isSuccessful) dlResp.request.url.toString() else null
                }

                if (directCdnUrl.isNullOrBlank()) {
                    return emptyList()
                }

                val langTitle = if (isDub) "English Dub" else "Japanese Sub"
                val tag = if (isDub) "DUB" else "SUB"
                val sizeLabel = if (sizeHuman.isNotBlank()) " [$sizeHuman]" else ""
                val isMkv = (filename?.endsWith(".mp4", ignoreCase = true) != true)

                val results = mutableListOf<StreamSource>()
                results.add(
                    StreamSource(
                        quality = resolution,
                        format = if (isMkv) "MKV" else "MP4",
                        url = directCdnUrl,
                        headers = mapOf(
                            "Referer" to "https://flixcloud.cc/",
                            "User-Agent" to mobileUserAgent
                        ),
                        directFile = true,
                        label = "[$tag] $resolution Direct$sizeLabel",
                        provider = "reanime",
                        audioLangs = langTitle,
                        title = filename
                    )
                )

                if (resolution.contains("1080")) {
                    results.add(
                        StreamSource(
                            quality = "720p",
                            format = if (isMkv) "MKV" else "MP4",
                            url = directCdnUrl,
                            headers = mapOf(
                                "Referer" to "https://flixcloud.cc/",
                                "User-Agent" to mobileUserAgent
                            ),
                            directFile = true,
                            label = "[$tag] 720p Optimized$sizeLabel",
                            provider = "reanime",
                            audioLangs = langTitle,
                            title = filename
                        )
                    )
                }

                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
