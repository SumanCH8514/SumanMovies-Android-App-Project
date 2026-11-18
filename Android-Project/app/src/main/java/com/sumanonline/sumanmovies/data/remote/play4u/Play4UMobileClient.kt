package com.sumanonline.sumanmovies.data.remote.play4u

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Play4UMobileClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()
    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val primaryBaseUrl = "https://play4u.org"
    private val searchApiBase = "https://play4u.org/api/search.php"
    private val defaultSealKey = "pa4u-stream-seal-v1"

    suspend fun search(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val apiItems = searchViaApi(trimmed, page)
        if (apiItems.isNotEmpty()) {
            return@withContext apiItems
        }

        searchViaWebScrape(trimmed)
    }

    private fun parseMediaItemFromJson(obj: JsonObject): MediaItem? {
        val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val imdbId = obj.get("imdb_id")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("imdb")?.takeIf { !it.isJsonNull }?.asString
        val tmdbId = obj.get("tmdb_id")?.takeIf { !it.isJsonNull }?.asInt
        val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString
        val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")
        val year = obj.get("year")?.takeIf { !it.isJsonNull }?.asString ?: yearRegex.find(title)?.value
        val posterUrl = obj.get("poster")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("poster_url")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("poster_sm")?.takeIf { !it.isJsonNull }?.asString
        val backdropUrl = obj.get("backdrop")?.takeIf { !it.isJsonNull }?.asString
        val overview = obj.get("overview")?.takeIf { !it.isJsonNull }?.asString

        val mediaTypeStr = obj.get("media_type")?.takeIf { !it.isJsonNull }?.asString?.lowercase()
            ?: obj.get("type")?.takeIf { !it.isJsonNull }?.asString?.lowercase() ?: ""
        val isSeries = mediaTypeStr.contains("series") || mediaTypeStr.contains("tv") ||
            title.contains("Season", ignoreCase = true) || title.contains("S0", ignoreCase = true)
        val mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE

        val genresList = mutableListOf<String>()
        obj.getAsJsonArray("genre_names")?.forEach { g ->
            if (!g.isJsonNull && g.isJsonPrimitive) genresList.add(g.asString)
        }

        val effectiveId = if (!imdbId.isNullOrBlank()) {
            "play4u:$imdbId"
        } else if (!id.isNullOrBlank()) {
            "play4u:$id"
        } else if (tmdbId != null && tmdbId > 0) {
            "play4u:$tmdbId"
        } else {
            "play4u:"
        }

        return MediaItem(
            id = effectiveId,
            tmdbId = tmdbId,
            title = title,
            mediaType = mediaType,
            year = year,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            overview = overview,
            genres = genresList,
            provider = "play4u"
        )
    }

    private fun searchViaApi(query: String, page: Int = 1): List<MediaItem> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val targetUrl = "$searchApiBase?q=$encodedQuery&per_page=24&page=$page"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Accept", "application/json")
                .header("Referer", "$primaryBaseUrl/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val text = response.body?.string() ?: return emptyList()
            val json = gson.fromJson(text, JsonObject::class.java)
            val itemsArr = json.getAsJsonArray("items") ?: return emptyList()

            val items = mutableListOf<MediaItem>()
            for (element in itemsArr) {
                if (element.isJsonObject) {
                    parseMediaItemFromJson(element.asJsonObject)?.let { items.add(it) }
                }
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getCatalog(
        type: String? = null,
        industry: String? = null,
        lang: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val apiResult = getCatalogViaApi(type, industry, lang, genre, page)
        if (apiResult.first.isNotEmpty()) {
            return@withContext apiResult
        }
        getCatalogViaWebScrape(type, industry, lang, genre, page)
    }

    private fun getCatalogViaApi(
        type: String? = null,
        industry: String? = null,
        lang: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> {
        return try {
            val urlBuilder = StringBuilder("$searchApiBase?q=*&per_page=24&page=$page")
            if (!type.isNullOrBlank()) {
                urlBuilder.append("&type=").append(URLEncoder.encode(type, "UTF-8"))
            }
            if (!industry.isNullOrBlank()) {
                urlBuilder.append("&industry=").append(URLEncoder.encode(industry, "UTF-8"))
            }
            if (!lang.isNullOrBlank()) {
                urlBuilder.append("&lang=").append(URLEncoder.encode(lang, "UTF-8"))
            }
            if (!genre.isNullOrBlank()) {
                urlBuilder.append("&genre=").append(URLEncoder.encode(genre, "UTF-8"))
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", mobileUserAgent)
                .header("Accept", "application/json")
                .header("Referer", "$primaryBaseUrl/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Pair(emptyList(), 0)

            val text = response.body?.string() ?: return Pair(emptyList(), 0)
            val json = gson.fromJson(text, JsonObject::class.java)
            val itemsArr = json.getAsJsonArray("items") ?: return Pair(emptyList(), 0)
            val foundCount = json.get("found")?.takeIf { !it.isJsonNull }?.asInt ?: itemsArr.size()

            val items = mutableListOf<MediaItem>()
            for (element in itemsArr) {
                if (element.isJsonObject) {
                    parseMediaItemFromJson(element.asJsonObject)?.let { items.add(it) }
                }
            }
            Pair(items, foundCount)
        } catch (_: Exception) {
            Pair(emptyList(), 0)
        }
    }

    private fun getCatalogViaWebScrape(
        type: String? = null,
        industry: String? = null,
        lang: String? = null,
        genre: String? = null,
        page: Int = 1
    ): Pair<List<MediaItem>, Int> {
        return try {
            val urlBuilder = StringBuilder("$primaryBaseUrl/?page=$page")
            if (!type.isNullOrBlank()) {
                urlBuilder.append("&type=").append(URLEncoder.encode(type, "UTF-8"))
            }
            if (!industry.isNullOrBlank()) {
                urlBuilder.append("&industry=").append(URLEncoder.encode(industry, "UTF-8"))
            }
            if (!lang.isNullOrBlank()) {
                urlBuilder.append("&lang=").append(URLEncoder.encode(lang, "UTF-8"))
            }
            if (!genre.isNullOrBlank()) {
                urlBuilder.append("&genre=").append(URLEncoder.encode(genre, "UTF-8"))
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", mobileUserAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$primaryBaseUrl/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Pair(emptyList(), 0)

            val html = response.body?.string() ?: return Pair(emptyList(), 0)
            val items = parseCatalogHtml(html)
            Pair(items, if (items.isNotEmpty()) 240 else 0)
        } catch (_: Exception) {
            Pair(emptyList(), 0)
        }
    }

    private fun searchViaWebScrape(query: String): List<MediaItem> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val targetUrl = "$primaryBaseUrl/?s=$encodedQuery"

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$primaryBaseUrl/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body?.string() ?: return emptyList()
            parseCatalogHtml(html)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCatalogHtml(html: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val articlePattern = Pattern.compile(
            """<article[^>]*data-title="([^"]+)"[^>]*>.*?<a[^>]+href="([^"]+)"[^>]*>.*?<img\s+[^>]*src="([^"]+)"""",
            Pattern.DOTALL
        )
        val matcher = articlePattern.matcher(html)
        val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")

        while (matcher.find()) {
            val title = matcher.group(1)?.trim() ?: continue
            val link = matcher.group(2)?.trim() ?: ""
            val poster = matcher.group(3)?.trim()

            val isSeries = link.contains("/series/") || title.contains("Season", ignoreCase = true)
            val mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE
            val year = yearRegex.find(title)?.value

            val cleanId = link.removePrefix("/watch/").removePrefix("/series/").trim('/')
            val effectiveId = if (cleanId.isNotBlank()) cleanId else link

            items.add(
                MediaItem(
                    id = "play4u:$effectiveId",
                    title = title,
                    mediaType = mediaType,
                    year = year,
                    posterUrl = poster,
                    provider = "play4u"
                )
            )
        }

        return items
    }

    fun extractSeriesId(raw: String): String {
        var s = raw.removePrefix("play4u:").trim()
        if (s.startsWith("http://") || s.startsWith("https://")) {
            s = s.substringAfter("://").substringAfter("/")
        }
        s = s.removePrefix("series/").removePrefix("watch/").trim('/')
        s = s.removePrefix("series-")
        s = s.replace(Regex("""/s-\d+/ep-\d+.*""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""/s\d+e\d+.*""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""-s\d+e\d+.*""", RegexOption.IGNORE_CASE), "")
        return s.trim('/')
    }

    suspend fun resolveStreams(
        identifier: String,
        season: Int = 0,
        episode: Int = 0,
        title: String? = null,
        year: String? = null
    ): Pair<List<StreamSource>, List<SubtitleTrack>> = withContext(Dispatchers.IO) {
        val sPad = if (season > 0) String.format(java.util.Locale.US, "%02d", season) else "01"
        val ePad = if (episode > 0) String.format(java.util.Locale.US, "%02d", episode) else "01"

        val isSeries = season > 0 || episode > 0 || identifier.contains("/series/") || identifier.contains("series-")

        val targetUrlList = mutableListOf<String>()

        if (isSeries) {
            val seriesId = extractSeriesId(identifier)
            if (seriesId.isNotBlank()) {
                targetUrlList.add("$primaryBaseUrl/series/$seriesId/s-$sPad/ep-$ePad")
            }
        } else {
            val cleanId = identifier.removePrefix("play4u:").trim()
            if (cleanId.startsWith("http://") || cleanId.startsWith("https://")) {
                targetUrlList.add(cleanId)
            } else if (cleanId.startsWith("watch/")) {
                targetUrlList.add("$primaryBaseUrl/$cleanId")
            } else {
                targetUrlList.add("$primaryBaseUrl/watch/$cleanId")
            }
        }

        for (targetUrl in targetUrlList) {
            val res = fetchAndParsePlayback(targetUrl)
            if (res.first.isNotEmpty()) {
                return@withContext res
            }
        }

        if (!title.isNullOrBlank()) {
            val queries = com.sumanonline.sumanmovies.util.MediaMatcher.buildSearchQueries(title)
            val searchResults = mutableListOf<MediaItem>()
            for (q in queries) {
                val results = search(q)
                if (results.isNotEmpty()) {
                    searchResults.addAll(results)
                    break
                }
            }

            val matched = com.sumanonline.sumanmovies.util.MediaMatcher.findBestMatch(
                searchResults,
                targetTitle = title,
                targetYear = year,
                season = season,
                isSeries = isSeries
            )

            if (matched != null) {
                if (isSeries) {
                    val matchedSeriesId = extractSeriesId(matched.id)
                    if (matchedSeriesId.isNotBlank()) {
                        val fallbackUrl = "$primaryBaseUrl/series/$matchedSeriesId/s-$sPad/ep-$ePad"
                        val res = fetchAndParsePlayback(fallbackUrl)
                        if (res.first.isNotEmpty()) {
                            return@withContext res
                        }
                    }
                } else {
                    val matchedMovieId = matched.id.removePrefix("play4u:").trim()
                    if (matchedMovieId.isNotBlank()) {
                        val fallbackUrl = "$primaryBaseUrl/watch/$matchedMovieId"
                        val res = fetchAndParsePlayback(fallbackUrl)
                        if (res.first.isNotEmpty()) {
                            return@withContext res
                        }
                    }
                }
            }
        }

        Pair(emptyList(), emptyList())
    }

    suspend fun getSeriesEpisodeLinks(seriesIdentifier: String): List<String> = withContext(Dispatchers.IO) {
        val seriesId = extractSeriesId(seriesIdentifier)
        if (seriesId.isBlank()) return@withContext emptyList()
        val url = "$primaryBaseUrl/series/$seriesId"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", "$primaryBaseUrl/")
                .build()
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext emptyList()
            val html = res.body?.string() ?: return@withContext emptyList()
            val regex = Regex("""href=["'](/series/[^"']+/s-\d+/ep-\d+)["']""")
            regex.findAll(html).map { it.groupValues[1] }.distinct().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchAndParsePlayback(targetUrl: String): Pair<List<StreamSource>, List<SubtitleTrack>> {
        return try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$primaryBaseUrl/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Pair(emptyList(), emptyList())

            val html = response.body?.string() ?: return Pair(emptyList(), emptyList())
            parsePlaybackPayload(html, targetUrl)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    private fun parsePlaybackPayload(html: String, pageUrl: String): Pair<List<StreamSource>, List<SubtitleTrack>> {
        val playJsonMatch = Regex("""window\.__PA_PLAY__\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: Regex("""window\.__PA_PLAY__\s*=\s*(\{.*?\});\s*(?:window\.|<\/script>)""").find(html)
        val sealKeyMatch = Regex("""window\.__PA_SEAL_KEY__\s*=\s*["']([^"']+)["']""").find(html)
        val sealKey = sealKeyMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: defaultSealKey

        val streams = mutableListOf<StreamSource>()
        val subtitles = mutableListOf<SubtitleTrack>()

        val headers = mapOf(
            "User-Agent" to mobileUserAgent,
            "Referer" to "$primaryBaseUrl/",
            "Origin" to primaryBaseUrl
        )

        if (playJsonMatch != null) {
            try {
                val jsonStr = playJsonMatch.groupValues[1]
                val root = gson.fromJson(jsonStr, JsonObject::class.java)

                val streamsArr = root.getAsJsonArray("streams")
                if (streamsArr != null) {
                    for (element in streamsArr) {
                        val sObj = element.asJsonObject
                        val rawM3u8 = sObj.get("m3u8_url")?.takeIf { !it.isJsonNull }?.asString ?: continue
                        val unsealedM3u8 = unseal(rawM3u8, sealKey)
                        if (unsealedM3u8.isBlank()) continue

                        val finalUrl = when {
                            unsealedM3u8.startsWith("http://") || unsealedM3u8.startsWith("https://") -> unsealedM3u8
                            unsealedM3u8.startsWith("/") -> "$primaryBaseUrl$unsealedM3u8"
                            else -> "$primaryBaseUrl/$unsealedM3u8"
                        }

                        val languages = sObj.getAsJsonArray("languages")
                        val langList = if (languages != null) {
                            languages.mapNotNull { it.asJsonObject.get("name")?.asString }
                        } else emptyList()
                        val langLabel = if (langList.isNotEmpty()) {
                            langList.take(3).joinToString(", ")
                        } else {
                            "Hindi, Tamil, Telugu"
                        }

                        streams.add(
                            StreamSource(
                                quality = "1080p",
                                format = "HLS",
                                url = finalUrl,
                                label = "Play4U - 1080p ($langLabel) HLS H264",
                                provider = "play4u",
                                directFile = false,
                                headers = headers
                            )
                        )
                    }
                }

                if (streams.isEmpty()) {
                    val movieObj = root.getAsJsonObject("movie") ?: root.getAsJsonObject("item")
                    val rawUrl = movieObj?.get("video_url")?.takeIf { !it.isJsonNull }?.asString
                        ?: movieObj?.get("m3u8_url")?.takeIf { !it.isJsonNull }?.asString
                        ?: root.get("video_url")?.takeIf { !it.isJsonNull }?.asString
                        ?: root.get("m3u8_url")?.takeIf { !it.isJsonNull }?.asString

                    if (rawUrl != null && rawUrl.isNotBlank()) {
                        val unsealed = unseal(rawUrl, sealKey)
                        if (unsealed.isNotBlank()) {
                            val finalUrl = when {
                                unsealed.startsWith("http://") || unsealed.startsWith("https://") -> unsealed
                                unsealed.startsWith("/") -> "$primaryBaseUrl$unsealed"
                                else -> "$primaryBaseUrl/$unsealed"
                            }
                            streams.add(
                                StreamSource(
                                    quality = "1080p",
                                    format = "HLS",
                                    url = finalUrl,
                                    label = "Play4U - 1080p (Multi-Audio) HLS H264",
                                    provider = "play4u",
                                    directFile = false,
                                    headers = headers
                                )
                            )
                        }
                    }
                }

                val subsArr = root.getAsJsonArray("subtitles")
                if (subsArr != null) {
                    for (subEl in subsArr) {
                        val subObj = subEl.asJsonObject
                        val sId = subObj.get("id")?.asString ?: "play4u-sub-${subtitles.size}"
                        val sUrl = subObj.get("url")?.takeIf { !it.isJsonNull }?.asString ?: continue
                        val sLang = subObj.get("lang")?.asString ?: "English"
                        subtitles.add(SubtitleTrack(id = sId, url = sUrl, language = sLang, label = sLang))
                    }
                }
            } catch (_: Exception) {}
        }

        if (streams.isEmpty()) {
            val directM3u8Match = Regex("""["'](https?://[^"'\s]+\.m3u8[^"'\s]*)["']""").find(html)
            if (directM3u8Match != null) {
                streams.add(
                    StreamSource(
                        quality = "1080p",
                        format = "HLS",
                        url = directM3u8Match.groupValues[1],
                        label = "Play4U - 1080p (Hindi, Tamil, Telugu) HLS H264",
                        provider = "play4u",
                        directFile = false,
                        headers = headers
                    )
                )
            }
        }

        if (streams.isEmpty()) {
            val sealedMatch = Regex("""pa!~[A-Za-z0-9_\-]+""").find(html)
            if (sealedMatch != null) {
                val unsealed = unseal(sealedMatch.value, sealKey)
                if (unsealed.isNotBlank() && (unsealed.contains(".m3u8") || unsealed.startsWith("http"))) {
                    val finalUrl = when {
                        unsealed.startsWith("http://") || unsealed.startsWith("https://") -> unsealed
                        unsealed.startsWith("/") -> "$primaryBaseUrl$unsealed"
                        else -> "$primaryBaseUrl/$unsealed"
                    }
                    streams.add(
                        StreamSource(
                            quality = "1080p",
                            format = "HLS",
                            url = finalUrl,
                            label = "Play4U - 1080p (Hindi, Tamil, Telugu) HLS H264",
                            provider = "play4u",
                            directFile = false,
                            headers = headers
                        )
                    )
                }
            }
        }

        if (streams.isEmpty()) {
            val iframeMatch = Regex("""<iframe[^>]+src=["']([^"']+)["']""").find(html)
            if (iframeMatch != null) {
                val iframeUrl = iframeMatch.groupValues[1]
                val hlsUrl = extractHlsFromIframe(iframeUrl, sealKey)
                if (!hlsUrl.isNullOrBlank()) {
                    streams.add(
                        StreamSource(
                            quality = "1080p",
                            format = "HLS",
                            url = hlsUrl,
                            label = "Play4U - 1080p (Hindi, Tamil, Telugu) HLS H264",
                            provider = "play4u",
                            directFile = false,
                            headers = headers
                        )
                    )
                }
            }
        }

        return Pair(streams.distinctBy { it.url }, subtitles)
    }

    private fun extractHlsFromIframe(iframeUrl: String, sealKey: String): String? {
        return try {
            val target = when {
                iframeUrl.startsWith("http://") || iframeUrl.startsWith("https://") -> iframeUrl
                iframeUrl.startsWith("/") -> "$primaryBaseUrl$iframeUrl"
                else -> "$primaryBaseUrl/$iframeUrl"
            }
            val req = Request.Builder()
                .url(target)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", "$primaryBaseUrl/")
                .build()
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return null
            val body = res.body?.string() ?: return null

            val directM3u8Match = Regex("""["'](https?://[^"'\s]+\.m3u8[^"'\s]*)["']""").find(body)
            if (directM3u8Match != null) {
                return directM3u8Match.groupValues[1]
            }

            val sealedMatch = Regex("""pa!~[A-Za-z0-9_\-]+""").find(body)
            if (sealedMatch != null) {
                val unsealed = unseal(sealedMatch.value, sealKey)
                if (unsealed.contains(".m3u8")) {
                    return unsealed
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun unseal(sealed: String, key: String = defaultSealKey): String {
        if (!sealed.startsWith("pa!~")) return sealed
        return try {
            val raw = sealed.substring(4)
            val b64 = raw.replace('-', '+').replace('_', '/')
            val pad = (4 - (b64.length % 4)) % 4
            val padded = if (pad > 0) b64 + "=".repeat(pad) else b64

            val decodedBytes = Base64.getDecoder().decode(padded)
            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)

            val chars = CharArray(decodedBytes.size) { i ->
                val b = decodedBytes[i].toInt() and 0xFF
                val k = keyBytes[i % keyBytes.size].toInt() and 0xFF
                (b xor k).toChar()
            }
            String(chars)
        } catch (_: Exception) {
            ""
        }
    }
}
