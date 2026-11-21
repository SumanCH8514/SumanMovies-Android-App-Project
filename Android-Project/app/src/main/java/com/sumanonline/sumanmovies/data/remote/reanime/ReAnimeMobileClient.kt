package com.sumanonline.sumanmovies.data.remote.reanime

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sumanonline.sumanmovies.data.models.Episode
import com.sumanonline.sumanmovies.data.models.MediaDetails
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.ReAnimeAnimeDetails
import com.sumanonline.sumanmovies.data.models.ReAnimeEpisode
import com.sumanonline.sumanmovies.data.models.ReAnimePlaybackInfo
import com.sumanonline.sumanmovies.data.models.ReAnimeSeasonRelation
import com.sumanonline.sumanmovies.data.models.ReAnimeServerOption
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

    suspend fun fetchAnimeDetails(idOrSlug: String, explicitAnilistId: Int? = null): ReAnimeAnimeDetails? = withContext(Dispatchers.IO) {
        val cleanId = idOrSlug.removePrefix("reanime:").trim()
        val parts = cleanId.split(":")
        var anilistId = explicitAnilistId ?: (if (parts.size >= 2) parts[0].toIntOrNull() else cleanId.toIntOrNull())
        var slug = if (parts.size >= 2) parts[1] else if (anilistId == null) cleanId else ""

        var watchEpisodes = emptyList<ReAnimeEpisode>()
        if (slug.isNotBlank()) {
            val meta = fetchReAnimeWatchMeta(slug)
            if (anilistId == null && meta.first != null) {
                anilistId = meta.first
            }
            watchEpisodes = meta.second
        }

        val cleanQuerySlug = slug.replace(Regex("""-[a-z0-9]{5,8}$"""), "").replace("-", " ").trim()
        val anilistJson = fetchAniListFullMedia(
            anilistId,
            if (anilistId == null && cleanQuerySlug.isNotBlank()) cleanQuerySlug else null
        )
        val mediaObj = anilistJson?.getAsJsonObject("data")?.getAsJsonObject("Media")

        if (anilistId == null && mediaObj != null) {
            anilistId = mediaObj.get("id")?.takeIf { !it.isJsonNull }?.asInt
        }

        val titleObj = mediaObj?.getAsJsonObject("title")
        val engTitle = titleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val romajiTitle = titleObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val nativeTitle = titleObj?.get("native")?.takeIf { !it.isJsonNull }?.asString ?: ""
        val userPrefTitle = titleObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString ?: ""

        val banner = mediaObj?.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
        val coverObj = mediaObj?.getAsJsonObject("coverImage")
        val poster = coverObj?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
            ?: coverObj?.get("large")?.takeIf { !it.isJsonNull }?.asString
            ?: coverObj?.get("medium")?.takeIf { !it.isJsonNull }?.asString

        val fmt = mediaObj?.get("format")?.takeIf { !it.isJsonNull }?.asString ?: "TV"
        val stat = mediaObj?.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "Finished"
        val yr = mediaObj?.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt?.toString()
        val sSeason = mediaObj?.get("season")?.takeIf { !it.isJsonNull }?.asString
        val epsTotal = mediaObj?.get("episodes")?.takeIf { !it.isJsonNull }?.asInt
            ?: mediaObj?.getAsJsonObject("nextAiringEpisode")?.get("episode")?.takeIf { !it.isJsonNull }?.asInt?.minus(1)
            ?: 1
        val dur = mediaObj?.get("duration")?.takeIf { !it.isJsonNull }?.asInt?.let { "${it}m" }
        val sc = mediaObj?.get("averageScore")?.takeIf { !it.isJsonNull }?.asFloat?.let { it / 10f }
        val desc = mediaObj?.get("description")?.takeIf { !it.isJsonNull }?.asString
            ?.replace("\\n", "\n")?.replace("<br>", "\n")?.replace(Regex("<[^>]*>"), "")?.trim()

        val genresList = mutableListOf<String>()
        mediaObj?.getAsJsonArray("genres")?.forEach { g ->
            if (!g.isJsonNull && g.isJsonPrimitive) genresList.add(g.asString)
        }

        val studioName = mediaObj?.getAsJsonObject("studios")
            ?.getAsJsonArray("nodes")?.firstOrNull()?.asJsonObject
            ?.get("name")?.takeIf { !it.isJsonNull }?.asString

        val streamingMap = mutableMapOf<Int, Pair<String, String>>()
        val streamingArr = mediaObj?.getAsJsonArray("streamingEpisodes")
        if (streamingArr != null) {
            for (elem in streamingArr) {
                if (!elem.isJsonObject) continue
                val sObj = elem.asJsonObject
                val sTitle = sObj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val sThumb = sObj.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val match = Regex("""(?:Episode|Ep\.?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(sTitle)
                val epNum = match?.groupValues?.get(1)?.toIntOrNull()
                if (epNum != null) {
                    val cleanT = sTitle.replace(Regex("""^(?:Episode|Ep\.?)\s*\d+\s*[-:]?\s*""", RegexOption.IGNORE_CASE), "").trim()
                    streamingMap[epNum] = Pair(if (cleanT.isNotBlank()) cleanT else sTitle, sThumb)
                }
            }
        }

        val relationsList = mutableListOf<ReAnimeSeasonRelation>()
        val relEdges = mediaObj?.getAsJsonObject("relations")?.getAsJsonArray("edges")
        if (relEdges != null) {
            for (elem in relEdges) {
                if (!elem.isJsonObject) continue
                val edge = elem.asJsonObject
                val rType = edge.get("relationType")?.takeIf { !it.isJsonNull }?.asString ?: "RELATED"
                val node = edge.getAsJsonObject("node") ?: continue
                val nType = node.get("type")?.takeIf { !it.isJsonNull }?.asString ?: ""
                if (!nType.equals("ANIME", ignoreCase = true)) continue

                val rId = node.get("id")?.asInt ?: continue
                val rTitleObj = node.getAsJsonObject("title")
                val rTitle = rTitleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
                    ?: rTitleObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
                    ?: rTitleObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                    ?: "Anime #$rId"

                val rFormat = node.get("format")?.takeIf { !it.isJsonNull }?.asString
                val rYear = node.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt?.toString()
                val rCover = node.getAsJsonObject("coverImage")?.get("large")?.takeIf { !it.isJsonNull }?.asString
                val rEps = node.get("episodes")?.takeIf { !it.isJsonNull }?.asInt

                relationsList.add(
                    ReAnimeSeasonRelation(
                        id = "reanime:$rId",
                        anilistId = rId,
                        title = rTitle,
                        relationType = rType,
                        format = rFormat,
                        year = rYear,
                        coverUrl = rCover,
                        episodesCount = rEps
                    )
                )
            }
        }

        var reanimeEpisodes: List<ReAnimeEpisode> = watchEpisodes
        if (reanimeEpisodes.isEmpty() && anilistId != null) {
            val queriedSlug = findReAnimeSlugByAnilistId(anilistId)
            if (!queriedSlug.isNullOrBlank()) {
                slug = queriedSlug
                reanimeEpisodes = fetchReAnimeWatchEpisodes(slug)
            }
        }

        val finalEpisodes = mutableListOf<ReAnimeEpisode>()
        if (reanimeEpisodes.isNotEmpty()) {
            for (ep in reanimeEpisodes) {
                val streamInfo = streamingMap[ep.episodeNumber]
                val resolvedTitle = if (ep.title.isNotBlank() && !ep.title.startsWith("Episode ", ignoreCase = true)) {
                    ep.title
                } else {
                    streamInfo?.first ?: ep.title
                }
                val resolvedThumb = streamInfo?.second ?: ep.thumbnailUrl ?: banner ?: poster
                finalEpisodes.add(
                    ep.copy(
                        title = resolvedTitle.ifBlank { "Episode ${ep.episodeNumber}" },
                        thumbnailUrl = resolvedThumb
                    )
                )
            }
        } else {
            val count = maxOf(epsTotal, streamingMap.keys.maxOrNull() ?: 1)
            for (i in 1..count) {
                val streamInfo = streamingMap[i]
                finalEpisodes.add(
                    ReAnimeEpisode(
                        episodeNumber = i,
                        title = streamInfo?.first ?: "Episode $i",
                        thumbnailUrl = streamInfo?.second ?: banner ?: poster,
                        isFiller = false,
                        isSubbed = true,
                        isDubbed = false
                    )
                )
            }
        }

        val fallbackTitle = cleanQuerySlug.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            .ifBlank { "Anime" }
        val effectiveTitleEng = engTitle.ifBlank { userPrefTitle.ifBlank { romajiTitle.ifBlank { fallbackTitle } } }
        val effectiveTitleRom = romajiTitle.ifBlank { userPrefTitle }

        ReAnimeAnimeDetails(
            id = idOrSlug,
            anilistId = anilistId,
            slug = slug,
            titleEnglish = effectiveTitleEng,
            titleRomaji = effectiveTitleRom,
            titleNative = nativeTitle,
            bannerUrl = banner,
            posterUrl = poster,
            format = fmt,
            status = stat,
            year = yr,
            season = sSeason,
            episodesTotal = maxOf(finalEpisodes.size, epsTotal),
            duration = dur,
            score = sc,
            description = desc,
            genres = genresList,
            studios = studioName,
            episodes = finalEpisodes,
            seasons = relationsList
        )
    }

    private fun fetchAniListFullMedia(anilistId: Int?, queryText: String?): JsonObject? {
        val q = """
            query (${'$'}id: Int, ${'$'}search: String) {
              Media (id: ${'$'}id, search: ${'$'}search, type: ANIME) {
                id
                idMal
                title {
                  english
                  romaji
                  native
                  userPreferred
                }
                format
                status
                description(asHtml: false)
                startDate { year month day }
                endDate { year month day }
                season
                seasonYear
                episodes
                duration
                genres
                averageScore
                bannerImage
                coverImage {
                  extraLarge
                  large
                  medium
                  color
                }
                studios(isMain: true) {
                  nodes { name }
                }
                nextAiringEpisode {
                  episode
                  airingAt
                }
                relations {
                  edges {
                    relationType
                    node {
                      id
                      title { english romaji userPreferred }
                      format
                      type
                      status
                      seasonYear
                      coverImage { large }
                      episodes
                    }
                  }
                }
                streamingEpisodes {
                  title
                  thumbnail
                  url
                  site
                }
              }
            }
        """.trimIndent()

        val payload = JsonObject().apply {
            addProperty("query", q)
            val vars = JsonObject()
            if (anilistId != null && anilistId > 0) {
                vars.addProperty("id", anilistId)
            } else if (!queryText.isNullOrBlank()) {
                vars.addProperty("search", queryText.trim())
            }
            add("variables", vars)
        }.toString()

        val body = payload.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body)
            .header("User-Agent", mobileUserAgent)
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val str = resp.body?.string() ?: return null
                JsonParser.parseString(str).asJsonObject
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchReAnimeWatchMeta(slug: String): Pair<Int?, List<ReAnimeEpisode>> {
        val list = mutableListOf<ReAnimeEpisode>()
        var foundAnilistId: Int? = null
        for (mirror in mirrors) {
            try {
                val url = "$mirror/watch/$slug/__data.json"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", "$mirror/")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val str = resp.body?.string() ?: return@use
                    val root = JsonParser.parseString(str).asJsonObject
                    val nodes = root.getAsJsonArray("nodes") ?: return@use
                    for (nodeElem in nodes) {
                        if (!nodeElem.isJsonObject) continue
                        val nodeObj = nodeElem.asJsonObject
                        val dataArr = nodeObj.getAsJsonArray("data") ?: continue
                        if (dataArr.size() == 0) continue

                        if (foundAnilistId == null) {
                            for (elem in dataArr) {
                                if (!elem.isJsonObject) continue
                                val obj = elem.asJsonObject
                                if (obj.has("anilist_id")) {
                                    val anilistIdx = obj.get("anilist_id")?.takeIf { it.isJsonPrimitive }?.asInt
                                    if (anilistIdx != null && anilistIdx in 0 until dataArr.size()) {
                                        val candidate = dataArr.get(anilistIdx).takeIf { it.isJsonPrimitive }?.asInt
                                        if (candidate != null && candidate > 0) {
                                            foundAnilistId = candidate
                                            break
                                        }
                                    }
                                }
                            }
                        }

                        if (list.isEmpty()) {
                            val rootObj = dataArr.get(0).takeIf { it.isJsonObject }?.asJsonObject
                            val epArrayIdx = rootObj?.get("episodes")?.takeIf { it.isJsonPrimitive }?.asInt
                            if (epArrayIdx != null && epArrayIdx in 0 until dataArr.size()) {
                                val epIndices = dataArr.get(epArrayIdx).takeIf { it.isJsonArray }?.asJsonArray
                                if (epIndices != null) {
                                    for (i in 0 until epIndices.size()) {
                                        val idx = epIndices.get(i).asInt
                                        if (idx !in 0 until dataArr.size()) continue
                                        val epObj = dataArr.get(idx).asJsonObject
                                        val epNumIdx = epObj.get("episode_number")?.asInt ?: epObj.get("number")?.asInt
                                        val num = if (epNumIdx != null && epNumIdx in 0 until dataArr.size()) {
                                            dataArr.get(epNumIdx).takeIf { it.isJsonPrimitive }?.asInt ?: (i + 1)
                                        } else (i + 1)

                                        val titleIdx = epObj.get("title")?.asInt
                                        val t = if (titleIdx != null && titleIdx in 0 until dataArr.size()) {
                                            dataArr.get(titleIdx).takeIf { it.isJsonPrimitive }?.asString ?: "Episode $num"
                                        } else "Episode $num"

                                        val fillerIdx = epObj.get("is_filler")?.asInt ?: epObj.get("filler")?.asInt
                                        val isFiller = if (fillerIdx != null && fillerIdx in 0 until dataArr.size()) {
                                            dataArr.get(fillerIdx).takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                                        } else false

                                        val subbedIdx = epObj.get("subbed")?.asInt
                                        val isSubbed = if (subbedIdx != null && subbedIdx in 0 until dataArr.size()) {
                                            dataArr.get(subbedIdx).takeIf { it.isJsonPrimitive }?.asBoolean ?: true
                                        } else true

                                        val dubbedIdx = epObj.get("dubbed")?.asInt
                                        val isDubbed = if (dubbedIdx != null && dubbedIdx in 0 until dataArr.size()) {
                                            dataArr.get(dubbedIdx).takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                                        } else false

                                        val airedIdx = epObj.get("aired")?.asInt
                                        val airedStr = if (airedIdx != null && airedIdx in 0 until dataArr.size()) {
                                            dataArr.get(airedIdx).takeIf { it.isJsonPrimitive }?.asString
                                        } else null

                                        list.add(
                                            ReAnimeEpisode(
                                                episodeNumber = num,
                                                title = t,
                                                isFiller = isFiller,
                                                isSubbed = isSubbed,
                                                isDubbed = isDubbed,
                                                airedDate = airedStr
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (list.isNotEmpty() || foundAnilistId != null) return Pair(foundAnilistId, list)
                }
            } catch (_: Exception) {}
        }
        return Pair(foundAnilistId, list)
    }

    private fun fetchReAnimeWatchEpisodes(slug: String): List<ReAnimeEpisode> {
        return fetchReAnimeWatchMeta(slug).second
    }

    suspend fun resolveEpisodePlayback(
        anilistId: Int?,
        slug: String?,
        episodeNumber: Int
    ): ReAnimePlaybackInfo = withContext(Dispatchers.IO) {
        var resolvedAnilistId = anilistId
        if (resolvedAnilistId == null && !slug.isNullOrBlank()) {
            val meta = fetchReAnimeWatchMeta(slug)
            resolvedAnilistId = meta.first
            if (resolvedAnilistId == null) {
                val cleanTitle = slug.replace(Regex("""-[a-z0-9]{5,8}$"""), "").replace("-", " ")
                resolvedAnilistId = resolveAniListId(cleanTitle, null, 1, false)
            }
        }

        if (resolvedAnilistId == null) {
            return@withContext ReAnimePlaybackInfo(episodeNumber = episodeNumber)
        }

        val flixServers = fetchFlixServers(resolvedAnilistId, episodeNumber)
        val serverOptions = mutableListOf<ReAnimeServerOption>()
        for (srv in flixServers) {
            val link = srv.dataLink ?: continue
            val accessId = if (link.contains("/e/")) {
                link.substringAfter("/e/").substringBefore("?")
            } else {
                link.substringBefore("?")
            }
            if (accessId.isBlank()) continue
            serverOptions.add(
                ReAnimeServerOption(
                    serverName = srv.serverName ?: "HD-1",
                    dataType = srv.dataType ?: "sub",
                    embedUrl = link,
                    accessId = accessId
                )
            )
        }

        val primaryServer = serverOptions.firstOrNull { it.dataType.equals("sub", ignoreCase = true) }
            ?: serverOptions.firstOrNull()

        val directStreams = mutableListOf<StreamSource>()
        if (primaryServer != null) {
            val isDub = primaryServer.dataType.equals("dub", ignoreCase = true)
            val resolved = resolveFlixDirectMkv(primaryServer.accessId, isDub)
            directStreams.addAll(resolved)
        }

        ReAnimePlaybackInfo(
            episodeNumber = episodeNumber,
            servers = serverOptions,
            selectedServer = primaryServer,
            embedUrl = primaryServer?.embedUrl,
            directStreams = directStreams,
            selectedStream = directStreams.firstOrNull()
        )
    }

    suspend fun resolveDirectStreamsForServer(server: ReAnimeServerOption): List<StreamSource> = withContext(Dispatchers.IO) {
        val isDub = server.dataType.equals("dub", ignoreCase = true)
        resolveFlixDirectMkv(server.accessId, isDub)
    }

    private fun findReAnimeSlugByAnilistId(anilistId: Int): String? {
        for (mirror in mirrors) {
            try {
                val url = "$mirror/"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", mobileUserAgent)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val html = resp.body?.string() ?: return@use
                    val regex = Regex("""\{anime_id:"([^"]+)"[^}]*?anilist_id:$anilistId\b""")
                    val m = regex.find(html)
                    if (m != null) return m.groupValues[1]
                }
            } catch (_: Exception) {}
        }
        return null
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
