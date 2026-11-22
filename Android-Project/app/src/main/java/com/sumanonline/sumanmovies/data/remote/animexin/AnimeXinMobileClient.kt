package com.sumanonline.sumanmovies.data.remote.animexin

import com.sumanonline.sumanmovies.data.models.AnimeXinGenre
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AnimeXinMobileClient {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val existing = cookieStore.getOrPut(host) { mutableListOf() }
                existing.removeAll { old -> cookies.any { it.name == old.name } }
                existing.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    private val baseUrl = "https://animexin.dev"
    private val mobileUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private var cachedGenres: List<AnimeXinGenre>? = null

    private val defaultGenres = listOf(
        AnimeXinGenre("Action", "genres/action/"),
        AnimeXinGenre("Adventure", "genres/adventure/"),
        AnimeXinGenre("Cultivation", "genres/cultivation/"),
        AnimeXinGenre("Martial Arts", "genres/martial-arts/"),
        AnimeXinGenre("Fantasy", "genres/fantasy/"),
        AnimeXinGenre("Romance", "genres/romance/"),
        AnimeXinGenre("Comedy", "genres/comedy/"),
        AnimeXinGenre("Demons", "genres/demons/"),
        AnimeXinGenre("Historical", "genres/historical/"),
        AnimeXinGenre("Mystery", "genres/mystery/"),
        AnimeXinGenre("Sci-Fi", "genres/sci-fi/"),
        AnimeXinGenre("Supernatural", "genres/supernatural/"),
        AnimeXinGenre("Magic", "genres/magic/"),
        AnimeXinGenre("Isekai", "genres/isekai/"),
        AnimeXinGenre("Reincarnation", "genres/reincarnation/"),
        AnimeXinGenre("Harem", "genres/harem/"),
        AnimeXinGenre("Shounen", "genres/shounen/"),
        AnimeXinGenre("Chinese Style", "genres/chinese-style/")
    )

    suspend fun getCatalog(categoryPath: String? = null, page: Int = 1): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val clean = categoryPath?.trim()?.trimStart('/')?.trimEnd('/')
        val isToday = clean == "today"
        val targetUrl = when {
            clean.isNullOrBlank() || clean == "all" -> {
                if (page == 1) "$baseUrl/anime/" else "$baseUrl/anime/?page=$page"
            }
            clean == "latest" -> {
                if (page == 1) "$baseUrl/anime/?status=&type=&order=update" else "$baseUrl/anime/?status=&type=&order=update&page=$page"
            }
            clean == "today" -> {
                if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
            }
            clean.startsWith("http://") || clean.startsWith("https://") -> {
                val base = clean.trimEnd('/')
                if (base.contains("genres", ignoreCase = true)) {
                    if (page == 1) "$base/" else "$base/page/$page/"
                } else if (base.contains("?")) {
                    if (page == 1) "$base" else "$base&page=$page"
                } else {
                    if (page == 1) "$base/" else "$base/?page=$page"
                }
            }
            clean.startsWith("genres", ignoreCase = true) -> {
                if (page == 1) "$baseUrl/$clean/" else "$baseUrl/$clean/page/$page/"
            }
            else -> {
                if (page == 1) "$baseUrl/$clean/" else "$baseUrl/$clean/page/$page/"
            }
        }
        val items = parseListingPage(targetUrl, isTodayLatestRelease = isToday)
        Pair(items, items.size)
    }

    suspend fun getGenres(): List<AnimeXinGenre> = withContext(Dispatchers.IO) {
        cachedGenres?.let { return@withContext it }
        try {
            val request = Request.Builder()
                .url("$baseUrl/genres/")
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext defaultGenres
                val html = response.body?.string() ?: return@withContext defaultGenres
                val pattern = Pattern.compile("<a[^>]+href=[\"'](?:https?://animexin\\.dev)?(/genres/[^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(html)
                val list = mutableListOf<AnimeXinGenre>()
                val seen = mutableSetOf<String>()
                while (matcher.find()) {
                    val rawPath = matcher.group(1)?.trim('/') ?: continue
                    val path = if (rawPath.endsWith("/")) rawPath else "$rawPath/"
                    val rawText = matcher.group(2)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: continue
                    val cleanText = rawText.replace(Regex("""\d+$"""), "").trim()
                    if (cleanText.isNotBlank() && !seen.contains(path)) {
                        seen.add(path)
                        list.add(AnimeXinGenre(cleanText, path))
                    }
                }
                if (list.isNotEmpty()) {
                    cachedGenres = list
                    list
                } else {
                    defaultGenres
                }
            }
        } catch (_: Exception) {
            defaultGenres
        }
    }

    suspend fun search(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8").replace("+", "%20")
        val searchUrl = if (page == 1) "$baseUrl/?s=$encoded" else "$baseUrl/page/$page/?s=$encoded"
        parseListingPage(searchUrl)
    }

    private fun parseListingPage(url: String, isTodayLatestRelease: Boolean = false): List<MediaItem> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body?.string() ?: return emptyList()
                val items = mutableListOf<MediaItem>()

                val targetHtml = if (isTodayLatestRelease) {
                    val idx = html.indexOf("releases latesthome")
                    if (idx != -1) {
                        val endIdx = html.indexOf("class=\"releases", idx + 30)
                        if (endIdx != -1) html.substring(idx, endIdx) else html.substring(idx)
                    } else {
                        html
                    }
                } else {
                    html
                }

                val articlePattern = Pattern.compile("""<article[^>]*>([\s\S]*?)</article>""", Pattern.CASE_INSENSITIVE)
                val matcher = articlePattern.matcher(targetHtml)
                while (matcher.find()) {
                    val block = matcher.group(1) ?: continue
                    val item = parseCardHtml(block)
                    if (item != null) items.add(item)
                }

                items.distinctBy { it.id }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCardHtml(cardHtml: String): MediaItem? {
        val linkMatch = Regex("""<a[^>]+href="([^"]+)"""", RegexOption.IGNORE_CASE).find(cardHtml) ?: return null
        val postUrl = linkMatch.groupValues[1].trim()
        if (postUrl.isBlank() || postUrl == baseUrl) return null

        val titleAttr = Regex("""title="([^"]+)"""", RegexOption.IGNORE_CASE).find(cardHtml)
        val hTitle = Regex("""<h[1-6][^>]*>([\s\S]*?)</h[1-6]>""", RegexOption.IGNORE_CASE).find(cardHtml)
        val ttMatch = Regex("""<div[^>]+class="tt"[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(cardHtml)

        val rawTitle = (titleAttr?.groupValues?.get(1) ?: hTitle?.groupValues?.get(1) ?: ttMatch?.groupValues?.get(1))
            ?.replace(Regex("""<[^>]+>"""), "")
            ?.trim() ?: return null

        val imgMatch = Regex("""<img[^>]+(?:src|data-src)="([^"]+)"""", RegexOption.IGNORE_CASE).find(cardHtml)
        val posterUrl = imgMatch?.groupValues?.get(1)?.trim()

        val typeMatch = Regex("""<div[^>]+class="typez[^"]*"[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(cardHtml)
            ?: Regex("""<div[^>]+class="eggtype[^"]*"[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(cardHtml)
        val typeStr = typeMatch?.groupValues?.get(1)?.replace(Regex("""<[^>]+>"""), "")?.trim()?.uppercase() ?: ""

        val isMovie = typeStr.contains("MOVIE") || postUrl.contains("-movie-", ignoreCase = true)
        val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.value

        val cleanTitle = cleanAnimeTitle(rawTitle)
        val effectiveId = "animexin:${URLEncoder.encode(postUrl, "UTF-8")}"

        return MediaItem(
            id = effectiveId,
            title = cleanTitle.ifBlank { rawTitle },
            mediaType = if (isMovie) MediaType.MOVIE else MediaType.SERIES,
            year = year,
            posterUrl = posterUrl,
            backdropUrl = posterUrl,
            overview = "AnimeXin • $rawTitle",
            genres = listOf("Donghua", if (isMovie) "Movie" else "Series"),
            provider = "animexin"
        )
    }

    private fun cleanAnimeTitle(raw: String): String {
        return raw
            .replace(Regex("""Indonesia,\s*English\s*Sub""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""Subtitle\s*Sub""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""Episode\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+""", RegexOption.IGNORE_CASE), " ")
            .trim()
    }

    suspend fun resolveStream(
        targetUrlOrId: String,
        season: Int = 1,
        episode: Int = 1
    ): List<StreamSource> = withContext(Dispatchers.IO) {
        try {
            val rawUrl = if (targetUrlOrId.startsWith("animexin:")) {
                java.net.URLDecoder.decode(targetUrlOrId.removePrefix("animexin:"), "UTF-8")
            } else {
                targetUrlOrId
            }

            var episodePageUrl = rawUrl
            val isDirectEpisode = rawUrl.contains("-episode-", ignoreCase = true)

            if (!isDirectEpisode) {
                val resolvedEpUrl = findEpisodeUrlOnSeriesPage(rawUrl, episode)
                if (!resolvedEpUrl.isNullOrBlank()) {
                    episodePageUrl = resolvedEpUrl
                }
            }

            val epReq = Request.Builder()
                .url(episodePageUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            var epHtml = ""
            client.newCall(epReq).execute().use { resp ->
                if (resp.isSuccessful) epHtml = resp.body?.string() ?: ""
            }
            if (epHtml.isBlank()) return@withContext emptyList()

            val streams = mutableListOf<StreamSource>()

            val blockPattern = Pattern.compile(
                """<div[^>]+class="soraddlx[^"]*"[^>]*>([\s\S]*?)(?=<div[^>]+class="soraddlx|<div[^>]+class="bignav"|<div[^>]+class="bottomtitle"|$)""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = blockPattern.matcher(epHtml)
            var englishBlock: String? = null

            while (matcher.find()) {
                val b = matcher.group(1) ?: continue
                val titlePattern = Pattern.compile("""<div[^>]+class="sorattlx"[^>]*>([\s\S]*?)</div>""", Pattern.CASE_INSENSITIVE)
                val tm = titlePattern.matcher(b)
                val title = if (tm.find()) tm.group(1)?.lowercase() ?: "" else ""
                if (title.contains("english") || title.contains("eng")) {
                    englishBlock = b
                    break
                }
            }

            if (englishBlock == null) {
                matcher.reset()
                while (matcher.find()) {
                    val b = matcher.group(1) ?: continue
                    if ((b.contains("_eng", ignoreCase = true) || b.contains("eng.mp4", ignoreCase = true)) &&
                        !b.contains("indonesia", ignoreCase = true) &&
                        !b.contains("_indo", ignoreCase = true)
                    ) {
                        englishBlock = b
                        break
                    }
                }
            }

            var serverIndex = 1
            if (englishBlock != null) {
                val mfMatches = Regex("""href="([^"]*mediafire\.com/file/[^"]+)"""", RegexOption.IGNORE_CASE).findAll(englishBlock)
                val mediafireLink = mfMatches.map { it.groupValues[1] }
                    .firstOrNull { !it.contains("_indo", ignoreCase = true) && !it.contains("indonesia", ignoreCase = true) }

                if (!mediafireLink.isNullOrBlank()) {
                    val directMf = resolveMediafireDirect(mediafireLink)
                    if (directMf != null) {
                        val sizeLabel = if (!directMf.second.isNullOrBlank()) " • ${directMf.second}" else ""
                        streams.add(
                            StreamSource(
                                title = "Server $serverIndex (Mediafire Fast CDN$sizeLabel)",
                                label = "Server $serverIndex (Mediafire Fast CDN$sizeLabel)",
                                mirrorName = "Mediafire Fast CDN",
                                url = directMf.first,
                                quality = "1080p",
                                format = "mp4",
                                provider = "animexin",
                                isStreamable = true
                            )
                        )
                        serverIndex++
                    }
                }

                val mirrorMatches = Regex("""href="([^"]*mirrored\.to/files/[^"]+)"""", RegexOption.IGNORE_CASE).findAll(englishBlock)
                val mirrorLink = mirrorMatches.map { it.groupValues[1] }
                    .firstOrNull { !it.contains("_indo", ignoreCase = true) && !it.contains("indonesia", ignoreCase = true) }

                if (!mirrorLink.isNullOrBlank()) {
                    val mirrorStreams = resolveMirrorHosts(mirrorLink, serverStartIndex = serverIndex)
                    streams.addAll(mirrorStreams)
                }
            }

            streams
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findEpisodeUrlOnSeriesPage(seriesUrl: String, targetEpisode: Int): String? {
        return try {
            val req = Request.Builder()
                .url(seriesUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                val epLinkPattern = Pattern.compile("""<a[^>]+href="([^"]+)"[^>]*>([\s\S]*?)</a>""", Pattern.CASE_INSENSITIVE)
                val matcher = epLinkPattern.matcher(html)
                val candidates = mutableListOf<Pair<Int, String>>()

                while (matcher.find()) {
                    val href = matcher.group(1) ?: continue
                    val text = matcher.group(2)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: ""
                    if (href.contains("-episode-", ignoreCase = true)) {
                        val numMatch = Regex("""episode[ -]?0*(\d+)""", RegexOption.IGNORE_CASE).find(href)
                            ?: Regex("""\b0*(\d+)\b""").find(text)
                        val num = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        candidates.add(Pair(num, href))
                    }
                }

                candidates.firstOrNull { it.first == targetEpisode }?.second
                    ?: candidates.firstOrNull { it.first == 1 }?.second
                    ?: candidates.firstOrNull()?.second
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveMediafireDirect(fileUrl: String): Pair<String, String?>? {
        return try {
            val req = Request.Builder()
                .url(fileUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                val btnMatch = Regex("""href=["\'](https?://download[^"\'\s]+)["\'][^>]*id=["\']downloadButton["\']""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""id=["\']downloadButton["\'][^>]*href=["\'](https?://download[^"\'\s]+)["\']""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""href=["\'](https?://download[^"\'\s]+)["\']""", RegexOption.IGNORE_CASE).find(html)

                val sizeMatch = Regex("""Download\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE).find(html)
                val size = sizeMatch?.groupValues?.get(1)?.trim()

                val directUrl = btnMatch?.groupValues?.get(1)
                if (!directUrl.isNullOrBlank()) {
                    Pair(directUrl, size)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveMirrorHosts(mirrorUrl: String, serverStartIndex: Int = 2): List<StreamSource> {
        val list = mutableListOf<StreamSource>()
        try {
            var dlPageUrl = mirrorUrl
            if (!dlPageUrl.contains("?hash=") && !dlPageUrl.contains("&dl=0")) {
                val req = Request.Builder()
                    .url(mirrorUrl)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", baseUrl)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val linkMatch = Regex("""href=["\']([^"\']*\?hash=[^"\']+)["\']""", RegexOption.IGNORE_CASE).find(html)
                        if (linkMatch != null) {
                            val candidate = linkMatch.groupValues[1]
                            dlPageUrl = if (candidate.startsWith("http")) candidate else "https://www.mirrored.to$candidate"
                        }
                    }
                }
            }

            val reqDl = Request.Builder()
                .url(dlPageUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", mirrorUrl)
                .build()

            var dlHtml = ""
            client.newCall(reqDl).execute().use { resp ->
                if (resp.isSuccessful) dlHtml = resp.body?.string() ?: ""
            }
            if (dlHtml.isBlank()) return list

            val mirstatsMatch = Regex("""["\'](/mirstats\.php[^"\']+)["\']""", RegexOption.IGNORE_CASE).find(dlHtml)
            val statsUrl = if (mirstatsMatch != null) {
                "https://www.mirrored.to" + mirstatsMatch.groupValues[1]
            } else {
                val uidMatch = Regex("""/files/([A-Z0-9]+)/""", RegexOption.IGNORE_CASE).find(dlPageUrl)
                    ?: Regex("""/files/([A-Z0-9]+)/""", RegexOption.IGNORE_CASE).find(mirrorUrl)
                val uid = uidMatch?.groupValues?.get(1) ?: ""
                val tmpMatch = Regex("""tmpID\s*=\s*["\']([^"\']+)["\']""", RegexOption.IGNORE_CASE).find(dlHtml)
                val tmpId = tmpMatch?.groupValues?.get(1) ?: ""
                "https://www.mirrored.to/mirstats.php?uid=$uid&tmpID=$tmpId"
            }

            val statsReq = Request.Builder()
                .url(statsUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", dlPageUrl)
                .build()

            var statsHtml = ""
            client.newCall(statsReq).execute().use { resp ->
                if (resp.isSuccessful) statsHtml = resp.body?.string() ?: ""
            }
            if (statsHtml.isBlank()) return list

            val rows = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE).findAll(statsHtml)
            var currentIdx = serverStartIndex

            val hostCandidates = mutableListOf<Triple<String, String, String>>()
            for (row in rows) {
                val rowHtml = row.groupValues[1]
                val altMatch = Regex("""alt=["\']([^"\']+)["\']""", RegexOption.IGNORE_CASE).find(rowHtml)
                val hostName = altMatch?.groupValues?.get(1) ?: ""
                val glMatch = Regex("""href=["\'](/getlink/[^"\']+)["\']""", RegexOption.IGNORE_CASE).find(rowHtml) ?: continue
                val fullGetLink = "https://www.mirrored.to${glMatch.groupValues[1]}"

                when {
                    hostName.contains("fileditch", ignoreCase = true) -> {
                        hostCandidates.add(Triple("fileditch", hostName, fullGetLink))
                    }
                    hostName.contains("gofile", ignoreCase = true) -> {
                        hostCandidates.add(Triple("gofile", hostName, fullGetLink))
                    }
                    hostName.contains("viking", ignoreCase = true) -> {
                        hostCandidates.add(Triple("viking", hostName, fullGetLink))
                    }
                }
            }

            val sortedHosts = hostCandidates.sortedBy {
                when (it.first) {
                    "fileditch" -> 1
                    "gofile" -> 2
                    "viking" -> 3
                    else -> 4
                }
            }

            for ((type, _, fullGetLink) in sortedHosts) {
                val destUrl = fetchGetLinkDestination(fullGetLink, dlPageUrl)
                if (destUrl.isNullOrBlank()) continue

                when (type) {
                    "fileditch" -> {
                        list.add(
                            StreamSource(
                                title = "Server $currentIdx (FileDitch High Speed Direct)",
                                label = "Server $currentIdx (FileDitch High Speed Direct)",
                                mirrorName = "FileDitch CDN",
                                url = destUrl,
                                quality = "1080p",
                                format = "mp4",
                                provider = "animexin",
                                isStreamable = true
                            )
                        )
                        currentIdx++
                    }
                    "gofile" -> {
                        list.add(
                            StreamSource(
                                title = "Server $currentIdx (GoFile Mirror)",
                                label = "Server $currentIdx (GoFile Mirror)",
                                mirrorName = "GoFile CDN",
                                url = destUrl,
                                quality = "1080p",
                                format = "mp4",
                                provider = "animexin",
                                isStreamable = true
                            )
                        )
                        currentIdx++
                    }
                    "viking" -> {
                        list.add(
                            StreamSource(
                                title = "Server $currentIdx (VikingFile Mirror)",
                                label = "Server $currentIdx (VikingFile Mirror)",
                                mirrorName = "VikingFile Cloud",
                                url = destUrl,
                                quality = "1080p",
                                format = "mp4",
                                provider = "animexin",
                                isStreamable = true
                            )
                        )
                        currentIdx++
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun fetchGetLinkDestination(getLinkUrl: String, referer: String): String? {
        return try {
            val req = Request.Builder()
                .url(getLinkUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                val destMatch = Regex("""<a[^>]+href=["\']([^"\']+)["\'][^>]*>(?:<button[^>]*>)?Download""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""<a[^>]+href=["\'](https?://fileditchvids\.st/[^"\']+)["\']""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""<a[^>]+href=["\'](https?://(?:gofile\.io|vikingfile\.com|vik1ngf1le)[^"\']+)["\']""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""href=["\'](https?://(?!www\.mirrored)[^"\'\s]+\.mp4)["\']""", RegexOption.IGNORE_CASE).find(html)
                destMatch?.groupValues?.get(1)
            }
        } catch (_: Exception) {
            null
        }
    }
}
