package com.sumanonline.sumanmovies.data.remote.moviesleech

import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.MoviesLeechGenre
import com.sumanonline.sumanmovies.data.models.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MoviesLeechMobileClient {
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

    private val baseUrl = "https://moviesleech.help"
    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    suspend fun getCatalog(categoryPath: String? = null, page: Int = 1): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val clean = categoryPath?.trim()?.trimStart('/')?.trimEnd('/')
        val targetUrl = when {
            clean.isNullOrBlank() || clean == "all" -> {
                if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
            }
            clean.startsWith("http://") || clean.startsWith("https://") -> {
                val base = clean.trimEnd('/')
                if (page == 1) "$base/" else "$base/page/$page/"
            }
            else -> {
                if (page == 1) "$baseUrl/$clean/" else "$baseUrl/$clean/page/$page/"
            }
        }
        val items = parseListingPage(targetUrl)
        Pair(items, items.size)
    }

    private var cachedGenres: List<MoviesLeechGenre>? = null

    private val defaultGenres = listOf(
        MoviesLeechGenre("Action", "movies-by-genre/action"),
        MoviesLeechGenre("Action Series", "tv-shows-by-genre/action-series"),
        MoviesLeechGenre("Adult Comedy", "movies-by-genre/adult-comedy"),
        MoviesLeechGenre("Adventure", "movies-by-genre/adventure"),
        MoviesLeechGenre("Biography", "movies-by-genre/biography"),
        MoviesLeechGenre("Comedy", "movies-by-genre/comedy"),
        MoviesLeechGenre("Comedy Series", "tv-shows-by-genre/comedy-series"),
        MoviesLeechGenre("Crime Series", "tv-shows-by-genre/crime-series"),
        MoviesLeechGenre("Dance", "movies-by-genre/dance"),
        MoviesLeechGenre("Drama", "movies-by-genre/drama"),
        MoviesLeechGenre("Drama Series", "tv-shows-by-genre/drama-series"),
        MoviesLeechGenre("Family", "movies-by-genre/family"),
        MoviesLeechGenre("Horror", "movies-by-genre/horror"),
        MoviesLeechGenre("Horror Series", "tv-shows-by-genre/horror-series"),
        MoviesLeechGenre("Mystery", "movies-by-genre/mystery"),
        MoviesLeechGenre("Mystery Series", "tv-shows-by-genre/mystery-series"),
        MoviesLeechGenre("Romance", "movies-by-genre/romance"),
        MoviesLeechGenre("Romance Series", "tv-shows-by-genre/romance-series"),
        MoviesLeechGenre("Thriller", "movies-by-genre/thriller"),
        MoviesLeechGenre("Thriller Series", "tv-shows-by-genre/thriller-series")
    )

    suspend fun getGenres(): List<MoviesLeechGenre> = withContext(Dispatchers.IO) {
        cachedGenres?.let { return@withContext it }
        try {
            val request = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext defaultGenres
                val html = response.body?.string() ?: return@withContext defaultGenres
                val pattern = Pattern.compile("<a[^>]+href=[\"'](?:https?://moviesleech\\.help/)?((?:movies-by-genre|tv-shows-by-genre)/[^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(html)
                val list = mutableListOf<MoviesLeechGenre>()
                val seen = mutableSetOf<String>()
                while (matcher.find()) {
                    val path = matcher.group(1)?.trim('/') ?: continue
                    val text = matcher.group(2)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: continue
                    if (path.isNotBlank() && text.isNotBlank() && !seen.contains(path)) {
                        seen.add(path)
                        list.add(MoviesLeechGenre(text, path))
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
        val searchPathUrl = if (page == 1) "$baseUrl/search/$encoded/" else "$baseUrl/search/$encoded/page/$page/"
        val results = parseListingPage(searchPathUrl)
        if (results.isNotEmpty()) {
            results
        } else {
            val fallbackUrl = if (page == 1) "$baseUrl/?s=$encoded" else "$baseUrl/page/$page/?s=$encoded"
            parseListingPage(fallbackUrl)
        }
    }

    private fun parseListingPage(url: String): List<MediaItem> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body?.string() ?: return emptyList()
                val articlePattern = Pattern.compile("<article[^>]*>([\\s\\S]*?)</article>")
                val matcher = articlePattern.matcher(html)
                val items = mutableListOf<MediaItem>()

                while (matcher.find()) {
                    val articleHtml = matcher.group(1) ?: continue
                    val item = parseArticle(articleHtml)
                    if (item != null) {
                        items.add(item)
                    }
                }
                items.distinctBy { it.id }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseArticle(articleHtml: String): MediaItem? {
        val linkMatch = Regex("""<a[^>]+href="([^"]+)"""").find(articleHtml) ?: return null
        val postUrl = linkMatch.groupValues[1]
        if (postUrl.isBlank() || postUrl == baseUrl) return null

        val titleMatch = Regex("""title="([^"]+)"""").find(articleHtml)
            ?: Regex("""<h[23][^>]*><a[^>]+>([\s\S]*?)</a>""").find(articleHtml)
        val rawTitle = titleMatch?.groupValues?.get(1)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: return null

        val imgMatch = Regex("""<img[^>]+(?:src|data-src)="([^"]+)"""").find(articleHtml)
        val posterUrl = imgMatch?.groupValues?.get(1)

        val cleanTitle = rawTitle
            .replace(Regex("""^Download\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\|\|.*$"""), "")
            .replace(Regex("""\{.*?\}"""), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\b(480p|720p|1080p|4k|uhd|web-dl|hdtc|camrip|bluray|v\d+)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[–—-]"""), " ")
            .trim()

        val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.value

        val isSeries = rawTitle.contains("Season", true) || rawTitle.contains("Series", true) ||
                postUrl.contains("season", true) || postUrl.contains("series", true)

        val effectiveId = "moviesleech:${URLEncoder.encode(postUrl, "UTF-8")}"

        return MediaItem(
            id = effectiveId,
            title = cleanTitle.ifBlank { rawTitle },
            mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
            year = year,
            posterUrl = posterUrl,
            backdropUrl = posterUrl,
            overview = "MoviesLeech • $rawTitle",
            genres = listOf("Indian Cinema", if (isSeries) "Series" else "Movie"),
            provider = "moviesleech"
        )
    }

    suspend fun resolveStream(
        targetUrlOrId: String,
        season: Int = 1,
        episode: Int = 1
    ): List<StreamSource> = withContext(Dispatchers.IO) {
        try {
            val postUrl = if (targetUrlOrId.startsWith("moviesleech:")) {
                java.net.URLDecoder.decode(targetUrlOrId.removePrefix("moviesleech:"), "UTF-8")
            } else {
                targetUrlOrId
            }

            val postReq = Request.Builder()
                .url(postUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", baseUrl)
                .build()

            var postHtml = ""
            client.newCall(postReq).execute().use { resp ->
                if (resp.isSuccessful) postHtml = resp.body?.string() ?: ""
            }
            if (postHtml.isBlank()) return@withContext emptyList()

            val anchorArchiveRegex = Regex("""<a[^>]+href="(https?://[^"]*leechpro\.blog/archives/\d+)"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
            val rawArchiveLinks = mutableListOf<Triple<String, String, Boolean>>()
            for (m in anchorArchiveRegex.findAll(postHtml)) {
                val url = m.groupValues[1]
                val anchorFull = m.value.lowercase()
                val isBatch = anchorFull.contains("batch") || anchorFull.contains("zip")
                val start = (m.range.first - 250).coerceAtLeast(0)
                val before = postHtml.substring(start, m.range.first).lowercase()
                val q = when {
                    before.contains("4k") || before.contains("2160p") -> "4K"
                    before.contains("1080p") -> "1080p"
                    before.contains("720p") -> "720p"
                    before.contains("480p") -> "480p"
                    else -> "1080p"
                }
                rawArchiveLinks.add(Triple(url, q, isBatch))
            }

            if (rawArchiveLinks.isEmpty()) {
                val plainArchive = Regex("""href="(https?://[^"]*leechpro\.blog/archives/\d+)"""")
                for (m in plainArchive.findAll(postHtml)) {
                    val url = m.groupValues[1]
                    rawArchiveLinks.add(Triple(url, "1080p", false))
                }
            }

            if (rawArchiveLinks.isEmpty()) return@withContext emptyList()

            val nonBatchArchives = rawArchiveLinks.filterNot { it.third }.ifEmpty { rawArchiveLinks }.distinctBy { it.first }

            val sortedArchives = nonBatchArchives.sortedBy { arc ->
                when (arc.second) {
                    "1080p" -> 1
                    "720p" -> 2
                    "480p" -> 3
                    "4K" -> 4
                    else -> 5
                }
            }

            val streams = mutableListOf<StreamSource>()
            for (candidate in sortedArchives) {
                val candidateArchive = candidate.first
                val detectedQuality = candidate.second.ifBlank { "1080p" }

                val arcReq = Request.Builder()
                    .url(candidateArchive)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", postUrl)
                    .build()

                var arcHtml = ""
                client.newCall(arcReq).execute().use { resp ->
                    if (resp.isSuccessful) arcHtml = resp.body?.string() ?: ""
                }
                if (arcHtml.isBlank()) continue

                val episodeLinks = mutableListOf<Pair<String, String>>()
                val linkRegex = Regex("""<a[^>]+href="([^"]+)"[^>]*>([\s\S]*?)</a>""")
                for (m in linkRegex.findAll(arcHtml)) {
                    val h = m.groupValues[1]
                    val t = m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
                    if (h.contains("thenaukriadda.in") || h.contains("sid=")) {
                        episodeLinks.add(Pair(t, h))
                    }
                }

                if (episodeLinks.isEmpty()) continue

                val epRegex = Regex("""\b(?:episode|ep|e)\s*0*$episode\b""", RegexOption.IGNORE_CASE)
                val targetEpLink = episodeLinks.firstOrNull { epRegex.containsMatchIn(it.first) }?.second
                    ?: episodeLinks.firstOrNull { it.first.contains("Fast Server", ignoreCase = true) }?.second
                    ?: episodeLinks.first().second

                val sid = Regex("""sid=([^&"'\s]+)""").find(targetEpLink)?.groupValues?.get(1) ?: continue
                val (cloudUrl, instantUrl, resumeUrl) = executeLinkPilotChain(sid, candidateArchive)

                if (!cloudUrl.isNullOrBlank()) {
                    streams.add(
                        StreamSource(
                            title = "Server 1 (Cloud Fast Stream)",
                            label = "Server 1 (Cloud Fast Stream)",
                            mirrorName = "Server 1 (Cloud Fast Stream)",
                            url = cloudUrl,
                            quality = detectedQuality,
                            format = "mkv",
                            provider = "moviesleech",
                            isStreamable = true
                        )
                    )
                }

                if (!instantUrl.isNullOrBlank()) {
                    val title = if (streams.isEmpty()) "Server 1 (Instant Stream)" else "Server 2 (Instant Stream)"
                    streams.add(
                        StreamSource(
                            title = title,
                            label = title,
                            mirrorName = title,
                            url = instantUrl,
                            quality = detectedQuality,
                            format = "mkv",
                            provider = "moviesleech",
                            isStreamable = true
                        )
                    )
                }

                if (!resumeUrl.isNullOrBlank()) {
                    val isResumeZip = resumeUrl.contains(".zip", ignoreCase = true) || resumeUrl.contains(".rar", ignoreCase = true)
                    val srvNum = streams.size + 1
                    val title = "Server $srvNum (Resume Cloud)"
                    streams.add(
                        StreamSource(
                            title = title,
                            label = title,
                            mirrorName = title,
                            url = resumeUrl,
                            quality = detectedQuality,
                            format = if (isResumeZip) "ZIP" else "mkv",
                            provider = "moviesleech",
                            isStreamable = !isResumeZip
                        )
                    )
                }

                if (streams.isNotEmpty()) break
            }

            streams
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun executeLinkPilotChain(sid: String, referer: String): Triple<String?, String?, String?> {
        return try {
            val step1Body = FormBody.Builder()
                .add("_lp_http", sid)
                .build()

            val step1Req = Request.Builder()
                .url("https://en.thenaukriadda.in/")
                .post(step1Body)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .header("Origin", "https://en.thenaukriadda.in")
                .build()

            var curHtml = ""
            client.newCall(step1Req).execute().use { resp ->
                if (resp.isSuccessful) curHtml = resp.body?.string() ?: ""
            }
            if (curHtml.isBlank()) return Triple(null, null, null)

            var curUrl = "https://en.thenaukriadda.in/"
            for (hop in 0 until 3) {
                val cMatch = Regex("""sc\(["'](lp-[^"']+)["'],\s*["']([^"']+)["'](?:,\s*[^)]+)?\)""").find(curHtml)
                val gMatch = Regex("""[?&]lp_go=[a-zA-Z0-9_-]+""").find(curHtml)
                if (cMatch != null && gMatch != null) break

                val formMatches = Regex("""<form[^>]+action="([^"]+)"[\s\S]*?</form>""").findAll(curHtml)
                val formMatch = formMatches.firstOrNull { it.value.contains("_lp_") } ?: formMatches.firstOrNull() ?: break
                val actRaw = formMatch.groupValues[1]
                val act = if (actRaw.startsWith("http")) actRaw else "https://en.thenaukriadda.in$actRaw"
                val inputs = Regex("""<input[^>]+name="([^"]+)"[^>]*value="([^"]*)"""").findAll(formMatch.value)
                val body = FormBody.Builder()
                inputs.forEach { body.add(it.groupValues[1], it.groupValues[2]) }
                val hopReq = Request.Builder()
                    .url(act)
                    .post(body.build())
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", curUrl)
                    .header("Origin", "https://en.thenaukriadda.in")
                    .build()

                client.newCall(hopReq).execute().use { resp ->
                    curHtml = if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
                curUrl = act
                if (curHtml.isBlank()) break
            }

            val cookieMatch = Regex("""sc\(["'](lp-[^"']+)["'],\s*["']([^"']+)["'](?:,\s*[^)]+)?\)""").find(curHtml) ?: return Triple(null, null, null)
            val lpGoId = Regex("""[?&]lp_go=([a-zA-Z0-9_-]+)""").find(curHtml)?.groupValues?.get(1) ?: return Triple(null, null, null)

            val cName = cookieMatch.groupValues[1]
            val cVal = cookieMatch.groupValues[2].replace("\\/", "/")
            val goUrl = "https://en.thenaukriadda.in/?lp_go=$lpGoId"

            val cookie = Cookie.Builder()
                .name(cName)
                .value(cVal)
                .domain("en.thenaukriadda.in")
                .path("/")
                .secure()
                .build()

            val existing = cookieStore.getOrPut("en.thenaukriadda.in") { mutableListOf() }
            existing.removeAll { it.name == cName }
            existing.add(cookie)

            val goReq = Request.Builder()
                .url(goUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", curUrl)
                .build()

            var destHtml = ""
            client.newCall(goReq).execute().use { resp ->
                if (resp.isSuccessful) destHtml = resp.body?.string() ?: ""
            }
            if (destHtml.isBlank()) return Triple(null, null, null)

            val driveSeedRedirect = Regex("""window\.location\.replace\(["'](https?[^"']+)["']\)""").find(destHtml)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: goUrl

            val dsReq = Request.Builder()
                .url(driveSeedRedirect)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", "https://en.thenaukriadda.in/")
                .build()

            var dsHtml = ""
            client.newCall(dsReq).execute().use { resp ->
                if (resp.isSuccessful) dsHtml = resp.body?.string() ?: ""
            }
            if (dsHtml.isBlank()) return Triple(null, null, null)

            val dsFilePathMatch = Regex("""window\.location\.replace\(["'](/file/[^"']+)["']\)""").find(dsHtml)
                ?: Regex("""href="([^"]*/file/[^"]+)"""").find(dsHtml)
            val fileHtml = if (dsFilePathMatch != null) {
                val dsFileUrl = "https://driveseed.org${dsFilePathMatch.groupValues[1]}"
                val fileReq = Request.Builder()
                    .url(dsFileUrl)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", "https://driveseed.org/")
                    .build()
                var fHtml = ""
                client.newCall(fileReq).execute().use { resp ->
                    if (resp.isSuccessful) fHtml = resp.body?.string() ?: ""
                }
                fHtml
            } else {
                dsHtml
            }

            if (fileHtml.isBlank()) return Triple(null, null, null)

            val cloudDirectMatch = Regex("""href="(https?://pub-[^"]+\.r2\.dev/[^"]+\.(?:mkv|mp4))"""").find(fileHtml)?.groupValues?.get(1)
                ?: Regex("""href="(https?://[^"]*(?:r2\.dev|workers\.dev)/[^"]+\.(?:mkv|mp4))"""").find(fileHtml)?.groupValues?.get(1)

            val instantMatches = Regex("""href="(https?://(?:cdn|instant)\.video-gen\.xyz/[^"]+)"""").findAll(fileHtml).map { it.groupValues[1] }.toList()
            val zfileMatch = Regex("""href="([^"]*/zfile/[^"]+)"""").find(fileHtml)?.groupValues?.get(1)

            var instantDirectUrl: String? = null
            for (instantUrl in instantMatches) {
                val direct = resolveInstantUrl(instantUrl)
                if (!direct.isNullOrBlank()) {
                    instantDirectUrl = direct
                    break
                }
            }

            var resumeCloudDirectUrl: String? = null
            if (!zfileMatch.isNullOrBlank()) {
                resumeCloudDirectUrl = resolveResumeCloudUrl(zfileMatch, driveSeedRedirect)
            }

            Triple(cloudDirectMatch, instantDirectUrl, resumeCloudDirectUrl)
        } catch (_: Exception) {
            Triple(null, null, null)
        }
    }

    private fun resolveResumeCloudUrl(zfileMatch: String, refererUrl: String): String? {
        return try {
            val zUrl = if (zfileMatch.startsWith("http")) zfileMatch else "https://driveseed.org$zfileMatch"
            val zReq = Request.Builder()
                .url(zUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", refererUrl)
                .build()

            var zHtml = ""
            client.newCall(zReq).execute().use { resp ->
                if (resp.isSuccessful) zHtml = resp.body?.string() ?: ""
            }
            if (zHtml.isBlank()) return null

            val directWorker = Regex("""href="(https?://[^"]*(?:workers\.dev|r2\.dev)[^"]+)"""").find(zHtml)?.groupValues?.get(1)
            if (!directWorker.isNullOrBlank()) {
                return directWorker
            }

            val key = Regex("""formData\.append\(["']key["'],\s*["']([^"']+)["']\)""").find(zHtml)?.groupValues?.get(1)
            if (!key.isNullOrBlank()) {
                val postBody = FormBody.Builder()
                    .add("action", "cloud")
                    .add("key", key)
                    .add("action_token", "")
                    .build()

                val postReq = Request.Builder()
                    .url(zUrl)
                    .post(postBody)
                    .header("User-Agent", mobileUserAgent)
                    .header("Referer", zUrl)
                    .header("Origin", "https://driveseed.org")
                    .header("x-token", "driveseed.org")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()

                var postJson = ""
                client.newCall(postReq).execute().use { resp ->
                    if (resp.isSuccessful) postJson = resp.body?.string() ?: ""
                }

                val tokenUrl = Regex(""""url"\s*:\s*"([^"]+)"""").find(postJson)?.groupValues?.get(1)?.replace("\\/", "/")
                    ?: Regex(""""visit_url"\s*:\s*"([^"]+)"""").find(postJson)?.groupValues?.get(1)?.replace("\\/", "/")

                if (!tokenUrl.isNullOrBlank()) {
                    val tokenReq = Request.Builder()
                        .url(tokenUrl)
                        .header("User-Agent", mobileUserAgent)
                        .header("Referer", zUrl)
                        .build()

                    var tHtml = ""
                    client.newCall(tokenReq).execute().use { resp ->
                        if (resp.isSuccessful) tHtml = resp.body?.string() ?: ""
                    }

                    val tokenWorker = Regex("""href="(https?://[^"]*(?:workers\.dev|r2\.dev)[^"]+)"""").find(tHtml)?.groupValues?.get(1)
                    if (!tokenWorker.isNullOrBlank()) {
                        return tokenWorker
                    }

                    val downloadMatch = Regex("""<a[^>]+href="([^"]+)"[^>]*>[\s\S]*?(?:Download|Resume|Cloud)[\s\S]*?</a>""", RegexOption.IGNORE_CASE).find(tHtml)?.groupValues?.get(1)
                    if (!downloadMatch.isNullOrBlank() && downloadMatch.startsWith("http")) {
                        return downloadMatch
                    }
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveInstantUrl(instantUrl: String): String? {
        return try {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val req = Request.Builder()
                .url(instantUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", "https://driveseed.org/")
                .build()

            noRedirectClient.newCall(req).execute().use { resp ->
                val loc = resp.header("Location")
                if (!loc.isNullOrBlank()) {
                    val urlParam = Regex("""[?&]url=([^&]+)""").find(loc)?.groupValues?.get(1)
                    if (!urlParam.isNullOrBlank()) {
                        val decoded = java.net.URLDecoder.decode(urlParam, "UTF-8")
                        if (decoded.isNotBlank()) return decoded
                    }
                    if (loc.contains("video-downloads.googleusercontent.com") || loc.contains(".mkv") || loc.contains(".mp4")) {
                        return loc
                    }
                }
            }

            val followReq = Request.Builder()
                .url(instantUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", "https://driveseed.org/")
                .build()

            client.newCall(followReq).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                val urlParam = Regex("""[?&]url=([^&]+)""").find(finalUrl)?.groupValues?.get(1)
                if (!urlParam.isNullOrBlank()) {
                    val decoded = java.net.URLDecoder.decode(urlParam, "UTF-8")
                    if (decoded.isNotBlank()) return decoded
                }
                if (finalUrl.contains("video-downloads.googleusercontent.com") || finalUrl.contains(".mkv") || finalUrl.contains(".mp4")) {
                    return finalUrl
                }
                val body = resp.body?.string() ?: ""
                val bodyUrlParam = Regex("""[?&]url=([^&"'\s<>]+)""").find(body)?.groupValues?.get(1)
                if (!bodyUrlParam.isNullOrBlank()) {
                    val decoded = java.net.URLDecoder.decode(bodyUrlParam, "UTF-8")
                    if (decoded.isNotBlank()) return decoded
                }
                val rawGoogle = Regex("""https?://video-downloads\.googleusercontent\.com/[^\s"\'<>]+""").find(body)?.value
                if (!rawGoogle.isNullOrBlank()) {
                    return rawGoogle
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
