package com.sumanonline.sumanmovies.data.remote.hdhub4u

import android.util.Base64
import java.util.Base64 as JavaBase64
import com.google.gson.Gson

import com.google.gson.JsonObject

import com.google.gson.JsonParser

import com.sumanonline.sumanmovies.data.models.MediaItem

import com.sumanonline.sumanmovies.data.models.MediaType

import com.sumanonline.sumanmovies.data.models.StreamMirror

import com.sumanonline.sumanmovies.data.models.StreamSource

import com.sumanonline.sumanmovies.data.models.SubtitleTrack

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext

import okhttp3.OkHttpClient

import okhttp3.Request

import java.net.URLDecoder

import java.net.URLEncoder

import java.nio.charset.StandardCharsets

import java.util.concurrent.TimeUnit

import java.util.regex.Pattern



class HDHub4uMobileClient {

    private val client = OkHttpClient.Builder()

        .connectTimeout(15, TimeUnit.SECONDS)

        .readTimeout(20, TimeUnit.SECONDS)

        .followRedirects(true)

        .followSslRedirects(true)

        .build()



    private val gson = Gson()

    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val primaryBaseUrl = "https://new15.hdhub4u.cl"

    private val searchApiBase = "https://search.pingora.fyi/collections/post/documents/search"



    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {

        val trimmed = query.trim()

        if (trimmed.isBlank()) return@withContext emptyList()



        val apiItems = searchViaPingoraApi(trimmed)

        if (apiItems.isNotEmpty()) {

            return@withContext apiItems

        }



        searchViaWebScrape(trimmed)

    }



    private fun searchViaPingoraApi(query: String): List<MediaItem> {

        try {

            val encodedQuery = URLEncoder.encode(query, "UTF-8")

            val targetUrl = "$searchApiBase?q=$encodedQuery&query_by=post_title,category,stars,director,imdb_id&query_by_weights=4,2,2,2,4&sort_by=sort_by_date:desc&limit=20&page=1"



            val request = Request.Builder()

                .url(targetUrl)

                .header("User-Agent", mobileUserAgent)

                .header("Accept", "application/json")

                .header("Origin", primaryBaseUrl)

                .header("Referer", "$primaryBaseUrl/")

                .build()



            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return emptyList()



            val text = response.body?.string() ?: return emptyList()

            val json = gson.fromJson(text, JsonObject::class.java)

            val hits = json.getAsJsonArray("hits") ?: return emptyList()



            val items = mutableListOf<MediaItem>()

            val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")



            for (element in hits) {

                val hitObj = element.asJsonObject

                val doc = hitObj.getAsJsonObject("document") ?: continue

                val postTitle = doc.get("post_title")?.asString ?: continue

                val permalink = doc.get("permalink")?.asString ?: ""

                val thumbnail = doc.get("post_thumbnail")?.asString

                val categories = doc.getAsJsonArray("category")?.map { it.asString } ?: emptyList()



                val isSeries = categories.any { cat ->

                    cat.contains("Series", ignoreCase = true) || cat.contains("TV", ignoreCase = true)

                } || postTitle.contains("Season", ignoreCase = true) || postTitle.contains("Episode", ignoreCase = true)



                val isAnime = categories.any { cat ->

                    cat.contains("Anime", ignoreCase = true) || cat.contains("Animated", ignoreCase = true)

                } || postTitle.contains("Anime", ignoreCase = true)



                val mediaType = when {

                    isAnime -> MediaType.ANIME

                    isSeries -> MediaType.SERIES

                    else -> MediaType.MOVIE

                }



                val year = yearRegex.find(postTitle)?.value



                val cleanPermalink = if (permalink.isNotBlank()) permalink else "$primaryBaseUrl/"



                items.add(

                    MediaItem(

                        id = "hdhub4u:$cleanPermalink",

                        title = postTitle,

                        mediaType = mediaType,

                        year = year,

                        posterUrl = thumbnail,

                        provider = "hdhub4u"

                    )

                )

            }

            return items

        } catch (_: Exception) {

            return emptyList()

        }

    }



    private fun searchViaWebScrape(query: String): List<MediaItem> {

        try {

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

            val items = mutableListOf<MediaItem>()



            val cardPattern = Pattern.compile(

                """<li\s+[^>]*class=["'][^"']*(?:thumb|post-item)[^"']*["'][^>]*>([\s\S]*?)</li>""",

                Pattern.CASE_INSENSITIVE

            )

            val matcher = cardPattern.matcher(html)



            val linkPattern = Pattern.compile("""<a\s+[^>]*href=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)

            val titlePattern = Pattern.compile("""<p>([^<]+)</p>|aria-label=["']([^"']+)["']|<h\d[^>]*>([^<]+)</h\d>""", Pattern.CASE_INSENSITIVE)

            val imgPattern = Pattern.compile("""<img[^>]+(?:src|data-src)=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)

            val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")



            while (matcher.find()) {

                val cardHtml = matcher.group(1) ?: continue



                val linkMatch = linkPattern.matcher(cardHtml)

                val rawHref = if (linkMatch.find()) linkMatch.group(1) ?: "" else ""

                if (rawHref.isBlank()) continue



                val titleMatch = titlePattern.matcher(cardHtml)

                var title = ""

                if (titleMatch.find()) {

                    title = titleMatch.group(1) ?: titleMatch.group(2) ?: titleMatch.group(3) ?: ""

                }

                title = title.trim()

                if (title.isBlank()) continue



                val imgMatch = imgPattern.matcher(cardHtml)

                val posterUrl = if (imgMatch.find()) imgMatch.group(1)?.trim() else null



                val isSeries = rawHref.contains("series", ignoreCase = true) || title.contains("Season", ignoreCase = true)

                val isAnime = title.contains("Anime", ignoreCase = true) || rawHref.contains("anime", ignoreCase = true)

                val year = yearRegex.find(title)?.value



                val fullUrl = if (rawHref.startsWith("http")) rawHref else "$primaryBaseUrl/${rawHref.removePrefix("/")}"



                items.add(

                    MediaItem(

                        id = "hdhub4u:$fullUrl",

                        title = title,

                        mediaType = if (isAnime) MediaType.ANIME else if (isSeries) MediaType.SERIES else MediaType.MOVIE,

                        year = year,

                        posterUrl = posterUrl,

                        provider = "hdhub4u"

                    )

                )

            }

            return items

        } catch (_: Exception) {

            return emptyList()

        }

    }



    suspend fun resolveStreams(

        permalinkOrSlug: String,

        season: Int = 1,

        episode: Int = 1

    ): Pair<List<StreamSource>, List<SubtitleTrack>> = withContext(Dispatchers.IO) {

        val clean = permalinkOrSlug.removePrefix("hdhub4u:")

        val targetSlug = if (clean.startsWith("http")) {

            val urlParts = clean.split("/")

            urlParts.filter { it.isNotBlank() }.lastOrNull() ?: clean

        } else {

            clean.trim().removePrefix("/").removeSuffix("/")

        }



        val detailUrl = "$primaryBaseUrl/$targetSlug/"



        try {

            val request = Request.Builder()

                .url(detailUrl)

                .header("User-Agent", mobileUserAgent)

                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

                .header("Referer", "$primaryBaseUrl/")

                .build()



            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext Pair(emptyList(), emptyList())



            val html = response.body?.string() ?: return@withContext Pair(emptyList(), emptyList())

            val streams = extractStreamsFromDetail(html, detailUrl, season, episode)

            Pair(streams, emptyList())

        } catch (_: Exception) {

            Pair(emptyList(), emptyList())

        }

    }



    private suspend fun extractStreamsFromDetail(

        html: String,

        pageUrl: String,

        season: Int,

        episode: Int

    ): List<StreamSource> {

        val streams = mutableListOf<StreamSource>()

        val seenUrls = mutableSetOf<String>()



        val episodeCandidateLinks = mutableListOf<Pair<String, String>>()

        if (season > 0 && episode > 0) {

            val pattern = Pattern.compile("""\b(?:EPiSODE|Episode)[\s.-]*0?(\d+)\b""", Pattern.CASE_INSENSITIVE)

            val epMatches = mutableListOf<Triple<Int, Int, Int>>()

            val matcherEp = pattern.matcher(html)

            while (matcherEp.find()) {

                val num = matcherEp.group(1)?.toIntOrNull() ?: continue

                epMatches.add(Triple(num, matcherEp.start(), matcherEp.end()))

            }



            for (i in epMatches.indices) {

                val (epNum, startPos, endPos) = epMatches[i]

                if (epNum != episode) continue



                val nextStart = if (i + 1 < epMatches.size) epMatches[i + 1].second else minOf(html.length, endPos + 3000)

                val section = html.substring(endPos, nextStart)



                val backStart = maxOf(0, startPos - 300)

                val backSlice = html.substring(backStart, startPos)

                val aMatcher = Pattern.compile("""<a\s+[^>]*href=["'](https?://[^"']+)["'][^>]*>(?:<[^>]+>)*\s*$""", Pattern.CASE_INSENSITIVE).matcher(backSlice)

                if (aMatcher.find()) {

                    val anchorHref = aMatcher.group(1)

                    if (anchorHref != null && isEligibleLockerLink(anchorHref, "Episode $epNum")) {

                        episodeCandidateLinks.add(Pair(anchorHref, "Episode $epNum"))

                    }

                }



                val lines = section.split(Regex("""(?i)</h[345]>|<hr\s*/?>|<p[^>]*>"""))

                for (line in lines) {

                    if (line.isBlank()) continue

                    val qMatcher = Pattern.compile("""\b(480p|720p|1080p|2160p|4k)\b""", Pattern.CASE_INSENSITIVE).matcher(line)

                    val qStr = if (qMatcher.find()) qMatcher.group(1)?.uppercase() ?: "HD" else "HD"



                    val lineAnchorMatcher = Pattern.compile("""<a\s+[^>]*href=["'](https?://[^"']+)["'][^>]*>([\s\S]*?)</a>""", Pattern.CASE_INSENSITIVE).matcher(line)

                    while (lineAnchorMatcher.find()) {

                        val h = lineAnchorMatcher.group(1) ?: continue

                        val t = lineAnchorMatcher.group(2)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""

                        if (isEligibleLockerLink(h, t)) {

                            val label = "$qStr $t".trim()

                            episodeCandidateLinks.add(Pair(h, label))

                        }

                    }

                }

            }

        }



        val anchorPattern = Pattern.compile(

            """<a\s+[^>]*href=["'](https?://[^"']+)["'][^>]*>([\s\S]*?)</a>""",

            Pattern.CASE_INSENSITIVE

        )

        val matcher = anchorPattern.matcher(html)



        val candidateLinks = mutableListOf<Pair<String, String>>()

        while (matcher.find()) {

            val href = matcher.group(1) ?: continue

            val text = matcher.group(2)?.replace(Regex("<[^>]+>"), " ")?.trim() ?: ""



            if (isEligibleLockerLink(href, text)) {

                candidateLinks.add(Pair(href, text))

            }

        }



        val targetLinks = if (episodeCandidateLinks.isNotEmpty()) {

            episodeCandidateLinks

        } else if (season > 0 && episode > 0 && (html.contains("Episode", ignoreCase = true) || html.contains("Season", ignoreCase = true))) {

            val epMatches = candidateLinks.filter { (_, text) ->

                val epNum = Regex("""\b(?:Ep|Episode|E)[\s.-]*(\d+)\b""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()

                epNum == episode

            }

            if (epMatches.isNotEmpty()) epMatches else candidateLinks

        } else {

            candidateLinks

        }



        for ((lockerUrl, labelText) in targetLinks) {

            val quality = determineQualityFromLabel(labelText)

            val cat = when {

                quality.contains("4K") || quality.contains("2160") -> "2160p / 4K"

                quality.contains("1080") -> "1080p"

                quality.contains("720") -> "720p"

                else -> "480p"

            }



            val mirrorName = when {

                lockerUrl.contains("hubdrive") -> "HubDrive"

                lockerUrl.contains("greenmotors") -> "HubCloud (GreenMotors)"

                lockerUrl.contains("hubcdn") -> "HubCDN Instant"

                lockerUrl.contains("pixeldrain") -> "PixelDrain Direct"

                else -> "HDHub4u Fast Mirror"

            }

            val mirrorList = listOf(StreamMirror(name = mirrorName, url = lockerUrl, isLocker = true))



            val isMkv = labelText.contains(".mkv", ignoreCase = true) || labelText.contains("mkv", ignoreCase = true) || labelText.contains("bluray", ignoreCase = true)

            if (seenUrls.add(lockerUrl)) {

                streams.add(

                    StreamSource(

                        quality = quality,

                        format = if (isMkv) "MKV" else "MP4",

                        url = lockerUrl,

                        headers = mapOf(

                            "User-Agent" to mobileUserAgent,

                            "Referer" to pageUrl

                        ),

                        directFile = true,

                        codec = if (labelText.contains("HEVC", ignoreCase = true) || quality.contains("4K")) "HEVC" else "H.264",

                        label = "HDhub4u $quality ${if (labelText.contains("10Bit", ignoreCase = true)) "10Bit" else ""}".trim(),

                        provider = "hdhub4u",

                        title = labelText.ifBlank { "HDhub4u $quality" },

                        sourceQualityTag = if (labelText.contains("10Bit", ignoreCase = true)) "10Bit HEVC" else "HD",

                        category = cat,

                        isLockerUrl = true,

                        mirrors = mirrorList

                    )

                )

            }

        }



        val firstStream = streams.firstOrNull()

        if (firstStream != null && firstStream.isLockerUrl) {

            try {

                val resolved = resolveLockerUrl(firstStream.url)

                if (resolved != null && resolved.url.isNotBlank()) {

                    val directStream = firstStream.copy(

                        url = resolved.url,

                        headers = resolved.headers,

                        format = resolved.format,

                        codec = resolved.codec,

                        isLockerUrl = false,

                        mirrors = listOf(StreamMirror(name = "Direct CDN", url = resolved.url, isLocker = false)) + firstStream.mirrors

                    )

                    return listOf(directStream) + streams.drop(1)

                }

            } catch (_: Exception) {}

        }



        val directPixelDrainRegex = Regex("""https?://pixeldrain\.com/(?:u|api/file)/([a-zA-Z0-9_-]+)""")

        for (pdMatch in directPixelDrainRegex.findAll(html)) {

            val fileId = pdMatch.groupValues[1]

            val pdUrl = "https://pixeldrain.com/api/file/$fileId"

            if (seenUrls.add(pdUrl)) {

                streams.add(

                    StreamSource(

                        quality = "1080p FHD",

                        format = "MKV",

                        url = pdUrl,

                        headers = mapOf("User-Agent" to mobileUserAgent),

                        directFile = true,

                        codec = "HEVC",

                        label = "HDhub4u PixelDrain Fast CDN (1080p)",

                        provider = "hdhub4u",

                        title = "PixelDrain Fast CDN (1080p)",

                        sourceQualityTag = "1080p",

                        category = "1080p",

                        isLockerUrl = false

                    )

                )

            }

        }



        return streams

    }



    private fun isEligibleLockerLink(href: String, text: String): Boolean {

        if (href.contains("facebook") || href.contains("telegram") || href.contains("twitter") ||

            href.contains("whatsapp") || href.contains("youtube") || href.contains("category/") ||

            href.contains("dmca") || href.contains("privacy-policy")) {

            return false

        }



        val lowerHref = href.lowercase()

        val lowerText = text.lowercase()



        return lowerHref.contains("hubcloud") || lowerHref.contains("hubdrive") ||

                lowerHref.contains("hubcdn") || lowerHref.contains("greenmotors") ||

                lowerHref.contains("pixeldrain") || lowerHref.contains("gamerxyt") ||

                lowerText.contains("480p") || lowerText.contains("720p") ||

                lowerText.contains("1080p") || lowerText.contains("2160p") ||

                lowerText.contains("4k") || lowerText.contains("download") ||

                lowerText.contains("watch") || lowerText.contains("instant") ||

                lowerText.contains("drive") || lowerText.contains("episode") ||

                lowerText.contains("ep ")

    }



    private fun determineQualityFromLabel(text: String): String {

        val lower = text.lowercase()

        return when {

            lower.contains("2160p") || lower.contains("4k") -> "4K 2160p HDR"

            lower.contains("1080p") -> if (lower.contains("hevc") || lower.contains("10bit")) "1080p 10Bit HEVC" else "1080p Full HD"

            lower.contains("720p") -> if (lower.contains("hevc") || lower.contains("10bit")) "720p 10Bit HEVC" else "720p HD"

            lower.contains("480p") -> "480p SD"

            else -> "1080p FHD"

        }

    }



    private suspend fun resolveLockerWorkflow(initialUrl: String, referer: String): List<String> = withContext(Dispatchers.IO) {

        val directUrls = mutableListOf<String>()



        try {

            when {

                initialUrl.contains("hubcdn.") -> {

                    val resolved = resolveHubCdnFile(initialUrl, referer)

                    if (resolved != null) directUrls.add(resolved)

                }



                initialUrl.contains("hubdrive.") -> {

                    val hubCloudLinks = resolveHubDrivePage(initialUrl, referer)

                    for (hc in hubCloudLinks) {

                        directUrls.addAll(resolveHubCloudDrive(hc, initialUrl))

                    }

                }



                initialUrl.contains("hubcloud.") -> {

                    directUrls.addAll(resolveHubCloudDrive(initialUrl, referer))

                }



                initialUrl.contains("gamerxyt.com") || initialUrl.contains("hubcloud.php") -> {

                    directUrls.addAll(resolveGamerXyt(initialUrl, referer))

                }



                initialUrl.contains("greenmotors.") || initialUrl.contains("greenmountmotors.") -> {

                    val nextLinks = resolveGreenMotors(initialUrl, referer)

                    for (next in nextLinks) {

                        directUrls.addAll(resolveLockerWorkflow(next, initialUrl))

                    }

                }



                initialUrl.contains("hblinks.") -> {

                    val nextLinks = resolveHblinks(initialUrl, referer)

                    for (next in nextLinks) {

                        directUrls.addAll(resolveLockerWorkflow(next, initialUrl))

                    }

                }



                initialUrl.contains("pixel.hubcloud.") -> {

                    val direct = resolvePixelHubCloud(initialUrl, referer)

                    if (direct != null) directUrls.add(direct)

                }



                initialUrl.contains("pixeldrain.com") -> {

                    val fileId = initialUrl.substringAfterLast("/")

                    if (fileId.isNotBlank()) {

                        directUrls.add("https://pixeldrain.com/api/file/$fileId?download")

                    }

                }

            }

        } catch (_: Exception) {}



        directUrls

    }



    private fun resolvePixelHubCloud(pixelUrl: String, referer: String): String? {

        try {

            val req = Request.Builder()

                .url(pixelUrl)

                .header("User-Agent", mobileUserAgent)

                .header("Referer", referer)

                .build()

            val resp = client.newCall(req).execute()

            val finalUrl = resp.request.url.toString()

            val linkParam = Regex("""[?&]link=([^&]+)""").find(finalUrl)?.groupValues?.get(1)

            if (!linkParam.isNullOrBlank()) {

                return URLDecoder.decode(linkParam, "UTF-8")

            }

            if (finalUrl.contains(".mkv", ignoreCase = true) || finalUrl.contains(".mp4", ignoreCase = true) || finalUrl.contains("googleusercontent.com")) {

                return finalUrl

            }

        } catch (_: Exception) {}

        return null

    }



    private fun resolveHblinks(hblinksUrl: String, referer: String): List<String> {

        val targets = mutableListOf<String>()

        try {

            val req = Request.Builder()

                .url(hblinksUrl)

                .header("User-Agent", mobileUserAgent)

                .header("Referer", referer)

                .build()

            val resp = client.newCall(req).execute()

            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()

            val anchors = Regex("""href=["'](https?://[^"'\s]+)["']""").findAll(html)

            for (m in anchors) {

                val href = m.groupValues[1]

                if (href.contains("hubdrive.") || href.contains("hubcloud.") || href.contains("hubcdn.")) {

                    targets.add(href)

                }

            }

        } catch (_: Exception) {}

        return targets

    }



        private fun safeBase64Decode(str: String): ByteArray {
        val clean = str.trim().replace("\n", "").replace("\r", "")
        val padLen = (4 - (clean.length % 4)) % 4
        val padded = if (padLen in 1..3) clean + "=".repeat(padLen) else clean
        return try {
            JavaBase64.getDecoder().decode(padded)
        } catch (_: Exception) {
            try {
                JavaBase64.getUrlDecoder().decode(padded)
            } catch (_: Exception) {
                Base64.decode(padded, Base64.DEFAULT)
            }
        }
    }

    private fun safeBase64DecodeToString(str: String): String {
        return try {
            String(safeBase64Decode(str), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun resolveHubCdnFile(fileUrl: String, referer: String): String? {
        try {
            val req = Request.Builder()
                .url(fileUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null

            val html = resp.body?.string() ?: return null

            val rMatch = Regex("""[?&]r=([a-zA-Z0-9+/=_-]{20,})""").find(html)
                ?: Regex("""[?&]r=([a-zA-Z0-9+/=_-]{20,})""").find(fileUrl)
            if (rMatch != null) {
                val b64 = rMatch.groupValues[1]
                val decoded = safeBase64DecodeToString(b64)
                val linkParam = Regex("""[?&]link=([^&"'\s]+)""").find(decoded)?.groupValues?.get(1)
                if (linkParam != null) {
                    return URLDecoder.decode(linkParam, "UTF-8").replace("&amp;", "&")
                }
                if (decoded.startsWith("http")) return decoded.replace("&amp;", "&")
            }

            val r2Match = Regex("""href=["'](https?://[^"']*(?:\.r2\.dev|\.r2\.cloudflarestorage\.com)[^"']*)["']""").find(html)
            if (r2Match != null) {
                return r2Match.groupValues[1].replace("&amp;", "&")
            }
        } catch (_: Exception) {}
        return null
    }

    private fun resolveHubDrivePage(driveUrl: String, referer: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val req = Request.Builder()
                .url(driveUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()
            val hubCloudMatch = Regex("""href=["'](https?://[^"'\s]*hubcloud\.[^"'\s]+/drive/[^"'\s]*)["']""").find(html)
                ?: Regex("""href=["'](https?://[^"'\s]*hubcloud\.[^"'\s]+/[^"'\s]*)["']""").find(html)
            if (hubCloudMatch != null) {
                results.add(hubCloudMatch.groupValues[1])
            }
        } catch (_: Exception) {}
        return results
    }

    private fun resolveHubCloudDrive(hubCloudUrl: String, referer: String): List<String> {
        val direct = mutableListOf<String>()
        try {
            val req = Request.Builder()
                .url(hubCloudUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()
            val gamerMatch = Regex("""href=["'](https?://[^"'\s]*(?:gamerxyt|hubcloud\.php)[^"'\s]*)["']""").find(html)
            if (gamerMatch != null) {
                direct.addAll(resolveGamerXyt(gamerMatch.groupValues[1], hubCloudUrl))
            }

            val pdMatches = Regex("""https?://pixeldrain\.(?:com|dev)/(?:u|api/file)/([a-zA-Z0-9_-]+)""").findAll(html)
            for (m in pdMatches) {
                direct.add("https://pixeldrain.com/api/file/${m.groupValues[1]}?download")
            }
        } catch (_: Exception) {}
        return direct
    }

    private fun resolveGamerXyt(gamerUrl: String, referer: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val req = Request.Builder()
                .url(gamerUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()

            val r2Matches = Regex("""href=["'](https?://[^"'\s]*(?:\.r2\.cloudflarestorage\.com|\.r2\.dev)/[^"'\s]+)["']""").findAll(html)
            for (r in r2Matches) {
                results.add(r.groupValues[1].replace("&amp;", "&"))
            }

            val hbPlayMatches = Regex("""href=["']https?://[^"']*hbplay[^"']*\?u=([a-zA-Z0-9+/=_-]+)["']""").findAll(html)
            for (hb in hbPlayMatches) {
                val decoded = safeBase64DecodeToString(hb.groupValues[1]).replace("&amp;", "&")
                if (decoded.startsWith("http")) {
                    results.add(decoded)
                }
            }

            val workersMatches = Regex("""href=["'](https?://[^"'\s]+\.workers\.dev/[^"'\s]+)["']""").findAll(html)
            for (w in workersMatches) {
                results.add(w.groupValues[1].replace("&amp;", "&"))
            }

            val pixelMatches = Regex("""href=["'](https?://pixel\.hubcloud\.[^"'\s]*/\?id=[^"'\s]+)["']""").findAll(html)
            for (p in pixelMatches) {
                val direct = resolvePixelHubCloud(p.groupValues[1], gamerUrl)
                if (direct != null) results.add(direct.replace("&amp;", "&"))
            }

            val gpdlMatches = Regex("""href=["'](https?://gpdl\.hubcloud\.[^"'\s]*)["']""").findAll(html)
            for (gp in gpdlMatches) {
                results.add(gp.groupValues[1].replace("&amp;", "&"))
            }

            val pdMatches = Regex("""https?://pixeldrain\.(?:com|dev)/u/([a-zA-Z0-9_-]+)""").findAll(html)
            for (m in pdMatches) {
                results.add("https://pixeldrain.com/api/file/${m.groupValues[1]}?download")
            }

            val fastMatch = Regex("""href=["'](https?://fuckingfast\.(?:net|co)/[^"'\s]+)["']""").find(html)
            if (fastMatch != null) {
                results.add(fastMatch.groupValues[1])
            }
        } catch (_: Exception) {}
        return results
    }

    private fun rot13(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            when (c) {
                in 'a'..'m', in 'A'..'M' -> sb.append((c.code + 13).toChar())
                in 'n'..'z', in 'N'..'Z' -> sb.append((c.code - 13).toChar())
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun extractGreenMotorsPayload(html: String): String? {
        val needle = "s("
        var searchIdx = 0
        while (true) {
            val pos = html.indexOf(needle, searchIdx)
            if (pos < 0) break
            val absPos = pos + needle.length
            val rest = html.substring(absPos).trimStart()
            val afterKey = when {
                rest.startsWith("'o'") -> rest.removePrefix("'o'")
                rest.startsWith("\"o\"") -> rest.removePrefix("\"o\"")
                else -> {
                    searchIdx = absPos
                    continue
                }
            }
            val trimmedAfterKey = afterKey.trimStart()
            if (!trimmedAfterKey.startsWith(",")) {
                searchIdx = absPos
                continue
            }
            val trimmedAfterComma = trimmedAfterKey.removePrefix(",").trimStart()
            val quote = trimmedAfterComma.firstOrNull()
            if (quote != '\'' && quote != '"') {
                searchIdx = absPos
                continue
            }
            val payloadSlice = trimmedAfterComma.substring(1)
            val end = payloadSlice.indexOf(quote)
            if (end >= 0) {
                return payloadSlice.substring(0, end)
            }
            searchIdx = absPos
        }
        return null
    }

    private fun decodeGreenMotorsPayload(payload: String): String? {
        return try {
            val step1Str = safeBase64DecodeToString(payload)
            val step2Str = safeBase64DecodeToString(step1Str)
            val step3Rot = rot13(step2Str)
            val step4Str = safeBase64DecodeToString(step3Rot)
            val json = JsonParser.parseString(step4Str).asJsonObject
            val targetB64 = json.get("o")?.asString ?: return null
            safeBase64DecodeToString(targetB64)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveGreenMotors(gmUrl: String, referer: String): List<String> {
        val nextUrls = mutableListOf<String>()
        try {
            val req = Request.Builder()
                .url(gmUrl)
                .header("User-Agent", mobileUserAgent)
                .header("Referer", referer)
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val html = resp.body?.string() ?: return emptyList()
            val payload = extractGreenMotorsPayload(html)
            val decodedUrl = payload?.let { decodeGreenMotorsPayload(it) }
            if (!decodedUrl.isNullOrBlank() && decodedUrl.startsWith("http")) {
                nextUrls.add(decodedUrl)
            }

            val hubcloudMatch = Regex("""href=["'](https?://[^"']*(?:hubcloud|hubdrive)[^"']*)["']""").find(html)
            if (hubcloudMatch != null) {
                nextUrls.add(hubcloudMatch.groupValues[1])
            }
        } catch (_: Exception) {}
        return nextUrls
    }

    suspend fun resolveLockerUrl(url: String): StreamSource? = withContext(Dispatchers.IO) {
        val directUrls = resolveLockerWorkflow(url, primaryBaseUrl)
        val firstDirect = directUrls.firstOrNull {
            (it.startsWith("http://") || it.startsWith("https://")) &&
                    !it.contains("greenmotors") &&
                    !it.contains("greenmountmotors") &&
                    !it.contains("hubcdn") &&
                    !it.contains("hubcloud") &&
                    !it.contains("hubdrive") &&
                    !it.contains("gamerxyt")
        } ?: return@withContext null

        val isMkv = firstDirect.contains(".mkv", ignoreCase = true)
        val quality = determineQualityFromLabel(url + " " + firstDirect)
        StreamSource(
            quality = quality,
            format = if (isMkv) "MKV" else "MP4",
            url = firstDirect.replace("&amp;", "&"),
            headers = mapOf(
                "User-Agent" to mobileUserAgent,
                "Referer" to primaryBaseUrl
            ),
            directFile = true,
            codec = if (quality.contains("4K") || firstDirect.contains("hevc", ignoreCase = true)) "HEVC" else "H.264",
            label = "HDhub4u Direct $quality",
            provider = "hdhub4u",
            isLockerUrl = false
        )
    }
}
