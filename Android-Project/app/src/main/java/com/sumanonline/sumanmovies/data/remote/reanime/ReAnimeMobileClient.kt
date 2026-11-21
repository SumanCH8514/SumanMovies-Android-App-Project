package com.sumanonline.sumanmovies.data.remote.reanime

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.Season
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

    suspend fun getCatalog(category: String? = null, page: Int = 1): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = when (category?.lowercase()) {
                "movies", "movie" -> "https://reanime.wtf/search?format=MOVIE"
                "series", "tv" -> "https://reanime.wtf/search?format=TV"
                else -> "https://reanime.wtf/"
            }
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", mobileUserAgent)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Pair(emptyList(), 0)
                val html = resp.body?.string() ?: return@withContext Pair(emptyList(), 0)
                val items = parseAnimeHtml(html)
                Pair(items, items.size)
            }
        } catch (_: Exception) {
            Pair(emptyList(), 0)
        }
    }

    suspend fun getDetails(idOrSlug: String): MediaDetails? = withContext(Dispatchers.IO) {
        try {
            val cleanId = idOrSlug.removePrefix("reanime:").trim()
            val parts = cleanId.split(":")
            val slug = if (parts.size >= 2) parts[1] else cleanId
            val anilistIdFromId = if (parts.size >= 2) parts[0].toIntOrNull() else cleanId.toIntOrNull()

            val url = "https://reanime.wtf/watch/$slug"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", mobileUserAgent)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string() ?: return@withContext null

                val animeIdx = html.indexOf("anime:{")
                if (animeIdx == -1) return@withContext null
                val block = html.substring(animeIdx, minOf(animeIdx + 4000, html.length))

                val engTitle = Regex("""title:\{english:"([^"]*)"""").find(block)?.groupValues?.get(1)
                val romajiTitle = Regex("""romaji:"([^"]*)"""").find(block)?.groupValues?.get(1)
                val nativeTitle = Regex("""native:"([^"]*)"""").find(block)?.groupValues?.get(1)
                val title = if (!engTitle.isNullOrBlank()) engTitle
                    else if (!romajiTitle.isNullOrBlank()) romajiTitle
                    else if (!nativeTitle.isNullOrBlank()) nativeTitle
                    else slug.replace("-", " ")

                val anilistId = Regex("""anilist_id:(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: anilistIdFromId
                val tmdbId = Regex("""themoviedb_id:(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
                val imdbId = Regex("""imdb_id:"([^"]*)"""").find(block)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

                val fmt = Regex("""format:"([^"]*)"""").find(block)?.groupValues?.get(1) ?: "TV"
                val isMovie = fmt.equals("MOVIE", ignoreCase = true)
                val mediaType = if (isMovie) MediaType.MOVIE else MediaType.SERIES

                val desc = Regex("""description:"([^"]*)"""").find(block)?.groupValues?.get(1)
                    ?.replace("\\n", "\n")?.replace("<br>", "\n")?.replace("\\u003Cbr>", "\n")?.replace(Regex("<[^>]*>"), "")

                val year = Regex("""season_year:(\d{4})""").find(block)?.groupValues?.get(1)
                val epTotal = Regex("""episodes_total:(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: (if (isMovie) 1 else 12)

                val episodes = mutableListOf<Episode>()
                val epIdx = html.indexOf("episodes:[")
                if (epIdx != -1) {
                    val epEnd = html.indexOf("],", epIdx).takeIf { it != -1 } ?: html.length
                    val epBlock = html.substring(epIdx, epEnd)
                    val epMatches = Regex("""\{aired:[^}]*?episode_number:(\d+)[^}]*?title:"([^"]*)"""").findAll(epBlock)
                    for (m in epMatches) {
                        val num = m.groupValues[1].toIntOrNull() ?: continue
                        val epT = m.groupValues[2].ifBlank { "Episode $num" }
                        episodes.add(
                            Episode(
                                id = "reanime:ep:$num",
                                episodeNumber = num,
                                seasonNumber = 1,
                                title = epT
                            )
                        )
                    }
                }

                if (episodes.isEmpty()) {
                    val count = if (isMovie) 1 else epTotal
                    for (i in 1..count) {
                        episodes.add(
                            Episode(
                                id = "reanime:ep:$i",
                                episodeNumber = i,
                                seasonNumber = 1,
                                title = "Episode $i"
                            )
                        )
                    }
                }

                val coverImg = Regex("""extra_large:"([^"]*)"""").find(block)?.groupValues?.get(1)
                    ?: Regex("""large:"([^"]*)"""").find(block)?.groupValues?.get(1)

                val seasons = listOf(
                    Season(
                        seasonNumber = 1,
                        name = "Season 1",
                        episodeCount = episodes.size
                    )
                )

                MediaDetails(
                    id = idOrSlug,
                    title = title,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    year = year,
                    releaseDate = year,
                    overview = desc,
                    posterUrl = coverImg,
                    backdropUrl = null,
                    totalSeasons = 1,
                    seasons = seasons,
                    genres = listOf(if (isMovie) "Anime Movie" else "Anime Series"),
                    provider = "reanime"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAnimeHtml(html: String): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        val parts = html.split("{anime_id:\"")
        for (i in 1 until parts.size) {
            val p = parts[i]
            val endIdx = p.indexOf("},{anime_id:\"").takeIf { it != -1 } ?: p.indexOf("}]").takeIf { it != -1 } ?: p.length
            val block = p.substring(0, endIdx)

            val slug = block.substringBefore("\"")
            if (slug.isBlank()) continue

            val anilistId = Regex("""anilist_id:(\d+)""").find(block)?.groupValues?.get(1) ?: ""

            val engTitle = Regex("""english:"([^"]*)"""").find(block)?.groupValues?.get(1)
            val romajiTitle = Regex("""romaji:"([^"]*)"""").find(block)?.groupValues?.get(1)
            val nativeTitle = Regex("""native:"([^"]*)"""").find(block)?.groupValues?.get(1)
            val title = if (!engTitle.isNullOrBlank()) engTitle
                else if (!romajiTitle.isNullOrBlank()) romajiTitle
                else if (!nativeTitle.isNullOrBlank()) nativeTitle
                else slug.replace("-", " ")

            val img = Regex("""extra_large:"([^"]*)"""").find(block)?.groupValues?.get(1)
                ?: Regex("""large:"([^"]*)"""").find(block)?.groupValues?.get(1)
                ?: Regex("""medium:"([^"]*)"""").find(block)?.groupValues?.get(1)

            val fmt = Regex("""format:"([^"]*)"""").find(block)?.groupValues?.get(1) ?: "TV"
            val isMovie = fmt.equals("MOVIE", ignoreCase = true)
            val mediaType = if (isMovie) MediaType.MOVIE else MediaType.SERIES

            val year = Regex("""season_year:(\d{4})""").find(block)?.groupValues?.get(1)
            val scoreStr = Regex("""average_score:(\d+)""").find(block)?.groupValues?.get(1)
            val rating = scoreStr?.toFloatOrNull()?.let { it / 10f }

            val genresBlock = Regex("""genres:\[(.*?)\]""").find(block)?.groupValues?.get(1)
            val genres = if (!genresBlock.isNullOrBlank()) {
                genresBlock.split(",").map { it.trim().trim('"') }.filter { it.isNotBlank() }
            } else {
                listOf(if (isMovie) "Anime Movie" else "Anime Series")
            }

            val effectiveId = "reanime:$anilistId:$slug"
            results.add(
                MediaItem(
                    id = effectiveId,
                    title = title,
                    mediaType = mediaType,
                    year = year,
                    rating = rating,
                    posterUrl = img,
                    backdropUrl = null,
                    overview = "ReAnime • ${if (isMovie) "Movie" else "Series"}",
                    genres = genres,
                    provider = "reanime"
                )
            )
        }
        return results.distinctBy { it.id }
    }

    private fun searchReAnimeWebsite(query: String): List<MediaItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://reanime.wtf/search?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", mobileUserAgent)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val html = resp.body?.string() ?: return emptyList()
                parseAnimeHtml(html)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val decomposed = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
        val stripped = Regex("\\p{InCombiningDiacriticalMarks}+").replace(decomposed, "")
        val trimmed = stripped.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val webResults = searchReAnimeWebsite(trimmed)
        if (webResults.isNotEmpty()) {
            return@withContext webResults
        }

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
        val idParts = cleanId.split(":")
        val explicitAniId = if (idParts.size >= 2) idParts[0].toIntOrNull() else cleanId.toIntOrNull()
        val targetSlug = if (idParts.size >= 2) idParts[1] else cleanId
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

        val baseId = explicitAniId
            ?: if (!cleanTitle.isNullOrBlank()) resolveAniListId(cleanTitle, releaseYear, 1, isMovie) else null
            ?: if (targetSlug.isNotBlank()) resolveAniListId(targetSlug, releaseYear, 1, isMovie) else null

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
